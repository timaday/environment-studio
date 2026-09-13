#define _GNU_SOURCE
#include "privacy-fork.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <stddef.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>
#ifndef SO_PASSPIDFD
#define SO_PASSPIDFD 76
#endif
#ifndef SCM_PIDFD
#define SCM_PIDFD 4
#endif
_Static_assert(ATOMIC_INT_LOCK_FREE==2,"qualified hook requires lock-free unsigned atomics");
static pthread_mutex_t window=PTHREAD_MUTEX_INITIALIZER;
static pthread_once_t handlers=PTHREAD_ONCE_INIT;
static int handler_result;
static uint64_t next_generation;
static __thread es_fork *active __attribute__((tls_model("initial-exec")));
static __thread uint64_t active_generation __attribute__((tls_model("initial-exec")));
static void child_hook(void){
 es_fork *p=active;if(!p)return;
 if(p->generation!=active_generation||atomic_load_explicit(&p->hook_done,memory_order_relaxed))_exit(125);
 if(send(p->send_fd,p->record,24,MSG_DONTWAIT|MSG_NOSIGNAL)!=24)_exit(125);
 if(close(p->send_fd))_exit(125);
 if(close(p->receive_fd))_exit(125);
}
static void parent_hook(void){
 es_fork *p=active;if(!p)return;
 if(p->generation!=active_generation||atomic_load_explicit(&p->hook_done,memory_order_relaxed)){
  atomic_store_explicit(&p->hook_failed,1,memory_order_release);return;
 }
 int fd=p->send_fd;p->send_fd=-1;
 if(fd<0||close(fd))atomic_store_explicit(&p->hook_failed,1,memory_order_release);
 atomic_store_explicit(&p->hook_done,1,memory_order_release);
}
static void install(void){handler_result=pthread_atfork(NULL,parent_hook,child_hook);}
static void wipe(void *v,size_t size){volatile unsigned char *p=v;while(size--)*p++=0;}
static es_fork_result from_peer(es_peer_result r){
 switch(r){case ES_PEER_OK:return ES_FORK_OK;case ES_PEER_INVALID:return ES_FORK_INVALID;
 case ES_PEER_UNSUPPORTED:return ES_FORK_UNSUPPORTED;case ES_PEER_DEAD:return ES_FORK_DEAD;
 case ES_PEER_IDENTITY:return ES_FORK_IDENTITY;case ES_PEER_DEADLINE:return ES_FORK_DEADLINE;
 case ES_PEER_CANCELLED:return ES_FORK_CANCELLED;case ES_PEER_CLEANUP:return ES_FORK_CLEANUP;
 default:return ES_FORK_IO;}
}
static es_fork_result fail(es_fork *p,es_fork_result r){
 if(p->terminal==ES_FORK_OK||r==ES_FORK_CLEANUP)p->terminal=r;
 return p->terminal;
}
static es_fork_result guard(es_fork *p,int *milliseconds){
 if(p->terminal!=ES_FORK_OK)return p->terminal;
 struct pollfd c={.fd=p->cancel_fd,.events=POLLIN};
 if(poll(&c,1,0)<0)return ES_FORK_IO;
 if(c.revents&POLLIN)return ES_FORK_CANCELLED;
 if(c.revents)return ES_FORK_IO;
 struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<0||t.tv_nsec<0||t.tv_nsec>=1000000000L)return ES_FORK_IO;
 if((uint64_t)t.tv_sec>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL)return ES_FORK_IO;
 uint64_t n=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;
 if(n>=p->deadline_ns)return ES_FORK_DEADLINE;
 uint64_t remaining=p->deadline_ns-n;if(remaining>10000000000ULL)return ES_FORK_INVALID;
 if(atomic_load_explicit(&p->hook_failed,memory_order_acquire))return ES_FORK_CLEANUP;
 if(milliseconds)*milliseconds=(int)((remaining+999999ULL)/1000000ULL);
 return ES_FORK_OK;
}
static es_fork_result close_fd(int *slot){int fd=*slot;*slot=-1;return fd>=0&&close(fd)?ES_FORK_CLEANUP:ES_FORK_OK;}
es_fork_result es_fork_arm(es_fork *p,int cancel,uint64_t deadline,const uint8_t id[16]){
 if(!p||p->state||cancel<0||!id||active)return ES_FORK_INVALID;
 if(pthread_mutex_trylock(&window))return ES_FORK_INVALID;
 if(next_generation==UINT64_MAX){pthread_mutex_unlock(&window);return ES_FORK_INVALID;}
 p->state=1;p->generation=++next_generation;p->receive_fd=-1;p->send_fd=-1;p->cancel_fd=cancel;p->deadline_ns=deadline;p->launcher=pthread_self();
 atomic_init(&p->hook_done,0);atomic_init(&p->hook_failed,0);atomic_init(&p->armed,0);
 memcpy(p->record,"ESFORK01",8);memcpy(p->record+8,id,16);
 es_fork_result r=guard(p,NULL);
 if(!r&&(!atomic_is_lock_free(&p->hook_done)||!atomic_is_lock_free(&p->hook_failed)||!atomic_is_lock_free(&p->armed)))r=ES_FORK_UNSUPPORTED;
 /* Default SCM_CREDENTIALS uses real IDs; proc validation uses effective IDs.
    This fixed non-set-id launch requires those exact identities to agree. */
 if(!r&&(getuid()!=geteuid()||getgid()!=getegid()))r=ES_FORK_IDENTITY;
 if(!r&&(pthread_once(&handlers,install)||handler_result))r=ES_FORK_UNSUPPORTED;
 if(!r){int pair[2];if(socketpair(AF_UNIX,SOCK_SEQPACKET|SOCK_CLOEXEC|SOCK_NONBLOCK,0,pair))r=ES_FORK_IO;
  else {p->receive_fd=pair[0];p->send_fd=pair[1];int one=1;
   for(unsigned i=0;i<2&&!r;i++){int option=i?SO_PASSCRED:SO_PASSPIDFD,value=0;socklen_t size=sizeof(value);
    if(setsockopt(pair[0],SOL_SOCKET,option,&one,sizeof(one))||getsockopt(pair[0],SOL_SOCKET,option,&value,&size)||size!=sizeof(value)||value!=1)r=ES_FORK_UNSUPPORTED;
   }
  }
 }
 if(!r){active=p;active_generation=p->generation;atomic_store_explicit(&p->armed,1,memory_order_release);return ES_FORK_OK;}
 if(close_fd(&p->send_fd))r=ES_FORK_CLEANUP;
 if(close_fd(&p->receive_fd))r=ES_FORK_CLEANUP;
 wipe(p->record,sizeof(p->record));p->state=3;p->terminal=r;pthread_mutex_unlock(&window);return r;
}
static es_fork_result packet(es_fork *p,int *pin,struct ucred *credentials,int eof){
 for(;;){int milliseconds=0;es_fork_result r=guard(p,&milliseconds);if(r)return r;
  struct pollfd f[2]={{.fd=p->cancel_fd,.events=POLLIN},{.fd=p->receive_fd,.events=POLLIN}};
  int ready=poll(f,2,milliseconds);if(ready<0){if(errno==EINTR)continue;return ES_FORK_IO;}
  r=guard(p,NULL);if(r)return r;if(!ready)continue;
  if(f[1].revents&(POLLNVAL|POLLERR))return ES_FORK_IO;
  if(!(f[1].revents&(POLLIN|POLLHUP)))continue;
  uint8_t bytes[25]={0};union {struct cmsghdr align;unsigned char data[512];} ancillary;
  memset(&ancillary,0,sizeof(ancillary));struct iovec vector={bytes,sizeof(bytes)};
  struct msghdr message={.msg_iov=&vector,.msg_iovlen=1,.msg_control=ancillary.data,.msg_controllen=sizeof(ancillary.data)};
  ssize_t count=recvmsg(p->receive_fd,&message,MSG_CMSG_CLOEXEC|MSG_DONTWAIT);
  if(count<0){if(errno==EINTR||errno==EAGAIN||errno==EWOULDBLOCK)continue;return ES_FORK_IO;}
  int received[128],fds=0,credential_count=0,pin_count=0,invalid=0;
  if(message.msg_controllen>sizeof(ancillary.data)){message.msg_controllen=sizeof(ancillary.data);invalid=1;}
  for(struct cmsghdr *h=CMSG_FIRSTHDR(&message);h;h=CMSG_NXTHDR(&message,h)){
   size_t available=(size_t)((unsigned char*)message.msg_control+message.msg_controllen-(unsigned char*)h);
   if(h->cmsg_len<CMSG_LEN(0)||h->cmsg_len>available){invalid=1;break;}
   size_t size=h->cmsg_len-CMSG_LEN(0);
   if(h->cmsg_level==SOL_SOCKET&&(h->cmsg_type==SCM_PIDFD||h->cmsg_type==SCM_RIGHTS)){
    if(h->cmsg_type==SCM_RIGHTS||size!=sizeof(int))invalid=1;
    for(size_t at=0;at+sizeof(int)<=size;at+=sizeof(int)){
     int fd;memcpy(&fd,(unsigned char*)CMSG_DATA(h)+at,sizeof(fd));int duplicate=0;
     for(int i=0;i<fds;i++)if(received[i]==fd)duplicate=1;
     if(fd<0||fd==p->cancel_fd||fd==p->receive_fd||duplicate||fds>=128)invalid=1;
     else received[fds++]=fd;
    }
    if(h->cmsg_type==SCM_PIDFD)pin_count++;
   }else if(h->cmsg_level==SOL_SOCKET&&h->cmsg_type==SCM_CREDENTIALS&&size==sizeof(*credentials)){
    memcpy(credentials,CMSG_DATA(h),sizeof(*credentials));credential_count++;
   }else invalid=1;
  }
  int correct=!invalid&&!(message.msg_flags&(MSG_TRUNC|MSG_CTRUNC));
  if(eof)correct=correct&&count==0&&fds==0&&credential_count==0;
  else correct=correct&&count==24&&!memcmp(bytes,p->record,24)&&fds==1&&pin_count==1&&credential_count==1&&credentials->pid>0;
  if(correct&&!eof){*pin=received[0];fds=0;}
  for(int i=0;i<fds;i++)if(close(received[i]))r=ES_FORK_CLEANUP;
  wipe(bytes,sizeof(bytes));wipe(&ancillary,sizeof(ancillary));
  if(r)return r;
  return correct?ES_FORK_OK:ES_FORK_PROTOCOL;
 }
}
es_fork_result es_fork_capture(es_fork *p,es_peer_identity *out){
 if(out)wipe(out,sizeof(*out));
 if(!p||!p->state)return ES_FORK_INVALID;
 if(!out)return fail(p,ES_FORK_INVALID);
 if(p->state!=1)return p->terminal?p->terminal:p->state==3?ES_FORK_INVALID:fail(p,ES_FORK_INVALID);
 int pin=-1;struct ucred credentials={0};es_fork_result r=packet(p,&pin,&credentials,0);
 if(!r){es_peer_identity expected={.pid=(uint32_t)credentials.pid,.uid=(uint32_t)credentials.uid,.gid=(uint32_t)credentials.gid};
  r=from_peer(es_peer_adopt_kernel_pin(&p->peer,&pin,expected,p->cancel_fd,p->deadline_ns));
 }
 if(pin>=0&&close(pin))r=ES_FORK_CLEANUP;
 if(!r){int extra=-1;r=packet(p,&extra,&credentials,1);if(extra>=0&&close(extra))r=ES_FORK_CLEANUP;}
 if(!r)r=from_peer(es_peer_read(&p->peer,out));
 if(r){wipe(out,sizeof(*out));return fail(p,r);}
 p->state=2;return ES_FORK_CAPTURED;
}
es_fork_result es_fork_read(es_fork *p,es_peer_identity *out){
 if(out)wipe(out,sizeof(*out));
 if(!p||!p->state)return ES_FORK_INVALID;
 if(!out)return fail(p,ES_FORK_INVALID);
 if(p->terminal)return p->terminal;
 if(p->state!=2)return p->state==3?ES_FORK_INVALID:fail(p,ES_FORK_INVALID);
 es_fork_result r=guard(p,NULL);if(!r)r=from_peer(es_peer_read(&p->peer,out));
 return r?fail(p,r):ES_FORK_CAPTURED;
}
es_fork_result es_fork_disarm(es_fork *p){
 if(!p||active!=p||!pthread_equal(p->launcher,pthread_self())||active_generation!=p->generation||!atomic_load_explicit(&p->armed,memory_order_acquire))return ES_FORK_INVALID;
 if(close_fd(&p->send_fd))atomic_store_explicit(&p->hook_failed,1,memory_order_release);
 active=NULL;active_generation=0;atomic_store_explicit(&p->armed,0,memory_order_release);
 int failed=atomic_load_explicit(&p->hook_failed,memory_order_acquire);pthread_mutex_unlock(&window);
 return failed?ES_FORK_CLEANUP:ES_FORK_OK;
}
es_fork_result es_fork_close(es_fork *p){
 if(!p||!p->state)return ES_FORK_INVALID;
 if(atomic_load_explicit(&p->armed,memory_order_acquire))return fail(p,ES_FORK_CLEANUP);
 if(p->state==3)return p->terminal;
 es_fork_result r=p->terminal;
 if(atomic_load_explicit(&p->hook_failed,memory_order_acquire))r=ES_FORK_CLEANUP;
 if(close_fd(&p->send_fd))r=ES_FORK_CLEANUP;
 if(close_fd(&p->receive_fd))r=ES_FORK_CLEANUP;
 if(p->peer.state){es_peer_result closed=es_peer_close(&p->peer);if(closed==ES_PEER_CLEANUP)r=ES_FORK_CLEANUP;}
 wipe(p->record,sizeof(p->record));p->state=3;p->terminal=r;return r;
}

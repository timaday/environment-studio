#define _GNU_SOURCE
#include "privacy-fork.h"
#include <errno.h>
#include <poll.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <dirent.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#define CHECK(x) do { if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);return 1;} } while(0)
static const char *mode;
static pid_t owned_child;
static void cleanup_child(void){if(owned_child>0){kill(owned_child,SIGKILL);while(waitpid(owned_child,NULL,0)<0&&errno==EINTR){}owned_child=0;}}
static int fault_fd=-1,closed_fault=0,received_pin=-1,replacement=-1;
int __real_close(int);
int __wrap_close(int fd){int result=__real_close(fd);if(fd==received_pin&&!strcmp(mode,"duplicate-pin")&&replacement<0){replacement=open("/dev/null",O_RDONLY|O_CLOEXEC);}if(fd==fault_fd&&!closed_fault){closed_fault=1;errno=EINTR;return -1;}return result;}
int __real_setsockopt(int,int,int,const void*,socklen_t);
int __wrap_setsockopt(int fd,int level,int option,const void *v,socklen_t n){
 if(!strcmp(mode,"setup-close")||!strcmp(mode,"unsupported")){errno=ENOPROTOOPT;return -1;}return __real_setsockopt(fd,level,option,v,n);
}
int __real_socketpair(int,int,int,int[2]);
static int original_pair[2];
int __wrap_socketpair(int domain,int type,int protocol,int pair[2]){int result=__real_socketpair(domain,type,protocol,pair);if(!result){original_pair[0]=pair[0];original_pair[1]=pair[1];if(!strcmp(mode,"setup-close"))fault_fd=pair[1];}return result;}

ssize_t __real_recvmsg(int,struct msghdr*,int);
ssize_t __wrap_recvmsg(int fd,struct msghdr *message,int flags){
 ssize_t n=__real_recvmsg(fd,message,flags);
 if(n>0){
  for(struct cmsghdr *h=CMSG_FIRSTHDR(message);h;h=CMSG_NXTHDR(message,h))if(h->cmsg_level==SOL_SOCKET&&h->cmsg_type==4&&h->cmsg_len==CMSG_LEN(sizeof(int)))memcpy(&received_pin,CMSG_DATA(h),sizeof(int));
  if(!strcmp(mode,"duplicate-pin")){size_t used=message->msg_controllen;struct cmsghdr *h=(struct cmsghdr*)((unsigned char*)message->msg_control+used);h->cmsg_level=SOL_SOCKET;h->cmsg_type=4;h->cmsg_len=CMSG_LEN(sizeof(int));memcpy(CMSG_DATA(h),&received_pin,sizeof(int));message->msg_controllen+=CMSG_SPACE(sizeof(int));}
  if(!strcmp(mode,"truncated"))message->msg_flags|=MSG_CTRUNC;
  if(!strcmp(mode,"wrong-record"))((unsigned char*)message->msg_iov[0].iov_base)[8]^=1;
  if(!strcmp(mode,"extra-control")){size_t used=message->msg_controllen;struct cmsghdr *h=(struct cmsghdr*)((unsigned char*)message->msg_control+used);h->cmsg_level=SOL_SOCKET;h->cmsg_type=SCM_CREDENTIALS;h->cmsg_len=CMSG_LEN(sizeof(struct ucred));memset(CMSG_DATA(h),0,sizeof(struct ucred));message->msg_controllen+=CMSG_SPACE(sizeof(struct ucred));}
 }
 return n;
}
ssize_t __real_send(int,const void*,size_t,int);
ssize_t __wrap_send(int fd,const void *bytes,size_t length,int flags){
 if(!strcmp(mode,"rights")||!strcmp(mode,"rights-truncated")){
  union {struct cmsghdr align;unsigned char data[CMSG_SPACE(sizeof(int)*253)];} ancillary={0};
  struct iovec vector={(void*)bytes,length};struct msghdr message={.msg_iov=&vector,.msg_iovlen=1,.msg_control=ancillary.data,.msg_controllen=sizeof(ancillary.data)};
  struct cmsghdr *h=CMSG_FIRSTHDR(&message);h->cmsg_level=SOL_SOCKET;h->cmsg_type=SCM_RIGHTS;int count=!strcmp(mode,"rights-truncated")?253:1;message.msg_controllen=CMSG_SPACE(sizeof(int)*(size_t)count);h->cmsg_len=CMSG_LEN(sizeof(int)*(size_t)count);for(int i=0;i<count;i++)memcpy((unsigned char*)CMSG_DATA(h)+sizeof(int)*(size_t)i,&fd,sizeof(fd));return sendmsg(fd,&message,flags);
 }
 ssize_t result=__real_send(fd,bytes,length,flags);
 if(result==(ssize_t)length&&!strcmp(mode,"extra-packet"))return __real_send(fd,bytes,length,flags);
 return result;
}
struct capture_work {es_fork *capture;es_peer_identity identity;es_fork_result result;};
static void *capture_thread(void *arg){struct capture_work *work=arg;work->result=es_fork_capture(work->capture,&work->identity);return NULL;}
static void *wrong_thread(void *p){return (void*)(uintptr_t)es_fork_disarm(p);}
static int descriptor_count(void){DIR *directory=opendir("/proc/self/fd");CHECK(directory);int count=0;struct dirent *entry;while((entry=readdir(directory)))if(entry->d_name[0]!='.')count++;CHECK(!closedir(directory));return count;}
static uint64_t now(void){struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t))return 0;return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
int main(int argc,char **argv){
 if(argc!=2)return 2;
 mode=argv[1];CHECK(!atexit(cleanup_child));
 if(!strcmp(mode,"owned-child")){const char out[]="INVENTED-OUT\n",err[]="INVENTED-ERR\n";if(write(1,out,sizeof(out)-1)!=(ssize_t)sizeof(out)-1||write(2,err,sizeof(err)-1)!=(ssize_t)sizeof(err)-1)return 20;char done;while(read(0,&done,1)<0&&errno==EINTR){}return 0;}

 int cancel=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel>=0);
 int initial_descriptors=descriptor_count();
 es_fork capture={0};uint8_t id[16]={1,3,5,7,9};
 uint64_t deadline=now()+2000000000ULL;
 if(!strcmp(argv[1],"deadline"))deadline=now()-1;
 if(!strcmp(mode,"stalled"))deadline=now()+100000000ULL;
 if(!strcmp(argv[1],"excess-deadline"))deadline=UINT64_MAX;
 if(!strcmp(argv[1],"cancel")){uint64_t one=1;CHECK(write(cancel,&one,sizeof(one))==sizeof(one));}
 es_fork_result r=es_fork_arm(&capture,cancel,deadline,id);
 if(!strcmp(mode,"unsupported")){
  CHECK(r==ES_FORK_UNSUPPORTED);CHECK(fcntl(original_pair[0],F_GETFD)==-1&&fcntl(original_pair[1],F_GETFD)==-1);CHECK(close(cancel)==0);return 0;
 }
 if(!strcmp(mode,"setup-close")){
  CHECK(r==ES_FORK_CLEANUP);CHECK(fcntl(original_pair[0],F_GETFD)==-1&&errno==EBADF);CHECK(fcntl(original_pair[1],F_GETFD)==-1&&errno==EBADF);CHECK(close(cancel)==0);return 0;
 }
 if(!strcmp(mode,"invalid-order")){
  CHECK(r==ES_FORK_OK);es_peer_identity out={0};CHECK(es_fork_read(&capture,&out)==ES_FORK_INVALID);CHECK(es_fork_disarm(&capture)==ES_FORK_OK);CHECK(es_fork_capture(&capture,&out)==ES_FORK_INVALID);CHECK(es_fork_close(&capture)==ES_FORK_INVALID);CHECK(close(cancel)==0);return 0;
 }
 if(!strcmp(mode,"stalled")){
  CHECK(r==ES_FORK_OK);es_peer_identity out={0};CHECK(es_fork_capture(&capture,&out)==ES_FORK_DEADLINE);CHECK(es_fork_disarm(&capture)==ES_FORK_OK);CHECK(es_fork_close(&capture)==ES_FORK_DEADLINE);CHECK(close(cancel)==0);return 0;
 }
 if(!strcmp(mode,"cancel-concurrent")){
  CHECK(r==ES_FORK_OK);struct capture_work work={.capture=&capture};pthread_t thread;CHECK(!pthread_create(&thread,NULL,capture_thread,&work));uint64_t one=1;CHECK(write(cancel,&one,8)==8);CHECK(es_fork_disarm(&capture)==ES_FORK_OK);CHECK(!pthread_join(thread,NULL));CHECK(work.result==ES_FORK_CANCELLED);CHECK(es_fork_close(&capture)==ES_FORK_CANCELLED);CHECK(read(cancel,&one,8)==8&&one==1);CHECK(close(cancel)==0);return 0;
 }
 if(!strcmp(mode,"missing-hook")){
  CHECK(r==ES_FORK_OK);CHECK(es_fork_disarm(&capture)==ES_FORK_OK);es_peer_identity out={0};CHECK(es_fork_capture(&capture,&out)==ES_FORK_PROTOCOL);CHECK(es_fork_close(&capture)==ES_FORK_PROTOCOL);CHECK(close(cancel)==0);return 0;
 }
 if(!strcmp(mode,"close-armed")){
  CHECK(r==ES_FORK_OK);int a=capture.receive_fd,b=capture.send_fd;CHECK(es_fork_close(&capture)==ES_FORK_CLEANUP);CHECK(fcntl(a,F_GETFD)>=0&&fcntl(b,F_GETFD)>=0);CHECK(es_fork_disarm(&capture)==ES_FORK_OK);CHECK(es_fork_close(&capture)==ES_FORK_CLEANUP);CHECK(fcntl(a,F_GETFD)==-1&&fcntl(b,F_GETFD)==-1);CHECK(close(cancel)==0);return 0;
 }
 if(!strcmp(mode,"deadline")||!strcmp(mode,"excess-deadline")||!strcmp(mode,"cancel")){
  CHECK(r==(!strcmp(argv[1],"deadline")?ES_FORK_DEADLINE:!strcmp(argv[1],"cancel")?ES_FORK_CANCELLED:ES_FORK_INVALID));
  CHECK(close(cancel)==0);return 0;
 }
 CHECK(r==ES_FORK_OK);
 if(!strcmp(mode,"wrong-thread")){pthread_t thread;void *result=NULL;CHECK(!pthread_create(&thread,NULL,wrong_thread,&capture));CHECK(!pthread_join(thread,&result));CHECK((uintptr_t)result==ES_FORK_INVALID);}
 if(!strcmp(mode,"parent-close"))fault_fd=capture.send_fd;
 if(!strcmp(mode,"reinit")){CHECK(es_fork_arm(&capture,cancel,deadline,id)==ES_FORK_INVALID);es_fork other={0};CHECK(es_fork_arm(&other,cancel,deadline,id)==ES_FORK_INVALID);}
 struct capture_work work={.capture=&capture};pthread_t thread;int concurrent=!strcmp(mode,"concurrent");
 if(concurrent)CHECK(!pthread_create(&thread,NULL,capture_thread,&work));
 int preexec[2];CHECK(!pipe2(preexec,O_CLOEXEC));
 pid_t child=fork();
 if(child==0){
  if(fcntl(original_pair[0],F_GETFD)!=-1||errno!=EBADF||fcntl(original_pair[1],F_GETFD)!=-1||errno!=EBADF)_exit(127);
  const char ready='C';if(write(preexec[1],&ready,1)!=1)_exit(128);
  execl("/bin/sleep","sleep","5",(char *)NULL);_exit(126);
 }
 owned_child=child;
 if(child<0){(void)es_fork_disarm(&capture);(void)es_fork_close(&capture);return 3;}
 CHECK(close(preexec[1])==0);
 if(strcmp(mode,"parent-close")){struct pollfd ready={.fd=preexec[0],.events=POLLIN};CHECK(poll(&ready,1,1000)==1);char closed;CHECK(read(preexec[0],&closed,1)==1&&closed=='C');}
 CHECK(close(preexec[0])==0);
 int dead=!strcmp(mode,"dead");
 if(dead){struct pollfd ready={.fd=capture.receive_fd,.events=POLLIN};CHECK(poll(&ready,1,1000)==1);CHECK(ready.revents&POLLIN);CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);owned_child=0;}
 if(!strcmp(mode,"post-cancel")){uint64_t one=1;CHECK(write(cancel,&one,sizeof(one))==sizeof(one));}
 es_peer_identity identity={0};es_fork_result disarmed;
 if(concurrent){disarmed=es_fork_disarm(&capture);CHECK(!pthread_join(thread,NULL));r=work.result;identity=work.identity;}
 else {r=es_fork_capture(&capture,&identity);disarmed=es_fork_disarm(&capture);}
 es_fork_result closed=es_fork_close(&capture);
 int killed=dead?0:kill(child,SIGKILL);int status=0;pid_t waited=dead?child:waitpid(child,&status,0);
 if(waited==child)owned_child=0;
 if(!strcmp(mode,"rights-truncated")||!strcmp(mode,"rights")||!strcmp(mode,"extra-packet")||!strcmp(mode,"duplicate-pin")||!strcmp(mode,"truncated")||!strcmp(mode,"wrong-record")||!strcmp(mode,"extra-control")||!strcmp(mode,"post-cancel")||!strcmp(mode,"dead")||!strcmp(mode,"parent-close")){
  es_fork_result expected=!strcmp(mode,"post-cancel")?ES_FORK_CANCELLED:dead?ES_FORK_DEAD:!strcmp(mode,"parent-close")?ES_FORK_CLEANUP:ES_FORK_PROTOCOL;
  CHECK(r==expected);CHECK(closed==expected);CHECK(waited==child);es_peer_identity zero={0};CHECK(!memcmp(&identity,&zero,sizeof(identity)));
  if(!strcmp(mode,"duplicate-pin")){CHECK(replacement==received_pin&&fcntl(replacement,F_GETFD)>=0);CHECK(__real_close(replacement)==0);}
  else if(received_pin>=0)CHECK(fcntl(received_pin,F_GETFD)==-1&&errno==EBADF);
  CHECK(descriptor_count()==initial_descriptors);CHECK(close(cancel)==0);return 0;
 }
 CHECK(r==ES_FORK_CAPTURED);CHECK(identity.pid==(uint32_t)child);CHECK(identity.uid==(uint32_t)geteuid());CHECK(identity.gid==(uint32_t)getegid());CHECK(identity.start_ticks>0);
 CHECK(disarmed==ES_FORK_OK);CHECK(closed==ES_FORK_OK);CHECK(killed==0);CHECK(waited==child);CHECK(close(cancel)==0);return 0;
}

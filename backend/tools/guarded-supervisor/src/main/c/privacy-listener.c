#define _GNU_SOURCE
#include "privacy-listener.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <stdatomic.h>
#include <stddef.h>
#include <string.h>
#include <sys/random.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>
#ifndef SYS_fchmodat2
#define SYS_fchmodat2 452
#endif
static _Atomic uint32_t generations;
static void wipe(void *v,size_t n){volatile unsigned char *p=v;while(n--)*p++=0;}
static int clock_ns(uint64_t *out){
 struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<0||t.tv_nsec<0||t.tv_nsec>=1000000000L)return 0;
 if((uint64_t)t.tv_sec>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL)return 0;
 *out=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;return 1;
}
static es_listener_result fail(es_listener *p,es_listener_result result){if(p->terminal==ES_LISTENER_OK)p->terminal=result;if(p->state!=3)p->state=2;return p->terminal;}
static int close_fd(es_listener *p,int *slot){int fd=*slot;*slot=-1;if(fd>=0&&close(fd)){p->cleanup=ES_LISTENER_CLOSED_INCONCLUSIVE;return 0;}return 1;}
static es_listener_result guard(es_listener *p,int *milliseconds){
 if(p->state!=1)return p->terminal?p->terminal:ES_LISTENER_FAILED;
 struct pollfd cancel={.fd=p->cancel_fd,.events=POLLIN};if(poll(&cancel,1,0)<0)return ES_LISTENER_IO;
 if(cancel.revents&POLLIN)return ES_LISTENER_CANCELLED;
 if(cancel.revents)return ES_LISTENER_IO;
 uint64_t n;if(!clock_ns(&n))return ES_LISTENER_IO;
 if(n>=p->startup_deadline_ns)return ES_LISTENER_DEADLINE;
 uint64_t remaining=p->startup_deadline_ns-n;if(remaining>10000000000ULL)return ES_LISTENER_INVALID;
 if(milliseconds)*milliseconds=(int)((remaining+999999ULL)/1000000ULL);
 return ES_LISTENER_OK;
}
static int cleanup_time(es_listener *p){uint64_t n;return clock_ns(&n)&&n<p->cleanup_deadline_ns;}
static int utf8(const unsigned char *s,size_t n){
 for(size_t i=0;i<n;){unsigned value=s[i++],more=0,minimum=0;
  if(value<128)continue;
  if(value>=194&&value<=223){more=1;minimum=128;value&=31;}
  else if(value>=224&&value<=239){more=2;minimum=2048;value&=15;}
  else if(value>=240&&value<=244){more=3;minimum=65536;value&=7;}else return 0;
  if(n-i<more)return 0;
  while(more--){unsigned c=s[i++];if((c&192)!=128)return 0;value=(value<<6)|(c&63);}
  if(value<minimum||value>0x10ffff||(value>=0xd800&&value<=0xdfff))return 0;
 }
 return 1;
}
static int directory(const struct stat *s,dev_t device,ino_t inode){return S_ISDIR(s->st_mode)&&s->st_uid==geteuid()&&(s->st_mode&07777)==0700&&s->st_dev==device&&s->st_ino==inode;}
static int socket_entry(const struct stat *s,es_listener *p){return S_ISSOCK(s->st_mode)&&s->st_uid==geteuid()&&(s->st_mode&07777)==0600&&s->st_nlink==1&&s->st_dev==p->socket_device&&s->st_ino==p->socket_inode;}
/* All names here are fixed native-owned components. The caller's admitted
   namespace mutation precondition is required: Linux has no conditional unlink. */
static int parent_matches(es_listener *p,int closing){
 struct stat expected;if(fstat(p->parent_fd,&expected)||!directory(&expected,p->parent_device,p->parent_inode))return 0;
 int current=open("/",O_PATH|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);if(current<0)return 0;
 const char *at=p->parent_path+1;int valid=1;
 while(*at&&valid){
  if(closing?!cleanup_time(p):guard(p,NULL)!=ES_LISTENER_OK){valid=0;break;}
  const char *slash=strchr(at,'/');size_t length=slash?(size_t)(slash-at):strlen(at);char component[104];
  if(!length||length>=sizeof(component)){valid=0;break;}
  memcpy(component,at,length);component[length]=0;
  if(!strcmp(component,".")||!strcmp(component,"..")){valid=0;break;}
  int next=openat(current,component,O_PATH|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);
  if(next<0){valid=0;break;}
  if(!close_fd(p,&current)){close_fd(p,&next);valid=0;break;}
  current=next;at=slash?slash+1:at+length;
 }
 struct stat actual;if(valid&&(fstat(current,&actual)||!directory(&actual,p->parent_device,p->parent_inode)))valid=0;
 if(!close_fd(p,&current))valid=0;
 return valid;
}
static int namespace_matches(es_listener *p){
 struct stat s;if(!parent_matches(p,0))return 0;
 if(fstat(p->directory_fd,&s)||!directory(&s,p->directory_device,p->directory_inode))return 0;
 if(fstatat(p->parent_fd,p->directory_name,&s,AT_SYMLINK_NOFOLLOW)||!directory(&s,p->directory_device,p->directory_inode))return 0;
 if(fstat(p->socket_path_fd,&s)||!socket_entry(&s,p))return 0;
 return !fstatat(p->directory_fd,"control.sock",&s,AT_SYMLINK_NOFOLLOW)&&socket_entry(&s,p);
}
static int chmod_pin(int fd,mode_t mode){return syscall(SYS_fchmodat2,fd,"",mode,AT_EMPTY_PATH)==0;}
es_listener_result es_listener_open(es_listener *p,int parent,const char *path,int cancel,uint64_t deadline,unsigned width){
 if(!p||p->state||parent<0||cancel<0||parent==cancel||!path||width<1||width>16)return ES_LISTENER_INVALID;
 size_t length=strnlen(path,104);if(length<2||length>54||path[0]!='/'||path[length-1]=='/'||!utf8((const unsigned char*)path,length))return ES_LISTENER_INVALID;
 p->state=1;p->parent_fd=parent;p->cancel_fd=cancel;p->directory_fd=-1;p->socket_path_fd=-1;p->listen_fd=-1;p->width=width;p->startup_deadline_ns=deadline;
 memcpy(p->parent_path,path,length+1);for(unsigned i=0;i<64;i++)p->peers[i].fd=-1;
 es_listener_result r=guard(p,NULL);if(r)return fail(p,r);
#if !defined(__linux__) || !defined(__x86_64__)
 return fail(p,ES_LISTENER_PLATFORM);
#endif
 uint32_t old=atomic_load(&generations);
 do {if(old==UINT32_MAX)return fail(p,ES_LISTENER_CAPACITY);}while(!atomic_compare_exchange_weak(&generations,&old,old+1));p->generation=old+1;
 struct stat s;int flags=fcntl(parent,F_GETFD);
 if(flags<0||!(flags&FD_CLOEXEC)||fstat(parent,&s)||!S_ISDIR(s.st_mode)||s.st_uid!=geteuid()||(s.st_mode&07777)!=0700)return fail(p,ES_LISTENER_IDENTITY);
 p->parent_device=s.st_dev;p->parent_inode=s.st_ino;if(!parent_matches(p,0))return fail(p,ES_LISTENER_IDENTITY);
 unsigned char random[16];ssize_t received=getrandom(random,sizeof(random),GRND_NONBLOCK);
 if(received!=sizeof(random)){wipe(random,sizeof(random));return fail(p,ES_LISTENER_PLATFORM);}
 static const char hex[]="0123456789abcdef";memcpy(p->directory_name,"es-",3);
 for(unsigned i=0;i<16;i++){p->directory_name[3+i*2]=hex[random[i]>>4];p->directory_name[4+i*2]=hex[random[i]&15];}p->directory_name[35]=0;wipe(random,sizeof(random));
 memcpy(p->path,path,length);p->path[length]='/';memcpy(p->path+length+1,p->directory_name,35);memcpy(p->path+length+36,"/control.sock",14);
 r=guard(p,NULL);if(r)return fail(p,r);
 if(mkdirat(parent,p->directory_name,0700))return fail(p,errno==EEXIST?ES_LISTENER_CAPACITY:ES_LISTENER_IO);
 p->directory_created=1;
 if(fstatat(parent,p->directory_name,&s,AT_SYMLINK_NOFOLLOW)||!S_ISDIR(s.st_mode)||s.st_uid!=geteuid())return fail(p,ES_LISTENER_IDENTITY);
 p->directory_device=s.st_dev;p->directory_inode=s.st_ino;
 p->directory_fd=openat(parent,p->directory_name,O_PATH|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);if(p->directory_fd<0)return fail(p,ES_LISTENER_IO);
 if(fstat(p->directory_fd,&s)||s.st_dev!=p->directory_device||s.st_ino!=p->directory_inode)return fail(p,ES_LISTENER_IDENTITY);
 if(!chmod_pin(p->directory_fd,0700))return fail(p,ES_LISTENER_PLATFORM);
 if(fstat(p->directory_fd,&s)||!directory(&s,p->directory_device,p->directory_inode)||!parent_matches(p,0))return fail(p,ES_LISTENER_IDENTITY);
 p->listen_fd=socket(AF_UNIX,SOCK_STREAM|SOCK_NONBLOCK|SOCK_CLOEXEC,0);if(p->listen_fd<0)return fail(p,ES_LISTENER_IO);
 struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,p->path,strlen(p->path)+1);
 if(bind(p->listen_fd,(struct sockaddr*)&address,(socklen_t)(offsetof(struct sockaddr_un,sun_path)+strlen(p->path)+1)))return fail(p,ES_LISTENER_IO);
 p->socket_created=1;
 if(fstatat(p->directory_fd,"control.sock",&s,AT_SYMLINK_NOFOLLOW)||!S_ISSOCK(s.st_mode)||s.st_uid!=geteuid())return fail(p,ES_LISTENER_IDENTITY);
 p->socket_device=s.st_dev;p->socket_inode=s.st_ino;
 p->socket_path_fd=openat(p->directory_fd,"control.sock",O_PATH|O_NOFOLLOW|O_CLOEXEC);if(p->socket_path_fd<0)return fail(p,ES_LISTENER_IO);
 if(fstat(p->socket_path_fd,&s)||s.st_dev!=p->socket_device||s.st_ino!=p->socket_inode||!S_ISSOCK(s.st_mode))return fail(p,ES_LISTENER_IDENTITY);
 if(!chmod_pin(p->socket_path_fd,0600))return fail(p,ES_LISTENER_PLATFORM);
 if(!namespace_matches(p))return fail(p,ES_LISTENER_IDENTITY);
 if(listen(p->listen_fd,(int)width))return fail(p,ES_LISTENER_IO);
 r=guard(p,NULL);return r?fail(p,r):ES_LISTENER_OK;
}
es_listener_result es_listener_path(es_listener *p,char out[104]){
 if(out)wipe(out,104);
 if(!p||!p->state)return ES_LISTENER_INVALID;
 if(!out)return fail(p,ES_LISTENER_INVALID);
 es_listener_result r=guard(p,NULL);if(r)return fail(p,r);
 if(!namespace_matches(p))return fail(p,ES_LISTENER_IDENTITY);
 memcpy(out,p->path,strlen(p->path)+1);return ES_LISTENER_OK;
}
es_listener_result es_listener_accept(es_listener *p,uint64_t *token){
 if(token)*token=0;
 if(!p||!p->state)return ES_LISTENER_INVALID;
 if(!token)return fail(p,ES_LISTENER_INVALID);
 es_listener_result r=guard(p,NULL);if(r)return fail(p,r);
 if(p->live>=p->width||p->accepted>=64)return fail(p,ES_LISTENER_CAPACITY);
 for(;;){int milliseconds=0;r=guard(p,&milliseconds);if(r)return fail(p,r);
  if(!namespace_matches(p))return fail(p,ES_LISTENER_IDENTITY);
  struct pollfd f[2]={{.fd=p->cancel_fd,.events=POLLIN},{.fd=p->listen_fd,.events=POLLIN}};
  int ready=poll(f,2,milliseconds);if(ready<0){if(errno==EINTR)continue;return fail(p,ES_LISTENER_IO);}
  r=guard(p,NULL);if(r)return fail(p,r);if(!ready)continue;
  if(f[1].revents&(POLLERR|POLLHUP|POLLNVAL))return fail(p,ES_LISTENER_IO);
  if(!(f[1].revents&POLLIN))continue;
  int fd=accept4(p->listen_fd,NULL,NULL,SOCK_NONBLOCK|SOCK_CLOEXEC);
  if(fd<0){if(errno==EAGAIN||errno==EWOULDBLOCK||errno==EINTR)continue;return fail(p,ES_LISTENER_IO);}
  unsigned ordinal=++p->accepted;es_listener_slot *slot=&p->peers[ordinal-1];slot->fd=fd;slot->state=1;p->live++;
  int descriptor_flags=fcntl(fd,F_GETFD),status_flags=fcntl(fd,F_GETFL);
  if(descriptor_flags<0||status_flags<0||!(descriptor_flags&FD_CLOEXEC)||!(status_flags&O_NONBLOCK)||!namespace_matches(p))return fail(p,ES_LISTENER_IDENTITY);
  r=guard(p,NULL);if(r)return fail(p,r);
  *token=((uint64_t)p->generation<<32)|ordinal;return ES_LISTENER_ACCEPTED;
 }
}
static es_listener_slot *find_slot(es_listener *p,uint64_t token){
 if(!p||!p->state||(uint32_t)(token>>32)!=p->generation)return NULL;
 uint32_t ordinal=(uint32_t)token;if(!ordinal||ordinal>p->accepted)return NULL;return &p->peers[ordinal-1];
}
es_listener_result es_listener_borrow(es_listener *p,uint64_t token,int *fd){
 if(fd)*fd=-1;
 if(!p||!p->state||!fd)return ES_LISTENER_INVALID;
 es_listener_slot *slot=find_slot(p,token);if(!slot||slot->state!=1)return ES_LISTENER_INVALID;
 es_listener_result r=guard(p,NULL);if(r)return fail(p,r);
 if(!namespace_matches(p))return fail(p,ES_LISTENER_IDENTITY);
 *fd=slot->fd;return ES_LISTENER_OK;
}
es_listener_cleanup es_listener_close_peer(es_listener *p,uint64_t token){
 es_listener_slot *slot=find_slot(p,token);if(!slot)return ES_LISTENER_CLOSE_INVALID;
 if(slot->state==2)return slot->cleanup;
 slot->cleanup=close_fd(p,&slot->fd)?ES_LISTENER_CLOSED_COMPLETE:ES_LISTENER_CLOSED_INCONCLUSIVE;
 slot->state=2;p->live--;if(slot->cleanup==ES_LISTENER_CLOSED_INCONCLUSIVE)fail(p,ES_LISTENER_FAILED);return slot->cleanup;
}
es_listener_cleanup es_listener_close(es_listener *p,uint64_t deadline){
 if(!p||!p->state)return ES_LISTENER_CLOSE_INVALID;
 if(p->state==3)return p->cleanup;
 p->state=2;p->cleanup_started=1;uint64_t n=0;
 if(!clock_ns(&n)||deadline<=n||deadline-n>10000000000ULL){p->cleanup=ES_LISTENER_CLOSED_INCONCLUSIVE;p->cleanup_deadline_ns=n;}
 else p->cleanup_deadline_ns=deadline;
 for(unsigned i=0;i<p->accepted;i++)(void)es_listener_close_peer(p,((uint64_t)p->generation<<32)|(i+1));
 close_fd(p,&p->listen_fd);
 if(p->socket_created){struct stat s;
  if(!cleanup_time(p)||!parent_matches(p,1)||!p->socket_inode||p->directory_fd<0||fstatat(p->directory_fd,"control.sock",&s,AT_SYMLINK_NOFOLLOW)||s.st_dev!=p->socket_device||s.st_ino!=p->socket_inode||!S_ISSOCK(s.st_mode)||s.st_uid!=geteuid()||s.st_nlink!=1)p->cleanup=ES_LISTENER_CLOSED_INCONCLUSIVE;
  else if(unlinkat(p->directory_fd,"control.sock",0))p->cleanup=ES_LISTENER_CLOSED_INCONCLUSIVE;
  else p->socket_created=0;
 }
 close_fd(p,&p->socket_path_fd);
 if(p->directory_created){struct stat s;
  if(!cleanup_time(p)||!parent_matches(p,1)||!p->directory_inode||fstatat(p->parent_fd,p->directory_name,&s,AT_SYMLINK_NOFOLLOW)||s.st_dev!=p->directory_device||s.st_ino!=p->directory_inode||!S_ISDIR(s.st_mode)||s.st_uid!=geteuid())p->cleanup=ES_LISTENER_CLOSED_INCONCLUSIVE;
  else if(unlinkat(p->parent_fd,p->directory_name,AT_REMOVEDIR))p->cleanup=ES_LISTENER_CLOSED_INCONCLUSIVE;
  else p->directory_created=0;
 }
 close_fd(p,&p->directory_fd);wipe(p->path,sizeof(p->path));wipe(p->parent_path,sizeof(p->parent_path));wipe(p->directory_name,sizeof(p->directory_name));p->state=3;return p->cleanup;
}

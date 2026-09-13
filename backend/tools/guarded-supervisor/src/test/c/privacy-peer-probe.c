#define _GNU_SOURCE
#include "privacy-peer.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/prctl.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#define CHECK(x) do { if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);} } while(0)
static uint64_t now(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+t.tv_nsec;}
static const char *mode;
static int pair[2]={-1,-1};
static pid_t child;static int connection=-1,listener=-1,gate[2]={-1,-1};static char directory[]="/tmp/es-peer-probe-XXXXXX";static char path[108];
static void cleanup(void){if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);}if(connection>=0)close(connection);if(listener>=0)close(listener);for(int i=0;i<2;i++)if(gate[i]>=0)close(gate[i]);if(path[0])unlink(path);for(int i=0;i<2;i++)if(pair[i]>=0)close(pair[i]);rmdir(directory);}
static void connect_child(void){
 CHECK(mkdtemp(directory));CHECK(!chmod(directory,0700));CHECK(snprintf(path,sizeof(path),"%s/control",directory)>0);
 struct sockaddr_un a={.sun_family=AF_UNIX};memcpy(a.sun_path,path,strlen(path)+1);
 listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(listener>=0);CHECK(!bind(listener,(struct sockaddr*)&a,sizeof(a)));CHECK(!listen(listener,1));CHECK(!pipe2(gate,O_CLOEXEC));
 if(!strcmp(mode,"inherited"))CHECK(!socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair));
 child=fork();CHECK(child>=0);if(!child){if(!strcmp(mode,"comm"))CHECK(!prctl(PR_SET_NAME,"a ) b\nc)",0,0,0));close(gate[1]);int s=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(s<0||connect(s,(struct sockaddr*)&a,sizeof(a)))_exit(41);char b;if(read(gate[0],&b,1)<0)_exit(42);close(s);_exit(0);}
 close(gate[0]);gate[0]=-1;connection=accept4(listener,NULL,NULL,SOCK_CLOEXEC|SOCK_NONBLOCK);CHECK(connection>=0);
}
static void stop_child(void){CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=0;}
static int installed=-1, special=-1, supplied=0, close_fault=0, borrowed_cancel=-1;
int __real_getsockopt(int,int,int,void*,socklen_t*);
int __wrap_getsockopt(int fd,int level,int option,void *value,socklen_t *length){
 if(option==77&&!strcmp(mode,"unsupported")){errno=ENOPROTOOPT;return -1;}
 int r=__real_getsockopt(fd,level,option,value,length);
 if(!r&&option==SO_PEERCRED&&!strcmp(mode,"short-cred"))*length=1;
 if(!r&&option==SO_PEERCRED&&!strcmp(mode,"wrong-uid"))((struct ucred*)value)->uid^=1;
 if(!r&&option==77){installed=*(int*)value;
  if(!strcmp(mode,"short-option"))*length=1;
  if(!strcmp(mode,"fd-alias")){CHECK(!close(installed));*(int*)value=borrowed_cancel;}
  if(!strcmp(mode,"no-cloexec"))CHECK(!fcntl(installed,F_SETFD,0));
 }
 return r;
}
int __real_open(const char*,int,...);
int __wrap_open(const char *path,int flags,...){
 int fd=__real_open(path,flags);
 if(fd>=0&&((strstr(path,"/fdinfo/")&&(!strcmp(mode,"bad-fdinfo")||!strcmp(mode,"oversized")))||(strstr(path,"/stat")&&!strcmp(mode,"during-read")))){special=fd;supplied=0;}
 return fd;
}
ssize_t __real_read(int,void*,size_t);
ssize_t __wrap_read(int fd,void *data,size_t size){
 if(fd==special){
  if(!strcmp(mode,"during-read")){stop_child();special=-1;}
  else if(!strcmp(mode,"oversized")){memset(data,'x',size);return (ssize_t)size;}
  else {const char text[]="Pid:\t0\n";if(supplied)return 0;CHECK(size>=sizeof(text)-1);memcpy(data,text,sizeof(text)-1);supplied=1;return sizeof(text)-1;}
 }
 if(!strcmp(mode,"partial")&&size>3)size=3;
 return __real_read(fd,data,size);
}
int __real_close(int);
int __wrap_close(int fd){if(fd==special)special=-1;int r=__real_close(fd);if(fd==installed&&close_fault){close_fault=0;errno=EINTR;return -1;}return r;}
int main(int argc,char **argv){CHECK(argc==2);mode=argv[1];atexit(cleanup);connect_child();int cancel=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel>=0);borrowed_cancel=cancel;es_peer p={0};es_peer_identity id;
 if(!strncmp(mode,"adopt-",6)){
  int pin=-1;socklen_t length=sizeof(pin);CHECK(!getsockopt(connection,SOL_SOCKET,77,&pin,&length));CHECK(pin>=0);int original=pin;
  es_peer_identity expected={.pid=(uint32_t)child,.uid=(uint32_t)geteuid(),.gid=(uint32_t)getegid()};
  if(!strcmp(mode,"adopt-cancel")){uint64_t one=1;CHECK(write(cancel,&one,8)==8);}
  if(!strcmp(mode,"adopt-dead"))stop_child();
  if(!strcmp(mode,"adopt-cloexec"))CHECK(!fcntl(pin,F_SETFD,0));
  if(!strcmp(mode,"adopt-mismatch"))expected.pid++;
  if(!strcmp(mode,"adopt-uid"))expected.uid^=1;
  if(!strcmp(mode,"adopt-gid"))expected.gid^=1;
  if(!strcmp(mode,"adopt-start"))expected.start_ticks=1;
  if(!strcmp(mode,"adopt-nonpin")){CHECK(!close(pin));pin=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(pin>=0);original=pin;}
  if(!strcmp(mode,"adopt-uncertain")){
   CHECK(es_peer_adopt_kernel_pin(&p,&pin,expected,cancel,now()+1000000000ULL)==ES_PEER_OK);CHECK(pin==-1);installed=original;close_fault=1;
   CHECK(es_peer_close(&p)==ES_PEER_CLEANUP);int replacement=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(replacement==original);CHECK(es_peer_close(&p)==ES_PEER_CLEANUP);CHECK(fcntl(replacement,F_GETFD)>=0);CHECK(close(replacement)==0);CHECK(close(cancel)==0);return 0;
  }
  if(!strcmp(mode,"adopt-reinit")){
   CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_OK);int retained=p.pidfd;
   CHECK(es_peer_adopt_kernel_pin(&p,&pin,expected,cancel,now()+1000000000ULL)==ES_PEER_INVALID);CHECK(pin==original&&p.pidfd==retained);CHECK(fcntl(pin,F_GETFD)>=0);CHECK(close(pin)==0);CHECK(es_peer_close(&p)==ES_PEER_OK);CHECK(close(cancel)==0);return 0;
  }
  if(!strcmp(mode,"adopt-null")){CHECK(es_peer_adopt_kernel_pin(NULL,&pin,expected,cancel,now()+1000000000ULL)==ES_PEER_INVALID);CHECK(pin==original&&fcntl(pin,F_GETFD)>=0);CHECK(close(pin)==0);CHECK(close(cancel)==0);return 0;}
  int borrowed=!strcmp(mode,"adopt-alias")?pin:cancel;
  es_peer_result r=es_peer_adopt_kernel_pin(&p,&pin,expected,borrowed,now()+1000000000ULL);
  if(!strcmp(mode,"adopt-start")||!strcmp(mode,"adopt-alias")){CHECK(r==ES_PEER_INVALID);CHECK(pin==original&&fcntl(pin,F_GETFD)>=0);CHECK(!close(pin));}
  else {CHECK(pin==-1);
   if(!strcmp(mode,"adopt-live")){CHECK(r==ES_PEER_OK);CHECK(es_peer_read(&p,&id)==ES_PEER_OK);CHECK(id.pid==expected.pid&&id.start_ticks>0);CHECK(es_peer_close(&p)==ES_PEER_OK);}
   else {CHECK(r==(!strcmp(mode,"adopt-cancel")?ES_PEER_CANCELLED:(!strcmp(mode,"adopt-dead")||!strcmp(mode,"adopt-nonpin"))?ES_PEER_DEAD:ES_PEER_IDENTITY));CHECK(es_peer_close(&p)==r);}
   CHECK(fcntl(original,F_GETFD)==-1&&errno==EBADF);
  }
  CHECK(fcntl(cancel,F_GETFD)>=0&&fcntl(connection,F_GETFD)>=0);
 }
 else if(!strcmp(argv[1],"live")||!strcmp(mode,"comm")||!strcmp(mode,"partial")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_OK);CHECK(es_peer_read(&p,&id)==ES_PEER_OK);CHECK(id.pid==(uint32_t)child&&id.uid==geteuid()&&id.gid==getegid()&&id.start_ticks>0);CHECK(fcntl(p.pidfd,F_GETFD)&FD_CLOEXEC);CHECK(es_peer_close(&p)==ES_PEER_OK);CHECK(fcntl(connection,F_GETFD)>=0&&fcntl(cancel,F_GETFD)>=0);}
 else if(!strcmp(argv[1],"dead")){stop_child();CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_DEAD);}
 else if(!strcmp(argv[1],"exit")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_OK);stop_child();memset(&id,0xff,sizeof(id));CHECK(es_peer_read(&p,&id)==ES_PEER_DEAD);es_peer_identity zero={0};CHECK(!memcmp(&id,&zero,sizeof(id)));CHECK(es_peer_close(&p)==ES_PEER_DEAD);}
 else if(!strcmp(argv[1],"deadline")){CHECK(es_peer_open(&p,connection,cancel,now()-1)==ES_PEER_DEADLINE);}
 else if(!strcmp(argv[1],"cancel")){uint64_t one=1;CHECK(write(cancel,&one,8)==8);CHECK(es_peer_open(&p,connection,cancel,now()-1)==ES_PEER_CANCELLED);CHECK(read(cancel,&one,8)==8&&one==1);}
 else if(!strcmp(argv[1],"reuse")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_OK);int saved=p.pidfd;CHECK(es_peer_close(&p)==ES_PEER_OK);int fd=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(fd==saved);CHECK(es_peer_close(&p)==ES_PEER_OK);CHECK(fcntl(fd,F_GETFD)>=0);close(fd);}
 else if(!strcmp(mode,"closed-read")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_OK);CHECK(es_peer_close(&p)==ES_PEER_OK);CHECK(es_peer_read(&p,&id)==ES_PEER_INVALID);}
 else if(!strcmp(mode,"uncertain-close")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_OK);close_fault=1;CHECK(es_peer_close(&p)==ES_PEER_CLEANUP);int fd=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(fd==installed);CHECK(es_peer_close(&p)==ES_PEER_CLEANUP);CHECK(fcntl(fd,F_GETFD)>=0);close(fd);}
 else if(!strcmp(mode,"unsupported")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_UNSUPPORTED);CHECK(installed==-1);}
 else if(!strcmp(mode,"during-read")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_DEAD);}
 else if(!strcmp(mode,"short-option")||!strcmp(mode,"no-cloexec")||!strcmp(mode,"wrong-uid")||!strcmp(mode,"bad-fdinfo")||!strcmp(mode,"oversized")){
  CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_IDENTITY);CHECK(installed>=0);CHECK(fcntl(installed,F_GETFD)==-1&&errno==EBADF);CHECK(fcntl(connection,F_GETFD)>=0&&fcntl(cancel,F_GETFD)>=0);
 }
 else if(!strcmp(mode,"start-mismatch")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_OK);p.identity.start_ticks++;CHECK(es_peer_read(&p,&id)==ES_PEER_IDENTITY);}
 else if(!strcmp(mode,"inherited")){CHECK(es_peer_open(&p,pair[0],cancel,now()+1000000000ULL)==ES_PEER_OK);CHECK(es_peer_read(&p,&id)==ES_PEER_OK);CHECK(id.pid==(uint32_t)getpid()&&id.pid!=(uint32_t)child);CHECK(es_peer_close(&p)==ES_PEER_OK);}
 else if(!strcmp(mode,"fd-alias")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_IDENTITY);CHECK(fcntl(cancel,F_GETFD)>=0);}
 else if(!strcmp(mode,"short-cred")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_IDENTITY);CHECK(installed==-1);}
 else if(!strcmp(mode,"reinit")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_OK);int original=p.pidfd;CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_INVALID);CHECK(p.pidfd==original&&fcntl(original,F_GETFD)>=0);CHECK(es_peer_close(&p)==ES_PEER_OK);}
 else if(!strcmp(mode,"excess-deadline")){CHECK(es_peer_open(&p,connection,cancel,UINT64_MAX)==ES_PEER_INVALID);CHECK(installed==-1);CHECK(fcntl(connection,F_GETFD)>=0&&fcntl(cancel,F_GETFD)>=0);}
 else if(!strcmp(mode,"long-deadline")){CHECK(es_peer_open(&p,connection,cancel,now()+11000000000ULL)==ES_PEER_INVALID);CHECK(installed==-1);}
 else if(!strcmp(mode,"max-deadline")){CHECK(es_peer_open(&p,connection,cancel,now()+10000000000ULL)==ES_PEER_OK);CHECK(es_peer_read(&p,&id)==ES_PEER_OK);CHECK(es_peer_close(&p)==ES_PEER_OK);}
 else if(!strcmp(mode,"null-output")){CHECK(es_peer_open(&p,connection,cancel,now()+1000000000ULL)==ES_PEER_OK);int pinned=p.pidfd;CHECK(es_peer_read(&p,NULL)==ES_PEER_INVALID);CHECK(fcntl(pinned,F_GETFD)==-1&&errno==EBADF);CHECK(es_peer_read(&p,&id)==ES_PEER_INVALID);es_peer_identity zero={0};CHECK(!memcmp(&id,&zero,sizeof(id)));CHECK(es_peer_close(&p)==ES_PEER_INVALID);CHECK(fcntl(connection,F_GETFD)>=0&&fcntl(cancel,F_GETFD)>=0);}
 else CHECK(0);
 close(cancel);return 0;
}

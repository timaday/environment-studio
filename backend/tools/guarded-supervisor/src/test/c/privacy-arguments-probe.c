#define _GNU_SOURCE
#include "privacy-arguments.h"
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
#include <sys/un.h>
#include <sys/vfs.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#define CHECK(x) do {if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}}while(0)
static const char *mode;static pid_t child;static int listener=-1,connection=-1,cancel_fd=-1;
static int special=-1,rounds=0,interrupted=0,changed=0,reused=-1,expired=0;
static char directory[]="/tmp/es-arguments-probe-XXXXXX",path[108];
static int is(const char *name){return mode&&!strcmp(mode,name);}
static void cleanup(void){if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);}if(connection>=0)close(connection);if(listener>=0)close(listener);if(path[0])unlink(path);rmdir(directory);}
static uint64_t now(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+t.tv_nsec;}
static struct sockaddr_un address(const char *pathname){struct sockaddr_un a={.sun_family=AF_UNIX};CHECK(strlen(pathname)<sizeof(a.sun_path));memcpy(a.sun_path,pathname,strlen(pathname)+1);return a;}
static void stop_child(void){CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=0;}
static char *child_argument;static int child_socket;
static void alter(int signal){(void)signal;child_argument[0]^=1;char ack='!';if(write(child_socket,&ack,1)!=1)_exit(41);}
static void change_actual_arguments(void){
 CHECK(!kill(child,SIGUSR1));struct pollfd p={.fd=connection,.events=POLLIN};CHECK(poll(&p,1,1000)==1);char ack;CHECK(read(connection,&ack,1)==1&&ack=='!');
}
int __real_open(const char*,int,...);
int __wrap_open(const char *name,int flags,...){
 int target=strstr(name,"/cmdline")!=NULL;
 if(target&&is("open-fault")){errno=EACCES;return -1;}
 int fd=__real_open(name,flags);
 if(target&&fd>=0){CHECK((flags&(O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK))==(O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK));special=fd;rounds++;}
 return fd;
}
int __real_fstatfs(int,struct statfs*);
int __wrap_fstatfs(int fd,struct statfs *s){int r=__real_fstatfs(fd,s);if(fd==special&&is("wrong-fs"))s->f_type=0;return r;}
int __real_clock_gettime(clockid_t,struct timespec*);
int __wrap_clock_gettime(clockid_t clock,struct timespec *time){int r=__real_clock_gettime(clock,time);if(!r&&clock==CLOCK_MONOTONIC&&expired)time->tv_sec+=6;return r;}
ssize_t __real_read(int,void*,size_t);
ssize_t __wrap_read(int fd,void *data,size_t size){
 if(fd!=special)return __real_read(fd,data,size);
 if(is("read-fault")||is("read-close-fault")){errno=EIO;return -1;}
 if(is("empty"))return 0;
 if(is("oversized")){memset(data,'x',size);return (ssize_t)size;}
 if(is("eintr")&&!interrupted++){errno=EINTR;return -1;}
 if(is("partial")&&size>3)size=3;
 ssize_t n=__real_read(fd,data,size);
 if(n>0){
  if(is("missing-nul"))((char*)data)[n-1]='x';
  if(is("changed")||(is("second-change")&&rounds==2))((char*)data)[0]^=1;
  if(is("truncate"))n--;
 }
 if(!n&&rounds==1&&is("actual-change")&&!changed++){change_actual_arguments();}
 if(!n&&rounds==2){
  if(is("final-death"))stop_child();
  if(is("final-cancel")){uint64_t one=1;CHECK(write(cancel_fd,&one,8)==8);}
  if(is("final-deadline"))expired=1;
 }
 return n;
}
ssize_t __wrap___read_chk(int fd,void *data,size_t count,size_t capacity){CHECK(count<=capacity);return __wrap_read(fd,data,count);}
int __real_close(int);
int __wrap_close(int fd){
 int target=fd==special;if(target)special=-1;int r=__real_close(fd);
 if(target&&(is("close-fault")||is("read-close-fault"))&&reused<0){
  reused=__real_open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(reused>=0);
  if(reused!=fd){CHECK(dup2(reused,fd)==fd);CHECK(!__real_close(reused));reused=fd;}
  errno=EINTR;return -1;
 }
 return r;
}
int main(int argc,char **argv){
 CHECK(argc>=2);
 if(!strcmp(argv[1],"child")){
  CHECK(argc>=5);struct sockaddr_un a=address(argv[2]);child_socket=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(child_socket>=0);
  child_argument=argv[3];struct sigaction action={0};action.sa_handler=alter;action.sa_flags=SA_RESTART;CHECK(!sigemptyset(&action.sa_mask));CHECK(!sigaction(SIGUSR1,&action,NULL));
  CHECK(!connect(child_socket,(struct sockaddr*)&a,sizeof(a)));char b;CHECK(read(child_socket,&b,1)>=0);close(child_socket);return 0;
 }
 mode=argv[1];atexit(cleanup);CHECK(mkdtemp(directory));CHECK(!chmod(directory,0700));CHECK(snprintf(path,sizeof(path),"%s/control",directory)>0);
 struct sockaddr_un a=address(path);listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(listener>=0);CHECK(!bind(listener,(struct sockaddr*)&a,sizeof(a)));CHECK(!listen(listener,1));
 char *arguments[130]={argv[0],"child",path,"mock space é 😀","",NULL};char fillers[17][1025];size_t count=5,n=0;
 for(size_t i=0;i<count;i++)n+=strlen(arguments[i])+1;
 if(is("max-count")){while(count<128)arguments[count++]="";}
 if(is("max-width")){memset(fillers[0],'x',1024);fillers[0][1024]=0;arguments[count++]=fillers[0];}
 if(is("max-bytes")||is("actual-overflow")){
  size_t slot=0;while(n<16384){CHECK(slot<17);size_t width=16384-n-1;if(width>1024)width=1024;memset(fillers[slot],'x',width);fillers[slot][width]=0;arguments[count++]=fillers[slot++];n+=width+1;}
 }
 unsigned char expected[16384];n=0;
 for(size_t i=0;i<count;i++){size_t z=strlen(arguments[i])+1;CHECK(n+z<=sizeof(expected));memcpy(expected+n,arguments[i],z);n+=z;}
 if(is("actual-overflow"))arguments[count++]="";
 arguments[count]=NULL;
 child=fork();CHECK(child>=0);if(!child){execv(argv[0],arguments);_exit(41);}
 connection=accept4(listener,NULL,NULL,SOCK_CLOEXEC|SOCK_NONBLOCK);CHECK(connection>=0);cancel_fd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel_fd>=0);es_peer peer={0};
 CHECK(es_peer_open(&peer,connection,cancel_fd,now()+5000000000ULL)==ES_PEER_OK);
 es_peer_result want=ES_PEER_OK;
 if(is("wrong-order")){expected[0]^=1;want=ES_PEER_IDENTITY;}
 if(is("reordered")){size_t width=strlen(arguments[3])+1,offset=n-width-1;memmove(expected+offset+1,expected+offset,width);expected[offset]=0;want=ES_PEER_IDENTITY;}
 if(is("extra-argument")){expected[n++]='x';expected[n++]=0;want=ES_PEER_IDENTITY;}
 if(is("unterminated")){expected[n-1]='x';want=ES_PEER_INVALID;}
 if(is("too-wide")){memset(expected,'x',1025);expected[1025]=0;n=1026;want=ES_PEER_INVALID;}
 if(is("too-many")){memset(expected,0,129);n=129;want=ES_PEER_INVALID;}
 if(is("zero")){n=0;want=ES_PEER_INVALID;}
 if(is("too-large")){n=16385;want=ES_PEER_INVALID;}
 if(is("dead")){stop_child();want=ES_PEER_DEAD;}
 if(is("cancel")){uint64_t one=1;CHECK(write(cancel_fd,&one,8)==8);want=ES_PEER_CANCELLED;}
 if(is("deadline")){expired=1;want=ES_PEER_DEADLINE;}
 if(is("changed-start")){peer.identity.start_ticks++;want=ES_PEER_IDENTITY;}
 if(is("open-fault")||is("wrong-fs")||is("empty")||is("oversized")||is("missing-nul")||is("changed")||is("second-change")||is("truncate")||is("actual-change")||is("actual-overflow"))want=ES_PEER_IDENTITY;
 if(is("read-fault"))want=ES_PEER_IO;
 if(is("close-fault")||is("read-close-fault"))want=ES_PEER_CLEANUP;
 if(is("final-death"))want=ES_PEER_DEAD;
 if(is("final-cancel"))want=ES_PEER_CANCELLED;
 if(is("final-deadline"))want=ES_PEER_DEADLINE;
 es_peer before=peer;
 if(is("null")||is("null-bytes")||is("overlap")){
  want=ES_PEER_INVALID;es_peer saved=peer;CHECK(es_arguments_check(is("null")?NULL:&peer,is("null-bytes")?NULL:is("overlap")?(unsigned char*)&peer:expected,n)==ES_PEER_INVALID);CHECK(!memcmp(&saved,&peer,sizeof(peer)));
 }else CHECK(es_arguments_check(&peer,expected,n)==want);
 if(want==ES_PEER_INVALID){CHECK(!memcmp(&before,&peer,sizeof(peer)));CHECK(rounds==0&&fcntl(peer.pidfd,F_GETFD)>=0);}
 if(reused>=0){CHECK(fcntl(reused,F_GETFD)>=0);CHECK(!__real_close(reused));}
 if(want==ES_PEER_OK){CHECK(rounds==2);CHECK(peer.state==1);}
 CHECK(fcntl(connection,F_GETFD)>=0&&fcntl(cancel_fd,F_GETFD)>=0);
 if(want==ES_PEER_CANCELLED){uint64_t one;CHECK(read(cancel_fd,&one,8)==8&&one==1);}
 es_peer_result closed=es_peer_close(&peer);CHECK(closed==ES_PEER_OK||closed==want);
 CHECK(!close(cancel_fd));return 0;
}

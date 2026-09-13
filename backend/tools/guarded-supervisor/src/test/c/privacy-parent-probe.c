#define _GNU_SOURCE
#include "privacy-parent.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <sys/vfs.h>
#include <time.h>
#include <unistd.h>
#define CHECK(x) do {if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}}while(0)
static pid_t parent_pid=-1,child_pid=-1;
static int listener=-1,accepted[2]={-1,-1},gate[2]={-1,-1};
static char directory[]="/tmp/es-parent-probe-XXXXXX",endpoint[108];
static const char *mode;
static int active=0,in_peer=0,record_fd=-1,record_number=0,closed_number=-1,close_count=0;
static int watched_child_pin=-1;
static int is(const char *name){return !strcmp(mode,name);}
static uint64_t now(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+t.tv_nsec;}
static void cleanup(void){
 if(child_pid>0)kill(child_pid,SIGKILL);
 if(parent_pid>0){kill(parent_pid,SIGKILL);waitpid(parent_pid,NULL,0);}
 if(child_pid>0)waitpid(child_pid,NULL,0);
 for(int i=0;i<2;i++){if(accepted[i]>=0)close(accepted[i]);if(gate[i]>=0)close(gate[i]);}
 if(listener>=0)close(listener);
 if(endpoint[0])unlink(endpoint);
 rmdir(directory);
}
static int connect_owned(void){
 int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(fd<0)_exit(41);
 struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,endpoint,strlen(endpoint)+1);
 if(connect(fd,(struct sockaddr*)&address,sizeof(address)))_exit(42);
 return fd;
}
static void setup(void){
 CHECK(!prctl(PR_SET_CHILD_SUBREAPER,1,0,0,0));CHECK(mkdtemp(directory));CHECK(!chmod(directory,0700));
 CHECK(snprintf(endpoint,sizeof(endpoint),"%s/control",directory)>0);
 struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,endpoint,strlen(endpoint)+1);
 listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(listener>=0);
 CHECK(!bind(listener,(struct sockaddr*)&address,sizeof(address)));CHECK(!listen(listener,4));CHECK(!pipe2(gate,O_CLOEXEC));
 parent_pid=fork();CHECK(parent_pid>=0);
 if(!parent_pid){
  close(gate[1]);close(listener);int parent_socket=connect_owned();
  pid_t child=fork();if(child<0)_exit(43);
  if(!child){close(parent_socket);if(is("comm")&&prctl(PR_SET_NAME,"a ) b\nc)",0,0,0))_exit(47);int child_socket=connect_owned();char b;ssize_t n=read(gate[0],&b,1);close(child_socket);_exit(n<0?44:0);}
  if(write(parent_socket,&child,sizeof(child))!=sizeof(child))_exit(45);
  char b;ssize_t n=read(gate[0],&b,1);close(parent_socket);_exit(n<0?46:0);
 }
 close(gate[0]);gate[0]=-1;
 accepted[0]=accept4(listener,NULL,NULL,SOCK_CLOEXEC);CHECK(accepted[0]>=0);
 CHECK(read(accepted[0],&child_pid,sizeof(child_pid))==sizeof(child_pid)&&child_pid>0);
 accepted[1]=accept4(listener,NULL,NULL,SOCK_CLOEXEC);CHECK(accepted[1]>=0);
}
es_peer_result __real_es_peer_read(es_peer *,es_peer_identity *);
es_peer_result __wrap_es_peer_read(es_peer *p,es_peer_identity *id){
 in_peer=1;es_peer_result r=__real_es_peer_read(p,id);in_peer=0;return r;
}
static void stop_parent(void){CHECK(parent_pid>0);CHECK(!kill(parent_pid,SIGKILL));CHECK(waitpid(parent_pid,NULL,0)==parent_pid);parent_pid=-1;}
static void stop_child(void){CHECK(child_pid>0);CHECK(!kill(child_pid,SIGKILL));struct pollfd p={.fd=watched_child_pin,.events=POLLIN};CHECK(watched_child_pin>=0&&poll(&p,1,1000)==1&&(p.revents&POLLIN));if(parent_pid<0){CHECK(waitpid(child_pid,NULL,0)==child_pid);child_pid=-1;}}
int __real_open(const char *,int,...);
int __wrap_open(const char *path,int flags,...){
 int record=active&&!in_peer&&strstr(path,"/stat");
 if(record){record_number++;CHECK((flags&(O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK))==(O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK));
  if(is("open-fault")){errno=EACCES;return -1;}
  if(is("second-parent-exit")&&record_number==2)stop_parent();
 }
 int fd=__real_open(path,flags);if(record&&fd>=0)record_fd=fd;return fd;
}
static size_t replace_token(char *text,size_t n,int field,const char *value,size_t cap){
 char *s=strrchr(text,')');CHECK(s);s+=2;
 for(int i=3;i<field;i++){s=strchr(s,' ');CHECK(s);s++;}
 char *end=strchr(s,' ');CHECK(end);size_t old=(size_t)(end-s),v=strlen(value);
 CHECK(n-old+v<cap);memmove(s+v,end,n-(size_t)(end-text));memcpy(s,value,v);return n-old+v;
}
ssize_t __real_read(int,void *,size_t);
ssize_t __wrap_read(int fd,void *data,size_t cap){
 if(fd!=record_fd||in_peer)return __real_read(fd,data,cap);
 if(is("read-fault")){errno=EIO;return -1;}
 if(is("oversized")){memset(data,'x',cap);return (ssize_t)cap;}
 if(is("partial")&&cap>3)cap=3;
 ssize_t n=__real_read(fd,data,cap);
 if(n>0){
  if(!is("partial")){CHECK((size_t)n<cap);((char*)data)[n]=0;}
  if(is("malformed")){memcpy(data,"bad\n",4);return 4;}
  if(is("embedded-nul"))((char*)data)[1]=0;
  if(is("wrong-parent")||(is("second-change")&&record_number==2))n=(ssize_t)replace_token(data,(size_t)n,4,"0",cap);
  if(is("overflow"))n=(ssize_t)replace_token(data,(size_t)n,4,"999999999999999999999999",cap);
  if(is("wrong-start"))n=(ssize_t)replace_token(data,(size_t)n,22,"0",cap);
 }
 if(!n&&(is("after-read-exit")||(is("final-read-exit")&&record_number==2)))stop_child();
 if(!n&&is("final-parent-exit")&&record_number==2)stop_parent();
 return n;
}
ssize_t __real___read_chk(int,void *,size_t,size_t);
ssize_t __wrap___read_chk(int fd,void *data,size_t cap,size_t available){
 CHECK(cap<=available);
 return active&&!in_peer&&fd==record_fd?__wrap_read(fd,data,cap):__real___read_chk(fd,data,cap,available);
}
int __real_fstatfs(int,struct statfs *);
int __wrap_fstatfs(int fd,struct statfs *out){int r=__real_fstatfs(fd,out);if(!r&&fd==record_fd&&is("wrong-fs"))out->f_type=0;return r;}
int __real_close(int);
int __wrap_close(int fd){
 int record=fd==record_fd&&!in_peer;if(record){record_fd=-1;closed_number=fd;close_count++;}
 int r=__real_close(fd);if(record&&is("close-fault")){errno=EINTR;return -1;}return r;
}
int main(int argc,char **argv){
 CHECK(argc==2);mode=argv[1];atexit(cleanup);setup();
 int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);CHECK(cancel>=0);uint64_t deadline=now()+5000000000ULL;
 es_peer parent={0},child={0};CHECK(es_peer_open(&parent,accepted[0],cancel,deadline)==ES_PEER_OK);
 CHECK(es_peer_open(&child,accepted[1],cancel,deadline)==ES_PEER_OK);
 watched_child_pin=child.pidfd;
 CHECK(parent.identity.pid==(uint32_t)parent_pid&&child.identity.pid==(uint32_t)child_pid);
 int foreign=-1;active=1;es_peer_result expected=ES_PEER_OK,result;
 if(is("cancel")){uint64_t one=1;CHECK(write(cancel,&one,sizeof(one))==sizeof(one));expected=ES_PEER_CANCELLED;}
 if(is("deadline")){child.deadline_ns=parent.deadline_ns=now()-1;expected=ES_PEER_DEADLINE;}
 if(is("parent-exit")){stop_parent();expected=ES_PEER_DEAD;}
 if(is("child-exit")){stop_child();expected=ES_PEER_DEAD;}
 if(is("second-parent-exit")||is("after-read-exit")||is("final-read-exit")||is("final-parent-exit"))expected=ES_PEER_DEAD;
 if(is("foreign-cancel")){foreign=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(foreign>=0);parent.cancel_fd=foreign;expected=ES_PEER_INVALID;}
 if(is("foreign-deadline")){parent.deadline_ns++;expected=ES_PEER_INVALID;}
 if(is("changed-start")){child.identity.start_ticks++;expected=ES_PEER_IDENTITY;}
 if(is("malformed")||is("embedded-nul")||is("wrong-parent")||is("second-change")||is("wrong-start")||is("overflow")||is("oversized")||is("open-fault")||is("wrong-fs"))expected=ES_PEER_IDENTITY;
 if(is("read-fault"))expected=ES_PEER_IO;
 if(is("close-fault"))expected=ES_PEER_CLEANUP;
 if(is("same")||is("null")||is("overlap")){
  es_peer before=child;
  result=es_parent_check(&child,is("same")?&child:is("null")?NULL:(es_peer*)((char*)&child+1));
  CHECK(result==ES_PEER_INVALID&&!memcmp(&child,&before,sizeof(child)));
 }else if(is("reverse")){CHECK(es_parent_check(&parent,&child)==ES_PEER_IDENTITY);}
 else if(is("grandparent")){
  int pair[2];CHECK(!socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair));es_peer grandparent={0};
  active=0;CHECK(es_peer_open(&grandparent,pair[0],cancel,deadline)==ES_PEER_OK);active=1;
  CHECK(grandparent.identity.pid==(uint32_t)getpid());CHECK(es_parent_check(&child,&grandparent)==ES_PEER_IDENTITY);
  CHECK(es_peer_close(&grandparent)==ES_PEER_OK);CHECK(!close(pair[0])&&!close(pair[1]));
 }
 else CHECK(es_parent_check(&child,&parent)==expected);
 active=0;
 if(expected==ES_PEER_OK&&!is("same")&&!is("null")&&!is("overlap")&&!is("reverse")&&!is("grandparent"))CHECK(record_number==2&&close_count==2);
 if(is("second-change"))CHECK(record_number==2&&close_count==2);
 int replacement=-1;
 if(is("close-fault")){CHECK(close_count==1&&closed_number>=0);int fd=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(fd>=0);replacement=dup2(fd,closed_number);CHECK(replacement==closed_number);if(fd!=replacement)CHECK(!close(fd));}
 es_peer_result c=es_peer_close(&child),p=es_peer_close(&parent);CHECK(c!=ES_PEER_CLEANUP&&p!=ES_PEER_CLEANUP);
 if(replacement>=0){CHECK(fcntl(replacement,F_GETFD)>=0&&close_count==1);CHECK(!close(replacement));}
 if(foreign>=0){CHECK(fcntl(foreign,F_GETFD)>=0);CHECK(!close(foreign));}
 if(is("cancel")){uint64_t value=0;CHECK(read(cancel,&value,sizeof(value))==sizeof(value)&&value==1);}
 CHECK(fcntl(accepted[0],F_GETFD)>=0&&fcntl(accepted[1],F_GETFD)>=0);
 CHECK(fcntl(cancel,F_GETFD)>=0);CHECK(!close(cancel));return 0;
}

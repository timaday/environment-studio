#define _GNU_SOURCE
#include "privacy-connection.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#ifndef SO_PEERPIDFD
#define SO_PEERPIDFD 77
#endif
#define CHECK(x) do{if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}}while(0)
static es_listener listener;static es_connection connection;
static int parent=-1,cancel=-1;static pid_t child=-1;
static char path[]="/tmp/es-connection-XXXXXX",owned_directory[36];
static const char *mode;
static int uncertain_fd=-1,uncertain_used,domain_calls;
static size_t received_bytes;
int __real_close(int);
int __wrap_close(int fd){int r=__real_close(fd);if(fd==uncertain_fd&&!uncertain_used){uncertain_used=1;errno=EINTR;return -1;}return r;}
static uint64_t now(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
static int is(const char *value){return !strcmp(mode,value);}
int __real_getsockopt(int,int,int,void*,socklen_t*);
int __wrap_getsockopt(int fd,int level,int option,void *value,socklen_t *length){
 if(is("unsupported")&&level==SOL_SOCKET&&option==SO_PEERPIDFD){errno=ENOPROTOOPT;return -1;}
 int result=__real_getsockopt(fd,level,option,value,length);
 if(!result&&is("identity-mismatch")&&level==SOL_SOCKET&&option==SO_PEERCRED)((struct ucred*)value)->pid=getpid();
 if(!result&&is("wire-setup")&&level==SOL_SOCKET&&option==SO_DOMAIN&&++domain_calls==2)*(int*)value=AF_INET;
 return result;
}
ssize_t __real_recvmsg(int,struct msghdr*,int);
ssize_t __wrap_recvmsg(int fd,struct msghdr *message,int flags){
 ssize_t result=__real_recvmsg(fd,message,flags);
 if(result>0&&is("death-during-prepare")){
  received_bytes+=(size_t)result;
  if(received_bytes>=12&&child>0){CHECK(!kill(child,SIGKILL));int status;CHECK(waitpid(child,&status,0)==child);child=-1;}
 }
 return result;
}
static int descriptors(void){DIR *dir=opendir("/proc/self/fd");CHECK(dir);int count=0;struct dirent *entry;while((entry=readdir(dir)))if(entry->d_name[0]!='.')count++;CHECK(!closedir(dir));return count;}
static void cleanup(void){
 if(connection.state)(void)es_connection_close(&connection,now()+1000000000ULL);
 if(listener.state)(void)es_listener_close(&listener,now()+1000000000ULL);
 if(child>0){int status;if(waitpid(child,&status,WNOHANG)==0){kill(child,SIGKILL);waitpid(child,&status,0);}}
 /* Independently owned fixture teardown, not proof of primitive cleanup. */
 if(parent>=0&&owned_directory[0]){int fd=openat(parent,owned_directory,O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);if(fd>=0){unlinkat(fd,"control.sock",0);close(fd);unlinkat(parent,owned_directory,AT_REMOVEDIR);}}
 if(parent>=0)close(parent);
 if(cancel>=0)close(cancel);
 rmdir(path);
}
static void child_run(const char *endpoint){
 close(parent);close(cancel);close(listener.listen_fd);close(listener.socket_path_fd);close(listener.directory_fd);
 int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,endpoint,strlen(endpoint)+1);
 if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof(address)))_exit(41);
 if(is("dead-before"))_exit(0);
 unsigned char prepare[24]={'E','S','P','R','V','0','0','1',1,0,0,0};
 if(is("malformed"))prepare[9]=1;
 if(is("duplicate")){memcpy(prepare+12,prepare,12);if(write(fd,prepare,24)!=24)_exit(42);}
 else if(is("truncated")){if(write(fd,prepare,7)!=7||shutdown(fd,SHUT_WR))_exit(42);}
 else if(is("ancillary")){
  int right=open("/dev/null",O_RDONLY|O_CLOEXEC);if(right<0)_exit(42);
  union{struct cmsghdr alignment;char bytes[CMSG_SPACE(sizeof(int))];} control={0};
  struct iovec vec={prepare,12};struct msghdr message={.msg_iov=&vec,.msg_iovlen=1,.msg_control=control.bytes,.msg_controllen=sizeof(control.bytes)};
  struct cmsghdr *c=CMSG_FIRSTHDR(&message);c->cmsg_level=SOL_SOCKET;c->cmsg_type=SCM_RIGHTS;c->cmsg_len=CMSG_LEN(sizeof(int));memcpy(CMSG_DATA(c),&right,sizeof(right));
  if(sendmsg(fd,&message,MSG_NOSIGNAL)!=12)_exit(42);
  close(right);
 }else if(!is("missing"))for(int i=0;i<3;i++){if(send(fd,prepare+i*4,4,MSG_NOSIGNAL)!=4)_exit(42);usleep(2000);}
 struct timeval timeout={.tv_sec=5};if(setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout)))_exit(43);
 char byte;int result=read(fd,&byte,1)==0?0:44;close(fd);_exit(result);
}
static void signal_cancel(void){uint64_t one=1;CHECK(write(cancel,&one,sizeof(one))==sizeof(one));}
int main(int argc,char **argv){
 CHECK(argc==2);mode=argv[1];CHECK(mkdtemp(path));CHECK(!atexit(cleanup));
 parent=open(path,O_PATH|O_DIRECTORY|O_CLOEXEC);cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);CHECK(parent>=0&&cancel>=0);
 uint64_t allowance=(is("missing")||is("expired-read"))?300000000ULL:3000000000ULL;
 CHECK(es_listener_open(&listener,parent,path,cancel,now()+allowance,1)==ES_LISTENER_OK);memcpy(owned_directory,listener.directory_name,sizeof(owned_directory));
 char endpoint[104];CHECK(es_listener_path(&listener,endpoint)==ES_LISTENER_OK);
 child=fork();CHECK(child>=0);if(!child)child_run(endpoint);
 uint64_t token;CHECK(es_listener_accept(&listener,&token)==ES_LISTENER_ACCEPTED);int before=descriptors();
 if(is("dead-before")){int status;CHECK(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0);child=-1;CHECK(es_connection_open(&connection,&listener,token)==ES_CONNECTION_DEAD);CHECK(descriptors()==before-1);return 0;}
 if(is("cancel-before")){signal_cancel();CHECK(es_connection_open(&connection,&listener,token)==ES_CONNECTION_CANCELLED);CHECK(connection.state==0&&listener.live==1);return 0;}
 if(is("wrong-token")){CHECK(es_connection_open(&connection,&listener,token^(1ULL<<32))==ES_CONNECTION_INVALID);CHECK(connection.state==0&&listener.live==1);return 0;}
 if(is("unsupported")||is("identity-mismatch")||is("wire-setup")){
  es_connection_result expected=is("unsupported")?ES_CONNECTION_PLATFORM:is("identity-mismatch")?ES_CONNECTION_IDENTITY:ES_CONNECTION_PROTOCOL;
  CHECK(es_connection_open(&connection,&listener,token)==expected);CHECK(descriptors()==before-1&&listener.live==0);
  CHECK(es_connection_close(&connection,now()+1000000000ULL)==ES_CONNECTION_CLOSED_COMPLETE);return 0;
 }
 CHECK(es_connection_open(&connection,&listener,token)==ES_CONNECTION_OK);int socket_fd=connection.wire.fd,pin_fd=connection.peer.pidfd;
 if(is("reopen")){CHECK(es_connection_open(&connection,&listener,token)==ES_CONNECTION_INVALID);CHECK(connection.wire.fd==socket_fd&&connection.peer.pidfd==pin_fd);}
 if(is("output-alias")||is("output-listener-alias")){
  es_peer_identity *aliased=(es_peer_identity*)(is("output-alias")?(void*)&connection:(void*)&listener);
  CHECK(es_connection_prepare(&connection,aliased)==ES_CONNECTION_INVALID);
  CHECK(fcntl(socket_fd,F_GETFD)==-1&&fcntl(pin_fd,F_GETFD)==-1&&listener.live==0);
  CHECK(es_connection_close(&connection,now()+1000000000ULL)==ES_CONNECTION_CLOSED_COMPLETE);return 0;
 }
 es_peer_identity identity={0};es_connection_result result=es_connection_prepare(&connection,&identity);
 if(is("malformed")||is("duplicate")||is("truncated")||is("ancillary")){CHECK(result==ES_CONNECTION_PROTOCOL);CHECK(!identity.pid&&!identity.uid&&!identity.gid&&!identity.start_ticks);}
 else if(is("missing")){CHECK(result==ES_CONNECTION_DEADLINE);CHECK(!identity.pid&&!identity.start_ticks);}
 else if(is("death-during-prepare")){CHECK(result==ES_CONNECTION_DEAD);CHECK(!identity.pid&&!identity.uid&&!identity.gid&&!identity.start_ticks);}
 else {
  CHECK(result==ES_CONNECTION_PREPARE_RECEIVED);CHECK(identity.pid==(uint32_t)child&&identity.uid==getuid()&&identity.gid==getgid()&&identity.start_ticks>0);CHECK(listener.live==1);
  if(is("post-dead")){CHECK(!kill(child,SIGKILL));int status;CHECK(waitpid(child,&status,0)==child);child=-1;CHECK(es_connection_read(&connection,&identity)==ES_CONNECTION_DEAD);}
  else if(is("cancel-ready")){signal_cancel();CHECK(es_connection_read(&connection,&identity)==ES_CONNECTION_CANCELLED);}
  else if(is("expired-read")){usleep(350000);CHECK(es_connection_read(&connection,&identity)==ES_CONNECTION_DEADLINE);}
  else if(is("read-alias")){CHECK(es_connection_read(&connection,(es_peer_identity*)&connection)==ES_CONNECTION_INVALID);CHECK(es_connection_read(&connection,&identity)==ES_CONNECTION_INVALID);}
  else if(is("null")){CHECK(es_connection_read(&connection,NULL)==ES_CONNECTION_INVALID);CHECK(es_connection_read(&connection,&identity)==ES_CONNECTION_INVALID);}
  else if(is("repeat-prepare")){CHECK(es_connection_prepare(&connection,&identity)==ES_CONNECTION_INVALID);}
  else if(is("listener-first")){CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_INCONCLUSIVE);CHECK(fcntl(socket_fd,F_GETFD)>=0);CHECK(es_connection_read(&connection,&identity)!=ES_CONNECTION_PREPARE_RECEIVED);}
  else CHECK(es_connection_read(&connection,&identity)==ES_CONNECTION_PREPARE_RECEIVED);
  if(is("post-dead")||is("cancel-ready")||is("expired-read")||is("null")||is("read-alias")||is("repeat-prepare")||is("listener-first"))CHECK(!identity.pid&&!identity.uid&&!identity.gid&&!identity.start_ticks);
 }
 if(is("wire-close-uncertain"))uncertain_fd=socket_fd;
 if(is("peer-close-uncertain"))uncertain_fd=pin_fd;
 es_connection_cleanup expected=(is("wire-close-uncertain")||is("peer-close-uncertain")||is("listener-first")||is("expired-close"))?ES_CONNECTION_CLOSED_INCONCLUSIVE:ES_CONNECTION_CLOSED_COMPLETE;
 CHECK(es_connection_close(&connection,is("expired-close")?now()-1:now()+1000000000ULL)==expected);
 CHECK(fcntl(socket_fd,F_GETFD)==-1&&fcntl(pin_fd,F_GETFD)==-1);CHECK(listener.live==0);
 if(uncertain_fd>=0){CHECK(uncertain_used);int replacement=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(replacement>=0);int reused=replacement==uncertain_fd?replacement:dup3(replacement,uncertain_fd,O_CLOEXEC);CHECK(reused==uncertain_fd);CHECK(es_connection_close(&connection,now()+1000000000ULL)==expected);CHECK(fcntl(reused,F_GETFD)>=0);CHECK(!close(reused));if(replacement!=reused)CHECK(!close(replacement));}
 CHECK(es_connection_close(&connection,0)==expected);
 if(!is("listener-first"))CHECK(descriptors()==before-1);
 es_listener_cleanup listener_expected=(is("wire-close-uncertain")||is("listener-first"))?ES_LISTENER_CLOSED_INCONCLUSIVE:ES_LISTENER_CLOSED_COMPLETE;
 CHECK(es_listener_close(&listener,now()+1000000000ULL)==listener_expected);
 if(is("cancel-ready")){uint64_t one=0;CHECK(read(cancel,&one,sizeof(one))==sizeof(one)&&one==1);}
 if(child>0){int status;CHECK(waitpid(child,&status,0)==child);if(is("live")||is("reopen"))CHECK(WIFEXITED(status)&&WEXITSTATUS(status)==0);child=-1;}
 return 0;
}

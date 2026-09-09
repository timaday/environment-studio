#define _GNU_SOURCE
#include "privacy-listener.h"
#include <errno.h>
#include <dirent.h>
#include <stdarg.h>
#include <sys/syscall.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <poll.h>
#include <time.h>
#include <unistd.h>
#define CHECK(x) do{if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}}while(0)
static es_listener listener;
static const char *mode;
static int uncertain_fd=-1,uncertain_used;
int __real_close(int);
int __wrap_close(int fd){int r=__real_close(fd);if(fd==uncertain_fd&&!uncertain_used){uncertain_used=1;errno=EINTR;return -1;}return r;}
long __real_syscall(long,...);
long __wrap_syscall(long number,...){
 va_list args;va_start(args,number);int fd=va_arg(args,int);const char *path=va_arg(args,const char*);unsigned int permissions=va_arg(args,unsigned int);int flags=va_arg(args,int);va_end(args);
 if(!strcmp(mode,"unsupported")){errno=ENOSYS;return -1;}return __real_syscall(number,fd,path,permissions,flags);
}
static char parent_path[128]="/tmp/es-listener-probe-XXXXXX";
static int parent=-1,cancel=-1,client=-1;
static uint64_t now(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
static void cleanup(void){
 if(client>=0)close(client);
 if(listener.state)(void)es_listener_close(&listener,now()+1000000000ULL);
 if(parent>=0)close(parent);
 if(cancel>=0)close(cancel);
 /* Remove only independently invented fixture entries in this test-owned root.
    This is test teardown, not a retry of the primitive's cleanup claim. */
 int fixture=open(parent_path,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);
 if(fixture>=0){DIR *entries=fdopendir(fixture);if(entries){struct dirent *entry;while((entry=readdir(entries))){
  if(!strncmp(entry->d_name,"es-",3)||!strcmp(entry->d_name,"held-dir")){
   int child=openat(fixture,entry->d_name,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);
   if(child>=0){unlinkat(child,"control.sock",0);unlinkat(child,"held",0);close(child);unlinkat(fixture,entry->d_name,AT_REMOVEDIR);}
  }
 }closedir(entries);}else close(fixture);}
 (void)rmdir(parent_path);
}
int main(int argc,char **argv){
 CHECK(argc==2);mode=argv[1];if(!strcmp(mode,"maximum-path")){memcpy(parent_path,"/tmp/",5);memset(parent_path+5,'a',43);memcpy(parent_path+48,"XXXXXX",7);}CHECK(mkdtemp(parent_path));CHECK(!atexit(cleanup));
 parent=open(parent_path,O_PATH|O_DIRECTORY|O_CLOEXEC);cancel=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(parent>=0&&cancel>=0);
 if(!strcmp(mode,"bad-parent-mode")){CHECK(!chmod(parent_path,0755));CHECK(es_listener_open(&listener,parent,parent_path,cancel,now()+1000000000ULL,1)==ES_LISTENER_IDENTITY);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);return 0;}
 if(!strcmp(mode,"symlink-parent")){char alias[128];CHECK(snprintf(alias,sizeof(alias),"%s/link",parent_path)>0);CHECK(!symlink(parent_path,alias));CHECK(es_listener_open(&listener,parent,alias,cancel,now()+1000000000ULL,1)==ES_LISTENER_IDENTITY);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);CHECK(!unlink(alias));return 0;}
 if(!strcmp(mode,"invalid")){char excessive[105];memset(excessive,'a',104);excessive[0]='/';excessive[104]=0;CHECK(es_listener_open(&listener,parent,excessive,cancel,now()+1000000000ULL,1)==ES_LISTENER_INVALID&&listener.state==0);CHECK(es_listener_open(&listener,parent,parent_path,cancel,now()+1000000000ULL,0)==ES_LISTENER_INVALID&&listener.state==0);CHECK(es_listener_open(&listener,parent,parent_path,cancel,now()+1000000000ULL,17)==ES_LISTENER_INVALID&&listener.state==0);CHECK(es_listener_open(&listener,parent,parent_path,cancel,UINT64_MAX,1)==ES_LISTENER_INVALID);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);return 0;}
 uint64_t deadline=now()+1000000000ULL;
 if(!strcmp(argv[1],"deadline"))deadline=now()-1;
 if(!strcmp(mode,"umask"))umask(0777);
 es_listener_result result=es_listener_open(&listener,parent,parent_path,cancel,deadline,1);
 if(!strcmp(mode,"unsupported")){CHECK(result==ES_LISTENER_PLATFORM);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);return 0;}
 if(!strcmp(argv[1],"deadline")){CHECK(result==ES_LISTENER_DEADLINE);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);return 0;}
 CHECK(result==ES_LISTENER_OK);
 if(!strcmp(mode,"umask")){mode_t inherited=umask(0777);CHECK(inherited==0777);}
 char path[104];CHECK(es_listener_path(&listener,path)==ES_LISTENER_OK);CHECK(strlen(path)<=103);if(!strcmp(mode,"maximum-path"))CHECK(strlen(path)==103);
 struct stat info;CHECK(!fstatat(parent,listener.directory_name,&info,AT_SYMLINK_NOFOLLOW));CHECK(S_ISDIR(info.st_mode)&&(info.st_mode&07777)==0700&&info.st_uid==geteuid());
 CHECK(!lstat(path,&info));CHECK(S_ISSOCK(info.st_mode)&&(info.st_mode&07777)==0600&&info.st_uid==geteuid());
 if(!strcmp(mode,"cancel")){uint64_t one=1,token=99;CHECK(write(cancel,&one,8)==8);CHECK(es_listener_accept(&listener,&token)==ES_LISTENER_CANCELLED&&token==0);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);CHECK(read(cancel,&one,8)==8&&one==1);return 0;}
 if(!strcmp(mode,"substitute")){
  int owned=openat(listener.directory_fd,".",O_RDONLY|O_DIRECTORY|O_CLOEXEC);CHECK(owned>=0);CHECK(!renameat(owned,"control.sock",owned,"held"));int replacement=openat(owned,"control.sock",O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600);CHECK(replacement>=0);CHECK(!close(replacement));
  uint64_t token;CHECK(es_listener_accept(&listener,&token)==ES_LISTENER_IDENTITY);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_INCONCLUSIVE);CHECK(!fstatat(owned,"control.sock",&info,AT_SYMLINK_NOFOLLOW)&&S_ISREG(info.st_mode));CHECK(!close(owned));return 0;
 }
 if(!strcmp(mode,"child")){
  pid_t child=fork();CHECK(child>=0);if(!child){int connection=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,path,strlen(path)+1);if(connection<0||connect(connection,(struct sockaddr*)&address,sizeof(address))||write(connection,"Q",1)!=1)_exit(41);close(connection);_exit(0);}
  uint64_t token;int fd;CHECK(es_listener_accept(&listener,&token)==ES_LISTENER_ACCEPTED);CHECK(es_listener_borrow(&listener,token,&fd)==ES_LISTENER_OK);struct pollfd ready={.fd=fd,.events=POLLIN};CHECK(poll(&ready,1,1000)==1);char marker=0;CHECK(read(fd,&marker,1)==1&&marker=='Q');int status;CHECK(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);return 0;
 }
 if(!strcmp(mode,"saturation")){
  int sockets[32],count=0,blocked=0;struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,path,strlen(path)+1);
  for(int i=0;i<32;i++){int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_NONBLOCK|SOCK_CLOEXEC,0);CHECK(fd>=0);sockets[count++]=fd;if(connect(fd,(struct sockaddr*)&address,sizeof(address))){CHECK(errno==EAGAIN||errno==EWOULDBLOCK);blocked=1;break;}}
  CHECK(blocked&&count>1);uint64_t first,extra=99;CHECK(es_listener_accept(&listener,&first)==ES_LISTENER_ACCEPTED);CHECK(es_listener_accept(&listener,&extra)==ES_LISTENER_CAPACITY&&extra==0);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);for(int i=0;i<count;i++)CHECK(!close(sockets[i]));return 0;
 }
 if(!strcmp(mode,"generations")){
  uint64_t old=0;for(unsigned i=0;i<64;i++){int connection=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(connection>=0);struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,path,strlen(path)+1);CHECK(!connect(connection,(struct sockaddr*)&address,sizeof(address)));uint64_t token;CHECK(es_listener_accept(&listener,&token)==ES_LISTENER_ACCEPTED&&token!=old);if(old){int ignored=99;CHECK(es_listener_borrow(&listener,old,&ignored)==ES_LISTENER_INVALID&&ignored==-1);}CHECK(es_listener_close_peer(&listener,token)==ES_LISTENER_CLOSED_COMPLETE);CHECK(!close(connection));old=token;}
  uint64_t extra=99;CHECK(es_listener_accept(&listener,&extra)==ES_LISTENER_CAPACITY&&extra==0);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);return 0;
 }
 client=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(client>=0);struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,path,strlen(path)+1);CHECK(!connect(client,(struct sockaddr*)&address,sizeof(address)));
 uint64_t token=0;CHECK(es_listener_accept(&listener,&token)==ES_LISTENER_ACCEPTED&&token!=0);int fd=-1;CHECK(es_listener_borrow(&listener,token,&fd)==ES_LISTENER_OK);CHECK(fd>=0&&(fcntl(fd,F_GETFD)&FD_CLOEXEC)&&(fcntl(fd,F_GETFL)&O_NONBLOCK));
 if(!strcmp(mode,"capacity")){uint64_t extra=99;CHECK(es_listener_accept(&listener,&extra)==ES_LISTENER_CAPACITY&&extra==0);}
 CHECK(es_listener_close_peer(&listener,token)==ES_LISTENER_CLOSED_COMPLETE);CHECK(es_listener_close_peer(&listener,token)==ES_LISTENER_CLOSED_COMPLETE);
 if(!strcmp(mode,"close-uncertain")){
  uncertain_fd=listener.listen_fd;CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_INCONCLUSIVE);int replacement=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(replacement>=0);int same=fcntl(replacement,F_DUPFD_CLOEXEC,uncertain_fd);CHECK(same==uncertain_fd);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_INCONCLUSIVE);CHECK(fcntl(same,F_GETFD)>=0);CHECK(!close(same));CHECK(!close(replacement));return 0;
 }
 if(!strcmp(mode,"cleanup-expired")){
  int listening=listener.listen_fd,pin=listener.socket_path_fd,directory=listener.directory_fd;
  CHECK(es_listener_close(&listener,now()-1)==ES_LISTENER_CLOSED_INCONCLUSIVE);CHECK(fcntl(listening,F_GETFD)==-1&&fcntl(pin,F_GETFD)==-1&&fcntl(directory,F_GETFD)==-1);CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_INCONCLUSIVE);return 0;
 }
 CHECK(es_listener_close(&listener,now()+1000000000ULL)==ES_LISTENER_CLOSED_COMPLETE);
 if(!strcmp(mode,"tombstone")){char ignored[104];CHECK(es_listener_path(&listener,ignored)!=ES_LISTENER_OK);}
 CHECK(es_listener_close(&listener,0)==ES_LISTENER_CLOSED_COMPLETE);
 CHECK(lstat(path,&info)==-1&&errno==ENOENT);CHECK(fcntl(parent,F_GETFD)>=0&&fcntl(cancel,F_GETFD)>=0);return 0;
}

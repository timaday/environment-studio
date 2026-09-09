#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/eventfd.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <poll.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <signal.h>
#define REQUIRE(x) do {if(!(x)){fprintf(stderr,"INDEPENDENT_SCRIPT_%d\n",__LINE__);exit(40);}} while(0)
#ifdef INDEPENDENT_CHILD
__attribute__((constructor)) static void hold(void){
 const char *endpoint=getenv("INDEPENDENT_CHANNEL");if(!endpoint)_exit(41);
 struct sockaddr_un address={.sun_family=AF_UNIX};if(strlen(endpoint)>=sizeof address.sun_path)_exit(42);
 strcpy(address.sun_path,endpoint);int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);
 if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof address)||write(fd,"R",1)!=1)_exit(43);
 char byte;if(read(fd,&byte,1)!=1||byte!='Q')_exit(44);
 if(close(fd))_exit(45);
}
int main(void){return write(STDOUT_FILENO,"M",1)==1?0:46;}
#else
#include "privacy-script.h"
#include "independent-script-expected.h"
static pid_t child=-1;
static int listener=-1,accepted=-1,cancel_fd=-1,output_fd=-1,reused=-1,tracked=-1;
static es_peer peer;static es_file script,interpreter;static es_hash hash;
static char directory[]="/tmp/es-script-independent-XXXXXX",path[256],endpoint[108];
static const char *mode;static int active,hash_calls,uncertain,opened_after_failure,reused_closes;
extern es_hash_result __real_es_hash_file(es_hash *,int,es_hash_identity *);
extern int __real_close(int);
extern int __real_open(const char *,int,...);
static int is(const char *value){return !strcmp(value,mode);}
es_hash_result __wrap_es_hash_file(es_hash *owner,int fd,es_hash_identity *identity){
 es_hash_result result=__real_es_hash_file(owner,fd,identity);
 if(active){
  hash_calls++;
  if(hash_calls==5){tracked=fd;if(is("device"))identity->device^=1;}
 }
 return result;
}
int __wrap_open(const char *name,int flags,...){
 REQUIRE(!(flags&O_CREAT));int fd=__real_open(name,flags);
 if(active&&is("earlier-identity-cleanup")&&hash_calls==1&&strstr(name,"/stat")){
  REQUIRE(fd>=0);tracked=fd;opened_after_failure++;
 }
 return fd;
}
int __wrap___open_2(const char *name,int flags){return __wrap_open(name,flags);}
int __wrap_close(int fd){
 if(active&&fd==reused)reused_closes++;
 int result=__real_close(fd),saved=errno;
 if(active&&fd==tracked&&!result&&!uncertain&&(is("resource-cleanup")||is("earlier-identity-cleanup"))){
  uncertain=1;tracked=-1;reused=__real_open("/dev/null",O_RDONLY|O_CLOEXEC);REQUIRE(reused>=0);
  if(reused!=fd){REQUIRE(dup3(reused,fd,O_CLOEXEC)==fd);REQUIRE(!__real_close(reused));reused=fd;}
  if(is("resource-cleanup")){uint64_t one=1;REQUIRE(write(cancel_fd,&one,sizeof one)==sizeof one);}
  errno=EINTR;return -1;
 }
 errno=saved;return result;
}
static void cleanup(void){
 active=0;
 if(accepted>=0)close(accepted);
 if(listener>=0)close(listener);
 if(output_fd>=0)close(output_fd);
 if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);}
 if(hash.state)es_hash_close(&hash);
 if(script.state)es_file_close(&script);
 if(interpreter.state)es_file_close(&interpreter);
 if(peer.state)es_peer_close(&peer);
 if(cancel_fd>=0)close(cancel_fd);
 if(reused>=0)close(reused);
 if(path[0])unlink(path);
 if(endpoint[0])unlink(endpoint);
 rmdir(directory);
}
static void ready(int fd){struct pollfd poller={.fd=fd,.events=POLLIN};REQUIRE(poll(&poller,1,5000)==1);}
static uint64_t now(void){struct timespec clock;REQUIRE(!clock_gettime(CLOCK_MONOTONIC,&clock));return (uint64_t)clock.tv_sec*1000000000ULL+(uint64_t)clock.tv_nsec;}
int main(int argc,char **argv){
 REQUIRE(argc==3);mode=argv[2];REQUIRE(!atexit(cleanup));REQUIRE(mkdtemp(directory));REQUIRE(!chmod(directory,0700));
 REQUIRE(snprintf(path,sizeof path,"%s/überlapping script.sh",directory)>0);
 REQUIRE(snprintf(endpoint,sizeof endpoint,"%s/control.sock",directory)>0);
 char prefix[512],source[600];REQUIRE(snprintf(prefix,sizeof prefix,"#!%s\n",argv[1])>0);
 int size=snprintf(source,sizeof source,"%s# independent source\n",prefix);REQUIRE(size>0&&(size_t)size<sizeof source);
 int file=openat(AT_FDCWD,path,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0700);REQUIRE(file>=0);REQUIRE(!fchmod(file,0700));
 REQUIRE(write(file,source,(size_t)size)==size);REQUIRE(!close(file));
 struct sockaddr_un address={.sun_family=AF_UNIX};strcpy(address.sun_path,endpoint);
 listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);REQUIRE(listener>=0);REQUIRE(!bind(listener,(struct sockaddr*)&address,sizeof address));REQUIRE(!listen(listener,1));
 int outputs[2];REQUIRE(!pipe2(outputs,O_CLOEXEC));child=fork();REQUIRE(child>=0);
 if(!child){
  if(dup2(outputs[1],STDOUT_FILENO)<0)_exit(47);
  close(outputs[0]);close(outputs[1]);if(setenv("INDEPENDENT_CHANNEL",endpoint,1))_exit(48);
  char *arguments[]={path,"mock-argument",NULL};execv(path,arguments);_exit(49);
 }
 close(outputs[1]);output_fd=outputs[0];REQUIRE(!fcntl(output_fd,F_SETFL,O_NONBLOCK));
 ready(listener);accepted=accept4(listener,NULL,NULL,SOCK_CLOEXEC);REQUIRE(accepted>=0);ready(accepted);
 char byte;REQUIRE(read(accepted,&byte,1)==1&&byte=='R');REQUIRE(read(output_fd,&byte,1)==-1&&errno==EAGAIN);
 cancel_fd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);REQUIRE(cancel_fd>=0);uint64_t deadline=now()+10000000000ULL;
 REQUIRE(es_peer_open(&peer,accepted,cancel_fd,deadline)==ES_PEER_OK);REQUIRE(peer.identity.pid==(uint32_t)child);
 REQUIRE(es_file_open(&script,cancel_fd,deadline,path,strlen(path))==ES_FILE_OK);
 REQUIRE(es_file_open(&interpreter,cancel_fd,deadline,argv[1],strlen(argv[1]))==ES_FILE_OK);
 REQUIRE(es_hash_open(&hash,cancel_fd,deadline)==ES_HASH_OK);
 unsigned char arguments[1024]={0};size_t used=0,path_offset=0;
 const char *parts[]={argv[1],path,"mock-argument"};
 for(unsigned i=0;i<3;i++){size_t n=strlen(parts[i])+1;REQUIRE(used+n<=sizeof arguments);if(i==1)path_offset=used;memcpy(arguments+used,parts[i],n);used+=n;}
 es_script_expected expected={.script_path=(const char*)arguments+path_offset,.script_path_length=strlen(path),
  .shebang=(const unsigned char*)prefix,.shebang_length=strlen(prefix),.arguments=arguments,.arguments_length=used,.script_index=1};
 memcpy(expected.script_sha256,script_sha,32);memcpy(expected.interpreter_sha256,interpreter_sha,32);
 if(is("resource-cleanup"))hash.objects=508;
 if(is("earlier-identity-cleanup"))expected.script_sha256[0]^=1;
 REQUIRE(lseek(script.fd,7,SEEK_SET)==7);REQUIRE(lseek(interpreter.fd,13,SEEK_SET)==13);
 unsigned char saved_arguments[1024];memcpy(saved_arguments,arguments,sizeof arguments);
 es_script_identity out;memset(&out,0xa5,sizeof out);active=1;
 es_script_result result=es_script_check(&peer,&script,&interpreter,&hash,&expected,&out);active=0;
 REQUIRE(read(output_fd,&byte,1)==-1&&errno==EAGAIN);REQUIRE(!memcmp(saved_arguments,arguments,sizeof arguments));
 REQUIRE(lseek(script.fd,0,SEEK_CUR)==7&&lseek(interpreter.fd,0,SEEK_CUR)==13);
 if(is("overlap")){
  REQUIRE(result==ES_SCRIPT_OK&&hash.objects==5&&hash_calls==5);struct stat script_stat,interpreter_stat;
  REQUIRE(!fstat(script.fd,&script_stat)&&!fstat(interpreter.fd,&interpreter_stat));
  REQUIRE(out.script.device==(uint64_t)script_stat.st_dev&&out.script.inode==(uint64_t)script_stat.st_ino&&out.script.size==(uint64_t)script_stat.st_size&&!memcmp(out.script.sha256,script_sha,32));
  REQUIRE(out.interpreter.device==(uint64_t)interpreter_stat.st_dev&&out.interpreter.inode==(uint64_t)interpreter_stat.st_ino&&out.interpreter.size==(uint64_t)interpreter_stat.st_size&&!memcmp(out.interpreter.sha256,interpreter_sha,32));
 }else{
  es_script_identity zero={0};REQUIRE(!memcmp(&out,&zero,sizeof out));
  if(is("device")){REQUIRE(result==ES_SCRIPT_IDENTITY&&hash_calls==5);}
  else{
   REQUIRE(result==ES_SCRIPT_CLEANUP&&uncertain==1&&reused_closes==0&&fcntl(reused,F_GETFD)>=0);
   if(is("resource-cleanup")){REQUIRE(hash.objects==512&&hash.terminal==ES_HASH_RESOURCE&&hash_calls==5);}
   else{REQUIRE(is("earlier-identity-cleanup")&&opened_after_failure==1&&hash_calls==1);}
  }
 }
 REQUIRE(write(accepted,"Q",1)==1);ready(output_fd);REQUIRE(read(output_fd,&byte,1)==1&&byte=='M');
 int status;REQUIRE(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0);child=-1;
 return 0;
}
#endif

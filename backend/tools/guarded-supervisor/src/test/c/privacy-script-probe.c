#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/eventfd.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <poll.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <time.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <stdarg.h>
#define CHECK(x) do {if(!(x)){fprintf(stderr,"SCRIPT_ASSERT_%d\n",__LINE__);exit(40);}} while(0)
#ifdef ES_SCRIPT_CHILD
int main(int argc,char **argv){
 int index=argc==4&&!strcmp(argv[1],"-e")?2:1;
 if(argc<index+2)return 41;
 struct sockaddr_un address={.sun_family=AF_UNIX};size_t size=strlen(argv[index+1]);
 if(size>=sizeof address.sun_path)return 41;
 memcpy(address.sun_path,argv[index+1],size+1);
 int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);
 if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof address)||write(fd,"R",1)!=1)return 41;
 char byte;ssize_t count;
 while((count=read(fd,&byte,1))==1){
  if(byte=='A'){argv[index+1][0]='X';if(write(fd,"R",1)!=1)return 41;}else break;
 }
 close(fd);return count<0?41:0;
}
#else
#include "privacy-script.h"
#include "script-expected.h"
static pid_t child=-1;
static int listener=-1,accepted=-1,cancel_fd=-1,foreign=-1;
static char directory[]="/tmp/es-script-probe-XXXXXX",endpoint[108],path[2048],other[160],empty[160],nested[16][2048];
static unsigned nested_count;
static es_peer peer;static es_file script,interpreter;static es_hash hash;
static const char *mode;
static int active,reopened_fd=-1,reused=-1,reused_closes,fired,expired,prefix_seen,argv_reads;
extern int __real_open(const char *,int,...);
extern int __real_openat(int,const char *,int,...);
extern int __real_close(int);
extern ssize_t __real_pread(int,void *,size_t,off_t);
extern int __real_clock_gettime(clockid_t,struct timespec *);
static int is(const char *name){return !strcmp(mode,name);}
static void cancel(void){uint64_t one=1;CHECK(write(cancel_fd,&one,sizeof one)==sizeof one);}
static void death(void){CHECK(child>0);CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=-1;}
static void changed_arguments(void){
 CHECK(write(accepted,"A",1)==1);struct pollfd p={.fd=accepted,.events=POLLIN};CHECK(poll(&p,1,5000)==1);
 char b;CHECK(read(accepted,&b,1)==1&&b=='R');
}
int __wrap_open(const char *name,int flags,...){
 if(active&&strstr(name,"/cmdline")){
  argv_reads++;
  if((is("args-change-second")&&argv_reads==2)||(is("args-change-final")&&argv_reads==4))changed_arguments();
 }
 if(flags&O_CREAT){va_list a;va_start(a,flags);mode_t m=(mode_t)va_arg(a,int);va_end(a);return __real_open(name,flags,m);}
 return __real_open(name,flags);
}
int __wrap___open_2(const char *name,int flags){CHECK(!(flags&O_CREAT));return __wrap_open(name,flags);}
int __wrap_openat(int parent,const char *name,int flags,...){
 CHECK(!(flags&O_CREAT));int fd=__real_openat(parent,name,flags);
 if(active&&fd>=0&&!strcmp(name,strrchr(path,'/')+1))reopened_fd=fd;
 return fd;
}
int __wrap___openat_2(int parent,const char *name,int flags){return __wrap_openat(parent,name,flags);}
ssize_t __wrap_pread(int fd,void *buffer,size_t count,off_t offset){
 if(active&&fd==script.fd&&count<=255){
  prefix_seen++;
  if(is("prefix-eintr")&&prefix_seen==1){errno=EINTR;return -1;}
  if(is("prefix-io")){errno=EIO;return -1;}
  if(is("prefix-eof"))return 0;
  if(is("prefix-partial")&&count>3)count=3;
 }
 return __real_pread(fd,buffer,count,offset);
}
ssize_t __wrap___pread_chk(int fd,void *buffer,size_t count,off_t offset,size_t available){CHECK(count<=available);return __wrap_pread(fd,buffer,count,offset);}
int __wrap_clock_gettime(clockid_t clock,struct timespec *value){
 int r=__real_clock_gettime(clock,value);if(active&&expired&&!r&&clock==CLOCK_MONOTONIC)value->tv_sec+=20;return r;
}
int __wrap_close(int fd){
 if(active&&fd==reused)reused_closes++;
 int final=active&&fd==reopened_fd;if(final)reopened_fd=-1;
 int r=__real_close(fd),error=errno;
 if(final&&!r&&!fired){
  fired=1;
  if(is("cancel-final")||is("close-cancel"))cancel();
  if(is("deadline-final"))expired=1;
  if(is("death-final"))death();
  if(is("close-fault")||is("close-cancel")){
   reused=__real_open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(reused>=0);
   if(reused!=fd){CHECK(dup3(reused,fd,O_CLOEXEC)==fd);CHECK(!__real_close(reused));reused=fd;}
   CHECK(fcntl(reused,F_GETFD)>=0);errno=EINTR;return -1;
  }
 }
 errno=error;return r;
}
static uint64_t now(void){struct timespec t;CHECK(!__real_clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
static void cleanup(void){
 active=0;
 if(accepted>=0)close(accepted);
 if(listener>=0)close(listener);
 if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);}
 if(hash.state)es_hash_close(&hash);
 if(script.state)es_file_close(&script);
 if(interpreter.state)es_file_close(&interpreter);
 if(peer.state)es_peer_close(&peer);
 if(cancel_fd>=0)close(cancel_fd);
 if(foreign>=0)close(foreign);
 if(reused>=0)close(reused);
 if(other[0])unlink(other);
 if(empty[0])unlink(empty);
 if(path[0])unlink(path);
 if(endpoint[0])unlink(endpoint);
 for(unsigned i=nested_count;i>0;i--)rmdir(nested[i-1]);
 rmdir(directory);
}
static void copy_file(const char *input,const char *output){
 int source=open(input,O_RDONLY|O_CLOEXEC),target=open(output,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0700);CHECK(source>=0&&target>=0);CHECK(!fchmod(target,0700));
 char bytes[4096];ssize_t count;while((count=read(source,bytes,sizeof bytes))>0)CHECK(write(target,bytes,(size_t)count)==count);
 CHECK(count==0);CHECK(!close(source));CHECK(!close(target));memset(bytes,0,sizeof bytes);
}
static void ready(int fd){struct pollfd p={.fd=fd,.events=POLLIN};CHECK(poll(&p,1,5000)==1);}
int main(int argc,char **argv){
 CHECK(argc==4);mode=argv[2];CHECK(!atexit(cleanup));CHECK(mkdtemp(directory));
 CHECK(snprintf(endpoint,sizeof endpoint,"%s/control.sock",directory)>0);
 CHECK(snprintf(path,sizeof path,"%s/invented ü script.sh",directory)>0);
 CHECK(snprintf(other,sizeof other,"%s/other",directory)>0);CHECK(snprintf(empty,sizeof empty,"%s/empty",directory)>0);
 if(is("path-exact")||is("path-over")){
  char parent[2048];CHECK(snprintf(parent,sizeof parent,"%s",directory)>0);size_t target=is("path-exact")?1024:1025;
  while(strlen(parent)+1+strlen("script")<target){
   size_t remaining=target-strlen(parent)-1-strlen("script"),width=remaining>201?200:remaining-1;
   CHECK(width&&width<=255&&nested_count<16);size_t used=strlen(parent);parent[used++]='/';memset(parent+used,'n',width);parent[used+width]=0;
   CHECK(!mkdir(parent,0700));CHECK(!chmod(parent,0700));strcpy(nested[nested_count++],parent);
  }
  CHECK(snprintf(path,sizeof path,"%s/script",parent)>0);CHECK(strlen(path)==target);
 }
 char prefix[512],content[1024];int option=is("option");
 int prefix_size=snprintf(prefix,sizeof prefix,"#!%s%s\n",argv[1],option?" -e":"");CHECK(prefix_size>0&&prefix_size<=256);
 int size=snprintf(content,sizeof content,"%s# independently invented script\n",prefix);CHECK(size>0&&(size_t)size<sizeof content);
 int fd=open(path,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0700);CHECK(fd>=0);CHECK(!fchmod(fd,0700));CHECK(write(fd,content,(size_t)size)==size);CHECK(!close(fd));
 struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,endpoint,strlen(endpoint)+1);
 listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(listener>=0);CHECK(!bind(listener,(struct sockaddr*)&address,sizeof address));CHECK(!listen(listener,1));
 unsigned char arguments[18000];size_t used=0;char *exec_arguments[130];unsigned total=0;
 const char *parts[]={argv[1],option?"-e":path,option?path:endpoint,endpoint};
 for(int i=0;i<(option?4:3);i++){size_t n=strlen(parts[i])+1;CHECK(used+n<=sizeof arguments);exec_arguments[total++]=(char*)arguments+used;memcpy(arguments+used,parts[i],n);used+=n;}
 if(is("arg-exact")||is("arg-over")){
  size_t n=is("arg-exact")?1024:1025;exec_arguments[total++]=(char*)arguments+used;memset(arguments+used,'q',n);used+=n;arguments[used++]=0;
 }
 if(is("count-exact")||is("count-over"))while(total<(is("count-exact")?128U:129U)){exec_arguments[total++]=(char*)arguments+used;arguments[used++]=0;}
 if(is("bytes-exact")||is("bytes-over")){
  size_t target=is("bytes-exact")?16384:16385;
  while(used<target){size_t width=target-used-1;if(width>1024)width=1024;CHECK(total<129);exec_arguments[total++]=(char*)arguments+used;memset(arguments+used,'b',width);used+=width;arguments[used++]=0;}
 }
 exec_arguments[total]=NULL;
 /* execve(script) replaces argv[0] with the compiled interpreter and script.
    Supply only the original script plus already-admitted remaining arguments. */
 char *launch[130];launch[0]=path;unsigned start=option?3:2;
 for(unsigned i=start;i<total;i++)launch[1+i-start]=exec_arguments[i];
 launch[1+total-start]=NULL;
 child=fork();CHECK(child>=0);if(child==0){execv(path,launch);_exit(41);}
 ready(listener);accepted=accept4(listener,NULL,NULL,SOCK_CLOEXEC);CHECK(accepted>=0);ready(accepted);char byte;CHECK(read(accepted,&byte,1)==1&&byte=='R');
 cancel_fd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel_fd>=0);uint64_t deadline=now()+10000000000ULL;
 CHECK(es_peer_open(&peer,accepted,cancel_fd,deadline)==ES_PEER_OK);CHECK(peer.identity.pid==(uint32_t)child);
 if(is("wrong-script-inode")){
  int copy=open(other,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0700);CHECK(copy>=0);CHECK(!fchmod(copy,0700));CHECK(write(copy,content,(size_t)size)==size);CHECK(!close(copy));
 }
 CHECK(es_file_open(&script,cancel_fd,deadline,is("wrong-script-inode")?other:path,strlen(is("wrong-script-inode")?other:path))==ES_FILE_OK);
 if(is("wrong-interpreter-inode"))copy_file(argv[3],other);
 CHECK(es_file_open(&interpreter,cancel_fd,deadline,is("wrong-interpreter-inode")?other:argv[3],strlen(is("wrong-interpreter-inode")?other:argv[3]))==ES_FILE_OK);
 CHECK(es_hash_open(&hash,cancel_fd,deadline)==ES_HASH_OK);
 es_script_expected expected={.script_path=path,.script_path_length=strlen(path),.shebang=(const unsigned char*)prefix,.shebang_length=(size_t)prefix_size,.arguments=arguments,.arguments_length=used,.script_index=option?2:1};
 memcpy(expected.script_sha256,is("literal-alias")?script_alias_expected:(is("shebang-exact")?script_long_expected:(option?script_option_expected:script_plain_expected)),32);memcpy(expected.interpreter_sha256,interpreter_expected,32);
 CHECK(lseek(script.fd,2,SEEK_SET)==2);CHECK(lseek(interpreter.fd,3,SEEK_SET)==3);
 es_script_identity out;memset(&out,0xa5,sizeof out);es_script_result want=ES_SCRIPT_OK;int preflight=0;
 if(is("wrong-script-digest")){expected.script_sha256[0]^=1;want=ES_SCRIPT_IDENTITY;}
 if(is("wrong-interpreter-digest")){expected.interpreter_sha256[0]^=1;want=ES_SCRIPT_IDENTITY;}
 if(is("wrong-script-inode")||is("wrong-interpreter-inode")||is("args-change-second")||is("args-change-final")||is("prefix-eof"))want=ES_SCRIPT_IDENTITY;
 if(is("prefix-io"))want=ES_SCRIPT_IO;
 if(is("path-substitution")){
  CHECK(!unlink(path));int replacement=open(path,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0700);CHECK(replacement>=0);CHECK(!fchmod(replacement,0700));CHECK(write(replacement,content,(size_t)size)==size);CHECK(!close(replacement));want=ES_SCRIPT_IDENTITY;
 }
 if(is("changed-trust")){CHECK(!chmod(path,0720));want=ES_SCRIPT_IDENTITY;}
 if(is("changed-header")){
  int writer=open(path,O_WRONLY|O_CLOEXEC);CHECK(writer>=0);CHECK(pwrite(writer,"?",1,0)==1);CHECK(!close(writer));want=ES_SCRIPT_IDENTITY;
 }
 if(is("extra-argument")){arguments[used++]='q';arguments[used++]=0;expected.arguments_length=used;want=ES_SCRIPT_IDENTITY;}
 if(is("bad-index")){expected.script_index++;preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("bad-option")){prefix[prefix_size-1]=' ';prefix[prefix_size++]='-';prefix[prefix_size++]='c';prefix[prefix_size++]='\n';expected.shebang_length=(size_t)prefix_size;preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("header-cr")){prefix[prefix_size-1]='\r';preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("header-tab")){prefix[2]='\t';preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("path-over")||is("arg-over")||is("count-over")||is("bytes-over")||is("shebang-over")){preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("death-before")){death();want=ES_SCRIPT_IDENTITY;}
 if(is("death-final"))want=ES_SCRIPT_IDENTITY;
 if(is("cancel-before")){cancel();want=ES_SCRIPT_CANCELLED;}
 if(is("cancel-final"))want=ES_SCRIPT_CANCELLED;
 if(is("deadline-before")){expired=1;want=ES_SCRIPT_DEADLINE;}
 if(is("deadline-final"))want=ES_SCRIPT_DEADLINE;
 if(is("close-fault")||is("close-cancel"))want=ES_SCRIPT_CLEANUP;
 int scriptfd=script.fd,interpreterfd=interpreter.fd,scriptcancel=script.cancel_fd,interpretercancel=interpreter.cancel_fd,hashcancel=hash.cancel_fd;
 uint64_t sd=script.deadline_ns,id=interpreter.deadline_ns,hd=hash.deadline_ns;
 if(is("script-control")||is("interpreter-control")||is("hash-control")){
  foreign=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(foreign>=0);
  if(is("script-control"))script.cancel_fd=foreign;else if(is("interpreter-control"))interpreter.cancel_fd=foreign;else hash.cancel_fd=foreign;preflight=1;want=ES_SCRIPT_INVALID;
 }
 if(is("script-deadline")){script.deadline_ns--;preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("interpreter-deadline")){interpreter.deadline_ns--;preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("hash-deadline")){hash.deadline_ns--;preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("descriptor-alias")){script.fd=interpreter.fd;preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("null-path")){expected.script_path=NULL;preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("overflow-span")){expected.arguments=(const unsigned char*)(UINTPTR_MAX-1);expected.arguments_length=8;preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("arguments-owner")){expected.arguments=(const unsigned char*)&hash;expected.arguments_length=2;preflight=1;want=ES_SCRIPT_INVALID;}
 if(is("objects-exact")||is("objects-over")){
  int emptyfd=open(empty,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0600);CHECK(emptyfd>=0);CHECK(!close(emptyfd));emptyfd=open(empty,O_RDONLY|O_CLOEXEC);CHECK(emptyfd>=0);
  es_hash_identity ignored;unsigned count=is("objects-exact")?507:508;for(unsigned i=0;i<count;i++)CHECK(es_hash_file(&hash,emptyfd,&ignored)==ES_HASH_OK);CHECK(!close(emptyfd));if(is("objects-over"))want=ES_SCRIPT_RESOURCE;
 }
 es_peer beforepeer=peer;es_file beforescript=script,beforeinterpreter=interpreter;es_hash beforehash=hash;
 active=1;
 if(is("expected-owner")){
  union {es_hash owner;es_script_expected expected;} alias;memset(&alias,0,sizeof alias);alias.owner=hash;
  unsigned char old[sizeof alias];memcpy(old,&alias,sizeof alias);
  CHECK(es_script_check(&peer,&script,&interpreter,&alias.owner,&alias.expected,&out)==ES_SCRIPT_INVALID);CHECK(!memcmp(old,&alias,sizeof alias));preflight=1;
 }else if(is("output-expected")){
  union {es_script_expected expected;es_script_identity out;} alias;memset(&alias,0,sizeof alias);alias.expected=expected;
  unsigned char old[sizeof alias];memcpy(old,&alias,sizeof alias);
  CHECK(es_script_check(&peer,&script,&interpreter,&hash,&alias.expected,&alias.out)==ES_SCRIPT_INVALID);CHECK(!memcmp(old,&alias,sizeof alias));preflight=1;
 }else if(is("output-owner")){
  CHECK(es_script_check(&peer,&script,&interpreter,&hash,&expected,(es_script_identity*)&hash)==ES_SCRIPT_INVALID);preflight=1;
 }else if(is("output-arguments")){
  unsigned char copy[sizeof out];memcpy(copy,arguments,sizeof copy);
  CHECK(es_script_check(&peer,&script,&interpreter,&hash,&expected,(es_script_identity*)arguments)==ES_SCRIPT_INVALID);CHECK(!memcmp(copy,arguments,sizeof copy));preflight=1;
 }else if(is("null-output")){
  CHECK(es_script_check(&peer,&script,&interpreter,&hash,&expected,NULL)==ES_SCRIPT_INVALID);preflight=1;
 }else{
  if(is("null-peer")||is("null-script")||is("null-interpreter")||is("null-hash")||is("null-expected")){want=ES_SCRIPT_INVALID;preflight=1;}
  es_script_result result=es_script_check(is("null-peer")?NULL:&peer,is("null-script")?NULL:&script,is("null-interpreter")?NULL:&interpreter,is("null-hash")?NULL:&hash,is("null-expected")?NULL:&expected,&out);
  if(result!=want)fprintf(stderr,"SCRIPT_RESULT_%d\n",result);
  CHECK(result==want);
  if(result==ES_SCRIPT_OK){
   CHECK(hash.objects==(is("objects-exact")?512:5));CHECK(!memcmp(out.script.sha256,expected.script_sha256,32));CHECK(!memcmp(out.interpreter.sha256,interpreter_expected,32));
   CHECK(hash.bytes==2*out.script.size+3*out.interpreter.size);CHECK(argv_reads==4);
  }else{es_script_identity zero={0};CHECK(!memcmp(&out,&zero,sizeof out));}
 }
 active=0;
 if(preflight){CHECK(!memcmp(&beforepeer,&peer,sizeof peer));CHECK(!memcmp(&beforescript,&script,sizeof script));CHECK(!memcmp(&beforeinterpreter,&interpreter,sizeof interpreter));CHECK(!memcmp(&beforehash,&hash,sizeof hash));}
 script.fd=scriptfd;interpreter.fd=interpreterfd;script.cancel_fd=scriptcancel;interpreter.cancel_fd=interpretercancel;hash.cancel_fd=hashcancel;script.deadline_ns=sd;interpreter.deadline_ns=id;hash.deadline_ns=hd;
 CHECK(lseek(script.fd,0,SEEK_CUR)==2&&lseek(interpreter.fd,0,SEEK_CUR)==3);
 CHECK(fcntl(script.fd,F_GETFD)>=0&&fcntl(interpreter.fd,F_GETFD)>=0&&fcntl(cancel_fd,F_GETFD)>=0);
 if(reused>=0)CHECK(fcntl(reused,F_GETFD)>=0&&!reused_closes);
 if(is("cancel-before")||is("cancel-final")||is("close-cancel")){struct pollfd c={.fd=cancel_fd,.events=POLLIN};CHECK(poll(&c,1,0)==1&&(c.revents&POLLIN));}

 return 0;
}
#endif

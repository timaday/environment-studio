#define _GNU_SOURCE
#include "privacy-maps.h"
#include "privacy-launch.h"
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <sys/vfs.h>
#include <time.h>
#include <unistd.h>
#define CHECK(x) do {if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}}while(0)
static char directory[]="/tmp/es-maps-probe-XXXXXX",endpoint[108],filename[160];
static int listener=-1,accepted=-1,cancel_fd=-1,file_fd=-1;static pid_t child=-1;
static es_peer peer;static es_maps_snapshot output;static es_launch launch;static int parent_fd=-1;
static uint64_t now(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+t.tv_nsec;}
static void cleanup(void){
 volatile unsigned char *clear=(volatile unsigned char*)&output;for(size_t i=0;i<sizeof(output);i++)clear[i]=0;
 if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);child=-1;}
 if(launch.initialized)(void)es_launch_close(&launch,1000000000ULL);
 if(parent_fd>=0)close(parent_fd);
 if(peer.state)(void)es_peer_close(&peer);
 if(accepted>=0)close(accepted);
 if(listener>=0)close(listener);
 if(cancel_fd>=0)close(cancel_fd);
 if(file_fd>=0)close(file_fd);
 if(endpoint[0])unlink(endpoint);
 if(filename[0])unlink(filename);
 rmdir(directory);
}
static const char *mode="actual";
static unsigned char synthetic[ES_MAPS_BYTES+8193];static size_t synthetic_length,cursor;
static int maps_fd=-1,maps_opens,maps_closes,last_closed=-1,read_calls;
static _Thread_local int in_peer;
static pthread_mutex_t hold_mutex=PTHREAD_MUTEX_INITIALIZER;static pthread_cond_t hold_changed=PTHREAD_COND_INITIALIZER;static int held,released;
static int is(const char *value){return !strcmp(mode,value);}
es_peer_result __real_es_peer_read(es_peer *,es_peer_identity *);
es_peer_result __wrap_es_peer_read(es_peer *p,es_peer_identity *out){in_peer++;es_peer_result r=__real_es_peer_read(p,out);in_peer--;return r;}
int __real_open(const char *,int,...);
static int open_maps(const char *path,int flags){
 int maps=!in_peer&&strlen(path)>5&&!strcmp(path+strlen(path)-5,"/maps");
 if(maps){++maps_opens;cursor=0;CHECK((flags&(O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK))==(O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK));
  if(is("open-fault")||(is("second-open-fault")&&maps_opens==2)){errno=EACCES;return -1;}}
 int fd=__real_open(path,flags);if(maps&&fd>=0)maps_fd=fd;return fd;
}
int __wrap_open(const char *path,int flags,...){
 if(flags&O_CREAT){va_list args;va_start(args,flags);mode_t permissions=(mode_t)va_arg(args,int);va_end(args);return __real_open(path,flags,permissions);}
 return open_maps(path,flags);
}
int __wrap___open_2(const char *path,int flags){return open_maps(path,flags);}
int __real_fstatfs(int,struct statfs *);
int __wrap_fstatfs(int fd,struct statfs *out){int r=__real_fstatfs(fd,out);if(fd==maps_fd&&!in_peer){if(is("filesystem-fault")){errno=EIO;return -1;}if(is("wrong-filesystem"))out->f_type=0;}return r;}
ssize_t __real_read(int,void *,size_t);
ssize_t __wrap_read(int fd,void *out,size_t capacity){
 if(fd!=maps_fd||in_peer)return __real_read(fd,out,capacity);
 ++read_calls;
 if(is("launch-held")&&read_calls==1){pthread_mutex_lock(&hold_mutex);held=1;pthread_cond_broadcast(&hold_changed);while(!released)pthread_cond_wait(&hold_changed,&hold_mutex);pthread_mutex_unlock(&hold_mutex);}
 if(read_calls==1&&is("read-death")){CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=-1;}
 if(read_calls==1&&is("read-cancel")){uint64_t one=1;CHECK(write(cancel_fd,&one,8)==8);}
 if(read_calls==1&&is("read-deadline"))peer.deadline_ns=now()-1;
 if(is("read-fault")){errno=EIO;return -1;}
 if(is("interrupts")){errno=EINTR;return -1;}
 if(is("fragment")&&capacity>3)capacity=3;
 if(synthetic_length){
  size_t length=synthetic_length;
  if(is("second-short")&&maps_opens==2)--length;
  if(cursor>=length){
   if(is("eof-cancel")){uint64_t one=1;CHECK(write(cancel_fd,&one,8)==8);}
   if(is("eof-deadline"))peer.deadline_ns=now()-1;
   return 0;
  }
  size_t count=length-cursor;if(count>capacity)count=capacity;
  memcpy(out,synthetic+cursor,count);
  if(is("second-change")&&maps_opens==2&&cursor==0)((unsigned char*)out)[0]='2';
  cursor+=count;return (ssize_t)count;
 }
 return __real_read(fd,out,capacity);
}
ssize_t __wrap___read_chk(int fd,void *out,size_t capacity,size_t object){CHECK(capacity<=object);return __wrap_read(fd,out,capacity);}
int __real_close(int);
int __wrap_close(int fd){
 int maps=fd==maps_fd&&!in_peer;if(maps){maps_fd=-1;last_closed=fd;++maps_closes;}
 int r=__real_close(fd);
 if(maps&&(is("close-uncertain")||is("close-cancel")||is("launch-uncertain"))){
  if(is("close-cancel")){uint64_t one=1;CHECK(write(cancel_fd,&one,sizeof(one))==sizeof(one));}
  errno=EINTR;return -1;
 }
 if(maps&&maps_opens==2&&is("final-death")){CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=-1;}
 if(maps&&maps_opens==2&&is("final-deadline"))peer.deadline_ns=now()-1;
 if(maps&&maps_opens==1&&is("actual-change")){char command='m',ack=0;CHECK(write(accepted,&command,1)==1);CHECK(read(accepted,&ack,1)==1&&ack=='m');}
 if(maps&&maps_opens==2&&is("final-cancel")){uint64_t one=1;CHECK(write(cancel_fd,&one,sizeof(one))==sizeof(one));}
 return r;
}
static void build_synthetic(void){
 const char *text="0001-0002 rwxs 0003 0004:0005 6  opaque \\012 \xff (deleted)\n";
 if(is("format-upper"))text="A-b r--p 0 0:0 0\n";
 if(is("format-overflow"))text="1-2 r--p 0 0:0 18446744073709551616\n";
 if(is("format-device"))text="1-2 r--p 0 100000000:0 0\n";
 if(is("format-permissions"))text="1-2 rwxq 0 0:0 0\n";
 if(is("format-overlap"))text="1-3 r--p 0 0:0 0\n2-4 r--p 0 0:0 0\n";
 if(is("format-truncated"))text="1-2 r--p 0 0:0 0";
 if(is("format-label"))text="1-2 r--p 0 0:0 0x\n";
 if(is("high"))text="ffffffffff600000-ffffffffff601000 --xp 0 0:0 0 [vsyscall]\n";
 memcpy(synthetic,text,strlen(text));synthetic_length=strlen(text);
 if(is("format-nul"))synthetic[3]=0;
 if(is("records-exact")||is("records-over")){
  unsigned count=is("records-exact")?ES_MAPS_RECORDS:ES_MAPS_RECORDS+1;synthetic_length=0;
  for(unsigned i=0;i<count;i++){
   int n=snprintf((char*)synthetic+synthetic_length,sizeof(synthetic)-synthetic_length,"%x-%x r--p 0 0:0 0\n",i+1,i+2);CHECK(n>0);synthetic_length+=(size_t)n;
  }
 }
 if(is("bytes-exact")||is("bytes-over")||is("line-over")){
  unsigned count=is("line-over")?1:32;synthetic_length=0;
  for(unsigned i=0;i<count;i++){
   size_t length=is("line-over")?8193:8192;
   int n=snprintf((char*)synthetic+synthetic_length,sizeof(synthetic)-synthetic_length,"%x-%x r--p 0 0:0 0 ",i+1,i+2);CHECK(n>0&&(size_t)n<length);
   memset(synthetic+synthetic_length+n,'x',length-(size_t)n-1);synthetic[synthetic_length+length-1]='\n';synthetic_length+=length;
  }
  if(is("bytes-over"))synthetic[synthetic_length++]='x';
 }
}
static void test_sample(void){
 es_maps_result expected=ES_MAPS_OK;
 if(!strncmp(mode,"format-",7))expected=ES_MAPS_FORMAT;
 if(is("records-over")||is("bytes-over")||is("line-over"))expected=ES_MAPS_RESOURCE;
 if(is("second-change")||is("second-short"))expected=ES_MAPS_IDENTITY;
 if(is("open-fault")||is("second-open-fault")||is("read-fault")||is("filesystem-fault")||is("interrupts"))expected=ES_MAPS_IO;
 if(is("wrong-filesystem"))expected=ES_MAPS_PLATFORM;
 if(is("close-uncertain")||is("close-cancel"))expected=ES_MAPS_CLEANUP;
 if(is("final-cancel")||is("read-cancel")||is("eof-cancel"))expected=ES_MAPS_CANCELLED;
 if(is("read-death")||is("final-death"))expected=ES_MAPS_IDENTITY;
 if(is("read-deadline")||is("final-deadline")||is("eof-deadline"))expected=ES_MAPS_DEADLINE;
 CHECK(es_maps_sample(&peer,&output)==expected);
 if(expected){unsigned char *bytes=(unsigned char*)&output;for(size_t i=0;i<sizeof(output);i++)CHECK(!bytes[i]);}
 else {
  CHECK(output.byte_length==synthetic_length&&!memcmp(output.bytes,synthetic,synthetic_length));
  if(is("records-exact"))CHECK(output.count==ES_MAPS_RECORDS);
  else if(is("bytes-exact"))CHECK(output.count==32&&output.byte_length==ES_MAPS_BYTES);
  else {
   CHECK(output.count==1);
   if(!is("high")){
    es_maps_record *r=&output.records[0];CHECK(r->start==1&&r->end==2&&r->offset==3&&r->inode==6&&r->device_major==4&&r->device_minor==5);
    CHECK(r->read&&r->write&&r->execute&&r->shared);
    const char *label="  opaque \\012 \xff (deleted)";CHECK(r->label_length==strlen(label)&&!memcmp(output.bytes+r->label_offset,label,r->label_length));
   }
  }
  for(size_t i=output.byte_length;i<ES_MAPS_BYTES;i++)CHECK(!output.bytes[i]);
  unsigned char *unused=(unsigned char*)&output.records[output.count];for(size_t i=0;i<(ES_MAPS_RECORDS-output.count)*sizeof(es_maps_record);i++)CHECK(!unused[i]);
 }
 if(is("interrupts"))CHECK(read_calls==33);
 if(is("close-uncertain")||is("close-cancel")){
  CHECK(maps_closes==1&&last_closed>=0);int dummy=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(dummy>=0);
  if(dummy!=last_closed){CHECK(dup3(dummy,last_closed,O_CLOEXEC)==last_closed);CHECK(!close(dummy));}
  CHECK(fcntl(last_closed,F_GETFD)>=0);CHECK(fcntl(cancel_fd,F_GETFD)>=0&&fcntl(accepted,F_GETFD)>=0);
  CHECK(es_peer_close(&peer)==(is("close-cancel")?ES_PEER_CANCELLED:ES_PEER_OK));CHECK(fcntl(last_closed,F_GETFD)>=0);CHECK(!close(last_closed));
 }
 if(expected==ES_MAPS_OK)CHECK(maps_opens==2&&maps_closes==2);
}
static void *launch_receiver(void *value){
 es_launch_result r=es_launch_capture(&launch);if(!r)r=es_launch_correlate(&launch);
 if(r==ES_LAUNCH_ROOT_CORRELATED)r=es_launch_maps(&launch,&output);
 if(is("launch-double")&&r==ES_LAUNCH_OK)r=es_launch_maps(&launch,&output);
 *(es_launch_result*)value=r;return NULL;
}
static void launch_case(long page){
 parent_fd=open(directory,O_PATH|O_DIRECTORY|O_CLOEXEC);CHECK(parent_fd>=0);
 char path[ES_LISTENER_PATH_BYTES];CHECK(es_launch_open(&launch,parent_fd,directory,3000000000ULL,path)==ES_LAUNCH_OK);
 void *mapped=mmap(NULL,(size_t)page,PROT_READ,MAP_PRIVATE,file_fd,page);CHECK(mapped!=MAP_FAILED);
 pthread_t receiver;es_launch_result result=ES_LAUNCH_INVALID;CHECK(!pthread_create(&receiver,NULL,launch_receiver,&result));
 CHECK(es_launch_arm(&launch)==ES_LAUNCH_OK);child=fork();CHECK(child>=0);
 if(!child){
  int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un a={.sun_family=AF_UNIX};memcpy(a.sun_path,path,strlen(path)+1);
  if(fd<0||connect(fd,(struct sockaddr*)&a,sizeof(a)))_exit(41);
  unsigned char frame[12]={'E','S','P','R','V','0','0','1',1,0,0,0};if(write(fd,frame,sizeof(frame))!=sizeof(frame))_exit(42);
  char c;ssize_t n=read(fd,&c,1);_exit(n?43:0);
 }
 CHECK(es_launch_register(&launch,(uint64_t)child)==ES_LAUNCH_OK);CHECK(es_launch_disarm(&launch)==ES_LAUNCH_OK);
 if(is("launch-held")){
  pthread_mutex_lock(&hold_mutex);while(!held)pthread_cond_wait(&hold_changed,&hold_mutex);pthread_mutex_unlock(&hold_mutex);
  int borrowed=launch.cancel_fd;CHECK(es_launch_close(&launch,10000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);CHECK(fcntl(borrowed,F_GETFD)>=0);
  pthread_mutex_lock(&hold_mutex);released=1;pthread_cond_broadcast(&hold_changed);pthread_mutex_unlock(&hold_mutex);
  CHECK(!pthread_join(receiver,NULL));CHECK(result==ES_LAUNCH_CANCELLED);for(size_t i=0;i<sizeof(output);i++)CHECK(!((unsigned char*)&output)[i]);
  CHECK(es_launch_close(&launch,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);CHECK(fcntl(borrowed,F_GETFD)<0&&errno==EBADF);
  int dummy=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(dummy>=0);if(dummy!=borrowed){CHECK(dup3(dummy,borrowed,O_CLOEXEC)==borrowed);CHECK(!close(dummy));}
  CHECK(es_launch_close(&launch,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);CHECK(fcntl(borrowed,F_GETFD)>=0);CHECK(!close(borrowed));CHECK(!munmap(mapped,(size_t)page));return;
 }
 CHECK(!pthread_join(receiver,NULL));
 if(is("launch-uncertain")){
  CHECK(result==ES_LAUNCH_CLEANUP&&launch.inconclusive&&maps_closes==1);for(size_t i=0;i<sizeof(output);i++)CHECK(!((unsigned char*)&output)[i]);
  int dummy=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(dummy>=0);if(dummy!=last_closed){CHECK(dup3(dummy,last_closed,O_CLOEXEC)==last_closed);CHECK(!close(dummy));}
  CHECK(es_launch_close(&launch,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);CHECK(fcntl(last_closed,F_GETFD)>=0);CHECK(es_launch_close(&launch,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);CHECK(fcntl(last_closed,F_GETFD)>=0);CHECK(!close(last_closed));CHECK(!munmap(mapped,(size_t)page));return;
 }
 if(is("launch-double")){CHECK(result==ES_LAUNCH_PROTOCOL);for(size_t i=0;i<sizeof(output);i++)CHECK(!((unsigned char*)&output)[i]);CHECK(es_launch_close(&launch,1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);CHECK(!munmap(mapped,(size_t)page));return;}
 CHECK(result==ES_LAUNCH_OK);
 unsigned found=0;for(unsigned i=0;i<output.count;i++)if(output.records[i].start==(uintptr_t)mapped&&output.records[i].end==(uintptr_t)mapped+(uint64_t)page)found++;
 CHECK(found==1);
 if(is("launch-wrong-thread")){CHECK(es_launch_maps(&launch,&output)==ES_LAUNCH_PROTOCOL);for(size_t i=0;i<sizeof(output);i++)CHECK(!((unsigned char*)&output)[i]);}
 CHECK(es_launch_close(&launch,1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);CHECK(!munmap(mapped,(size_t)page));
}
int main(int argc,char **argv){
 if(argc==2)mode=argv[1];
 CHECK(mkdtemp(directory));CHECK(!atexit(cleanup));
 CHECK(snprintf(endpoint,sizeof(endpoint),"%s/control",directory)>0);
 CHECK(snprintf(filename,sizeof(filename),"%s/%s",directory,is("actual-newline")?"invented\nmapped":is("actual-nonutf8")?"invented\xffmapped":"invented mapped file")>0);
 file_fd=open(filename,O_RDWR|O_CREAT|O_EXCL|O_CLOEXEC,0600);CHECK(file_fd>=0);
 long page=sysconf(_SC_PAGESIZE);CHECK(page>0&&page<=65536);CHECK(!ftruncate(file_fd,3*page));
 struct stat metadata;CHECK(!fstat(file_fd,&metadata));
 if(!strncmp(mode,"launch",6)){launch_case(page);return 0;}
 listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(listener>=0);
 struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,endpoint,strlen(endpoint)+1);
 CHECK(!bind(listener,(struct sockaddr*)&address,sizeof(address)));CHECK(!listen(listener,1));
 child=fork();CHECK(child>=0);
 if(!child){
  void *mapping=mmap(NULL,(size_t)page*2,PROT_READ,MAP_PRIVATE,file_fd,page);if(mapping==MAP_FAILED)_exit(41);
  if(mprotect((char*)mapping+page,(size_t)page,PROT_READ|PROT_WRITE))_exit(42);
  int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof(address)))_exit(43);
  void *executable=mmap(NULL,(size_t)page,PROT_READ|PROT_EXEC,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);if(executable==MAP_FAILED)_exit(46);
  uint64_t addresses[2]={(uintptr_t)mapping,(uintptr_t)executable};if(write(fd,addresses,sizeof(addresses))!=sizeof(addresses))_exit(44);
  char byte;ssize_t stopped;while((stopped=read(fd,&byte,1))>0){
   if(byte!='m'||mprotect(mapping,(size_t)page,PROT_READ|PROT_WRITE)||write(fd,&byte,1)!=1)_exit(45);
  }_exit(stopped<0?45:0);
 }
 accepted=accept4(listener,NULL,NULL,SOCK_CLOEXEC);CHECK(accepted>=0);uint64_t addresses[2]={0};
 CHECK(read(accepted,addresses,sizeof(addresses))==sizeof(addresses)&&addresses[0]>0&&addresses[1]>0);uint64_t start=addresses[0];
 cancel_fd=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);CHECK(cancel_fd>=0);
 CHECK(es_peer_open(&peer,accepted,cancel_fd,now()+10000000000ULL)==ES_PEER_OK);
 if(is("invalid")){
  es_peer saved=peer,fresh={0};memset(&output,0xa5,sizeof(output));CHECK(es_maps_sample(NULL,&output)==ES_MAPS_INVALID);
  for(size_t i=0;i<sizeof(output);i++)CHECK(!((unsigned char*)&output)[i]);
  CHECK(es_maps_sample(&fresh,&output)==ES_MAPS_INVALID&&es_maps_sample(&peer,NULL)==ES_MAPS_INVALID);
  CHECK(es_maps_sample(&peer,(es_maps_snapshot*)&peer)==ES_MAPS_INVALID&&!memcmp(&peer,&saved,sizeof(peer)));
  CHECK(es_launch_maps(NULL,&output)==ES_LAUNCH_INVALID);CHECK(fcntl(peer.pidfd,F_GETFD)>=0);return 0;
 }
 if(strncmp(mode,"actual",6)){build_synthetic();test_sample();return 0;}
 if(is("actual-deleted"))CHECK(!unlink(filename));
 if(is("actual-change")){CHECK(es_maps_sample(&peer,&output)==ES_MAPS_IDENTITY);for(size_t i=0;i<sizeof(output);i++)CHECK(!((unsigned char*)&output)[i]);return 0;}
 CHECK(es_maps_sample(&peer,&output)==ES_MAPS_OK);
 CHECK(output.identity.pid==(uint32_t)child&&output.count>2&&output.byte_length>0&&output.bytes[output.byte_length-1]=='\n');
 unsigned found=0,executable_found=0;
 for(unsigned i=0;i<output.count;i++){
  es_maps_record *r=&output.records[i];
  if(r->start==addresses[1]){CHECK(r->end==addresses[1]+(uint64_t)page&&r->read&&!r->write&&r->execute&&!r->shared&&!r->inode&&!r->device_major&&!r->device_minor);executable_found++;}
  if(r->start==start||r->start==start+(uint64_t)page){
   unsigned second=r->start!=start;
   CHECK(r->end==r->start+(uint64_t)page&&r->offset==(uint64_t)page*(1+second));
   CHECK(r->inode==(uint64_t)metadata.st_ino&&r->device_major==major(metadata.st_dev)&&r->device_minor==minor(metadata.st_dev));
   CHECK(r->read==1&&r->write==second&&!r->execute&&!r->shared);
   const char *expected=is("actual-newline")?"invented\\012mapped":is("actual-nonutf8")?"invented\xffmapped":is("actual-deleted")?"invented mapped file (deleted)":"invented mapped file";
   CHECK(r->label_length>=strlen(expected)&&!memcmp(output.bytes+r->label_offset+r->label_length-strlen(expected),expected,strlen(expected)));found++;
  }
 }
 CHECK(found==2&&executable_found==1);return 0;
}

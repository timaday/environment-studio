#define _GNU_SOURCE
#include "privacy-maps.h"
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
#include <time.h>
#include <unistd.h>
/* Independent mock child and oracles. Wrappers affect maps I/O only; peer
   validation and actual procfs opens/closes remain production operations. */
#define CHECK(x) do {if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}}while(0)
static char directory[]="/tmp/es-independent-maps-XXXXXX",endpoint[108],filename[160];
static int listener=-1,socket_fd=-1,cancel_fd=-1,file_fd=-1,reused_fd=-1;
static pid_t child=-1;
static es_peer peer;
static es_maps_snapshot output;
static const char *mode;
static unsigned char bytes[4096];
static size_t length,cursor;
static int maps_fd=-1,opens,closes,interrupts[2];
static _Thread_local int in_peer;
static int is(const char *value){return !strcmp(mode,value);}
static uint64_t now(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+t.tv_nsec;}
static void zero(const void *p,size_t n){const unsigned char *b=p;for(size_t i=0;i<n;i++)CHECK(!b[i]);}
static void cleanup(void){
 volatile unsigned char *p=(volatile unsigned char*)&output;for(size_t i=0;i<sizeof(output);i++)p[i]=0;
 if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);}
 if(peer.state)(void)es_peer_close(&peer);
 if(reused_fd>=0)close(reused_fd);
 if(socket_fd>=0)close(socket_fd);
 if(listener>=0)close(listener);
 if(cancel_fd>=0)close(cancel_fd);
 if(file_fd>=0)close(file_fd);
 if(endpoint[0])unlink(endpoint);
 if(filename[0])unlink(filename);
 rmdir(directory);
}
es_peer_result __real_es_peer_read(es_peer *,es_peer_identity *);
es_peer_result __wrap_es_peer_read(es_peer *p,es_peer_identity *identity){in_peer++;es_peer_result r=__real_es_peer_read(p,identity);in_peer--;return r;}
int __real_open(const char *,int,...);
static int open_path(const char *path,int flags){
 int fd=__real_open(path,flags);size_t n=strlen(path);
 if(!in_peer&&n>=5&&!strcmp(path+n-5,"/maps")){
  CHECK(fd>=0&&opens<2);++opens;cursor=0;maps_fd=fd;
  CHECK((flags&O_ACCMODE)==O_RDONLY&&(flags&O_NOFOLLOW)&&(flags&O_NONBLOCK)&&(flags&O_CLOEXEC));
 }
 return fd;
}
int __wrap_open(const char *path,int flags,...){
 if(flags&O_CREAT){va_list a;va_start(a,flags);mode_t m=(mode_t)va_arg(a,int);va_end(a);return __real_open(path,flags,m);}
 return open_path(path,flags);
}
int __wrap___open_2(const char *path,int flags){return open_path(path,flags);}
ssize_t __real_read(int,void *,size_t);
ssize_t __wrap_read(int fd,void *out,size_t capacity){
 if(in_peer||fd!=maps_fd||is("actual-shared"))return __real_read(fd,out,capacity);
 if(is("retry-exact")||is("retry-over")){
  int maximum=opens==2&&is("retry-over")?17:16;
  if(interrupts[opens-1]<maximum){++interrupts[opens-1];errno=EINTR;return -1;}
 }
 if(cursor==length){
  if(is("second-extra")&&opens==2){CHECK(capacity);*(unsigned char*)out='x';++cursor;return 1;}
  return 0;
 }
 CHECK(cursor<length);size_t n=length-cursor;if(n>capacity)n=capacity;
 if(n>7)n=7; /* Different boundaries from both full rows and the author's fragments. */
 memcpy(out,bytes+cursor,n);cursor+=n;return (ssize_t)n;
}
ssize_t __wrap___read_chk(int fd,void *out,size_t n,size_t cap){CHECK(n<=cap);return __wrap_read(fd,out,n);}
int __real_close(int);
int __wrap_close(int fd){
 int maps=!in_peer&&fd==maps_fd;
 if(maps){maps_fd=-1;++closes;}
 int r=__real_close(fd);
 if(maps&&closes==2&&(is("second-close")||is("second-close-cancel"))){
  int fresh=__real_open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(fresh>=0);
  if(fresh!=fd){CHECK(dup3(fresh,fd,O_CLOEXEC)==fd);CHECK(!__real_close(fresh));}reused_fd=fd;
  if(is("second-close-cancel")){uint64_t one=1;CHECK(write(cancel_fd,&one,sizeof(one))==sizeof(one));}
  errno=EINTR;return -1;
 }
 return r;
}
static void synthetic(void){
 if(is("empty"))return;
 for(unsigned i=0;i<16;i++){
  int n=snprintf((char*)bytes+length,sizeof(bytes)-length,"%x-%x %c%c%c%c 0 0:0 0\n",2*i+1,2*i+2,
   i&1?'r':'-',i&2?'w':'-',i&4?'x':'-',i&8?'s':'p');CHECK(n>0);length+=(size_t)n;
 }
 const char tail[]="fffffffffffffffe-ffffffffffffffff rwxs ffffffffffffffff ffffffff:ffffffff 18446744073709551615 \topaque\n";
 CHECK(length+sizeof(tail)<sizeof(bytes));memcpy(bytes+length,tail,sizeof(tail)-1);length+=sizeof(tail)-1;
}
int main(int argc,char **argv){
 CHECK(argc==2);mode=argv[1];CHECK(mkdtemp(directory));CHECK(!atexit(cleanup));
 CHECK(snprintf(endpoint,sizeof(endpoint),"%s/control",directory)>0);
 CHECK(snprintf(filename,sizeof(filename),"%s/independent-mapping",directory)>0);
 file_fd=open(filename,O_RDWR|O_CREAT|O_EXCL|O_CLOEXEC,0600);CHECK(file_fd>=0);
 long page=sysconf(_SC_PAGESIZE);CHECK(page>0&&page<=65536);CHECK(!ftruncate(file_fd,4*page));
 struct stat metadata;CHECK(!fstat(file_fd,&metadata));
 listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(listener>=0);
 struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,endpoint,strlen(endpoint)+1);
 CHECK(!bind(listener,(struct sockaddr*)&address,sizeof(address))&&!listen(listener,1));
 child=fork();CHECK(child>=0);
 if(!child){
  void *mapped=mmap(NULL,(size_t)page*2,PROT_NONE,MAP_SHARED,file_fd,page*2);
  if(mapped==MAP_FAILED||mprotect((char*)mapped+page,(size_t)page,PROT_READ|PROT_WRITE))_exit(41);
  int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof(address)))_exit(42);
  uint64_t start=(uintptr_t)mapped;if(write(fd,&start,sizeof(start))!=sizeof(start))_exit(43);
  char c;ssize_t count=read(fd,&c,1);_exit(count?44:0);
 }
 socket_fd=accept4(listener,NULL,NULL,SOCK_CLOEXEC);CHECK(socket_fd>=0);uint64_t start=0;
 CHECK(read(socket_fd,&start,sizeof(start))==sizeof(start)&&start);
 cancel_fd=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);CHECK(cancel_fd>=0);
 CHECK(es_peer_open(&peer,socket_fd,cancel_fd,now()+9000000000ULL)==ES_PEER_OK);
 synthetic();memset(&output,0xa7,sizeof(output));
 es_maps_result expected=is("empty")?ES_MAPS_FORMAT:is("retry-over")?ES_MAPS_IO:is("second-extra")?ES_MAPS_IDENTITY:
  is("second-close")||is("second-close-cancel")?ES_MAPS_CLEANUP:ES_MAPS_OK;
 CHECK(es_maps_sample(&peer,&output)==expected);
 if(expected)zero(&output,sizeof(output));
 else if(is("actual-shared")){
  unsigned seen=0;
  for(unsigned i=0;i<output.count;i++){
   es_maps_record *r=&output.records[i];if(r->start!=start&&r->start!=start+(uint64_t)page)continue;
   unsigned second=r->start!=start;
   CHECK(r->end==r->start+(uint64_t)page&&r->offset==(uint64_t)page*(2+second));
   CHECK(r->read==second&&r->write==second&&!r->execute&&r->shared);
   CHECK(r->inode==(uint64_t)metadata.st_ino&&r->device_major==major(metadata.st_dev)&&r->device_minor==minor(metadata.st_dev));++seen;
  }
  CHECK(seen==2&&output.identity.pid==(uint32_t)child);
 }else{
  CHECK(output.count==17&&output.byte_length==length&&!memcmp(bytes,output.bytes,length));
  for(unsigned i=0;i<16;i++){
   es_maps_record *r=&output.records[i];CHECK(r->start==2*i+1&&r->end==2*i+2&&!r->label_length);
   CHECK(r->read==!!(i&1)&&r->write==!!(i&2)&&r->execute==!!(i&4)&&r->shared==!!(i&8));
  }
  es_maps_record *r=&output.records[16];CHECK(r->end==UINT64_MAX&&r->offset==UINT64_MAX&&r->inode==UINT64_MAX);
  CHECK(r->device_major==UINT32_MAX&&r->device_minor==UINT32_MAX&&r->label_length==8);
  CHECK(!memcmp(output.bytes+r->label_offset," \topaque",8));
 }
 if(is("retry-exact"))CHECK(interrupts[0]==16&&interrupts[1]==16&&opens==2&&closes==2);
 if(is("retry-over"))CHECK(interrupts[0]==16&&interrupts[1]==17&&opens==2&&closes==2);
 if(reused_fd>=0){
  CHECK(closes==2&&fcntl(reused_fd,F_GETFD)>=0);
  es_peer_result r=es_peer_close(&peer);CHECK(r==(is("second-close-cancel")?ES_PEER_CANCELLED:ES_PEER_OK));
  CHECK(es_peer_close(&peer)==r&&fcntl(reused_fd,F_GETFD)>=0);
 }
 CHECK(fcntl(socket_fd,F_GETFD)>=0&&fcntl(cancel_fd,F_GETFD)>=0&&fcntl(file_fd,F_GETFD)>=0);
 return 0;
}

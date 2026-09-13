#define _GNU_SOURCE
#include "privacy-file.h"
#include "privacy-hash.h"
#include <sys/eventfd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"INDEPENDENT_FILE_%d\n",__LINE__); exit(40); } } while(0)
static uint64_t now(void) {struct timespec t; CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
static void file(const char *path) {int fd=open(path,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0600);CHECK(fd>=0);CHECK(write(fd,"abc",3)==3);CHECK(!close(fd));}
static void accept(const char *path,size_t length) {
 int cancel=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel>=0);uint64_t deadline=now()+10000000000ULL;
 es_file owner={0};CHECK(es_file_open(&owner,cancel,deadline,path,length)==ES_FILE_OK);
 CHECK(owner.fd>=0&&(fcntl(owner.fd,F_GETFD)&FD_CLOEXEC));CHECK((fcntl(owner.fd,F_GETFL)&O_ACCMODE)==O_RDONLY);
 es_hash hash={0};es_hash_identity actual;CHECK(es_hash_open(&hash,cancel,deadline)==ES_HASH_OK);CHECK(es_hash_file(&hash,owner.fd,&actual)==ES_HASH_OK);
 static const unsigned char digest[]={0xba,0x78,0x16,0xbf,0x8f,0x01,0xcf,0xea,0x41,0x41,0x40,0xde,0x5d,0xae,0x22,0x23,0xb0,0x03,0x61,0xa3,0x96,0x17,0x7a,0x9c,0xb4,0x10,0xff,0x61,0xf2,0x00,0x15,0xad};
 CHECK(actual.size==3&&!memcmp(actual.sha256,digest,32));CHECK(es_hash_close(&hash)==ES_HASH_OK);CHECK(es_file_close(&owner)==ES_FILE_OK);CHECK(es_file_close(&owner)==ES_FILE_OK);CHECK(fcntl(cancel,F_GETFD)>=0);CHECK(!close(cancel));
}
int main(int argc,char **argv) {
 CHECK(argc==2);
 if(!strcmp(argv[1],"invalid")) {
  int cancel=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel>=0);
  char directory[]="/tmp/es-invalid-file-XXXXXX";CHECK(mkdtemp(directory));
  char path[100];int prefix=snprintf(path,sizeof path,"%s/",directory);CHECK(prefix>0);
  path[prefix]=(char)0xe2;path[prefix+1]='x';path[prefix+2]=(char)0x82;path[prefix+3]=0;
  file(path);
  es_file owner={0},before=owner;es_file_result result=es_file_open(&owner,cancel,now()+10000000000ULL,path,(size_t)prefix+3);
  CHECK(!unlink(path));CHECK(!rmdir(directory));
  CHECK(result==ES_FILE_INVALID);CHECK(!memcmp(&owner,&before,sizeof owner));CHECK(!close(cancel));return 0;
 }
 char directory[80];CHECK(snprintf(directory,sizeof directory,"%s/es-independent-file-XXXXXX",!strcmp(argv[1],"tmpfs")?"/dev/shm":"/tmp")>0);CHECK(mkdtemp(directory));
 char path[4097];CHECK(snprintf(path,sizeof path,"%s",directory)>0);size_t ends[32];size_t count=0;
 if(!strcmp(argv[1],"maximum")) {
  while(4095-strlen(path)-1>255) {
   size_t n=strlen(path);path[n]='/';memset(path+n+1,'a'+(int)(count%20),250);path[n+251]=0;CHECK(!mkdir(path,0700));ends[count++]=n;CHECK(count<32);
  }
  size_t n=strlen(path);path[n]='/';memset(path+n+1,'z',4095-n-1);path[4095]=0;CHECK(strlen(path)==4095);file(path);
  path[4095]='Q';accept(path,4095);path[4095]=0;
  int cancel=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel>=0);es_file owner={0},before=owner;
  path[4095]='x';path[4096]=0;CHECK(es_file_open(&owner,cancel,now()+10000000000ULL,path,4096)==ES_FILE_INVALID);CHECK(!memcmp(&owner,&before,sizeof owner));CHECK(!close(cancel));path[4095]=0;
 } else {CHECK(!strcmp(argv[1],"tmpfs"));size_t n=strlen(path);CHECK(snprintf(path+n,sizeof path-n,"/file")>0);file(path);accept(path,strlen(path));}
 CHECK(!unlink(path));
 if(count) {char *last=strrchr(path,'/');CHECK(last);*last=0;while(count){CHECK(!rmdir(path));path[ends[--count]]=0;}}
 CHECK(!rmdir(directory));return 0;
}

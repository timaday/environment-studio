#define _GNU_SOURCE
#ifdef ES_ELF_MOCK
int main(void) {return 0;}
#else
#include "privacy-elf.h"
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#define CHECK(x) do {if(!(x)){printf("ELF_ASSERT_%d\n",__LINE__);exit(40);}}while(0)
static const char *mode;static int source=-1,writer=-1,cancel=-1,hash_depth,hash_calls,reads,interrupted;
static uint64_t deadline;static int late_expiry;
es_hash_result __real_es_hash_file(es_hash *,int,es_hash_identity *);
ssize_t __real_pread(int,void *,size_t,off_t);
int __real_clock_gettime(clockid_t,struct timespec *);
int __wrap_clock_gettime(clockid_t clock,struct timespec *out) {
 int result=__real_clock_gettime(clock,out);
 if(!result&&late_expiry&&clock==CLOCK_MONOTONIC){out->tv_sec=(time_t)(deadline/1000000000);out->tv_nsec=(long)(deadline%1000000000);}
 return result;
}
es_hash_result __wrap_es_hash_file(es_hash *owner,int fd,es_hash_identity *out) {
 hash_depth++;hash_calls++;es_hash_result result=__real_es_hash_file(owner,fd,out);hash_depth--;
 if(!strcmp(mode,"change-after-first")&&hash_calls==1&&result==ES_HASH_OK){unsigned char byte=7;CHECK(pwrite(writer,&byte,1,8191)==1);}
 if(hash_calls==2) {
  if(!strcmp(mode,"second-device")&&result==ES_HASH_OK)out->device^=1;
  if(!strcmp(mode,"second-inode")&&result==ES_HASH_OK)out->inode^=1;
  if(!strcmp(mode,"second-size")&&result==ES_HASH_OK)out->size++;
  if(!strcmp(mode,"cancel-final")||!strcmp(mode,"cleanup-cancel")){uint64_t value=1;CHECK(write(cancel,&value,sizeof(value))==sizeof(value));}
  if(!strcmp(mode,"deadline-final"))late_expiry=1;
  if(!strcmp(mode,"cleanup-cancel")||!strcmp(mode,"hash-cleanup")){owner->cleanup=ES_HASH_CLEANUP;owner->terminal=ES_HASH_CLEANUP;return ES_HASH_CLEANUP;}
 }
 return result;
}
static ssize_t reading(int fd,void *out,size_t n,off_t offset) {
 if(fd==source&&!hash_depth){
  reads++;
  if(!strcmp(mode,"partial")&&n>3)n=3;
  if(!strcmp(mode,"eintr")&&!interrupted++){errno=EINTR;return -1;}
  if(!strcmp(mode,"read-error")){errno=EIO;return -1;}
  if(!strcmp(mode,"read-eof"))return 0;
 }
 return __real_pread(fd,out,n,offset);
}
ssize_t __wrap_pread(int fd,void *out,size_t n,off_t offset){return reading(fd,out,n,offset);}
ssize_t __wrap___pread_chk(int fd,void *out,size_t n,off_t offset,size_t bound){CHECK(n<=bound);return reading(fd,out,n,offset);}
static int zero(const void *p,size_t n){const unsigned char *b=p;while(n--)if(*b++)return 0;return 1;}
int main(int argc,char **argv) {
 CHECK(argc==5);mode=argv[2];unsigned want=(unsigned)strtoul(argv[3],NULL,10);CHECK(strlen(argv[4])==64);
 unsigned char expected[32];for(unsigned i=0;i<32;i++){unsigned byte;CHECK(sscanf(argv[4]+i*2,"%2x",&byte)==1);expected[i]=(unsigned char)byte;}
 struct timespec now;CHECK(clock_gettime(CLOCK_MONOTONIC,&now)==0);
 deadline=(uint64_t)now.tv_sec*1000000000+(uint64_t)now.tv_nsec+UINT64_C(9000000000);
 cancel=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel>=0);
 es_file file={0};es_hash hash={0};es_elf_layout layout;memset(&layout,0x55,sizeof(layout));
 writer=open(argv[1],O_RDWR|O_CLOEXEC);CHECK(writer>=0);
 CHECK(es_file_open(&file,cancel,deadline,argv[1],strlen(argv[1]))==ES_FILE_OK);source=file.fd;
 CHECK(es_hash_open(&hash,cancel,deadline)==ES_HASH_OK);CHECK(lseek(file.fd,7,SEEK_SET)==7);
 if(!strcmp(mode,"objects-exact")||!strcmp(mode,"objects-over")) {
  unsigned n=!strcmp(mode,"objects-exact")?510:511;es_hash_identity ignored;
  for(unsigned i=0;i<n;i++)CHECK(__real_es_hash_file(&hash,file.fd,&ignored)==ES_HASH_OK);
  CHECK(hash.objects==n);
 }
 if(!strcmp(mode,"wrong-digest"))expected[0]^=1;
 int other=-1;
 if(!strcmp(mode,"foreign-cancel")){other=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(other>=0);hash.cancel_fd=other;}
 if(!strcmp(mode,"foreign-deadline"))hash.deadline_ns++;
 if(!strcmp(mode,"cancel-before")){uint64_t value=1;CHECK(write(cancel,&value,sizeof(value))==sizeof(value));}
 if(!strcmp(mode,"deadline-before"))late_expiry=1;
 if(!strcmp(mode,"cloexec"))CHECK(fcntl(file.fd,F_SETFD,0)==0);
 if(!strcmp(mode,"changed-mode"))CHECK(fchmod(writer,0620)==0);
 es_file before_file=file;es_hash before_hash=hash;
 const es_file *fp=&file;es_hash *hp=&hash;const unsigned char *ep=expected;es_elf_layout *op=&layout;
 if(!strcmp(mode,"null-file"))fp=NULL;
 if(!strcmp(mode,"null-hash"))hp=NULL;
 if(!strcmp(mode,"null-digest"))ep=NULL;
 if(!strcmp(mode,"null-output"))op=NULL;
 if(!strcmp(mode,"output-file"))op=(es_elf_layout *)(void *)&file;
 if(!strcmp(mode,"output-hash"))op=(es_elf_layout *)(void *)&hash;
 if(!strcmp(mode,"output-span"))op=(es_elf_layout *)(UINTPTR_MAX-8);
 if(!strcmp(mode,"output-expected"))ep=(const unsigned char *)&layout;
 if(!strcmp(mode,"digest-file"))ep=(const unsigned char *)&file;
 if(!strcmp(mode,"digest-hash"))ep=(const unsigned char *)&hash;
 if(!strcmp(mode,"digest-span"))ep=(const unsigned char *)(UINTPTR_MAX-8);
 if(!strcmp(mode,"owner-alias"))hp=(es_hash *)(void *)&file;
 es_elf_result result=es_elf_check(fp,hp,ep,op);
 if((unsigned)result!=want)printf("ELF_RESULT_%u\n",(unsigned)result);
 CHECK((unsigned)result==want);
 if(!strcmp(mode,"wrong-digest"))CHECK(hash_calls==1&&reads==0);
 if(want==ES_ELF_INVALID){CHECK(!memcmp(&file,&before_file,sizeof(file)));CHECK(!memcmp(&hash,&before_hash,sizeof(hash)));}
 if(want){if(op==&layout&&strcmp(mode,"output-expected"))CHECK(zero(&layout,sizeof(layout)));}
 else {
  CHECK(layout.file.size==(uint64_t)lseek(writer,0,SEEK_END));CHECK(!memcmp(layout.file.sha256,expected,32));
  CHECK(hash_calls==2);CHECK(hash.objects==(!strcmp(mode,"objects-exact")?512:2));
  CHECK(layout.phnum>0&&layout.phnum<=128&&layout.load_count>0&&layout.load_count<=32);
  CHECK(zero(&layout.programs[layout.phnum],(128-layout.phnum)*sizeof(layout.programs[0])));
  if(!strcmp(mode,"basic")||!strcmp(mode,"partial")||!strcmp(mode,"eintr")) {
   CHECK(hash.bytes==16384&&layout.type==2&&layout.machine==62&&layout.phnum==3&&layout.load_count==2);
   CHECK(layout.programs[0].type==1&&layout.programs[0].flags==4&&layout.programs[0].filesz==4096);
   CHECK(layout.programs[1].offset==4096&&layout.programs[1].vaddr==UINT64_C(0x401000)&&layout.programs[1].flags==5);
   CHECK(layout.programs[2].type==UINT32_C(0x6474e551)&&layout.programs[2].flags==6);
  }
  if(!strcmp(mode,"all-headers")) {
   const es_elf_program records[]={
    {6,4,64,0x400040,0x400040,616,616,8}, {3,4,800,0x400320,0x400320,16,16,1},
    {1,4,0,0x400000,0x400000,4096,4096,4096}, {1,6,4096,0x401000,0x401000,4096,4096,4096},
    {2,6,1024,0x400400,0x400400,16,16,8}, {7,4,1200,0x4004b0,0x4004b0,8,16,8},
    {4,4,1500,0,0,16,16,4}, {0x6474e550,4,1600,0x400640,0x400640,32,32,4},
    {0x6474e551,6,0,0,0,0,0,16}, {0x6474e552,4,4096,0x401000,0x401000,128,128,1},
    {0x6474e553,4,1600,0x400640,0x400640,32,32,8}};
   CHECK(layout.phnum==11&&layout.load_count==2);CHECK(!memcmp(layout.programs,records,sizeof(records)));
  }
  if(!strcmp(mode,"section-metadata"))CHECK(layout.shoff==UINT64_MAX&&layout.shentsize==65535&&layout.shnum==65534&&layout.shstrndx==65533);
  if(!strcmp(mode,"max-headers"))CHECK(layout.phnum==128&&layout.programs[127].type==0&&layout.programs[127].vaddr==UINT64_MAX);
  if(!strcmp(mode,"max-loads"))CHECK(layout.load_count==32);
 }
 CHECK(lseek(file.fd,0,SEEK_CUR)==7);
 if(!strcmp(mode,"partial"))CHECK(reads>20);
 if(!strcmp(mode,"eintr"))CHECK(interrupted>0);
 if(!strcmp(mode,"cancel-final")||!strcmp(mode,"cancel-before")||!strcmp(mode,"cleanup-cancel")){uint64_t value=0;CHECK(read(cancel,&value,sizeof(value))==sizeof(value)&&value==1);}
 es_hash_result closed=es_hash_close(&hash);CHECK(closed==((!strcmp(mode,"cleanup-cancel")||!strcmp(mode,"hash-cleanup"))?ES_HASH_CLEANUP:ES_HASH_OK));
 CHECK(es_file_close(&file)==ES_FILE_OK);CHECK(close(cancel)==0);CHECK(close(writer)==0);if(other>=0)CHECK(close(other)==0);return 0;
}
#endif

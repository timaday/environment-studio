#define _GNU_SOURCE
#include "privacy-elf.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <time.h>
#include <unistd.h>
#define REQUIRE(x) do{if(!(x)){printf("INDEPENDENT_ELF_%d\n",__LINE__);exit(71);}}while(0)
static int mode,cancel_fd,hashing,hashes,layout_reads;
es_hash_result __real_es_hash_file(es_hash *,int,es_hash_identity *);
ssize_t __real_pread(int,void *,size_t,off_t);
static void cancel(void){uint64_t one=1;REQUIRE(write(cancel_fd,&one,8)==8);}
es_hash_result __wrap_es_hash_file(es_hash *hash,int fd,es_hash_identity *out){
 hashing=1;hashes++;es_hash_result result=__real_es_hash_file(hash,fd,out);hashing=0;
 if(mode==2&&hashes==1){REQUIRE(result==ES_HASH_OK);hash->cleanup=ES_HASH_CLEANUP;hash->terminal=ES_HASH_CLEANUP;cancel();return ES_HASH_CLEANUP;}
 return result;
}
static ssize_t reading(int fd,void *out,size_t n,off_t offset){
 ssize_t count=__real_pread(fd,out,n,offset);
 if(!hashing){layout_reads++;if(mode==1&&layout_reads==1)cancel();}
 return count;
}
ssize_t __wrap_pread(int fd,void *out,size_t n,off_t offset){return reading(fd,out,n,offset);}
ssize_t __wrap___pread_chk(int fd,void *out,size_t n,off_t offset,size_t bound){REQUIRE(n<=bound);return reading(fd,out,n,offset);}
int main(int argc,char **argv){
 REQUIRE(argc==5);mode=atoi(argv[2]);int expected_result=atoi(argv[3]);unsigned char sha[32];
 REQUIRE(strlen(argv[4])==64);for(unsigned i=0;i<32;i++){unsigned byte;REQUIRE(sscanf(argv[4]+2*i,"%2x",&byte)==1);sha[i]=(unsigned char)byte;}
 struct timespec now;REQUIRE(clock_gettime(CLOCK_MONOTONIC,&now)==0);uint64_t end=(uint64_t)now.tv_sec*1000000000+(uint64_t)now.tv_nsec+9000000000;
 cancel_fd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);REQUIRE(cancel_fd>=0);
 es_file file={0};es_hash hash={0};es_elf_layout output;memset(&output,0xa3,sizeof(output));
 REQUIRE(es_file_open(&file,cancel_fd,end,argv[1],strlen(argv[1]))==ES_FILE_OK);REQUIRE(es_hash_open(&hash,cancel_fd,end)==ES_HASH_OK);
 REQUIRE(lseek(file.fd,19,SEEK_SET)==19);es_file before=file;
 es_elf_result result=es_elf_check(&file,&hash,sha,&output);REQUIRE((int)result==expected_result);
 REQUIRE(!memcmp(&before,&file,sizeof(file)));REQUIRE(lseek(file.fd,0,SEEK_CUR)==19);
 if(result){unsigned char *p=(unsigned char *)&output;for(size_t i=0;i<sizeof(output);i++)REQUIRE(p[i]==0);}
 else {REQUIRE(hashes==2);REQUIRE(hash.objects==2&&hash.bytes==16384);REQUIRE(output.file.size==8192);REQUIRE(!memcmp(output.file.sha256,sha,32));}
 if(mode){REQUIRE(hashes==1);REQUIRE(hash.objects==1&&hash.bytes==8192);REQUIRE(layout_reads==(mode==1?1:0));uint64_t one=0;REQUIRE(read(cancel_fd,&one,8)==8&&one==1);}
 REQUIRE(es_hash_close(&hash)==(mode==2?ES_HASH_CLEANUP:ES_HASH_OK));REQUIRE(es_file_close(&file)==ES_FILE_OK);REQUIRE(close(cancel_fd)==0);return 0;
}

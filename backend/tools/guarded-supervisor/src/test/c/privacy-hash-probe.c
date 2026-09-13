#define _GNU_SOURCE
#include "privacy-hash.h"
#include <errno.h>
#include <fcntl.h>
#include <openssl/crypto.h>
#include <openssl/evp.h>
#include <openssl/provider.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#define REQUIRE(x) do {if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}} while(0)
static const char *mode;
static int file=-1,writer=-1,cancel=-1,reads,stats,injected,expired;
static int contexts_freed,providers_freed,libraries_freed,algorithms_freed;
static int is(const char *s){return strcmp(mode,s)==0;}
extern ssize_t __real_pread(int,void *,size_t,off_t);
extern int __real_fstat(int,struct stat *);
extern int __real_clock_gettime(clockid_t,struct timespec *);
static uint64_t now(void){struct timespec t;REQUIRE(__real_clock_gettime(CLOCK_MONOTONIC,&t)==0);return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
static void signal_cancel(void){uint64_t one=1;REQUIRE(write(cancel,&one,sizeof(one))==sizeof(one));}
ssize_t __wrap_pread(int fd,void *out,size_t size,off_t offset){
 if(fd!=file)return __real_pread(fd,out,size,offset);
 reads++;
 if(is("read-fault")){errno=EIO;return -1;}
 if(is("eintr")&&!injected++){errno=EINTR;return -1;}
 if((is("partial")||is("truncate"))&&size>1)size=1;
 ssize_t count=__real_pread(fd,out,size,offset);
 if(count>0&&!injected){
  if(is("grow")){REQUIRE(pwrite(writer,"z",1,3)==1);injected=1;}
  if(is("truncate")){REQUIRE(ftruncate(writer,0)==0);injected=1;}
  if(is("change-content")){REQUIRE(pwrite(writer,"xyz",3,0)==3);injected=1;}
  if(is("cancel-read")){signal_cancel();injected=1;}
 }
 if(count==0&&is("final-cancel"))signal_cancel();
 if(count==0&&is("final-deadline"))expired=1;
 return count;
}
ssize_t __wrap___pread_chk(int fd,void *out,size_t size,off_t offset,size_t capacity){REQUIRE(size<=capacity);return __wrap_pread(fd,out,size,offset);}
int __wrap_fstat(int fd,struct stat *out){
 int r=__real_fstat(fd,out);if(fd!=file)return r;stats++;
 if(is("stat-fault")||(is("final-stat-fault")&&stats==2)){errno=EIO;return -1;}
 if(!r&&stats==2){
  if(is("device"))out->st_dev++;
  if(is("inode"))out->st_ino++;
  if(is("size"))out->st_size++;
  if(is("mode"))out->st_mode^=S_IXUSR;
  if(is("uid"))out->st_uid++;
  if(is("gid"))out->st_gid++;
  if(is("mtime"))out->st_mtim.tv_nsec^=1;
  if(is("ctime"))out->st_ctim.tv_nsec^=1;
  if(is("cancel-stat"))signal_cancel();
 }
 return r;
}
int __wrap_clock_gettime(clockid_t clock,struct timespec *out){int r=__real_clock_gettime(clock,out);if(!r&&expired)out->tv_sec+=20;return r;}
extern OSSL_LIB_CTX *__real_OSSL_LIB_CTX_new(void);
OSSL_LIB_CTX *__wrap_OSSL_LIB_CTX_new(void){return is("library-fault")?NULL:__real_OSSL_LIB_CTX_new();}
extern void __real_OSSL_LIB_CTX_free(OSSL_LIB_CTX *);
void __wrap_OSSL_LIB_CTX_free(OSSL_LIB_CTX *p){if(p)libraries_freed++;__real_OSSL_LIB_CTX_free(p);}
extern OSSL_PROVIDER *__real_OSSL_PROVIDER_load(OSSL_LIB_CTX *,const char *);
OSSL_PROVIDER *__wrap_OSSL_PROVIDER_load(OSSL_LIB_CTX *ctx,const char *name){REQUIRE(!strcmp(name,"default"));return is("provider-fault")?NULL:__real_OSSL_PROVIDER_load(ctx,name);}
extern int __real_OSSL_PROVIDER_unload(OSSL_PROVIDER *);
int __wrap_OSSL_PROVIDER_unload(OSSL_PROVIDER *p){providers_freed++;int r=__real_OSSL_PROVIDER_unload(p);return (is("close-fault")||is("update-close-fault"))?0:r;}
extern EVP_MD *__real_EVP_MD_fetch(OSSL_LIB_CTX *,const char *,const char *);
EVP_MD *__wrap_EVP_MD_fetch(OSSL_LIB_CTX *ctx,const char *name,const char *properties){REQUIRE(!strcmp(name,"SHA2-256")&&!strcmp(properties,"provider=default"));return is("algorithm-fault")?NULL:__real_EVP_MD_fetch(ctx,name,properties);}
extern void __real_EVP_MD_free(EVP_MD *);
void __wrap_EVP_MD_free(EVP_MD *p){if(p)algorithms_freed++;__real_EVP_MD_free(p);}
extern EVP_MD_CTX *__real_EVP_MD_CTX_new(void);
EVP_MD_CTX *__wrap_EVP_MD_CTX_new(void){return is("context-fault")?NULL:__real_EVP_MD_CTX_new();}
extern void __real_EVP_MD_CTX_free(EVP_MD_CTX *);
void __wrap_EVP_MD_CTX_free(EVP_MD_CTX *p){if(p)contexts_freed++;__real_EVP_MD_CTX_free(p);}
extern int __real_EVP_DigestInit_ex2(EVP_MD_CTX *,const EVP_MD *,const OSSL_PARAM *);
int __wrap_EVP_DigestInit_ex2(EVP_MD_CTX *ctx,const EVP_MD *algorithm,const OSSL_PARAM *parameters){return is("init-fault")?0:__real_EVP_DigestInit_ex2(ctx,algorithm,parameters);}
extern int __real_EVP_DigestUpdate(EVP_MD_CTX *,const void *,size_t);
int __wrap_EVP_DigestUpdate(EVP_MD_CTX *p,const void *data,size_t size){return (is("update-fault")||is("update-close-fault"))?0:__real_EVP_DigestUpdate(p,data,size);}
extern int __real_EVP_DigestFinal_ex(EVP_MD_CTX *,unsigned char *,unsigned int *);
int __wrap_EVP_DigestFinal_ex(EVP_MD_CTX *p,unsigned char *out,unsigned int *size){
 if(is("final-fault"))return 0;
 int r=__real_EVP_DigestFinal_ex(p,out,size);if(is("wrong-digest-size"))*size=31;
 if(is("cancel-final"))signal_cancel();
 return r;
}
static void zeros(const void *data,size_t size){const unsigned char *p=data;for(size_t i=0;i<size;i++)REQUIRE(p[i]==0);}
static void digest(const es_hash_identity *actual,const char *hex){
 REQUIRE(strlen(hex)==64);for(size_t i=0;i<32;i++){unsigned int value;REQUIRE(sscanf(hex+2*i,"%2x",&value)==1);REQUIRE(actual->sha256[i]==value);}
}
int main(int argc,char **argv){
 REQUIRE(argc==2);mode=argv[1];
 char name[]="/tmp/es-hash-invented-XXXXXX";writer=mkstemp(name);REQUIRE(writer>=0);REQUIRE(write(writer,"abc",3)==3);
 file=open(name,O_RDONLY|O_CLOEXEC);REQUIRE(file>=0);REQUIRE(unlink(name)==0);cancel=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);REQUIRE(cancel>=0);
 if(is("empty")||is("max-objects"))REQUIRE(ftruncate(writer,0)==0);
 if(is("large")||is("max-total")){REQUIRE(ftruncate(writer,0)==0);REQUIRE(ftruncate(writer,512LL*1024*1024)==0);}
 if(is("too-large"))REQUIRE(ftruncate(writer,512LL*1024*1024+1)==0);
 if(is("million")){char block[1000];memset(block,'a',sizeof block);REQUIRE(ftruncate(writer,0)==0);REQUIRE(lseek(writer,0,SEEK_SET)==0);for(int i=0;i<1000;i++)REQUIRE(write(writer,block,sizeof block)==sizeof block);}
 if(is("wrong-mode")){REQUIRE(close(file)==0);file=fcntl(writer,F_DUPFD_CLOEXEC,3);REQUIRE(file>=0);}
 if(is("no-cloexec"))REQUIRE(fcntl(file,F_SETFD,0)==0);
 if(is("directory")){REQUIRE(close(file)==0);file=open("/tmp",O_RDONLY|O_CLOEXEC|O_DIRECTORY);REQUIRE(file>=0);}
 es_hash owner={0};uint64_t deadline=now()+10000000000ULL;
 if(is("expired-open"))deadline=now()-1;
 if(is("long-open"))deadline=now()+20000000000ULL;
 if(is("cancel-open"))signal_cancel();
 es_hash_result opened=es_hash_open(&owner,cancel,deadline);
 if(is("expired-open")||is("long-open")||is("cancel-open")||is("library-fault")||is("provider-fault")||is("algorithm-fault")){
  es_hash_result expected=is("expired-open")?ES_HASH_DEADLINE:is("long-open")?ES_HASH_INVALID:is("cancel-open")?ES_HASH_CANCELLED:ES_HASH_CRYPTO;
  REQUIRE(opened==expected);REQUIRE(owner.objects==0&&owner.bytes==0);REQUIRE(es_hash_open(&owner,cancel,now()+10000000000ULL)==ES_HASH_INVALID);
  REQUIRE(es_hash_close(&owner)==ES_HASH_OK);REQUIRE(es_hash_close(&owner)==ES_HASH_OK);goto cleanup;
 }
 REQUIRE(opened==ES_HASH_OK);REQUIRE(lseek(file,2,SEEK_SET)==2||is("directory"));es_hash_identity actual;memset(&actual,0xa5,sizeof actual);
 if(is("null-owner")||is("null-output")||is("overlap")||is("negative-fd")||is("control-alias")){
  es_hash saved=owner;es_hash_result r;
  if(is("null-owner"))r=es_hash_file(NULL,file,&actual);
  else if(is("null-output"))r=es_hash_file(&owner,file,NULL);
  else if(is("overlap"))r=es_hash_file(&owner,file,(es_hash_identity *)&owner);
  else r=es_hash_file(&owner,is("negative-fd")?-1:cancel,&actual);
  REQUIRE(r==ES_HASH_INVALID&&!memcmp(&saved,&owner,sizeof owner)&&reads==0&&stats==0);
  if(!is("null-output")&&!is("overlap"))zeros(&actual,sizeof actual);
  REQUIRE(es_hash_close(&owner)==ES_HASH_OK);goto cleanup;
 }
 es_hash_result expected=ES_HASH_OK;
 if(is("too-large"))expected=ES_HASH_RESOURCE;
 if(is("read-fault"))expected=ES_HASH_IO;
 if(is("cancel-read")||is("final-cancel")||is("cancel-stat")||is("cancel-final"))expected=ES_HASH_CANCELLED;
 if(is("final-deadline"))expected=ES_HASH_DEADLINE;
 if(is("context-fault")||is("init-fault")||is("update-close-fault")||is("update-fault")||is("final-fault")||is("wrong-digest-size"))expected=ES_HASH_CRYPTO;
 if(is("grow")||is("truncate")||is("change-content")||is("stat-fault")||is("final-stat-fault")||is("device")||is("inode")||is("size")||is("mode")||is("uid")||is("gid")||is("mtime")||is("ctime")||is("wrong-mode")||is("no-cloexec")||is("directory"))expected=ES_HASH_FILE;
 REQUIRE(es_hash_file(&owner,file,&actual)==expected);
 if(expected){zeros(&actual,sizeof actual);uint64_t bytes=owner.bytes;uint32_t objects=owner.objects;REQUIRE(es_hash_file(&owner,file,&actual)==expected);REQUIRE(owner.bytes==bytes&&owner.objects==objects);}
 else if(is("empty")||is("max-objects"))digest(&actual,"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
 else if(is("million"))digest(&actual,"cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0");
 else if(is("large")||is("max-total"))digest(&actual,"9acca8e8c22201155389f65abbf6bc9723edc7384ead80503839f49dcc56d767");
 else digest(&actual,"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
 if(is("max-total")){
  for(int i=0;i<3;i++){REQUIRE(es_hash_file(&owner,file,&actual)==ES_HASH_OK);digest(&actual,"9acca8e8c22201155389f65abbf6bc9723edc7384ead80503839f49dcc56d767");}
  REQUIRE(owner.bytes==2ULL*1024*1024*1024&&owner.objects==4);REQUIRE(ftruncate(writer,0)==0);REQUIRE(es_hash_file(&owner,file,&actual)==ES_HASH_OK);REQUIRE(owner.objects==5);
  REQUIRE(pwrite(writer,"x",1,0)==1);int before=reads;REQUIRE(es_hash_file(&owner,file,&actual)==ES_HASH_RESOURCE);REQUIRE(reads==before);zeros(&actual,sizeof actual);
 }
 if(is("max-objects")){
  for(int i=1;i<512;i++)REQUIRE(es_hash_file(&owner,file,&actual)==ES_HASH_OK);
  REQUIRE(owner.objects==512&&owner.bytes==0);int before=reads;REQUIRE(es_hash_file(&owner,file,&actual)==ES_HASH_RESOURCE);REQUIRE(reads==before);zeros(&actual,sizeof actual);
 }
 if(!is("directory")&&!is("wrong-mode"))REQUIRE(lseek(file,0,SEEK_CUR)==2);
 {es_hash_result closed=es_hash_close(&owner);REQUIRE(closed==((is("close-fault")||is("update-close-fault"))?ES_HASH_CLEANUP:ES_HASH_OK));int count=contexts_freed+providers_freed+algorithms_freed+libraries_freed;
 REQUIRE(es_hash_close(&owner)==closed);REQUIRE(count==contexts_freed+providers_freed+algorithms_freed+libraries_freed);REQUIRE(providers_freed==1&&algorithms_freed==1&&libraries_freed==1);
 REQUIRE(es_hash_open(&owner,cancel,now()+10000000000ULL)==ES_HASH_INVALID);}
 cleanup:
 REQUIRE(fcntl(file,F_GETFD)>=0&&fcntl(cancel,F_GETFD)>=0);
 if(is("cancel-read")||is("final-cancel")||is("cancel-stat")||is("cancel-final")||is("cancel-open")){uint64_t event;REQUIRE(read(cancel,&event,sizeof event)==sizeof event&&event>0);}
 REQUIRE(close(file)==0&&close(writer)==0&&close(cancel)==0);return 0;
}

#define _GNU_SOURCE
#include "privacy-hash.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stddef.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include <openssl/crypto.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/provider.h>
#define FILE_MAX (512ULL*1024*1024)
#define TOTAL_MAX (2ULL*1024*1024*1024)
static void wipe(void *memory,size_t length){volatile unsigned char *p=memory;while(length--)*p++=0;}
static es_hash_result stop(es_hash *owner,es_hash_result result){
 if(result&&owner->terminal==ES_HASH_OK)owner->terminal=result;
 if(result==ES_HASH_CLEANUP)owner->terminal=result;
 if(result&&owner->state==1)owner->state=2;
 ERR_clear_error();return owner->terminal;
}
static es_hash_result guard(es_hash *owner){
 struct pollfd cancel={.fd=owner->cancel_fd,.events=POLLIN};
 if(poll(&cancel,1,0)<0)return ES_HASH_IO;
 if(cancel.revents&POLLIN)return ES_HASH_CANCELLED;
 if(cancel.revents)return ES_HASH_IO;
 struct timespec t;
 if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<0||t.tv_nsec<0||t.tv_nsec>=1000000000L)return ES_HASH_IO;
 if((uint64_t)t.tv_sec>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL)return ES_HASH_IO;
 uint64_t current=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;
 if(current>=owner->deadline_ns)return ES_HASH_DEADLINE;
 return owner->deadline_ns-current>10000000000ULL?ES_HASH_INVALID:ES_HASH_OK;
}
es_hash_result es_hash_open(es_hash *owner,int cancel,uint64_t deadline){
 if(!owner||owner->state||owner->library||owner->provider||owner->algorithm||owner->bytes||owner->objects||owner->deadline_ns||owner->cancel_fd||owner->terminal||owner->cleanup||cancel<0)return ES_HASH_INVALID;
 owner->state=1;owner->cancel_fd=cancel;owner->deadline_ns=deadline;
 es_hash_result result=guard(owner);if(result)return stop(owner,result);
#if !defined(__linux__) || !defined(__x86_64__) || defined(__ILP32__)
 return stop(owner,ES_HASH_PLATFORM);
#else
 if(OPENSSL_init_crypto(OPENSSL_INIT_NO_LOAD_CONFIG,NULL)!=1)return stop(owner,ES_HASH_CRYPTO);
 owner->library=OSSL_LIB_CTX_new();if(!owner->library)return stop(owner,ES_HASH_CRYPTO);
 owner->provider=OSSL_PROVIDER_load(owner->library,"default");if(!owner->provider)return stop(owner,ES_HASH_CRYPTO);
 owner->algorithm=EVP_MD_fetch(owner->library,"SHA2-256","provider=default");
 if(!owner->algorithm||EVP_MD_get_size(owner->algorithm)!=32)return stop(owner,ES_HASH_CRYPTO);
 return stop(owner,guard(owner));
#endif
}
static int overlaps(const void *left,size_t ln,const void *right,size_t rn){
 uintptr_t l=(uintptr_t)left,r=(uintptr_t)right;
 if(l>UINTPTR_MAX-(ln-1)||r>UINTPTR_MAX-(rn-1))return 1;
 return l>=r?l-r<rn:r-l<ln;
}
static int unchanged(const struct stat *a,const struct stat *b){
 return a->st_dev==b->st_dev&&a->st_ino==b->st_ino&&a->st_size==b->st_size&&a->st_mode==b->st_mode&&a->st_uid==b->st_uid&&a->st_gid==b->st_gid&&
 a->st_mtim.tv_sec==b->st_mtim.tv_sec&&a->st_mtim.tv_nsec==b->st_mtim.tv_nsec&&a->st_ctim.tv_sec==b->st_ctim.tv_sec&&a->st_ctim.tv_nsec==b->st_ctim.tv_nsec;
}
es_hash_result es_hash_file(es_hash *owner,int fd,es_hash_identity *out){
 if(!out||(owner&&overlaps(owner,sizeof(*owner),out,sizeof(*out))))return ES_HASH_INVALID;
 wipe(out,sizeof(*out));
 if(!owner||fd<0||fd==owner->cancel_fd)return ES_HASH_INVALID;
 if(owner->state!=1)return owner->terminal?owner->terminal:ES_HASH_INVALID;
 es_hash_result result=guard(owner);if(result)return stop(owner,result);
 if(owner->objects>=512||owner->bytes>TOTAL_MAX)return stop(owner,ES_HASH_RESOURCE);
 owner->objects++;
 unsigned char scratch[65536],digest[EVP_MAX_MD_SIZE];unsigned int digest_size=0;
 struct stat initial={0},final={0};uint64_t offset=0;EVP_MD_CTX *context=NULL;
 int flags=fcntl(fd,F_GETFL),descriptor=fcntl(fd,F_GETFD);
 if(flags<0||descriptor<0||(flags&O_ACCMODE)!=O_RDONLY||(flags&O_PATH)||!(descriptor&FD_CLOEXEC)||fstat(fd,&initial)||!S_ISREG(initial.st_mode)||initial.st_size<0){result=ES_HASH_FILE;goto done;}
 if((uint64_t)initial.st_size>FILE_MAX){result=ES_HASH_RESOURCE;goto done;}
 result=guard(owner);if(result)goto done;
 context=EVP_MD_CTX_new();if(!context||EVP_DigestInit_ex2(context,owner->algorithm,NULL)!=1){result=ES_HASH_CRYPTO;goto done;}
 while(offset<(uint64_t)initial.st_size){
  result=guard(owner);if(result)goto done;
  uint64_t remaining=(uint64_t)initial.st_size-offset;
  if(remaining>TOTAL_MAX-owner->bytes)remaining=TOTAL_MAX-owner->bytes;
  if(!remaining){result=ES_HASH_RESOURCE;goto done;}
  size_t requested=remaining>sizeof(scratch)?sizeof(scratch):(size_t)remaining;
  ssize_t count=pread(fd,scratch,requested,(off_t)offset);int error=errno;
  result=guard(owner);if(result)goto done;
  if(count<0){if(error==EINTR)continue;result=ES_HASH_IO;goto done;}
  if(!count||(size_t)count>requested){result=ES_HASH_FILE;goto done;}
  if(EVP_DigestUpdate(context,scratch,(size_t)count)!=1){result=ES_HASH_CRYPTO;goto done;}
  owner->bytes+=(uint64_t)count;offset+=(uint64_t)count;
  wipe(scratch,(size_t)count);result=guard(owner);if(result)goto done;
 }
 for(;;){
  result=guard(owner);if(result)goto done;
  ssize_t count=pread(fd,scratch,1,(off_t)offset);int error=errno;
  result=guard(owner);if(result)goto done;
  if(count<0&&error==EINTR)continue;
  if(count){result=count<0?ES_HASH_IO:ES_HASH_FILE;goto done;}break;
 }
 if(EVP_DigestFinal_ex(context,digest,&digest_size)!=1||digest_size!=32){result=ES_HASH_CRYPTO;goto done;}
 result=guard(owner);if(result)goto done;
 if(fstat(fd,&final)||!unchanged(&initial,&final)){result=ES_HASH_FILE;goto done;}
 result=guard(owner);
 done:
 EVP_MD_CTX_free(context);
 if(!result)result=guard(owner);
 if(!result){out->device=(uint64_t)initial.st_dev;out->inode=(uint64_t)initial.st_ino;out->size=(uint64_t)initial.st_size;memcpy(out->sha256,digest,32);}
 wipe(scratch,sizeof(scratch));wipe(digest,sizeof(digest));wipe(&initial,sizeof(initial));wipe(&final,sizeof(final));
 return stop(owner,result);
}
es_hash_result es_hash_close(es_hash *owner){
 if(!owner||!owner->state)return ES_HASH_INVALID;
 if(owner->state==3)return owner->cleanup;
 void *algorithm=owner->algorithm,*provider=owner->provider,*library=owner->library;
 owner->algorithm=NULL;owner->provider=NULL;owner->library=NULL;
 EVP_MD_free(algorithm);
 if(provider&&OSSL_PROVIDER_unload(provider)!=1)owner->cleanup=ES_HASH_CLEANUP;
 OSSL_LIB_CTX_free(library);owner->state=3;
 if(owner->cleanup==ES_HASH_CLEANUP)owner->terminal=ES_HASH_CLEANUP;
 ERR_clear_error();return owner->cleanup;
}

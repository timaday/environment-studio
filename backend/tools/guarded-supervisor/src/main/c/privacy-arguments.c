#define _GNU_SOURCE
#include "privacy-arguments.h"
#include <errno.h>
#include <fcntl.h>
#include <linux/magic.h>
#include <stdio.h>
#include <string.h>
#include <sys/vfs.h>
#include <unistd.h>
static void wipe(void *v,size_t n){volatile unsigned char *p=v;while(n--)*p++=0;}
static int vector(const unsigned char *bytes,size_t length){
 if(!bytes||!length||length>16384||(uintptr_t)bytes>UINTPTR_MAX-(length-1))return 0;
 size_t width=0,count=0;
 for(size_t i=0;i<length;i++){
  if(bytes[i]){if(++width>1024)return 0;}
  else {if(++count>128)return 0;width=0;}
 }
 return bytes[length-1]==0&&count>0;
}
static es_peer_result live(es_peer *peer){
 es_peer_identity identity={0};es_peer_result r=es_peer_read(peer,&identity);wipe(&identity,sizeof(identity));return r;
}
static es_peer_result record(es_peer *peer,const unsigned char *expected,size_t length){
 char path[64];unsigned char data[16385];size_t used=0;int fd=-1,complete=0;es_peer_result r=live(peer);
 if(r)goto done;
 int size=snprintf(path,sizeof(path),"/proc/%u/cmdline",peer->identity.pid);
 if(size<0||(size_t)size>=sizeof(path)){r=ES_PEER_IDENTITY;goto done;}
 fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK);
 if(fd<0){r=ES_PEER_IDENTITY;goto finish;}
 struct statfs filesystem;
 if(fstatfs(fd,&filesystem)||filesystem.f_type!=PROC_SUPER_MAGIC){r=ES_PEER_IDENTITY;goto finish;}
 while(used<sizeof(data)){
  r=live(peer);if(r)break;
  ssize_t count=read(fd,data+used,sizeof(data)-used);int error=errno;
  r=live(peer);if(r)break;
  if(!count){complete=1;break;}
  if(count<0){if(error==EINTR)continue;r=ES_PEER_IO;break;}
  used+=(size_t)count;
 }
 if(!r&&(!complete||used!=length||memcmp(data,expected,length)))r=ES_PEER_IDENTITY;
 finish:
 if(fd>=0&&close(fd))r=ES_PEER_CLEANUP;
 if(r!=ES_PEER_CLEANUP){es_peer_result final=live(peer);if(final)r=final;}
 done:wipe(data,sizeof(data));wipe(path,sizeof(path));return r;
}
es_peer_result es_arguments_check(es_peer *peer,const unsigned char *expected,size_t length){
 if(!peer||!expected||!length||length>16384)return ES_PEER_INVALID;
 uintptr_t x=(uintptr_t)expected,y=(uintptr_t)peer;
 if((x>=y?x-y<sizeof(*peer):y-x<length)||!vector(expected,length)||peer->state!=1||peer->pidfd<0)return ES_PEER_INVALID;
 es_peer_result r=record(peer,expected,length);return r?r:record(peer,expected,length);
}

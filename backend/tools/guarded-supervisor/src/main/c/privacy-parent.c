#define _GNU_SOURCE
#include "privacy-parent.h"
#include <errno.h>
#include <fcntl.h>
#include <linux/magic.h>
#include <stdio.h>
#include <string.h>
#include <sys/vfs.h>
#include <unistd.h>
static void wipe(void *v,size_t n){volatile unsigned char *p=v;while(n--)*p++=0;}
static int overlap(const void *a,const void *b,size_t n){
 uintptr_t x=(uintptr_t)a,y=(uintptr_t)b;return x>=y?x-y<n:y-x<n;
}
static es_peer_result both(es_peer *child,es_peer *parent){
 es_peer_identity id={0};es_peer_result c=es_peer_read(child,&id),p=es_peer_read(parent,&id);
 wipe(&id,sizeof(id));
 if(c==ES_PEER_CLEANUP||p==ES_PEER_CLEANUP)return ES_PEER_CLEANUP;
 return c?c:p;
}
static int decimal(const char **text,uint64_t *value){
 const char *s=*text;uint64_t v=0;unsigned n=0;
 while(*s>='0'&&*s<='9'){unsigned d=(unsigned)(*s-'0');if(v>(UINT64_MAX-d)/10)return 0;v=v*10+d;s++;n++;}
 if(!n)return 0;
 *text=s;*value=v;return 1;
}
static int matches(const char *data,const es_peer_identity *child,const es_peer_identity *parent){
 const char *s=data;uint64_t value=0;
 if(!decimal(&s,&value)||value!=child->pid||s[0]!=' '||s[1]!='(')return 0;
 s=strrchr(s,')');if(!s||s[1]!=' '||!s[2]||s[2]==' '||s[3]!=' ')return 0;
 s+=4;
 if(!decimal(&s,&value)||value!=parent->pid||*s!=' ')return 0;
 ++s;
 for(int field=5;field<22;field++){const char *next=strchr(s,' ');if(!next||next==s)return 0;s=next+1;}
 return decimal(&s,&value)&&value==child->start_ticks&&*s==' ';
}
static es_peer_result record(es_peer *child,es_peer *parent){
 char path[64],data[16384];es_peer_result r=both(child,parent);int fd=-1,complete=0;size_t n=0;
 if(r)goto done;
 int length=snprintf(path,sizeof(path),"/proc/%u/stat",child->identity.pid);
 if(length<0||(size_t)length>=sizeof(path)){r=ES_PEER_IDENTITY;goto done;}
 fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK);
 if(fd<0){r=ES_PEER_IDENTITY;goto finish;}
 struct statfs filesystem;
 if(fstatfs(fd,&filesystem)||filesystem.f_type!=PROC_SUPER_MAGIC){r=ES_PEER_IDENTITY;goto finish;}
 while(n<sizeof(data)-1){
  r=both(child,parent);if(r)break;
  ssize_t count=read(fd,data+n,sizeof(data)-1-n);
  if(!count){complete=1;break;}
  if(count<0){if(errno==EINTR)continue;r=ES_PEER_IO;break;}
  n+=(size_t)count;
 }
 data[n]=0;
 if(!r&&(!complete||memchr(data,0,n)||!matches(data,&child->identity,&parent->identity)))r=ES_PEER_IDENTITY;
 finish:
 if(fd>=0&&close(fd))r=ES_PEER_CLEANUP;
 if(r!=ES_PEER_CLEANUP){es_peer_result final=both(child,parent);if(final)r=final;}
 done:wipe(data,sizeof(data));wipe(path,sizeof(path));return r;
}
es_peer_result es_parent_check(es_peer *child,es_peer *parent){
 if(!child||!parent||overlap(child,parent,sizeof(*child))||child->state!=1||parent->state!=1||
    child->pidfd<0||parent->pidfd<0||child->pidfd==parent->pidfd||
    child->identity.pid==parent->identity.pid||!child->identity.start_ticks||!parent->identity.start_ticks||
    child->cancel_fd<0||child->cancel_fd!=parent->cancel_fd||child->deadline_ns!=parent->deadline_ns)
  return ES_PEER_INVALID;
 es_peer_result r=record(child,parent);return r?r:record(child,parent);
}

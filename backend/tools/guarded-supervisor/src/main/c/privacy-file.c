#define _GNU_SOURCE
#include "privacy-file.h"
#include <errno.h>
#include <fcntl.h>
#include <linux/magic.h>
#include <poll.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/vfs.h>
#include <sys/xattr.h>
#include <time.h>
#include <unistd.h>
static void wipe(void *memory,size_t length){volatile unsigned char *p=memory;while(length--)*p++=0;}
static es_file_result guard(es_file *owner){
 struct pollfd cancel={.fd=owner->cancel_fd,.events=POLLIN};
 if(poll(&cancel,1,0)<0)return ES_FILE_IO;
 if(cancel.revents&POLLIN)return ES_FILE_CANCELLED;
 if(cancel.revents)return ES_FILE_IO;
 struct timespec t;
 if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<0||t.tv_nsec<0||t.tv_nsec>=1000000000L)return ES_FILE_IO;
 if((uint64_t)t.tv_sec>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL)return ES_FILE_IO;
 uint64_t current=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;
 if(current>=owner->deadline_ns)return ES_FILE_DEADLINE;
 return owner->deadline_ns-current>10000000000ULL?ES_FILE_INVALID:ES_FILE_OK;
}
static es_file_result metadata(const struct stat *st,int directory,uid_t uid){
 if(directory?!S_ISDIR(st->st_mode):!S_ISREG(st->st_mode))return ES_FILE_INVALID;
 if(st->st_uid!=0&&st->st_uid!=uid)return ES_FILE_TRUST;
 if((st->st_mode&0022)&&!(directory&&(st->st_mode&01000)))return ES_FILE_TRUST;
 if(!directory&&(st->st_mode&06000))return ES_FILE_TRUST;
 return ES_FILE_OK;
}
static es_file_result inspect(es_file *owner,int fd,int directory,uid_t uid){
 struct stat st={0};struct statfs fs={0};es_file_result result=guard(owner);int outcome,error;
 if(result)goto done;
 outcome=fstat(fd,&st);result=guard(owner);if(result)goto done;
 if(outcome){result=ES_FILE_IO;goto done;}
 result=metadata(&st,directory,uid);if(result)goto done;
 outcome=fstatfs(fd,&fs);result=guard(owner);if(result)goto done;
 if(outcome){result=ES_FILE_IO;goto done;}
 if(fs.f_type!=EXT4_SUPER_MAGIC&&fs.f_type!=TMPFS_MAGIC&&fs.f_type!=OVERLAYFS_SUPER_MAGIC){result=ES_FILE_PLATFORM;goto done;}
 if(!directory){
  ssize_t capability=fgetxattr(fd,"security.capability",NULL,0);error=errno;
  result=guard(owner);if(result)goto done;
  if(capability>=0){result=ES_FILE_TRUST;goto done;}
  if(error!=ENODATA){result=error==ENOTSUP?ES_FILE_PLATFORM:ES_FILE_IO;goto done;}
  outcome=fstat(fd,&st);result=guard(owner);if(result)goto done;
  if(outcome){result=ES_FILE_IO;goto done;}
  result=metadata(&st,0,uid);
 }
 done:wipe(&st,sizeof(st));wipe(&fs,sizeof(fs));return result;
}
static int valid_path(const char *path,size_t length){
 if(!path||length<2||length>4095||path[0]!='/'||path[length-1]=='/')return 0;
 size_t start=1;
 for(size_t i=0;i<length;){
  unsigned char first=(unsigned char)path[i];
  if(first==0||first=='\r'||first=='\n')return 0;
  if(first=='/'&&i){size_t n=i-start;if(!n||(n==1&&path[start]=='.')||(n==2&&path[start]=='.'&&path[start+1]=='.'))return 0;start=i+1;}
  if(first<128){i++;continue;}
  unsigned count;uint32_t scalar,minimum;
  if(first>=0xc2&&first<=0xdf){count=2;scalar=first&31;minimum=0x80;}
  else if(first>=0xe0&&first<=0xef){count=3;scalar=first&15;minimum=0x800;}
  else if(first>=0xf0&&first<=0xf4){count=4;scalar=first&7;minimum=0x10000;}
  else return 0;
  if(count>length-i)return 0;
  for(unsigned n=1;n<count;n++){unsigned char next=(unsigned char)path[i+n];if((next&0xc0)!=0x80)return 0;scalar=(scalar<<6)|(next&63);}
  if(scalar<minimum||scalar>0x10ffff||(scalar>=0xd800&&scalar<=0xdfff))return 0;
  i+=count;
 }
 size_t last=length-start;
 return !((last==1&&path[start]=='.')||(last==2&&path[start]=='.'&&path[start+1]=='.'));
}
static void release(int *fd,es_file *owner){
 int value=*fd;*fd=-1;
 if(value>=0&&close(value))owner->cleanup=ES_FILE_CLEANUP;
}
es_file_result es_file_open(es_file *owner,int cancel,uint64_t deadline,const char *path,size_t length){
 if(!owner||owner->state||owner->fd||owner->cancel_fd||owner->deadline_ns||owner->terminal||owner->cleanup||cancel<0||!valid_path(path,length))return ES_FILE_INVALID;
 char scratch[4096];memcpy(scratch,path,length);scratch[length]=0;
 owner->cancel_fd=cancel;owner->deadline_ns=deadline;owner->fd=-1;owner->state=1;
 int current=-1,next=-1;es_file_result result=guard(owner);struct stat leaf={0};
 if(result)goto done;
#if !defined(__linux__) || !defined(__x86_64__) || defined(__ILP32__)
 result=ES_FILE_PLATFORM;goto done;
#endif
 if(getuid()!=geteuid()){result=ES_FILE_PLATFORM;goto done;}
 uid_t uid=getuid();
 current=open("/",O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);result=guard(owner);if(result)goto done;
 if(current<0){result=ES_FILE_IO;goto done;}
 result=inspect(owner,current,1,uid);if(result)goto done;
 char *part=scratch+1;
 for(;;){
  char *slash=strchr(part,'/');if(slash)*slash=0;
  result=guard(owner);if(result)goto done;
  if(!slash){
   int checked=fstatat(current,part,&leaf,AT_SYMLINK_NOFOLLOW);result=guard(owner);if(result)goto done;
   if(checked){result=ES_FILE_IO;goto done;}
   result=metadata(&leaf,0,uid);if(result)goto done;
  }
  next=openat(current,part,O_RDONLY|O_NOFOLLOW|O_CLOEXEC|O_NONBLOCK|O_NOCTTY|(slash?O_DIRECTORY:0));
  result=guard(owner);if(result)goto done;
  if(next<0){result=ES_FILE_IO;goto done;}
  result=inspect(owner,next,slash!=NULL,uid);if(result)goto done;
  release(&current,owner);if(owner->cleanup){result=ES_FILE_CLEANUP;goto done;}
  if(!slash){result=guard(owner);if(result)goto done;owner->fd=next;next=-1;break;}
  current=next;next=-1;part=slash+1;
 }
 done:
 release(&next,owner);release(&current,owner);
 wipe(scratch,sizeof(scratch));wipe(&leaf,sizeof(leaf));
 if(owner->cleanup)result=ES_FILE_CLEANUP;
 owner->terminal=result;if(result)owner->state=2;return result;
}
es_file_result es_file_close(es_file *owner){
 if(!owner||!owner->state)return ES_FILE_INVALID;
 if(owner->state==3)return owner->cleanup;
 release(&owner->fd,owner);owner->state=3;
 if(owner->cleanup)owner->terminal=ES_FILE_CLEANUP;
 return owner->cleanup;
}

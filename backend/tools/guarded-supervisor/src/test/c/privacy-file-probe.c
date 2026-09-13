#define _GNU_SOURCE
#include "privacy-file.h"
#include "privacy-hash.h"
#include <sys/eventfd.h>
#include <sys/stat.h>
#include <sys/vfs.h>
#include <sys/xattr.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <time.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#define CHECK(x) do {if(!(x)){fprintf(stderr,"FILE_ASSERT_%d\n",__LINE__);exit(40);}}while(0)
static const char *mode;
static int active,fired,leaf_opened,cancel_fd=-1,reused=-1,reused_closes,closing_owner,expired;
static char directory[]="/tmp/es-trusted-file-probe-XXXXXX",file[4096],other[4096],linkname[4096],subdir[4096],nested[4096];
static int is(const char *value){return !strcmp(mode,value);}
extern int __real_fstat(int,struct stat *);
extern int __real_fstatat(int,const char *,struct stat *,int);
extern int __real_fstatfs(int,struct statfs *);
extern ssize_t __real_fgetxattr(int,const char *,void *,size_t);
extern int __real_openat(int,const char *,int,...);
extern int __real_close(int);
extern int __real_clock_gettime(clockid_t,struct timespec *);
static void cancel(void){uint64_t one=1;CHECK(write(cancel_fd,&one,sizeof one)==sizeof one);}
static uint64_t now(void){struct timespec t;CHECK(!__real_clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
int __wrap_fstat(int fd,struct stat *st){
 if(active&&is("stat-fail")){errno=EIO;return -1;}int result=__real_fstat(fd,st);
 if(active&&!result&&is("owner-directory")&&S_ISDIR(st->st_mode))st->st_uid=getuid()+1;
 return result;
}
int __wrap_fstatat(int fd,const char *name,struct stat *st,int flags){
 if(active&&is("lookup-fail")){errno=EIO;return -1;}int result=__real_fstatat(fd,name,st,flags);
 if(active&&!result){
  if(is("owner-file"))st->st_uid=getuid()+1;
  if(is("cancel-lookup")&&!fired){fired=1;cancel();}
  if(is("symlink-race")&&!fired){fired=1;CHECK(!unlink(file)&&!symlink(other,file));}
 }return result;
}
int __wrap_fstatfs(int fd,struct statfs *st){int result=__real_fstatfs(fd,st);if(active&&!result&&is("filesystem"))st->f_type=0x6969;return result;}
ssize_t __wrap_fgetxattr(int fd,const char *name,void *value,size_t size){
 if(active&&is("capability"))return 0;
 if(active&&is("capability-unknown")){errno=EOPNOTSUPP;return -1;}
 ssize_t result=__real_fgetxattr(fd,name,value,size);int error=errno;
 if(active&&is("cancel-capability")&&!fired){fired=1;cancel();}errno=error;return result;
}
int __wrap_openat(int fd,const char *name,int flags,...){
 CHECK(!(flags&O_CREAT));
 if(active&&is("open-fail")){errno=EIO;return -1;}
 int result=__real_openat(fd,name,flags);
 if(active&&result>=0&&!(flags&O_DIRECTORY))leaf_opened=1;
 return result;
}
int __wrap___openat_2(int fd,const char *name,int flags){return __wrap_openat(fd,name,flags);}
int __wrap_close(int fd){
 if(active&&fd==reused)reused_closes++;
 int result=__real_close(fd);int error=errno;
 if(active&&!result&&!fired&&((is("close-fault")&&!closing_owner)||(is("leaf-close-fault")&&closing_owner))){
  fired=1;reused=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(reused>=0);
  if(reused!=fd){int exact=fcntl(reused,F_DUPFD_CLOEXEC,fd);CHECK(exact==fd);CHECK(!__real_close(reused));reused=exact;}
  errno=EINTR;return -1;
 }
 if(active&&!result&&leaf_opened&&!closing_owner&&!fired&&(is("cancel-close")||is("deadline-close"))){
  fired=1;if(is("cancel-close"))cancel();else expired=1;
 }errno=error;return result;
}
int __wrap_clock_gettime(clockid_t clock,struct timespec *time){
 if(active&&is("clock-fail")){errno=EIO;return -1;}
 int result=__real_clock_gettime(clock,time);if(active&&!result&&expired&&clock==CLOCK_MONOTONIC)time->tv_sec+=20;return result;
}
static void cleanup(void){
 active=0;if(reused>=0)__real_close(reused);if(cancel_fd>=0)__real_close(cancel_fd);
 if(directory[0]){chmod(directory,0700);unlink(file);unlink(other);unlink(linkname);unlink(nested);rmdir(subdir);rmdir(directory);}
}
static void make_file(const char *path,const char *content){
 int fd=open(path,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0600);CHECK(fd>=0);
 size_t length=strlen(content);CHECK(write(fd,content,length)==(ssize_t)length&&!__real_close(fd));
}
static void verify_hash(es_file *owner,uint64_t deadline){
 CHECK(owner->fd>=0&&(fcntl(owner->fd,F_GETFD)&FD_CLOEXEC));CHECK((fcntl(owner->fd,F_GETFL)&O_ACCMODE)==O_RDONLY);
 es_hash hash={0};es_hash_identity out;CHECK(es_hash_open(&hash,cancel_fd,deadline)==ES_HASH_OK);CHECK(es_hash_file(&hash,owner->fd,&out)==ES_HASH_OK);
 static const unsigned char expected[]={0xba,0x78,0x16,0xbf,0x8f,0x01,0xcf,0xea,0x41,0x41,0x40,0xde,0x5d,0xae,0x22,0x23,0xb0,0x03,0x61,0xa3,0x96,0x17,0x7a,0x9c,0xb4,0x10,0xff,0x61,0xf2,0x00,0x15,0xad};
 CHECK(out.size==3&&!memcmp(out.sha256,expected,32));CHECK(es_hash_close(&hash)==ES_HASH_OK);
}
int main(int argc,char **argv){
 CHECK(argc==2);mode=argv[1];CHECK(!atexit(cleanup));CHECK(mkdtemp(directory));
 CHECK(snprintf(file,sizeof file,"%s/file",directory)>0&&snprintf(other,sizeof other,"%s/other",directory)>0);
 CHECK(snprintf(linkname,sizeof linkname,"%s/link",directory)>0&&snprintf(subdir,sizeof subdir,"%s/sub",directory)>0);
 CHECK(snprintf(nested,sizeof nested,"%s/sub/file",directory)>0);make_file(file,"abc");make_file(other,"xyz");
 cancel_fd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel_fd>=0);uint64_t deadline=now()+10000000000ULL;
 const char *path=file;size_t length=strlen(path);char malformed[4096];es_file_result expected=ES_FILE_OK;int preflight=0;
 static const struct {const char *mode,*path;size_t length;} bad[]={
  {"relative","relative",8},{"empty-component","/tmp//file",10},{"dot","/tmp/./file",11},{"dotdot","/tmp/../file",12},
  {"trailing","/tmp/file/",10},{"root","/",1},{"nul","/tmp/ab\0cd",10},{"lf","/tmp/ab\ncd",10},{"cr","/tmp/ab\rcd",10},
  {"overlong-utf8","/tmp/\xc0\xaf",7},{"surrogate","/tmp/\xed\xa0\x80",8},{"invalid-continuation","/tmp/\xe2x",7},{"null-path",NULL,9}};
 for(size_t i=0;i<sizeof bad/sizeof bad[0];i++)if(is(bad[i].mode)){path=bad[i].path;length=bad[i].length;expected=ES_FILE_INVALID;preflight=1;}
 if(is("too-long")){memset(malformed,'x',sizeof malformed);malformed[0]='/';path=malformed;length=sizeof malformed;expected=ES_FILE_INVALID;preflight=1;}
 if(is("unicode")){CHECK(!unlink(file));CHECK(snprintf(file,sizeof file,"%s/é-😀",directory)>0);make_file(file,"abc");path=file;length=strlen(path);}
 if(is("leaf-symlink")){CHECK(!symlink(file,linkname));path=linkname;length=strlen(path);expected=ES_FILE_INVALID;}
 if(is("ancestor-symlink")){CHECK(!mkdir(subdir,0700));make_file(nested,"abc");CHECK(!symlink(subdir,linkname));CHECK(snprintf(malformed,sizeof malformed,"%s/link/file",directory)>0);path=malformed;length=strlen(path);expected=ES_FILE_IO;}
 if(is("write-file")){CHECK(!chmod(file,0660));expected=ES_FILE_TRUST;}
 if(is("write-directory")){CHECK(!chmod(directory,0777));expected=ES_FILE_TRUST;}
 if(is("sticky-directory"))CHECK(!chmod(directory,01777));
 if(is("setuid")){CHECK(!chmod(file,04600));expected=ES_FILE_TRUST;}
 if(is("setgid")){CHECK(!chmod(file,02600));expected=ES_FILE_TRUST;}
 if(is("fifo")){CHECK(!unlink(file)&&!mkfifo(file,0600));expected=ES_FILE_INVALID;}
 if(is("directory")){path=directory;length=strlen(path);expected=ES_FILE_INVALID;}
 if(is("owner-file")||is("owner-directory")||is("capability"))expected=ES_FILE_TRUST;
 if(is("capability-unknown")||is("filesystem"))expected=ES_FILE_PLATFORM;
 if(is("stat-fail")||is("lookup-fail")||is("open-fail")||is("clock-fail")||is("symlink-race"))expected=ES_FILE_IO;
 if(is("cancel-open"))cancel();
 if(is("cancel-open")||is("cancel-lookup")||is("cancel-capability")||is("cancel-close"))expected=ES_FILE_CANCELLED;
 if(is("deadline-open"))deadline=now()-1;
 if(is("deadline-open")||is("deadline-close"))expected=ES_FILE_DEADLINE;
 if(is("long-open")){deadline=now()+20000000000ULL;expected=ES_FILE_INVALID;}
 if(is("close-fault"))expected=ES_FILE_CLEANUP;
 es_file owner={0},fresh={0};active=1;
 if(is("null-owner")){CHECK(es_file_open(NULL,cancel_fd,deadline,path,length)==ES_FILE_INVALID);active=0;return 0;}
 es_file_result result=es_file_open(&owner,cancel_fd,deadline,path,length);CHECK(result==expected);
 if(preflight){CHECK(!memcmp(&owner,&fresh,sizeof owner));CHECK(es_file_close(&owner)==ES_FILE_INVALID);active=0;return 0;}
 if(result==ES_FILE_OK){
  if(is("replace-after-open")){CHECK(!unlink(file));make_file(file,"xyz");}
  verify_hash(&owner,deadline);
  es_file before=owner;CHECK(es_file_open(&owner,cancel_fd,deadline,path,length)==ES_FILE_INVALID);CHECK(!memcmp(&before,&owner,sizeof owner));
 }
 if(result!=ES_FILE_OK)CHECK(owner.fd==-1&&owner.terminal==expected);
 closing_owner=1;es_file_result close_result=es_file_close(&owner);
 CHECK(close_result==((is("close-fault")||is("leaf-close-fault"))?ES_FILE_CLEANUP:ES_FILE_OK));
 CHECK(es_file_close(&owner)==close_result&&owner.fd==-1);CHECK(fcntl(cancel_fd,F_GETFD)>=0);
 if(reused>=0)CHECK(fcntl(reused,F_GETFD)>=0&&!reused_closes);
 if(expected==ES_FILE_CANCELLED){struct pollfd pollfd={.fd=cancel_fd,.events=POLLIN};CHECK(poll(&pollfd,1,0)==1&&(pollfd.revents&POLLIN));}
 active=0;return 0;
}

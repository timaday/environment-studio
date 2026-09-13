#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/eventfd.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <poll.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <time.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#define CHECK(x) do { if(!(x)){fprintf(stderr,"IMAGE_ASSERT_%d\n",__LINE__);exit(40);} } while(0)
#include <errno.h>
#include <stdarg.h>
#include <sys/vfs.h>
#ifdef ES_IMAGE_CHILD
int main(int argc,char **argv) {
    if(argc!=3)return 41;
    struct sockaddr_un address={.sun_family=AF_UNIX};size_t n=strlen(argv[1]);
    if(n>=sizeof address.sun_path)return 41;
    memcpy(address.sun_path,argv[1],n+1);int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);
    if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof address))return 41;
    if(write(fd,"R",1)!=1)return 41;
    char byte;ssize_t count=read(fd,&byte,1);close(fd);
    if(count==1&&byte=='X'){execl(argv[2],argv[2],argv[1],argv[2],(char*)NULL);return 41;}
    return count<0?41:0;
}
#else
#include "privacy-image.h"
#include "image-expected.h"
#include <openssl/evp.h>
static pid_t child=-1;
static int listener=-1,socket_fd=-1,second_socket=-1,cancel_fd=-1,foreign_fd=-1;
static char directory[]="/tmp/es-image-probe-XXXXXX",endpoint[108],runfile[160],other[160],empty[160];
static es_peer peer;static es_file file;static es_hash hash;
static const char *mode;
static int active,fired,expired,roots,pids,executables,proc_fd=-1,pid_dir=-1,exe_fd=-1,reused=-1,reused_closes;
extern int __real_open(const char *,int,...);
extern int __real_openat(int,const char *,int,...);
extern int __real_close(int);
extern int __real_fstat(int,struct stat *);
extern int __real_fstatfs(int,struct statfs *);
extern int __real_clock_gettime(clockid_t,struct timespec *);
extern int __real_EVP_DigestUpdate(EVP_MD_CTX *,const void *,size_t);
static int is(const char *name){return !strcmp(mode,name);}
int __wrap_EVP_DigestUpdate(EVP_MD_CTX *context,const void *bytes,size_t length){
    if(active&&is("crypto-fail"))return 0;
    return __real_EVP_DigestUpdate(context,bytes,length);
}
static uint64_t now(void){struct timespec t;CHECK(!__real_clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
static void signal_cancel(void){uint64_t one=1;CHECK(write(cancel_fd,&one,sizeof one)==sizeof one);}
static void stop_child(void){CHECK(child>0);CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=-1;}
static void ready(int fd){struct pollfd p={.fd=fd,.events=POLLIN};CHECK(poll(&p,1,5000)==1);}
static void execute_other(void){
    CHECK(write(socket_fd,"X",1)==1);ready(listener);
    second_socket=accept4(listener,NULL,NULL,SOCK_CLOEXEC);CHECK(second_socket>=0);ready(second_socket);
    char byte;CHECK(read(second_socket,&byte,1)==1&&byte=='R');
}
int __wrap_open(const char *path,int flags,...){
    int fd;
    if(flags&O_CREAT){va_list args;va_start(args,flags);mode_t permissions=(mode_t)va_arg(args,int);va_end(args);fd=__real_open(path,flags,permissions);}
    else {
        if(active&&!strcmp(path,"/proc")&&is("proc-open-fail")){errno=EIO;return -1;}
        fd=__real_open(path,flags);
    }
    if(active&&!strcmp(path,"/proc")&&fd>=0){proc_fd=fd;roots++;}
    return fd;
}
int __wrap___open_2(const char *path,int flags){CHECK(!(flags&O_CREAT));return __wrap_open(path,flags);}
int __wrap_openat(int parent,const char *name,int flags,...){
    CHECK(!(flags&O_CREAT));
    if(active&&parent==pid_dir&&!strcmp(name,"exe")){
        executables++;
        if(executables==2&&is("exec-second"))execute_other();
        if(executables==2&&is("death-second"))stop_child();
        if(is("exe-open-fail")){errno=EACCES;return -1;}
    }
    int fd=__real_openat(parent,name,flags);
    if(active&&fd>=0){
        if(parent==proc_fd){pid_dir=fd;pids++;}
        else if(parent==pid_dir&&!strcmp(name,"exe"))exe_fd=fd;
    }
    return fd;
}
int __wrap___openat_2(int parent,const char *name,int flags){return __wrap_openat(parent,name,flags);}
int __wrap_fstat(int fd,struct stat *st){
    if(active&&fd==exe_fd&&is("stat-fail")){errno=EIO;return -1;}
    int result=__real_fstat(fd,st);
    if(active&&!result&&fd==exe_fd&&is("size-mismatch"))st->st_size++;
    return result;
}
int __wrap_fstatfs(int fd,struct statfs *st){
    int result=__real_fstatfs(fd,st);
    if(active&&!result&&((fd==proc_fd&&is("proc-filesystem"))||(fd==pid_dir&&is("pid-filesystem"))))st->f_type=0x6969;
    return result;
}
int __wrap_clock_gettime(clockid_t clock,struct timespec *value){
    int result=__real_clock_gettime(clock,value);
    if(active&&expired&&!result&&clock==CLOCK_MONOTONIC)value->tv_sec+=20;
    return result;
}
int __wrap_close(int fd){
    if(active&&fd==reused)reused_closes++;
    int owned=active&&(fd==proc_fd||fd==pid_dir||fd==exe_fd);
    int last=active&&fd==proc_fd&&roots==2;
    if(active&&fd==proc_fd)proc_fd=-1;
    if(active&&fd==pid_dir)pid_dir=-1;
    if(active&&fd==exe_fd)exe_fd=-1;
    int result=__real_close(fd);int error=errno;
    if(owned&&!result&&!fired&&(is("close-fault")||is("mismatch-close-fault"))){
        fired=1;reused=__real_open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(reused>=0);
        if(reused!=fd){CHECK(dup3(reused,fd,O_CLOEXEC)==fd);CHECK(!__real_close(reused));reused=fd;}
        CHECK(fcntl(reused,F_GETFD)>=0);errno=EINTR;return -1;
    }
    if(last&&!result&&!fired){
        fired=1;
        if(is("cancel-final"))signal_cancel();
        if(is("deadline-final"))expired=1;
        if(is("death-final"))stop_child();
    }
    errno=error;return result;
}
static void cleanup(void){
    active=0;
    if(socket_fd>=0)__real_close(socket_fd);
    if(second_socket>=0)__real_close(second_socket);
    if(listener>=0)__real_close(listener);
    if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);}
    if(hash.state)es_hash_close(&hash);
    if(file.state)es_file_close(&file);
    if(peer.state)es_peer_close(&peer);
    if(cancel_fd>=0)__real_close(cancel_fd);
    if(foreign_fd>=0)__real_close(foreign_fd);
    if(reused>=0)__real_close(reused);
    if(runfile[0])unlink(runfile);
    if(other[0])unlink(other);
    if(empty[0])unlink(empty);
    if(endpoint[0])unlink(endpoint);
    if(directory[0])rmdir(directory);
}
static void copy(const char *source,const char *target){
    int input=__real_open(source,O_RDONLY|O_CLOEXEC);CHECK(input>=0);
    int output=__real_open(target,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0700);CHECK(output>=0);CHECK(!fchmod(output,0700));
    char buffer[4096];ssize_t count;
    while((count=read(input,buffer,sizeof buffer))>0)CHECK(write(output,buffer,(size_t)count)==count);
    CHECK(count==0);CHECK(!__real_close(input));CHECK(!__real_close(output));memset(buffer,0,sizeof buffer);
}
static void setup(const char *executable){
    CHECK(!atexit(cleanup));CHECK(mkdtemp(directory));
    CHECK(snprintf(endpoint,sizeof endpoint,"%s/control.sock",directory)>0);
    CHECK(snprintf(runfile,sizeof runfile,"%s/run",directory)>0);
    CHECK(snprintf(other,sizeof other,"%s/other",directory)>0);
    CHECK(snprintf(empty,sizeof empty,"%s/empty",directory)>0);
    copy(executable,runfile);copy(executable,other);
    struct sockaddr_un address={.sun_family=AF_UNIX};memcpy(address.sun_path,endpoint,strlen(endpoint)+1);
    listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);CHECK(listener>=0);CHECK(!bind(listener,(struct sockaddr*)&address,sizeof address));CHECK(!listen(listener,2));
    child=fork();CHECK(child>=0);
    if(child==0){execl(runfile,runfile,endpoint,other,(char*)NULL);_exit(41);}
    ready(listener);socket_fd=accept4(listener,NULL,NULL,SOCK_CLOEXEC);CHECK(socket_fd>=0);ready(socket_fd);
    char byte;CHECK(read(socket_fd,&byte,1)==1&&byte=='R');
    cancel_fd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(cancel_fd>=0);uint64_t deadline=now()+10000000000ULL;
    CHECK(es_peer_open(&peer,socket_fd,cancel_fd,deadline)==ES_PEER_OK);
    const char *trusted=(is("wrong-inode")||is("mismatch-close-fault"))?other:runfile;
    CHECK(es_file_open(&file,cancel_fd,deadline,trusted,strlen(trusted))==ES_FILE_OK);
    CHECK(es_hash_open(&hash,cancel_fd,deadline)==ES_HASH_OK);
}
int main(int argc,char **argv){
    CHECK(argc==3);mode=argv[2];setup(argv[1]);
    es_hash_identity out;memset(&out,0xa5,sizeof out);unsigned char expected[32];memcpy(expected,image_expected,32);
    es_image_result want=ES_IMAGE_OK;
    int preflight=0;
    int filefd=file.fd,hashcancel=hash.cancel_fd,filecancel=file.cancel_fd;uint64_t hashdeadline=hash.deadline_ns,filedeadline=file.deadline_ns;
    if(is("wrong-inode")||is("exec-second")||is("death-before")||is("death-second")||is("death-final")||is("size-mismatch"))want=ES_IMAGE_IDENTITY;
    if(is("wrong-digest")){expected[0]^=1;want=ES_IMAGE_IDENTITY;}
    if(is("replace-path")){CHECK(!unlink(runfile));copy(other,runfile);}
    if(is("death-before"))stop_child();
    if(is("cancel-before")||is("cancel-final"))want=ES_IMAGE_CANCELLED;
    if(is("cancel-before"))signal_cancel();
    if(is("deadline-before")||is("deadline-final"))want=ES_IMAGE_DEADLINE;
    if(is("deadline-before"))expired=1;
    if(is("proc-filesystem")||is("pid-filesystem"))want=ES_IMAGE_PLATFORM;
    if(is("stat-fail")||is("proc-open-fail")||is("crypto-fail"))want=ES_IMAGE_IO;
    if(is("exe-open-fail"))want=ES_IMAGE_IDENTITY;
    if(is("close-fault")||is("mismatch-close-fault"))want=ES_IMAGE_CLEANUP;
    if(is("foreign-hash-control")||is("foreign-file-control")){
        foreign_fd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);CHECK(foreign_fd>=0);
        if(is("foreign-hash-control"))hash.cancel_fd=foreign_fd;else file.cancel_fd=foreign_fd;
        preflight=1;want=ES_IMAGE_INVALID;
    }
    if(is("foreign-hash-deadline")){hash.deadline_ns--;preflight=1;want=ES_IMAGE_INVALID;}
    if(is("foreign-file-deadline")){file.deadline_ns--;preflight=1;want=ES_IMAGE_INVALID;}
    if(is("descriptor-alias")){file.fd=peer.pidfd;preflight=1;want=ES_IMAGE_INVALID;}
    if(is("objects-exact")||is("objects-over")){
        int fd=__real_open(empty,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0600);CHECK(fd>=0);CHECK(!__real_close(fd));fd=__real_open(empty,O_RDONLY|O_CLOEXEC);CHECK(fd>=0);
        es_hash_identity ignored;unsigned count=is("objects-exact")?509:510;
        for(unsigned i=0;i<count;i++)CHECK(es_hash_file(&hash,fd,&ignored)==ES_HASH_OK);
        CHECK(!__real_close(fd));if(is("objects-over"))want=ES_IMAGE_RESOURCE;
    }
    if(is("bytes-over")){hash.bytes=2147483648ULL;want=ES_IMAGE_RESOURCE;}
    es_peer beforepeer=peer;es_file beforefile=file;es_hash beforehash=hash;
    active=1;
    if(is("null-output")){CHECK(es_image_check(&peer,&file,&hash,expected,NULL)==ES_IMAGE_INVALID);preflight=1;}
    else if(is("owner-alias")){CHECK(es_image_check(&peer,(const es_file*)&peer,&hash,expected,&out)==ES_IMAGE_INVALID);preflight=1;}
    else if(is("output-owner")){CHECK(es_image_check(&peer,&file,&hash,expected,(es_hash_identity*)&hash)==ES_IMAGE_INVALID);preflight=1;}
    else if(is("output-expected")){
        union {es_hash_identity out;unsigned char bytes[64];} alias;memset(&alias,0xa5,sizeof alias);unsigned char before[sizeof alias];memcpy(before,&alias,sizeof alias);
        CHECK(es_image_check(&peer,&file,&hash,alias.bytes,&alias.out)==ES_IMAGE_INVALID);CHECK(!memcmp(before,&alias,sizeof alias));preflight=1;
    } else {
        es_image_result result=es_image_check(is("null-peer")?NULL:&peer,is("null-file")?NULL:&file,is("null-hash")?NULL:&hash,is("null-digest")?NULL:expected,&out);
        if(is("null-peer")||is("null-file")||is("null-hash")||is("null-digest")){want=ES_IMAGE_INVALID;preflight=1;}
        if(result!=want)fprintf(stderr,"IMAGE_RESULT_%d\n",result);
        CHECK(result==want);
        if(result==ES_IMAGE_OK){
            CHECK(!memcmp(out.sha256,image_expected,32));CHECK(hash.objects==(is("objects-exact")?512:3));CHECK(hash.bytes==3*out.size);
            struct stat expected_stat;CHECK(!fstat(file.fd,&expected_stat));CHECK(out.device==(uint64_t)expected_stat.st_dev&&out.inode==(uint64_t)expected_stat.st_ino&&out.size==(uint64_t)expected_stat.st_size);
            CHECK(roots==2&&pids==2&&executables==2);
        } else {es_hash_identity zero={0};CHECK(!memcmp(&out,&zero,sizeof out));}
    }
    active=0;
    if(preflight){CHECK(!memcmp(&beforepeer,&peer,sizeof peer));CHECK(!memcmp(&beforefile,&file,sizeof file));CHECK(!memcmp(&beforehash,&hash,sizeof hash));}
    file.fd=filefd;file.cancel_fd=filecancel;file.deadline_ns=filedeadline;hash.cancel_fd=hashcancel;hash.deadline_ns=hashdeadline;
    CHECK(fcntl(file.fd,F_GETFD)>=0&&fcntl(cancel_fd,F_GETFD)>=0);
    if(is("cancel-before")||is("cancel-final")){struct pollfd p={.fd=cancel_fd,.events=POLLIN};CHECK(poll(&p,1,0)==1&&(p.revents&POLLIN));}
    if(reused>=0)CHECK(fcntl(reused,F_GETFD)>=0&&!reused_closes);
    return 0;
}
#endif

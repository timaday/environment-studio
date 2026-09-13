#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/eventfd.h>
#include <sys/stat.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <poll.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <time.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <stdarg.h>
#define REQUIRE(x) do { if(!(x)){fprintf(stderr,"INDEPENDENT_IMAGE_%d\n",__LINE__);exit(40);} } while(0)
#ifdef INDEPENDENT_IMAGE_CHILD
/* Independently invented native child: no DB/client/application data. */
int main(int argc,char **argv) {
    if(argc!=2)return 41;
    struct sockaddr_un address={.sun_family=AF_UNIX};
    if(strlen(argv[1])>=sizeof address.sun_path)return 41;
    strcpy(address.sun_path,argv[1]);
    if(prctl(PR_SET_NAME,"odd ) child\n"))return 41;
    int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);
    if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof address)||write(fd,"R",1)!=1)return 41;
    char byte;int result=read(fd,&byte,1)<0?41:0;close(fd);return result;
}
#else
#include "privacy-image.h"
#include "independent-image-expected.h"
static int active,root=-1,process_dir=-1,executable=-1,cancel_fd=-1,listener=-1,connection=-1;
static unsigned acquisitions,closed;static pid_t child=-1;
static char directory[]="/tmp/es-independent-image-XXXXXX",endpoint[108];
static const char *mode;
static es_peer peer;static es_file file;static es_hash hash;
extern int __real_open(const char *,int,...);
extern int __real_openat(int,const char *,int,...);
extern int __real_fcntl(int,int,...);
extern int __real_fstat(int,struct stat *);
extern int __real_close(int);
static int is(const char *s){return !strcmp(mode,s);}
int __wrap_open(const char *path,int flags,...){
    REQUIRE(!(flags&O_CREAT));int fd=__real_open(path,flags);
    if(active&&fd>=0&&!strcmp(path,"/proc"))root=fd;
    return fd;
}
int __wrap___open_2(const char *path,int flags){return __wrap_open(path,flags);}
int __wrap_openat(int parent,const char *path,int flags,...){
    REQUIRE(!(flags&O_CREAT));int fd=__real_openat(parent,path,flags);
    if(active&&fd>=0){if(parent==root)process_dir=fd;else if(parent==process_dir&&!strcmp(path,"exe")){executable=fd;acquisitions++;}}
    return fd;
}
int __wrap___openat_2(int parent,const char *path,int flags){return __wrap_openat(parent,path,flags);}
int __wrap_fcntl(int fd,int command,...){
    /* All production calls in this private prerequisite are queries. */
    REQUIRE(command==F_GETFD||command==F_GETFL);
    int result=__real_fcntl(fd,command);
    int selected=active&&((fd==root&&strstr(mode,"root"))||(fd==process_dir&&strstr(mode,"directory"))||(fd==executable&&strstr(mode,"executable")));
    if(selected&&result>=0){
        if(command==F_GETFD&&strstr(mode,"cloexec"))result&=~FD_CLOEXEC;
        if(command==F_GETFL&&strstr(mode,"write"))result=(result&~O_ACCMODE)|O_WRONLY;
        if(command==F_GETFL&&strstr(mode,"path"))result|=O_PATH;
    }
    return result;
}
int __wrap_fstat(int fd,struct stat *metadata){
    int result=__real_fstat(fd,metadata);
    if(active&&!result&&fd==executable&&is("device-only"))metadata->st_dev^=1;
    return result;
}
int __wrap_close(int fd){
    int target=active&&fd==executable;
    if(active&&fd==root){root=-1;closed++;}
    if(active&&fd==process_dir){process_dir=-1;closed++;}
    if(target){executable=-1;closed++;}
    int result=__real_close(fd);
    if(target&&!result&&is("cleanup-cancel")){
        uint64_t one=1;REQUIRE(write(cancel_fd,&one,sizeof one)==sizeof one);
        errno=EINTR;return -1;
    }
    return result;
}
static void cleanup(void){
    active=0;
    if(connection>=0)__real_close(connection);
    if(listener>=0)__real_close(listener);
    if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);}
    if(hash.state)es_hash_close(&hash);
    if(file.state)es_file_close(&file);
    if(peer.state)es_peer_close(&peer);
    if(cancel_fd>=0)__real_close(cancel_fd);
    if(endpoint[0])unlink(endpoint);
    if(directory[0])rmdir(directory);
}
static void await_read(int fd){struct pollfd p={.fd=fd,.events=POLLIN};REQUIRE(poll(&p,1,5000)==1&&(p.revents&POLLIN));}
int main(int argc,char **argv){
    REQUIRE(argc==3);mode=argv[2];REQUIRE(!atexit(cleanup));REQUIRE(mkdtemp(directory));
    REQUIRE(snprintf(endpoint,sizeof endpoint,"%s/control",directory)>0);
    struct sockaddr_un address={.sun_family=AF_UNIX};strcpy(address.sun_path,endpoint);
    listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);REQUIRE(listener>=0);
    REQUIRE(!bind(listener,(struct sockaddr*)&address,sizeof address));REQUIRE(!listen(listener,1));
    child=fork();REQUIRE(child>=0);
    if(!child){execl(argv[1],argv[1],endpoint,(char*)NULL);_exit(41);}
    await_read(listener);connection=accept4(listener,NULL,NULL,SOCK_CLOEXEC);REQUIRE(connection>=0);
    await_read(connection);char byte;REQUIRE(read(connection,&byte,1)==1&&byte=='R');
    cancel_fd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);REQUIRE(cancel_fd>=0);
    struct timespec t;REQUIRE(!clock_gettime(CLOCK_MONOTONIC,&t));
    uint64_t deadline=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec+10000000000ULL;
    REQUIRE(es_peer_open(&peer,connection,cancel_fd,deadline)==ES_PEER_OK);
    REQUIRE(es_file_open(&file,cancel_fd,deadline,argv[1],strlen(argv[1]))==ES_FILE_OK);
    REQUIRE(es_hash_open(&hash,cancel_fd,deadline)==ES_HASH_OK);
    REQUIRE(lseek(file.fd,17,SEEK_SET)==17);
    es_peer old_peer=peer;es_file old_file=file;es_hash old_hash=hash;
    es_hash_identity out;memset(&out,0xa5,sizeof out);active=1;
    const unsigned char *expected=is("expected-owner")?(unsigned char*)&hash+1:independent_expected;
    es_image_result result=es_image_check(&peer,&file,&hash,expected,&out);active=0;
    es_image_result want=is("live")?ES_IMAGE_OK:is("device-only")?ES_IMAGE_IDENTITY:
        is("cleanup-cancel")?ES_IMAGE_CLEANUP:is("expected-owner")?ES_IMAGE_INVALID:ES_IMAGE_IO;
    REQUIRE(result==want);
    if(!result){
        REQUIRE(acquisitions==2&&closed==6&&hash.objects==3);
        REQUIRE(!memcmp(out.sha256,independent_expected,32));
        REQUIRE(hash.bytes==3*out.size);
    } else {es_hash_identity zero={0};REQUIRE(!memcmp(&out,&zero,sizeof out));}
    if(is("expected-owner")){
        REQUIRE(!memcmp(&old_peer,&peer,sizeof peer)&&!memcmp(&old_file,&file,sizeof file)&&!memcmp(&old_hash,&hash,sizeof hash));
        REQUIRE(!closed&&!acquisitions);
    }
    if(is("cleanup-cancel")){
        REQUIRE(closed==3&&peer.terminal==ES_PEER_CANCELLED);
        struct pollfd p={.fd=cancel_fd,.events=POLLIN};REQUIRE(poll(&p,1,0)==1&&(p.revents&POLLIN));
    }
    REQUIRE(root==-1&&process_dir==-1&&executable==-1);
    REQUIRE(lseek(file.fd,0,SEEK_CUR)==17);
    REQUIRE(__real_fcntl(file.fd,F_GETFD)>=0&&__real_fcntl(cancel_fd,F_GETFD)>=0);
    return 0;
}
#endif

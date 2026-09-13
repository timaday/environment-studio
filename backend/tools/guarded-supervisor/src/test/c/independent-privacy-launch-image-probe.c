#define _GNU_SOURCE
#include "privacy-launch.h"
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>
#define REQUIRE(x) do { if(!(x)){fprintf(stderr,"INDEPENDENT_IMAGE_ASSERT_%d\n",__LINE__);exit(40);} } while(0)
static es_launch owner;
static es_launch_image_record installed;
static es_elf_layout observed;
static pid_t child=-1;
static int parent=-1,hold_hash;
static char directory[]="/tmp/es-independent-image-XXXXXX";
static unsigned hash_opens,hash_closes;
static pthread_mutex_t held_mutex=PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t changed=PTHREAD_COND_INITIALIZER;
static int entered,released;
es_hash_result __real_es_hash_open(es_hash*,int,uint64_t);
es_hash_result __wrap_es_hash_open(es_hash *hash,int fd,uint64_t deadline){
    hash_opens++;
    REQUIRE(hash==&owner.hash&&fd==owner.cancel_fd);
    REQUIRE(deadline==owner.startup_deadline&&deadline<owner.operation_deadline);
    REQUIRE(fd==owner.connection.peer.cancel_fd&&deadline==owner.connection.peer.deadline_ns);
    return __real_es_hash_open(hash,fd,deadline);
}
es_hash_result __real_es_hash_close(es_hash*);
es_hash_result __wrap_es_hash_close(es_hash *hash){
    hash_closes++;
    REQUIRE(hash==&owner.hash&&hash->cancel_fd==owner.cancel_fd);
    REQUIRE(fcntl(owner.cancel_fd,F_GETFD)>=0);
    if(hold_hash){
        pthread_mutex_lock(&held_mutex);entered=1;pthread_cond_broadcast(&changed);
        while(!released)pthread_cond_wait(&changed,&held_mutex);
        pthread_mutex_unlock(&held_mutex);
    }
    return __real_es_hash_close(hash);
}
static void clean(void){
    if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);child=-1;}
    if(parent>=0)close(parent);
    rmdir(directory);
}
static void *receive(void *result){
    es_launch_result value=es_launch_capture(&owner);
    if(value==ES_LAUNCH_OK)value=es_launch_correlate(&owner);
    if(value==ES_LAUNCH_ROOT_CORRELATED)value=es_launch_image(&owner,&installed,&observed);
    *(es_launch_result*)result=value;return NULL;
}
static void *settling(void *result){*(es_launch_cleanup*)result=es_launch_close(&owner,1000000000ULL);return NULL;}
int main(int argc,char **argv){
    REQUIRE(argc==5&&strlen(argv[2])==64);
    installed.path_length=(uint32_t)strlen(argv[1]);REQUIRE(installed.path_length<4096);
    memcpy(installed.path,argv[1],installed.path_length+1);
    for(unsigned index=0;index<32;index++){unsigned byte;REQUIRE(sscanf(argv[2]+2*index,"%2x",&byte)==1);installed.expected_sha256[index]=(unsigned char)byte;}
    unsigned expected_type=(unsigned)strtoul(argv[3],NULL,10);
    hold_hash=!strcmp(argv[4],"held-hash");int expect_static=!strcmp(argv[4],"static");
    REQUIRE(mkdtemp(directory)!=NULL);REQUIRE(atexit(clean)==0);
    parent=open(directory,O_PATH|O_DIRECTORY|O_CLOEXEC);REQUIRE(parent>=0);
    char endpoint[ES_LISTENER_PATH_BYTES];
    REQUIRE(es_launch_open(&owner,parent,directory,30000000000ULL,endpoint)==ES_LAUNCH_OK);
    es_file control={0};
    REQUIRE(es_file_open(&control,owner.cancel_fd,owner.startup_deadline,(char*)installed.path,installed.path_length)==ES_FILE_OK);
    REQUIRE(es_file_close(&control)==ES_FILE_OK);
    pthread_t receiver;es_launch_result result=ES_LAUNCH_INVALID;
    REQUIRE(pthread_create(&receiver,NULL,receive,&result)==0);REQUIRE(es_launch_arm(&owner)==ES_LAUNCH_OK);
    child=fork();REQUIRE(child>=0);
    if(child==0){if(setenv("ES_INDEPENDENT_ENDPOINT",endpoint,1))_exit(41);execl(argv[1],argv[1],(char*)NULL);_exit(42);}
    REQUIRE(es_launch_register(&owner,(uint64_t)child)==ES_LAUNCH_OK);REQUIRE(es_launch_disarm(&owner)==ES_LAUNCH_OK);
    REQUIRE(pthread_join(receiver,NULL)==0);REQUIRE(hash_opens==1&&hash_closes==0&&owner.hash.objects==5);
    REQUIRE(result==(expect_static?ES_LAUNCH_PROTOCOL:ES_LAUNCH_OK));
    if(expect_static){for(size_t i=0;i<sizeof(observed);i++)REQUIRE(((unsigned char*)&observed)[i]==0);}
    else {
        REQUIRE(observed.type==expected_type&&observed.machine==62);
        REQUIRE(memcmp(observed.file.sha256,installed.expected_sha256,32)==0);
        unsigned interpreter=0,dynamic=0;
        for(unsigned i=0;i<observed.phnum;i++){interpreter+=observed.programs[i].type==3;dynamic+=observed.programs[i].type==2;}
        REQUIRE(interpreter==1&&dynamic==1);
    }
    int cancel_fd=owner.cancel_fd;
    if(hold_hash){
        pthread_t closer;es_launch_cleanup first=ES_LAUNCH_CLOSE_INVALID;
        REQUIRE(pthread_create(&closer,NULL,settling,&first)==0);
        pthread_mutex_lock(&held_mutex);while(!entered)pthread_cond_wait(&changed,&held_mutex);pthread_mutex_unlock(&held_mutex);
        REQUIRE(es_launch_close(&owner,10000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
        REQUIRE(fcntl(cancel_fd,F_GETFD)>=0);
        pthread_mutex_lock(&held_mutex);released=1;pthread_cond_broadcast(&changed);pthread_mutex_unlock(&held_mutex);
        REQUIRE(pthread_join(closer,NULL)==0&&first==ES_LAUNCH_CLOSED_INCONCLUSIVE);
        REQUIRE(es_launch_close(&owner,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
    } else REQUIRE(es_launch_close(&owner,1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);
    REQUIRE(hash_closes==1&&owner.hash.state==3);
    REQUIRE(fcntl(cancel_fd,F_GETFD)<0&&errno==EBADF);
    int status;REQUIRE(waitpid(child,&status,0)==child);child=-1;
    REQUIRE(WIFEXITED(status)&&WEXITSTATUS(status)==0);
    return 0;
}

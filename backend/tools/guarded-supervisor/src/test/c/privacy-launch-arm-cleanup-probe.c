#define _GNU_SOURCE
#include "privacy-launch.h"
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
/* Independently invented ownership schedules. Production launch/root/fork and
   descriptor cleanup run unchanged; only the explicit disarm-fault mode replaces
   a root disarm result. No platform component or kernel socket option is faked. */
#define CHECK(x) do {if(!(x)){fprintf(stderr,"ARM_CLEANUP_ASSERT_LINE_%d\n",__LINE__);exit(40);}} while(0)
static es_launch first,second,recovery;
static int parent_fd,close_first,disarm_fault;
static char first_path[ES_LISTENER_PATH_BYTES],first_directory[ES_LISTENER_PATH_BYTES];
static char parent[]="/tmp/es-arm-cleanup-XXXXXX";
es_root_result __real_es_root_disarm(es_root *);
es_root_result __wrap_es_root_disarm(es_root *p){return disarm_fault==2?ES_ROOT_CLEANUP:disarm_fault?ES_ROOT_INVALID:__real_es_root_disarm(p);}
static void opened(es_launch *p){char out[ES_LISTENER_PATH_BYTES];CHECK(es_launch_open(p,parent_fd,parent,5000000000ULL,out)==ES_LAUNCH_OK);CHECK(!access(out,F_OK));if(p==&first){strcpy(first_path,p->listener.path);strcpy(first_directory,p->listener.directory_name);}}
static void complete(es_launch *p){
 int fd=p->cancel_fd;char path[ES_LISTENER_PATH_BYTES];strcpy(path,p->listener.path);
 CHECK(es_launch_close(p,1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);
 CHECK(p->cancel_fd==-1&&fcntl(fd,F_GETFD)==-1&&errno==EBADF);CHECK(access(path,F_OK)==-1&&errno==ENOENT);
 es_launch_status s;CHECK(es_launch_status_read(p,&s)==ES_LAUNCH_OK);
 CHECK(s.close_state==ES_LAUNCH_CLOSE_SETTLED&&s.calls_quiescent&&!s.cleanup_inconclusive);
 int other=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(other>=0);if(other!=fd){CHECK(dup2(other,fd)==fd);CHECK(!close(other));}
 CHECK(es_launch_cancel(p)==ES_LAUNCH_OK);CHECK(es_launch_close(p,1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);
 CHECK(fcntl(fd,F_GETFD)>=0);CHECK(!close(fd));
}
static void *contender(void *unused){
 (void)unused;CHECK(es_launch_arm(&second)==ES_LAUNCH_INVALID);
 CHECK(!second.root.generation&&!second.root.fork.state);
 CHECK(es_launch_cancel(&second)==ES_LAUNCH_OK);
 if(close_first){CHECK(es_launch_close(&second,1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);}
 if(disarm_fault==2){int fd=second.cancel_fd;CHECK(es_launch_disarm(&second)==ES_LAUNCH_CLEANUP);
  CHECK(es_launch_close(&second,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
  es_launch_status s;CHECK(es_launch_status_read(&second,&s)==ES_LAUNCH_OK&&s.close_state==ES_LAUNCH_CLOSE_SETTLED&&s.cleanup_inconclusive);
  CHECK(second.cancel_fd==-1&&fcntl(fd,F_GETFD)==-1&&errno==EBADF);
  CHECK(es_launch_close(&second,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);return NULL;}
 CHECK(es_launch_disarm(&second)==ES_LAUNCH_INVALID);
 if(!close_first)complete(&second);else CHECK(es_launch_close(&second,1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);
 es_launch_status s;CHECK(es_launch_status_read(&second,&s)==ES_LAUNCH_OK&&s.failure==ES_LAUNCH_INVALID&&!s.disarm_completed);
 CHECK(es_launch_arm(&second)==ES_LAUNCH_INVALID);return NULL;
}
int main(int argc,char **argv){
 CHECK(argc==2&&mkdtemp(parent));parent_fd=open(parent,O_PATH|O_DIRECTORY|O_CLOEXEC);CHECK(parent_fd>=0);opened(&first);
 CHECK(es_launch_arm(&first)==ES_LAUNCH_OK);CHECK(first.root.generation&&atomic_load(&first.root.fork.armed));
 if(!strcmp(argv[1],"contention")||!strcmp(argv[1],"close-first")||!strcmp(argv[1],"unowned-cleanup-fault")){
  disarm_fault=!strcmp(argv[1],"unowned-cleanup-fault")?2:0;
  close_first=!strcmp(argv[1],"close-first");opened(&second);pthread_t t;CHECK(!pthread_create(&t,NULL,contender,NULL));CHECK(!pthread_join(t,NULL));disarm_fault=0;
  CHECK(atomic_load(&first.root.fork.armed)&&fcntl(first.cancel_fd,F_GETFD)>=0&&!access(first.listener.path,F_OK));
  CHECK(es_launch_cancel(&first)==ES_LAUNCH_OK);CHECK(es_launch_disarm(&first)==ES_LAUNCH_OK);complete(&first);
 }else{
  CHECK(!strcmp(argv[1],"acquired-pending")||!strcmp(argv[1],"disarm-fault"));disarm_fault=!strcmp(argv[1],"disarm-fault");
  int fd=first.cancel_fd;CHECK(es_launch_cancel(&first)==ES_LAUNCH_OK);
  if(disarm_fault)CHECK(es_launch_disarm(&first)==ES_LAUNCH_INVALID);
  CHECK(es_launch_close(&first,10000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
  es_launch_status s;CHECK(es_launch_status_read(&first,&s)==ES_LAUNCH_OK&&s.close_state==ES_LAUNCH_CLOSE_REQUESTED&&s.cleanup_inconclusive);
  CHECK(first.cancel_fd==fd&&fcntl(fd,F_GETFD)>=0&&atomic_load(&first.root.fork.armed));
  if(disarm_fault){disarm_fault=0;CHECK(__real_es_root_disarm(&first.root)==ES_ROOT_OK);
   /* Fault mode deliberately has no published launch disarm completion. Owner
      remains quarantined; process exit releases it, never an upgraded close. */
   CHECK(es_launch_close(&first,10000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
   CHECK(!unlink(first.listener.path));CHECK(!unlinkat(parent_fd,first.listener.directory_name,AT_REMOVEDIR));
  }else{CHECK(es_launch_disarm(&first)==ES_LAUNCH_OK);CHECK(es_launch_close(&first,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);CHECK(first.cancel_fd==-1);}
 }
 opened(&recovery);CHECK(es_launch_arm(&recovery)==ES_LAUNCH_OK);CHECK(es_launch_disarm(&recovery)==ES_LAUNCH_OK);complete(&recovery);
 /* Expired native cleanup remains inconclusive; exclusive mock fixture teardown
    removes any residual namespace without changing its outcome. */
 if(first_path[0])(void)unlink(first_path);
 if(first_directory[0])(void)unlinkat(parent_fd,first_directory,AT_REMOVEDIR);
 CHECK(fcntl(parent_fd,F_GETFD)>=0);CHECK(!close(parent_fd));CHECK(!rmdir(parent));return 0;
}

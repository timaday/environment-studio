#define _GNU_SOURCE
#include "privacy-launch.h"
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/random.h>
#include <time.h>
#include <unistd.h>
/* Independent ownership tests: real descriptors, invented private namespace.
   The close wrapper delays only return, after actual subordinate cleanup. */
#define CHECK(x) do {if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}} while(0)
static es_launch owners[2];
static char parent[]="/tmp/es-independent-launch-XXXXXX";
static int parent_fd=-1,partial_entropy,entropy_calls,event_calls,close_mode;
static int observed_fd=-1,observed_closes,held,released;
static pthread_mutex_t lock=PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t changed=PTHREAD_COND_INITIALIZER;
static uint64_t tick(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
static void nap(uint64_t ns){struct timespec t={(time_t)(ns/1000000000ULL),(long)(ns%1000000000ULL)};while(nanosleep(&t,&t)){CHECK(errno==EINTR);}}
int __real_eventfd(unsigned,int);
int __wrap_eventfd(unsigned v,int f){++event_calls;return __real_eventfd(v,f);}
ssize_t __real_getrandom(void *,size_t,unsigned);
ssize_t __wrap_getrandom(void *p,size_t n,unsigned f){if(!partial_entropy)return __real_getrandom(p,n,f);++entropy_calls;CHECK(n&&f==GRND_NONBLOCK);*(unsigned char*)p=0xa1;return 1;}
int __real_close(int);
int __wrap_close(int fd){if(fd==observed_fd)++observed_closes;return __real_close(fd);}
es_listener_cleanup __real_es_listener_close(es_listener *,uint64_t);
es_listener_cleanup __wrap_es_listener_close(es_listener *p,uint64_t deadline){
 es_listener_cleanup r=__real_es_listener_close(p,deadline);
 if(close_mode){CHECK(r==ES_LISTENER_CLOSED_COMPLETE);pthread_mutex_lock(&lock);held=1;pthread_cond_broadcast(&changed);
  while(close_mode==2&&!released)pthread_cond_wait(&changed,&lock);
  pthread_mutex_unlock(&lock);
  if(close_mode==1){uint64_t n=tick();if(n<=deadline)nap(deadline-n+20000000ULL);}
 }
 return r;
}
static void fixture_cleanup(void){
 close_mode=0;
 for(unsigned i=0;i<2;i++){
  if(owners[i].initialized)(void)es_launch_close(&owners[i],1000000000ULL);
  if(owners[i].listener.path[0])(void)unlink(owners[i].listener.path);
  if(parent_fd>=0&&owners[i].listener.directory_name[0])(void)unlinkat(parent_fd,owners[i].listener.directory_name,AT_REMOVEDIR);
 }
 if(parent_fd>=0)(void)close(parent_fd);
 (void)rmdir(parent);
}
static void opened(unsigned i,char out[ES_LISTENER_PATH_BYTES]){CHECK(es_launch_open(&owners[i],parent_fd,parent,5000000000ULL,out)==ES_LAUNCH_OK);CHECK(out[0]);}
static void zero(const void *p,size_t n){const unsigned char *b=p;for(size_t i=0;i<n;i++)CHECK(!b[i]);}
struct closing {uint64_t allowance;es_launch_cleanup result;};
static void *closer(void *v){struct closing *c=v;c->result=es_launch_close(&owners[0],c->allowance);return NULL;}
int main(int argc,char **argv){
 CHECK(argc==2&&mkdtemp(parent));parent_fd=open(parent,O_PATH|O_DIRECTORY|O_CLOEXEC);CHECK(parent_fd>=0);CHECK(!atexit(fixture_cleanup));
 char out[ES_LISTENER_PATH_BYTES];const char *mode=argv[1];
 if(!strcmp(mode,"aliases")){
  es_launch before=owners[0];memset(out,0xa1,sizeof(out));
  CHECK(es_launch_open(&owners[0],parent_fd,parent,1,(char*)&owners[0])==ES_LAUNCH_INVALID);CHECK(!memcmp(&before,&owners[0],sizeof(before)));
  CHECK(es_launch_open(&owners[0],parent_fd,(char*)&owners[0],1,out)==ES_LAUNCH_INVALID);CHECK(!memcmp(&before,&owners[0],sizeof(before)));
  char overlap[ES_LISTENER_PATH_BYTES+16];memset(overlap,0xa1,sizeof(overlap));strcpy(overlap,"/invented");char copy[sizeof(overlap)];memcpy(copy,overlap,sizeof(copy));
  CHECK(es_launch_open(&owners[0],parent_fd,overlap,1,overlap+3)==ES_LAUNCH_INVALID);CHECK(!memcmp(overlap,copy,sizeof(copy)));
  CHECK(es_launch_open(&owners[0],parent_fd,NULL,1,out)==ES_LAUNCH_INVALID);zero(out,sizeof(out));CHECK(!event_calls);zero(&owners[0],sizeof(owners[0]));
 }else if(!strcmp(mode,"entropy")){
  partial_entropy=1;memset(out,0xa1,sizeof(out));CHECK(es_launch_open(&owners[0],parent_fd,parent,5000000000ULL,out)==ES_LAUNCH_RESOURCE);
  CHECK(entropy_calls==4&&event_calls==1&&owners[0].initialized&&owners[0].cancel_fd>=0);zero(out,sizeof(out));
  observed_fd=owners[0].cancel_fd;CHECK(es_launch_close(&owners[0],1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);CHECK(observed_closes==1);zero(owners[0].launch_id,sizeof(owners[0].launch_id));
  CHECK(es_launch_close(&owners[0],1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE&&observed_closes==1);
 }else if(!strcmp(mode,"saturated")){
  opened(0,out);char second[ES_LISTENER_PATH_BYTES];opened(1,second);CHECK(strcmp(out,second));
  uint64_t value=UINT64_MAX-1,readback=0;int fd=owners[0].cancel_fd;CHECK(write(fd,&value,sizeof(value))==sizeof(value));
  CHECK(es_launch_cancel(&owners[0])==ES_LAUNCH_OK);CHECK(es_launch_arm(&owners[0])==ES_LAUNCH_CANCELLED);
  CHECK(read(fd,&readback,sizeof(readback))==sizeof(readback)&&readback==value);CHECK(es_launch_arm(&owners[0])==ES_LAUNCH_CANCELLED);
  es_launch_status status;CHECK(es_launch_status_read(&owners[1],&status)==ES_LAUNCH_OK&&status.phase==ES_LAUNCH_PHASE_OPEN&&status.failure==ES_LAUNCH_OK);
  CHECK(!access(second,F_OK));CHECK(es_launch_close(&owners[0],1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);CHECK(!access(second,F_OK));
  CHECK(es_launch_close(&owners[1],1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);
 }else{
  CHECK(!strcmp(mode,"late")||!strcmp(mode,"shortened"));opened(0,out);observed_fd=owners[0].cancel_fd;
  close_mode=!strcmp(mode,"late")?1:2;struct closing c={close_mode==1?200000000ULL:3000000000ULL,ES_LAUNCH_CLOSE_INVALID};pthread_t thread;
  CHECK(!pthread_create(&thread,NULL,closer,&c));pthread_mutex_lock(&lock);while(!held)pthread_cond_wait(&changed,&lock);pthread_mutex_unlock(&lock);
  es_launch_status status;CHECK(es_launch_status_read(&owners[0],&status)==ES_LAUNCH_OK&&status.close_state==ES_LAUNCH_CLOSE_SETTLING&&!status.calls_quiescent);
  CHECK(es_launch_cancel(&owners[0])==ES_LAUNCH_OK&&fcntl(observed_fd,F_GETFD)>=0);
  if(close_mode==2){CHECK(es_launch_close(&owners[0],20000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);pthread_mutex_lock(&lock);released=1;pthread_cond_broadcast(&changed);pthread_mutex_unlock(&lock);}
  CHECK(!pthread_join(thread,NULL));CHECK(c.result==ES_LAUNCH_CLOSED_INCONCLUSIVE&&observed_closes==1);
  CHECK(es_launch_status_read(&owners[0],&status)==ES_LAUNCH_OK&&status.close_state==ES_LAUNCH_CLOSE_SETTLED&&status.calls_quiescent&&status.cleanup_inconclusive);
  int reused=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(reused>=0);if(reused!=observed_fd){CHECK(dup2(reused,observed_fd)==observed_fd);CHECK(!close(reused));}
  CHECK(es_launch_cancel(&owners[0])==ES_LAUNCH_OK);CHECK(es_launch_close(&owners[0],1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
  CHECK(fcntl(observed_fd,F_GETFD)>=0&&observed_closes==1);CHECK(!close(observed_fd));observed_fd=-1;
 }
 CHECK(fcntl(parent_fd,F_GETFD)>=0);return 0;
}

#define _GNU_SOURCE
#include "privacy-launch.h"
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <errno.h>
#include <time.h>
#include <sys/random.h>
#include <sys/wait.h>
#include <signal.h>
#define CHECK(x) do { if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);} } while(0)
static es_launch *active;
static pid_t owned_child=-1,foreign_child=-1;
static es_launch owner;static int parent_fd=-1;static char parent_path[]="/tmp/es-launch-probe-XXXXXX";
static void cleanup_fixture(void){
 if(owner.initialized){if(owner.arm_entered&&!owner.disarm_done)(void)es_launch_disarm(&owner);(void)es_launch_close(&owner,1000000000ULL);}
 if(owned_child>0){kill(owned_child,SIGKILL);waitpid(owned_child,NULL,0);owned_child=-1;}
 if(foreign_child>0){kill(foreign_child,SIGKILL);waitpid(foreign_child,NULL,0);foreign_child=-1;}
 /* Test-only exclusive namespace. An expired native cleanup remains inconclusive;
    this later fixture teardown does not upgrade its outcome. */
 if(owner.listener.path[0])(void)unlink(owner.listener.path);
 if(owner.listener.directory_name[0]&&parent_fd>=0)(void)unlinkat(parent_fd,owner.listener.directory_name,AT_REMOVEDIR);
 if(parent_fd>=0){(void)close(parent_fd);parent_fd=-1;}
 (void)rmdir(parent_path);
}
static int fault_listener,cancel_accept,connection_opens,expire_arm,cancel_match,partial_listener,fail_event,hold_accept,hold_register,hold_disarm,root_uncertain,root_close_calls;
static int entropy_interrupt,entropy_calls,signal_interrupt;
static int fault_entropy,hold_capture,hold_signal,held,released,uncertain_fd=-1,uncertain_hit;
static pthread_mutex_t test_lock=PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t test_changed=PTHREAD_COND_INITIALIZER;
static uint64_t tick(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
static void hold(void){pthread_mutex_lock(&test_lock);held=1;pthread_cond_broadcast(&test_changed);while(!released)pthread_cond_wait(&test_changed,&test_lock);pthread_mutex_unlock(&test_lock);}
static void wait_held(void){pthread_mutex_lock(&test_lock);while(!held)pthread_cond_wait(&test_changed,&test_lock);pthread_mutex_unlock(&test_lock);}
static void release_hold(void){pthread_mutex_lock(&test_lock);released=1;pthread_cond_broadcast(&test_changed);pthread_mutex_unlock(&test_lock);}
int __real_eventfd(unsigned,int);
int __wrap_eventfd(unsigned value,int flags){if(fail_event){errno=EMFILE;return -1;}return __real_eventfd(value,flags);}
es_root_result __real_es_root_match(es_root *,es_connection *,es_peer_identity *);
es_root_result __wrap_es_root_match(es_root *p,es_connection *c,es_peer_identity *out){es_root_result r=__real_es_root_match(p,c,out);if(cancel_match&&r==ES_ROOT_CORRELATED)CHECK(es_launch_cancel(active)==ES_LAUNCH_OK);return r;}
ssize_t __real_getrandom(void *,size_t,unsigned);
ssize_t __wrap_getrandom(void *p,size_t n,unsigned flags){if(fault_entropy){++entropy_calls;errno=entropy_interrupt?EINTR:EAGAIN;return -1;}return __real_getrandom(p,n,flags);}
int __real_close(int);
int __wrap_close(int fd){int r=__real_close(fd);if(fd==uncertain_fd&&!uncertain_hit){uncertain_hit=1;errno=EINTR;return -1;}return r;}
ssize_t __real_write(int,const void *,size_t);
ssize_t __wrap_write(int fd,const void *p,size_t n){
 if(signal_interrupt&&active&&fd==active->cancel_fd){signal_interrupt=0;errno=EINTR;return -1;}
 int block=0;pthread_mutex_lock(&test_lock);if(hold_signal&&active&&fd==active->cancel_fd){hold_signal=0;block=1;}pthread_mutex_unlock(&test_lock);
 if(block)hold();
 return __real_write(fd,p,n);
}
es_root_result __real_es_root_arm(es_root *,int,uint64_t,const unsigned char *);
es_root_result __wrap_es_root_arm(es_root *p,int fd,uint64_t deadline,const unsigned char *id){return __real_es_root_arm(p,fd,expire_arm?tick()-1:deadline,id);}
es_root_result __real_es_root_register(es_root *,uint64_t);
es_root_result __wrap_es_root_register(es_root *p,uint64_t pid){if(hold_register)hold();return __real_es_root_register(p,pid);}
es_root_result __real_es_root_disarm(es_root *);
es_root_result __wrap_es_root_disarm(es_root *p){if(hold_disarm){hold_disarm=0;hold();}return __real_es_root_disarm(p);}
es_root_result __real_es_root_capture(es_root *,es_peer_identity *);
es_root_result __wrap_es_root_capture(es_root *p,es_peer_identity *out){if(hold_capture)hold();return __real_es_root_capture(p,out);}
static void *receiver(void *result){*(es_launch_result*)result=es_launch_capture(active);return NULL;}
static void *wrong_disarm(void *result){*(es_launch_result*)result=es_launch_disarm(active);return NULL;}
static void *canceller(void *result){*(es_launch_result*)result=es_launch_cancel(active);return NULL;}
struct closing {uint64_t duration,ended;es_launch_cleanup result;};
static void *closer(void *v){struct closing *c=v;c->result=es_launch_close(active,c->duration);c->ended=tick();return NULL;}
es_root_cleanup __real_es_root_close(es_root *,uint64_t);
es_root_cleanup __wrap_es_root_close(es_root *p,uint64_t deadline){++root_close_calls;es_root_cleanup r=__real_es_root_close(p,deadline);return root_uncertain?ES_ROOT_CLOSED_INCONCLUSIVE:r;}
static void *close_release(void *unused){(void)unused;wait_held();CHECK(es_launch_close(active,30000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);release_hold();return NULL;}
es_listener_result __real_es_listener_open(es_listener *,int,const char *,int,uint64_t,unsigned);
es_listener_result __wrap_es_listener_open(es_listener *p,int fd,const char *path,int cancel,uint64_t deadline,unsigned width){
 if(fault_listener)return ES_LISTENER_PLATFORM;
 es_listener_result r=__real_es_listener_open(p,fd,path,cancel,deadline,width);return partial_listener&&r==ES_LISTENER_OK?ES_LISTENER_IO:r;
}
es_listener_result __real_es_listener_accept(es_listener *,uint64_t *);
es_listener_result __wrap_es_listener_accept(es_listener *p,uint64_t *token){
 if(hold_accept)hold();
 es_listener_result r=__real_es_listener_accept(p,token);if(cancel_accept&&r==ES_LISTENER_ACCEPTED)CHECK(es_launch_cancel(active)==ES_LAUNCH_OK);return r;
}
es_connection_result __real_es_connection_open(es_connection *,es_listener *,uint64_t);
es_connection_result __wrap_es_connection_open(es_connection *p,es_listener *l,uint64_t token){++connection_opens;return __real_es_connection_open(p,l,token);}
static void *receiver_pair(void *result){es_launch_result r=es_launch_capture(active);*(es_launch_result*)result=r==ES_LAUNCH_OK?es_launch_correlate(active):r;return NULL;}
static void mock_child(const char *endpoint){
 int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un address={.sun_family=AF_UNIX};
 memcpy(address.sun_path,endpoint,strlen(endpoint)+1);if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof(address)))_exit(41);
 unsigned char prepare[12]={'E','S','P','R','V','0','0','1',1,0,0,0};if(send(fd,prepare,12,MSG_NOSIGNAL)!=12)_exit(42);
 char byte;if(read(fd,&byte,1)!=0)_exit(43);close(fd);_exit(0);
}
__attribute__((constructor)) static void child_constructor(void){
 const char *endpoint=getenv("ES_LAUNCH_TEST_ENDPOINT");if(!endpoint)return;
 int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un address={.sun_family=AF_UNIX};
 size_t n=strlen(endpoint);if(n>=sizeof(address.sun_path))_exit(41);memcpy(address.sun_path,endpoint,n+1);
 if(fd<0||connect(fd,(struct sockaddr*)&address,sizeof(address)))_exit(42);
 unsigned char prepare[12]={'E','S','P','R','V','0','0','1',1,0,0,0};
 if(send(fd,prepare,12,MSG_NOSIGNAL)!=12)_exit(43);
 if(write(1,"INVENTED-OUT\n",13)!=13||write(2,"INVENTED-ERR\n",13)!=13)_exit(44);
 char byte;if(read(fd,&byte,1)!=0)_exit(45);if(close(fd))_exit(46);_exit(0);
}
int main(int argc,char **argv) {
 const char *mode=argc==2?argv[1]:"open";
 char *parent=parent_path;if(!mkdtemp(parent))return 41;
 CHECK(!atexit(cleanup_fixture));
 int fd=open(parent,O_PATH|O_DIRECTORY|O_CLOEXEC);parent_fd=fd;if(fd<0)return 41;
 active=&owner;char path[ES_LISTENER_PATH_BYTES]={0};
 if(!strcmp(mode,"entropy")||!strcmp(mode,"entropy-interrupted"))fault_entropy=1;
 if(!strcmp(mode,"entropy-interrupted"))entropy_interrupt=1;
 if(!strcmp(mode,"listener-fault"))fault_listener=1;
 if(!strcmp(mode,"partial-listener"))partial_listener=1;
 if(!strcmp(mode,"event-fault"))fail_event=1;
 es_launch_result r=es_launch_open(&owner,fd,parent,1000000000ULL,path);
 int ok=r==ES_LAUNCH_OK;
 if(!strcmp(mode,"entropy")||!strcmp(mode,"entropy-interrupted")){CHECK(r==ES_LAUNCH_RESOURCE&&owner.initialized&&owner.cancel_fd>=0);CHECK(!path[0]);CHECK(entropy_calls==(entropy_interrupt?4:1));ok=1;}
 else if(!strcmp(mode,"listener-fault")){CHECK(r==ES_LAUNCH_PLATFORM&&owner.initialized&&owner.cancel_fd>=0&&!path[0]);ok=1;}
 else if(!strcmp(mode,"partial-listener")){CHECK(r==ES_LAUNCH_IO&&owner.initialized&&owner.listener.state&&!path[0]);ok=1;}
 else if(!strcmp(mode,"event-fault")){CHECK(r==ES_LAUNCH_RESOURCE&&owner.initialized&&owner.cancel_fd<0&&!path[0]);ok=1;}
 else CHECK(r==ES_LAUNCH_OK);
 if(!strcmp(mode,"fresh")){
  es_launch fresh={0};es_launch_status status;memset(&status,1,sizeof(status));
  CHECK(es_launch_capture(&fresh)==ES_LAUNCH_INVALID);CHECK(es_launch_register(&fresh,1)==ES_LAUNCH_INVALID);
  CHECK(es_launch_disarm(&fresh)==ES_LAUNCH_INVALID);CHECK(es_launch_correlate(&fresh)==ES_LAUNCH_INVALID);
  CHECK(es_launch_cancel(&fresh)==ES_LAUNCH_INVALID);CHECK(es_launch_close(&fresh,1)==ES_LAUNCH_CLOSE_INVALID);
  CHECK(es_launch_status_read(&fresh,&status)==ES_LAUNCH_INVALID);unsigned char *bytes=(unsigned char*)&status;for(size_t i=0;i<sizeof(status);i++)CHECK(!bytes[i]);
 }
 if(!strcmp(mode,"status-alias")){
  es_launch_status status;CHECK(es_launch_status_read(&owner,&status)==ES_LAUNCH_OK&&status.phase==ES_LAUNCH_PHASE_OPEN);
  CHECK(es_launch_status_read(&owner,(es_launch_status*)&owner)==ES_LAUNCH_INVALID);
  CHECK(es_launch_status_read(&owner,NULL)==ES_LAUNCH_INVALID);CHECK(es_launch_status_read(&owner,&status)==ES_LAUNCH_OK&&status.failure==ES_LAUNCH_OK);
 }
 if(!strcmp(mode,"rearm")||!strcmp(mode,"wrong-thread")||!strcmp(mode,"double-disarm")){
  CHECK(es_launch_arm(&owner)==ES_LAUNCH_OK);
  if(!strcmp(mode,"rearm"))CHECK(es_launch_arm(&owner)==ES_LAUNCH_PROTOCOL);
  if(!strcmp(mode,"wrong-thread")){pthread_t thread;es_launch_result result;CHECK(!pthread_create(&thread,NULL,wrong_disarm,&result));CHECK(!pthread_join(thread,NULL));CHECK(result==ES_LAUNCH_PROTOCOL);}
  CHECK(es_launch_disarm(&owner)==ES_LAUNCH_OK);
  if(!strcmp(mode,"double-disarm"))CHECK(es_launch_disarm(&owner)==ES_LAUNCH_PROTOCOL);
  CHECK(es_launch_capture(&owner)==ES_LAUNCH_PROTOCOL);
 }
 if(!strcmp(mode,"failed-arm")){
  expire_arm=1;CHECK(es_launch_arm(&owner)==ES_LAUNCH_DEADLINE);
  CHECK(es_launch_disarm(&owner)==ES_LAUNCH_OK);
  CHECK(es_launch_capture(&owner)==ES_LAUNCH_DEADLINE);
  CHECK(es_launch_register(&owner,(uint64_t)getpid())==ES_LAUNCH_DEADLINE);
  es_launch_status status;CHECK(es_launch_status_read(&owner,&status)==ES_LAUNCH_OK);
  CHECK(status.failure==ES_LAUNCH_DEADLINE&&status.disarm_completed);
 }
 if(!strcmp(mode,"signal-interrupted")){signal_interrupt=1;CHECK(es_launch_cancel(&owner)==ES_LAUNCH_IO);CHECK(es_launch_arm(&owner)==ES_LAUNCH_CANCELLED);}
 if(!strcmp(mode,"duration")){
  for(unsigned i=0;i<3;i++){es_launch fresh={0};char out[104];memset(out,1,sizeof(out));uint64_t invalid=i==0?0:i==1?180000000001ULL:UINT64_MAX;
   CHECK(es_launch_open(&fresh,fd,parent,invalid,out)==ES_LAUNCH_INVALID&&!fresh.initialized);for(unsigned n=0;n<104;n++)CHECK(!out[n]);}
  CHECK(owner.startup_deadline<=owner.operation_deadline);
 }
 if(!strcmp(mode,"live")||!strcmp(mode,"foreign")||!strcmp(mode,"wrong-pid")||!strcmp(mode,"cancel-accepted")||!strcmp(mode,"dead-captured")||!strcmp(mode,"cancel-match")||!strcmp(mode,"close-correlate")||!strcmp(mode,"close-register")||!strcmp(mode,"close-disarm")||!strcmp(mode,"root-uncertain")){
  if(!strcmp(mode,"cancel-accepted"))cancel_accept=1;
  if(!strcmp(mode,"cancel-match"))cancel_match=1;
  if(!strcmp(mode,"close-correlate"))hold_accept=1;
  if(!strcmp(mode,"close-register"))hold_register=1;
  if(!strcmp(mode,"close-disarm"))hold_disarm=1;
  pthread_t release_thread;int releasing=hold_register||hold_disarm;
  if(releasing)CHECK(!pthread_create(&release_thread,NULL,close_release,NULL));
  pthread_t thread;es_launch_result result=ES_LAUNCH_INVALID;CHECK(!pthread_create(&thread,NULL,receiver_pair,&result));
  CHECK(es_launch_arm(&owner)==ES_LAUNCH_OK);owned_child=fork();CHECK(owned_child>=0);
  if(!owned_child){if(!strcmp(mode,"foreign"))for(;;)pause();mock_child(path);}
  uint64_t returned_pid=(uint64_t)owned_child;
  if(!strcmp(mode,"dead-captured")){
   es_launch_status status;uint64_t end=tick()+500000000ULL;
   do {CHECK(es_launch_status_read(&owner,&status)==ES_LAUNCH_OK);if(status.phase==ES_LAUNCH_PHASE_CAPTURED)break;usleep(1000);}while(tick()<end);
   CHECK(status.phase==ES_LAUNCH_PHASE_CAPTURED);CHECK(!kill(owned_child,SIGKILL));CHECK(waitpid(owned_child,NULL,0)==owned_child);owned_child=-1;
  }
  es_launch_result registered=es_launch_register(&owner,!strcmp(mode,"wrong-pid")?(uint64_t)getpid():returned_pid);
  CHECK(registered==(!strcmp(mode,"close-register")?ES_LAUNCH_CANCELLED:(!strcmp(mode,"wrong-pid")||!strcmp(mode,"dead-captured"))?ES_LAUNCH_IDENTITY:ES_LAUNCH_OK));CHECK(es_launch_disarm(&owner)==ES_LAUNCH_OK);
  if(!strcmp(mode,"foreign")){foreign_child=fork();CHECK(foreign_child>=0);if(!foreign_child)mock_child(path);}
  if(!strcmp(mode,"close-correlate")){
   wait_held();pthread_t closing_thread;struct closing close_call={1000000000ULL,0,0};
   CHECK(!pthread_create(&closing_thread,NULL,closer,&close_call));usleep(20000);
   es_launch_status status;CHECK(es_launch_status_read(&owner,&status)==ES_LAUNCH_OK&&status.close_state==ES_LAUNCH_CLOSE_REQUESTED&&!status.calls_quiescent);
   release_hold();CHECK(!pthread_join(closing_thread,NULL));CHECK(close_call.result==ES_LAUNCH_CLOSED_COMPLETE);
  }
  CHECK(!pthread_join(thread,NULL));if(releasing)CHECK(!pthread_join(release_thread,NULL));
  if(!strcmp(mode,"live")||!strcmp(mode,"root-uncertain"))CHECK(result==ES_LAUNCH_ROOT_CORRELATED);
  else if(!strcmp(mode,"cancel-accepted")){CHECK(result==ES_LAUNCH_CANCELLED);CHECK(connection_opens==0);}
  else if(!strcmp(mode,"cancel-match")||!strcmp(mode,"close-correlate")||releasing)CHECK(result==ES_LAUNCH_CANCELLED);
  else CHECK(result==ES_LAUNCH_IDENTITY);
 }
 if(!strcmp(mode,"root-uncertain")){
  int event=owner.cancel_fd;root_uncertain=1;
  CHECK(es_launch_close(&owner,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
  CHECK(root_close_calls==1&&fcntl(event,F_GETFD)<0&&access(path,F_OK)<0);
  int replacement=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(replacement>=0);
  if(replacement!=event){CHECK(dup2(replacement,event)==event);CHECK(!close(replacement));replacement=event;}
  CHECK(es_launch_close(&owner,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
  CHECK(root_close_calls==1&&fcntl(replacement,F_GETFD)>=0);CHECK(!close(replacement));
 }
 if(!strcmp(mode,"early-cancel")||!strcmp(mode,"early-deadline")){
  es_launch_result result;pthread_t thread;CHECK(!pthread_create(&thread,NULL,receiver,&result));
  if(!strcmp(mode,"early-cancel"))CHECK(es_launch_cancel(&owner)==ES_LAUNCH_OK);
  CHECK(!pthread_join(thread,NULL));CHECK(result==(!strcmp(mode,"early-cancel")?ES_LAUNCH_CANCELLED:ES_LAUNCH_DEADLINE));
 }
 if(!strcmp(mode,"short-close")){
  hold_capture=1;CHECK(es_launch_arm(&owner)==ES_LAUNCH_OK);es_launch_result result;pthread_t recv,a,b;
  CHECK(!pthread_create(&recv,NULL,receiver,&result));wait_held();
  struct closing first={3000000000ULL,0,0},second={30000000ULL,0,0};
  CHECK(!pthread_create(&a,NULL,closer,&first));usleep(20000);uint64_t start=tick();CHECK(!pthread_create(&b,NULL,closer,&second));
  CHECK(!pthread_join(b,NULL));CHECK(!pthread_join(a,NULL));CHECK(first.ended-start<250000000ULL&&first.result==ES_LAUNCH_CLOSED_INCONCLUSIVE&&second.result==ES_LAUNCH_CLOSED_INCONCLUSIVE);
  CHECK(es_launch_disarm(&owner)==ES_LAUNCH_OK);release_hold();CHECK(!pthread_join(recv,NULL));CHECK(result!=ES_LAUNCH_OK);
  CHECK(es_launch_close(&owner,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
 }
 if(!strcmp(mode,"signal-close")){
  hold_signal=1;es_launch_result result;pthread_t thread,close_thread;int event=owner.cancel_fd;
  CHECK(!pthread_create(&thread,NULL,canceller,&result));wait_held();struct closing closing={1000000000ULL,0,0};
  CHECK(!pthread_create(&close_thread,NULL,closer,&closing));usleep(20000);CHECK(fcntl(event,F_GETFD)>=0);
  release_hold();CHECK(!pthread_join(thread,NULL));CHECK(!pthread_join(close_thread,NULL));CHECK(result==ES_LAUNCH_OK&&closing.result==ES_LAUNCH_CLOSED_COMPLETE);
 }
 if(!strcmp(mode,"uncertain"))uncertain_fd=owner.cancel_fd;
 if(!strcmp(mode,"reuse")||!strcmp(mode,"uncertain")){
  int event=owner.cancel_fd;es_launch_cleanup expected=!strcmp(mode,"uncertain")?ES_LAUNCH_CLOSED_INCONCLUSIVE:ES_LAUNCH_CLOSED_COMPLETE;
  CHECK(es_launch_close(&owner,1000000000ULL)==expected);CHECK(fcntl(event,F_GETFD)<0);
  int replacement=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(replacement>=0);
  if(replacement!=event){CHECK(dup2(replacement,event)==event);CHECK(!close(replacement));replacement=event;}
  CHECK(es_launch_cancel(&owner)==ES_LAUNCH_OK);CHECK(es_launch_close(&owner,0)==expected);CHECK(fcntl(replacement,F_GETFD)>=0);CHECK(!close(replacement));
 }
 if(!strcmp(mode,"null-open")){
  char out[104];memset(out,1,sizeof(out));
  CHECK(es_launch_open(NULL,fd,parent,1000000000ULL,out)==ES_LAUNCH_INVALID);
  for(unsigned i=0;i<sizeof(out);i++)CHECK(!out[i]);
  es_launch fresh={0};memset(out,1,sizeof(out));
  CHECK(es_launch_open(&fresh,fd,NULL,1000000000ULL,out)==ES_LAUNCH_INVALID);
  for(unsigned i=0;i<sizeof(out);i++)CHECK(!out[i]);
  CHECK(!fresh.initialized);
 }
 if(!strcmp(mode,"long-path")){
  es_launch fresh={0};char long_path[105];memset(long_path,'x',104);long_path[104]=0;char out[104];memset(out,1,sizeof(out));
  CHECK(es_launch_open(&fresh,fd,long_path,1000000000ULL,out)==ES_LAUNCH_INVALID);for(unsigned i=0;i<sizeof(out);i++)CHECK(!out[i]);
 }

 es_launch_cleanup expected=(!strcmp(mode,"short-close")||!strcmp(mode,"uncertain")||!strcmp(mode,"close-register")||!strcmp(mode,"close-disarm")||!strcmp(mode,"root-uncertain"))?ES_LAUNCH_CLOSED_INCONCLUSIVE:ES_LAUNCH_CLOSED_COMPLETE;
 if(owner.initialized&&es_launch_close(&owner,1000000000ULL)!=expected)ok=0;
 CHECK(fcntl(fd,F_GETFD)>=0);cleanup_fixture();
 if(!ok){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);return 40;}return 0;
}

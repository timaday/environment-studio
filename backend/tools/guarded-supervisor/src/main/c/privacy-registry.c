#define _GNU_SOURCE
#include "privacy-registry.h"
#include "privacy-controls.h"
#include <fcntl.h>
#include <limits.h>
#include <string.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#define LIMIT 256U
#define NS10 10000000000ULL
struct es_registry_entry {
 es_launch owner;
 unsigned refs,published,retired,finally_owed,arm_called,arm_done,arm_no_window,disarm_called,coordinator_bound,event_entered,event_active;
 unsigned uncertain,complete,settled;
 pthread_t launcher,coordinator;
 es_bridge_failure failure;
 uint64_t token;
};
static struct {pthread_mutex_t mutex;unsigned issued,live,self_state;es_bridge_failure self_failure;
 int parent;const es_compiled_mechanism *mechanism;} invocation={.mutex=PTHREAD_MUTEX_INITIALIZER,.parent=-1};
static es_registry_entry entries[LIMIT];
_Static_assert(sizeof(es_registry_entry)<=16384,"bounded retained owner");
_Static_assert(sizeof(es_registry_entry)-sizeof(es_launch)<=256,"bounded retirement metadata");
_Static_assert(sizeof(invocation)<=4096,"bounded invocation bookkeeping");
uint64_t es_registry_now(void){
 struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<0||t.tv_nsec<0||t.tv_nsec>=1000000000L)return 0;
 uint64_t seconds=(uint64_t)t.tv_sec;if(seconds>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL)return 0;
 return seconds*1000000000ULL+(uint64_t)t.tv_nsec;
}
static void latch(es_registry_entry *p,es_bridge_failure f){if(p->failure==ES_BRIDGE_NONE)p->failure=f;}
static es_bridge_failure mapped(es_launch_result r){switch(r){
 case ES_LAUNCH_OK:case ES_LAUNCH_ROOT_CORRELATED:return ES_BRIDGE_NONE;
 case ES_LAUNCH_INVALID:case ES_LAUNCH_PROTOCOL:return ES_BRIDGE_PROTOCOL;
 case ES_LAUNCH_RESOURCE:return ES_BRIDGE_RESOURCE;case ES_LAUNCH_IDENTITY:return ES_BRIDGE_IDENTITY;
 case ES_LAUNCH_DEADLINE:return ES_BRIDGE_DEADLINE;case ES_LAUNCH_CANCELLED:return ES_BRIDGE_CANCELLED;
 case ES_LAUNCH_CLEANUP:return ES_BRIDGE_CLEANUP;default:return ES_BRIDGE_PLATFORM;}}
static int admitted_parent(const char *path,es_bridge_failure *failure){
 size_t n=path?strnlen(path,ES_LISTENER_PATH_BYTES):0;if(n<2||n>=ES_LISTENER_PATH_BYTES||path[0]!='/')return -1;
 int fd=open("/",O_PATH|O_DIRECTORY|O_CLOEXEC);if(fd<0)return -1;
 size_t at=1;while(at<n){size_t end=at;while(end<n&&path[end]!='/')end++;
  size_t size=end-at;char part[ES_LISTENER_PATH_BYTES];if(!size||size>=sizeof(part))goto bad;
  memcpy(part,path+at,size);part[size]=0;if(!strcmp(part,".")||!strcmp(part,".."))goto bad;
  struct stat previous;if(fstat(fd,&previous))goto bad;
  if(previous.st_uid!=0&&previous.st_uid!=geteuid())goto bad;
  if((previous.st_mode&0022)&&!(previous.st_mode&S_ISVTX))goto bad;
  int next=openat(fd,part,O_PATH|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);if(next<0)goto bad;
  int old=fd;fd=next;if(close(old)){*failure=ES_BRIDGE_CLEANUP;goto bad;}at=end+1;
 }
 struct stat st;if(fstat(fd,&st)||st.st_uid!=geteuid()||(st.st_mode&07777)!=0700)goto bad;
 return fd;
 bad:if(fd>=0&&close(fd))*failure=ES_BRIDGE_CLEANUP;return -1;
}
es_bridge_failure es_registry_self(const es_compiled_mechanism *record){
 pthread_mutex_lock(&invocation.mutex);
 if(invocation.self_state){es_bridge_failure f=invocation.self_state==2&&record==invocation.mechanism?ES_BRIDGE_NONE:invocation.self_failure;
  unsigned established=invocation.self_state==2&&record==invocation.mechanism;
  pthread_mutex_unlock(&invocation.mutex);return established?ES_BRIDGE_NONE:(f==ES_BRIDGE_NONE?ES_BRIDGE_INSTALLATION:f);}
 invocation.self_state=1;invocation.self_failure=ES_BRIDGE_INSTALLATION;pthread_mutex_unlock(&invocation.mutex);
 es_bridge_failure failure=ES_BRIDGE_INSTALLATION;int parent=-1;
 if(record&&record->ordinal>0&&record->version==1&&record->java_runtime&&record->parent_path&&!getenv("ES_PRIVACY_CONTROL")){
  parent=admitted_parent(record->parent_path,&failure);if(parent>=0)failure=ES_BRIDGE_NONE;
  if(failure==ES_BRIDGE_NONE){es_privacy_result r=es_privacy_establish();switch(r){case ES_PRIVACY_OK:break;
   case ES_PRIVACY_PLATFORM:failure=ES_BRIDGE_PLATFORM;break;case ES_PRIVACY_THREAD_SYNC:failure=ES_BRIDGE_THREAD_SYNC;break;
   default:failure=ES_BRIDGE_SELF_PRIVACY;}}
 }
 if(failure!=ES_BRIDGE_NONE&&parent>=0){int fd=parent;parent=-1;if(close(fd))failure=ES_BRIDGE_CLEANUP;}
 pthread_mutex_lock(&invocation.mutex);invocation.parent=parent;invocation.mechanism=record;invocation.self_failure=failure;invocation.self_state=failure==ES_BRIDGE_NONE?2:3;pthread_mutex_unlock(&invocation.mutex);return failure;
}
es_registry_ref es_registry_open(int ordinal,int64_t left,uint64_t entered,char out[ES_LISTENER_PATH_BYTES]){
 memset(out,0,ES_LISTENER_PATH_BYTES);es_registry_ref ref={.failure=ES_BRIDGE_INSTALLATION};
 const es_compiled_chain *record=es_compiled_find_chain(ordinal);
 pthread_mutex_lock(&invocation.mutex);
 if(invocation.self_state!=2||!record||record->ordinal!=ordinal||record->mechanism!=invocation.mechanism->ordinal||record->version!=1||record->width!=1){pthread_mutex_unlock(&invocation.mutex);return ref;}
 if(left<=0||(uint64_t)left>180000000000ULL||(uint64_t)left>record->lifetime){ref.failure=ES_BRIDGE_DEADLINE;pthread_mutex_unlock(&invocation.mutex);return ref;}
 if(invocation.issued==LIMIT||invocation.live==4){ref.failure=ES_BRIDGE_RESOURCE;pthread_mutex_unlock(&invocation.mutex);return ref;}
 unsigned index=invocation.issued++;invocation.live++;es_registry_entry *p=&entries[index];p->refs=1;p->failure=ES_BRIDGE_NONE;p->token=((uint64_t)(index+1)<<9)|(index+1);
 ref.entry=p;ref.token=p->token;int parent=invocation.parent;const char *path=invocation.mechanism->parent_path;pthread_mutex_unlock(&invocation.mutex);
 ref.failure=mapped(es_launch_open_started(&p->owner,parent,path,(uint64_t)left,entered,out));
 if(ref.failure!=ES_BRIDGE_NONE){pthread_mutex_lock(&invocation.mutex);latch(p,ref.failure);pthread_mutex_unlock(&invocation.mutex);(void)es_registry_close(&ref,left>(int64_t)NS10?(int64_t)NS10:left);}
 return ref;
}
es_registry_ref es_registry_acquire(int64_t token){
 es_registry_ref ref={.failure=ES_BRIDGE_PROTOCOL};if(token<=0)return ref;uint64_t value=(uint64_t)token;unsigned number=(unsigned)(value&511U);if(!number||number>LIMIT)return ref;
 pthread_mutex_lock(&invocation.mutex);es_registry_entry *p=&entries[number-1];
 if(number<=invocation.issued&&p->published&&p->token==value){ref.token=value;ref.failure=p->failure;
  if(p->retired){ref.retired=1;ref.complete=p->complete;}else if(p->refs==UINT_MAX){ref.failure=ES_BRIDGE_RESOURCE;}else{p->refs++;ref.entry=p;}}
 pthread_mutex_unlock(&invocation.mutex);return ref;
}
unsigned es_registry_publish(es_registry_ref *ref){
 if(!ref->entry)return 0;
 pthread_mutex_lock(&invocation.mutex);es_registry_entry *p=ref->entry;
 unsigned ok=!p->published&&!p->retired&&!p->settled&&p->failure==ES_BRIDGE_NONE;
 /* JNI construction and waiting for this mutex consume the original startup
    allowance. The initialized owner's clocks are immutable; no owner lock or
    I/O is needed while serializing publication with registry refusal. */
 if(ok){uint64_t n=es_registry_now();if(!n||n>=p->owner.startup_deadline){latch(p,ES_BRIDGE_DEADLINE);ok=0;}}
 if(ok)p->published=1;
 pthread_mutex_unlock(&invocation.mutex);return ok;
}
void es_registry_release(es_registry_ref *ref){
 es_registry_entry *p=ref->entry;if(!p)return;
 es_launch_status s={0};if(p->owner.initialized)(void)es_launch_status_read(&p->owner,&s);
 pthread_mutex_lock(&invocation.mutex);
 if(!p->owner.initialized||s.close_state==ES_LAUNCH_CLOSE_SETTLED){if(!p->finally_owed&&!p->event_active)p->settled=1;if(s.cleanup_inconclusive)p->uncertain=1;}
 if(p->refs)--p->refs;
 if(!p->refs&&p->settled&&!p->finally_owed&&!p->event_active&&!p->retired){p->retired=1;p->complete=!p->uncertain;invocation.live--;}
 pthread_mutex_unlock(&invocation.mutex);ref->entry=NULL;
}
void es_registry_cancel(es_registry_ref *ref){es_registry_entry *p=ref->entry;if(!p)return;(void)es_launch_cancel(&p->owner);pthread_mutex_lock(&invocation.mutex);latch(p,ES_BRIDGE_CANCELLED);pthread_mutex_unlock(&invocation.mutex);}
void es_registry_result_delivered(es_registry_ref *ref){
 if(!ref->entry||!ref->disarm_completed)return;
 pthread_mutex_lock(&invocation.mutex);ref->entry->finally_owed=0;pthread_mutex_unlock(&invocation.mutex);
}
void es_registry_result_failed(es_registry_ref *ref){if(!ref->entry)return;pthread_mutex_lock(&invocation.mutex);latch(ref->entry,ES_BRIDGE_RESOURCE);pthread_mutex_unlock(&invocation.mutex);es_registry_cancel(ref);}
es_bridge_result es_registry_arm(es_registry_ref *ref){
 es_bridge_result out={.failure=ES_BRIDGE_PROTOCOL};es_registry_entry *p=ref->entry;if(!p)return out;
 pthread_mutex_lock(&invocation.mutex);if(p->arm_called||p->settled||p->failure!=ES_BRIDGE_NONE){latch(p,out.failure);pthread_mutex_unlock(&invocation.mutex);return out;}
 p->arm_called=1;p->finally_owed=1;p->launcher=pthread_self();pthread_mutex_unlock(&invocation.mutex);
 out.failure=mapped(es_launch_arm(&p->owner));
 pthread_mutex_lock(&p->owner.mutex);out.no_window=out.failure!=ES_BRIDGE_NONE&&p->owner.arm_done&&p->owner.arm_unowned;pthread_mutex_unlock(&p->owner.mutex);
 pthread_mutex_lock(&invocation.mutex);p->arm_done=1;p->arm_no_window=out.no_window;if(out.failure!=ES_BRIDGE_NONE)latch(p,out.failure);
 pthread_mutex_unlock(&invocation.mutex);return out;
}
es_bridge_result es_registry_register(es_registry_ref *ref,int64_t pid){
 es_bridge_result out={.failure=ES_BRIDGE_PROTOCOL};es_registry_entry *p=ref->entry;if(!p)return out;
 out.failure=mapped(es_launch_register(&p->owner,pid>0?(uint64_t)pid:0));pthread_mutex_lock(&invocation.mutex);if(out.failure!=ES_BRIDGE_NONE)latch(p,out.failure);
 pthread_mutex_unlock(&invocation.mutex);return out;
}
es_bridge_result es_registry_disarm(es_registry_ref *ref){
 es_bridge_result out={.failure=ES_BRIDGE_PROTOCOL};es_registry_entry *p=ref->entry;if(!p)return out;
 pthread_mutex_lock(&invocation.mutex);
 if(!p->arm_done||p->disarm_called||!pthread_equal(p->launcher,pthread_self())){latch(p,out.failure);pthread_mutex_unlock(&invocation.mutex);return out;}
 p->disarm_called=1;unsigned no_window=p->arm_no_window;pthread_mutex_unlock(&invocation.mutex);
 es_launch_result r=es_launch_disarm(&p->owner);out.failure=mapped(r);out.no_window=no_window&&r==ES_LAUNCH_INVALID;
 pthread_mutex_lock(&invocation.mutex);if(out.failure==ES_BRIDGE_NONE||out.no_window)ref->disarm_completed=1;else p->uncertain=1;
 if(out.failure!=ES_BRIDGE_NONE)latch(p,out.failure);
 pthread_mutex_unlock(&invocation.mutex);return out;
}
es_bridge_result es_registry_event(es_registry_ref *ref){
 es_bridge_result out={.failure=ES_BRIDGE_PROTOCOL};if(ref->retired){out.closed=1;return out;}es_registry_entry *p=ref->entry;if(!p)return out;
 pthread_mutex_lock(&invocation.mutex);
 if(p->coordinator_bound&&!pthread_equal(p->coordinator,pthread_self())){latch(p,out.failure);pthread_mutex_unlock(&invocation.mutex);return out;}
 if(!p->coordinator_bound){p->coordinator_bound=1;p->coordinator=pthread_self();}
 if(p->event_active){latch(p,out.failure);pthread_mutex_unlock(&invocation.mutex);return out;}
 if(p->settled){out.closed=1;pthread_mutex_unlock(&invocation.mutex);return out;}
 if(p->event_entered||p->failure!=ES_BRIDGE_NONE){out.failure=p->failure;pthread_mutex_unlock(&invocation.mutex);return out;}
 p->event_entered=1;p->event_active=1;pthread_mutex_unlock(&invocation.mutex);
 es_launch_result r=es_launch_capture(&p->owner);if(r==ES_LAUNCH_OK)r=es_launch_correlate(&p->owner);
 /* No compiled complete mapped/loader strategy exists. Correlation cannot admit. */
 out.failure=r==ES_LAUNCH_ROOT_CORRELATED?ES_BRIDGE_INSTALLATION:mapped(r);
 pthread_mutex_lock(&invocation.mutex);p->event_active=0;latch(p,out.failure);out.failure=p->failure;pthread_mutex_unlock(&invocation.mutex);
 (void)es_launch_cancel(&p->owner);return out;
}
es_bridge_result es_registry_status(es_registry_ref *ref){
 es_bridge_result out={.failure=ref->failure,.closed=ref->retired};es_registry_entry *p=ref->entry;if(!p)return out;
 pthread_mutex_lock(&invocation.mutex);out.failure=p->failure;out.closed=p->settled;pthread_mutex_unlock(&invocation.mutex);return out;
}
unsigned es_registry_close(es_registry_ref *ref,int64_t remaining){
 if(ref->retired)return ref->complete;
 es_registry_entry *p=ref->entry;if(!p)return 0;
 if(!p->owner.initialized){pthread_mutex_lock(&invocation.mutex);p->settled=1;pthread_mutex_unlock(&invocation.mutex);return 1;}
 pthread_mutex_lock(&invocation.mutex);latch(p,ES_BRIDGE_CANCELLED);pthread_mutex_unlock(&invocation.mutex);
 es_launch_cleanup result=es_launch_close(&p->owner,remaining>0?(uint64_t)remaining:0);
 for(;;){pthread_mutex_lock(&invocation.mutex);unsigned owed=p->finally_owed;pthread_mutex_unlock(&invocation.mutex);if(!owed)break;
  uint64_t deadline;pthread_mutex_lock(&p->owner.mutex);deadline=p->owner.cleanup_deadline;pthread_mutex_unlock(&p->owner.mutex);
  uint64_t n=es_registry_now();if(!n||n>=deadline){result=ES_LAUNCH_CLOSED_INCONCLUSIVE;break;}struct timespec delay={0,1000000};(void)nanosleep(&delay,NULL);
 }
 pthread_mutex_lock(&invocation.mutex);if(result!=ES_LAUNCH_CLOSED_COMPLETE)p->uncertain=1;unsigned complete=result==ES_LAUNCH_CLOSED_COMPLETE&&!p->uncertain;pthread_mutex_unlock(&invocation.mutex);return complete;
}

void es_registry_unpublished(es_registry_ref *ref){
 if(!ref->entry)return;
 uint64_t deadline=ref->entry->owner.startup_deadline,n=es_registry_now();
 (void)es_registry_close(ref,deadline>n&&n?(int64_t)(deadline-n):0);
}

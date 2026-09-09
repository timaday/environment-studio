#define _GNU_SOURCE
#include "privacy-launch.h"
#include <errno.h>
#include <limits.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/random.h>
#include <time.h>
#include <unistd.h>
#define NS10 10000000000ULL
#define MAGIC 0x4c415531U
static void wipe(void *v,size_t n){volatile unsigned char *p=v;while(n--)*p++=0;}
static int overlaps(const void *a,size_t an,const void *b,size_t bn){uintptr_t x=(uintptr_t)a,y=(uintptr_t)b;return x<=y?y-x<an:x-y<bn;}
static uint64_t now(void){struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<0||t.tv_nsec<0||t.tv_nsec>=1000000000L)return 0;uint64_t s=(uint64_t)t.tv_sec;return s>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL?0:s*1000000000ULL+(uint64_t)t.tv_nsec;}
static int valid(es_launch *p){return p&&p->initialized==MAGIC;}
static es_launch_result fail(es_launch *p,es_launch_result r){if(p->failure==ES_LAUNCH_OK)p->failure=r;p->phase=ES_LAUNCH_PHASE_FAILED;pthread_cond_broadcast(&p->changed);return p->failure;}
static es_launch_result guard(es_launch *p){
 if(p->failure)return p->failure;
 if(p->cancelled||p->closing)return fail(p,ES_LAUNCH_CANCELLED);
 uint64_t n=now();if(!n||n>=p->startup_deadline)return fail(p,ES_LAUNCH_DEADLINE);
 return ES_LAUNCH_OK;
}
/* All waits use CLOCK_MONOTONIC and bounded polling so a shortened cleanup clock
   wakes old callers even without a condition signal. State mutex is released. */
static void pause_locked(es_launch *p,uint64_t deadline){
 uint64_t n=now();if(!n||n>=deadline)return;
 uint64_t end=deadline-n>1000000ULL?n+1000000ULL:deadline;
 struct timespec t={(time_t)(end/1000000000ULL),(long)(end%1000000000ULL)};
 int r=pthread_cond_timedwait(&p->changed,&p->mutex,&t);
 if(r&&r!=ETIMEDOUT){p->inconclusive=1;fail(p,ES_LAUNCH_IO);}
}
static es_launch_result checkpoint(es_launch *p){pthread_mutex_lock(&p->mutex);es_launch_result r=guard(p);pthread_mutex_unlock(&p->mutex);return r;}
static es_launch_result root_result(es_root_result r){
 switch(r){case ES_ROOT_OK:case ES_ROOT_CAPTURED:case ES_ROOT_REGISTERED:return ES_LAUNCH_OK;
 case ES_ROOT_CORRELATED:return ES_LAUNCH_ROOT_CORRELATED;
 case ES_ROOT_INVALID:return ES_LAUNCH_INVALID;case ES_ROOT_PLATFORM:return ES_LAUNCH_PLATFORM;
 case ES_ROOT_IDENTITY:case ES_ROOT_DEAD:return ES_LAUNCH_IDENTITY;
 case ES_ROOT_PROTOCOL:return ES_LAUNCH_PROTOCOL;case ES_ROOT_DEADLINE:return ES_LAUNCH_DEADLINE;
 case ES_ROOT_CANCELLED:return ES_LAUNCH_CANCELLED;case ES_ROOT_CLEANUP:return ES_LAUNCH_CLEANUP;
 default:return ES_LAUNCH_IO;}
}
static es_launch_result listener_result(es_listener_result r){
 switch(r){case ES_LISTENER_OK:case ES_LISTENER_ACCEPTED:return ES_LAUNCH_OK;
 case ES_LISTENER_INVALID:return ES_LAUNCH_INVALID;case ES_LISTENER_PLATFORM:return ES_LAUNCH_PLATFORM;
 case ES_LISTENER_IDENTITY:return ES_LAUNCH_IDENTITY;case ES_LISTENER_CAPACITY:return ES_LAUNCH_RESOURCE;
 case ES_LISTENER_DEADLINE:return ES_LAUNCH_DEADLINE;case ES_LISTENER_CANCELLED:return ES_LAUNCH_CANCELLED;
 default:return ES_LAUNCH_IO;}
}
static es_launch_result connection_result(es_connection_result r){
 switch(r){case ES_CONNECTION_OK:case ES_CONNECTION_PREPARE_RECEIVED:return ES_LAUNCH_OK;
 case ES_CONNECTION_INVALID:return ES_LAUNCH_INVALID;case ES_CONNECTION_PLATFORM:return ES_LAUNCH_PLATFORM;
 case ES_CONNECTION_IDENTITY:case ES_CONNECTION_DEAD:return ES_LAUNCH_IDENTITY;
 case ES_CONNECTION_PROTOCOL:return ES_LAUNCH_PROTOCOL;case ES_CONNECTION_DEADLINE:return ES_LAUNCH_DEADLINE;
 case ES_CONNECTION_CANCELLED:return ES_LAUNCH_CANCELLED;case ES_CONNECTION_CLEANUP:return ES_LAUNCH_CLEANUP;
 default:return ES_LAUNCH_IO;}
}
static uint64_t cleanup_clock(es_launch *p){pthread_mutex_lock(&p->mutex);uint64_t d=p->cleanup_deadline;pthread_mutex_unlock(&p->mutex);return d;}
static void settle(es_launch *p){
 pthread_mutex_lock(&p->mutex);
 if(p->closing!=ES_LAUNCH_CLOSE_REQUESTED||p->users||p->signals||p->root_active||p->disarm_active||(p->arm_entered&&!p->disarm_done)){
  pthread_mutex_unlock(&p->mutex);return;
 }
 p->closing=ES_LAUNCH_CLOSE_SETTLING;pthread_mutex_unlock(&p->mutex);
 unsigned bad=0;
 if(p->connection.state&&es_connection_close(&p->connection,cleanup_clock(p))!=ES_CONNECTION_CLOSED_COMPLETE)bad=1;
 if(p->root.state&&es_root_close(&p->root,cleanup_clock(p))!=ES_ROOT_CLOSED_COMPLETE)bad=1;
 if(p->listener.state&&es_listener_close(&p->listener,cleanup_clock(p))!=ES_LISTENER_CLOSED_COMPLETE)bad=1;
 pthread_mutex_lock(&p->mutex);int fd=p->cancel_fd;p->cancel_fd=-1;pthread_mutex_unlock(&p->mutex);
 if(fd>=0&&close(fd))bad=1;
 wipe(p->launch_id,sizeof(p->launch_id));
 pthread_mutex_lock(&p->mutex);uint64_t n=now();if(!n||n>=p->cleanup_deadline)bad=1;
 p->inconclusive|=bad;p->closing=ES_LAUNCH_CLOSE_SETTLED;pthread_cond_broadcast(&p->changed);pthread_mutex_unlock(&p->mutex);
}
static es_launch_result leave(es_launch *p,es_launch_result r){
 if(r!=ES_LAUNCH_OK&&r!=ES_LAUNCH_ROOT_CORRELATED)r=fail(p,r);
 --p->users;pthread_cond_broadcast(&p->changed);pthread_mutex_unlock(&p->mutex);settle(p);return r;
}
es_launch_result es_launch_open(es_launch *p,int parent,const char *path,uint64_t left,char out[ES_LISTENER_PATH_BYTES]){
 if(!out)return ES_LAUNCH_INVALID;
 size_t path_size=path?strnlen(path,ES_LISTENER_PATH_BYTES):0;
 if(path&&path_size<ES_LISTENER_PATH_BYTES)++path_size;
 if(!p||!path){
  if((!p||!overlaps(p,sizeof(*p),out,ES_LISTENER_PATH_BYTES))&&(!path||!overlaps(path,path_size,out,ES_LISTENER_PATH_BYTES)))wipe(out,ES_LISTENER_PATH_BYTES);
  return ES_LAUNCH_INVALID;
 }
 if(overlaps(p,sizeof(*p),out,ES_LISTENER_PATH_BYTES)||overlaps(p,sizeof(*p),path,path_size)||overlaps(out,ES_LISTENER_PATH_BYTES,path,path_size))return ES_LAUNCH_INVALID;
 wipe(out,ES_LISTENER_PATH_BYTES);
 if(path_size==ES_LISTENER_PATH_BYTES&&path[path_size-1]!=0)return ES_LAUNCH_INVALID;
 if(p->initialized||parent<0||!left||left>180000000000ULL||strnlen(path,ES_LISTENER_PATH_BYTES)==ES_LISTENER_PATH_BYTES)return ES_LAUNCH_INVALID;
 uint64_t n=now();if(!n||left>UINT64_MAX-n)return ES_LAUNCH_DEADLINE;
 if(pthread_mutex_init(&p->mutex,NULL))return ES_LAUNCH_PLATFORM;
 pthread_condattr_t attr;if(pthread_condattr_init(&attr)){pthread_mutex_destroy(&p->mutex);return ES_LAUNCH_PLATFORM;}
 int error=pthread_condattr_setclock(&attr,CLOCK_MONOTONIC);if(!error)error=pthread_cond_init(&p->changed,&attr);
 pthread_condattr_destroy(&attr);if(error){pthread_mutex_destroy(&p->mutex);return ES_LAUNCH_PLATFORM;}
 p->initialized=MAGIC;p->cancel_fd=-1;p->operation_deadline=n+left;p->startup_deadline=n+(left<NS10?left:NS10);
 p->cancel_fd=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);
 es_launch_result r=p->cancel_fd<0?ES_LAUNCH_RESOURCE:ES_LAUNCH_OK;
 size_t used=0;
 for(unsigned attempts=0;r==ES_LAUNCH_OK&&used<sizeof(p->launch_id)&&attempts<4;attempts++){
  ssize_t got=getrandom(p->launch_id+used,sizeof(p->launch_id)-used,GRND_NONBLOCK);
  if(got>0)used+=(size_t)got;else if(got<0&&errno==EINTR)continue;else r=ES_LAUNCH_RESOURCE;
 }
 if(r==ES_LAUNCH_OK&&used!=sizeof(p->launch_id))r=ES_LAUNCH_RESOURCE;
 if(r==ES_LAUNCH_OK)r=listener_result(es_listener_open(&p->listener,parent,path,p->cancel_fd,p->startup_deadline,1));
 if(r==ES_LAUNCH_OK)r=listener_result(es_listener_path(&p->listener,out));
 pthread_mutex_lock(&p->mutex);if(r==ES_LAUNCH_OK)r=guard(p);else fail(p,r);pthread_mutex_unlock(&p->mutex);
 if(r!=ES_LAUNCH_OK)wipe(out,ES_LISTENER_PATH_BYTES);
 return r;
}
es_launch_result es_launch_arm(es_launch *p){
 if(!valid(p))return ES_LAUNCH_INVALID;
 pthread_mutex_lock(&p->mutex);++p->users;
 es_launch_result r=guard(p);if(r)return leave(p,r);
 if(p->arm_entered)return leave(p,ES_LAUNCH_PROTOCOL);
 p->launcher=pthread_self();p->launcher_bound=1;p->arm_entered=1;p->root_active=1;pthread_mutex_unlock(&p->mutex);
 r=root_result(es_root_arm(&p->root,p->cancel_fd,p->startup_deadline,p->launch_id));
 pthread_mutex_lock(&p->mutex);p->root_active=0;p->arm_done=1;
 if(r==ES_LAUNCH_OK){r=guard(p);if(!r)p->phase=ES_LAUNCH_PHASE_ARMED;}
 return leave(p,r);
}
es_launch_result es_launch_capture(es_launch *p){
 if(!valid(p))return ES_LAUNCH_INVALID;
 pthread_mutex_lock(&p->mutex);++p->users;
 if(p->capture_entered)return leave(p,ES_LAUNCH_PROTOCOL);
 p->capture_entered=1;p->receiver=pthread_self();p->receiver_bound=1;
 es_launch_result r;
 while(!(r=guard(p))&&!p->arm_done)pause_locked(p,p->startup_deadline);
 if(r)return leave(p,r);
 if(p->root_active||p->phase!=ES_LAUNCH_PHASE_ARMED)return leave(p,ES_LAUNCH_PROTOCOL);
 p->root_active=1;pthread_mutex_unlock(&p->mutex);es_peer_identity id={0};r=root_result(es_root_capture(&p->root,&id));wipe(&id,sizeof(id));
 pthread_mutex_lock(&p->mutex);p->root_active=0;p->capture_done=1;
 if(!r){r=guard(p);if(!r)p->phase=ES_LAUNCH_PHASE_CAPTURED;}
 return leave(p,r);
}
es_launch_result es_launch_register(es_launch *p,uint64_t pid){
 if(!valid(p))return ES_LAUNCH_INVALID;
 pthread_mutex_lock(&p->mutex);++p->users;
 if(!p->launcher_bound||!pthread_equal(p->launcher,pthread_self())||p->register_entered)return leave(p,ES_LAUNCH_PROTOCOL);
 p->register_entered=1;es_launch_result r;
 while(!(r=guard(p))&&!p->capture_done)pause_locked(p,p->startup_deadline);
 if(r)return leave(p,r);
 if(p->root_active||p->disarm_entered||p->phase!=ES_LAUNCH_PHASE_CAPTURED)return leave(p,ES_LAUNCH_PROTOCOL);
 p->root_active=1;pthread_mutex_unlock(&p->mutex);r=root_result(es_root_register(&p->root,pid));
 pthread_mutex_lock(&p->mutex);p->root_active=0;p->register_done=1;
 if(!r){r=guard(p);if(!r)p->phase=ES_LAUNCH_PHASE_REGISTERED;}
 return leave(p,r);
}
es_launch_result es_launch_disarm(es_launch *p){
 if(!valid(p))return ES_LAUNCH_INVALID;
 pthread_mutex_lock(&p->mutex);++p->users;
 if(!p->launcher_bound||!pthread_equal(p->launcher,pthread_self())||!p->arm_done||p->disarm_entered)return leave(p,ES_LAUNCH_PROTOCOL);
 p->disarm_entered=1;p->disarm_active=1;pthread_mutex_unlock(&p->mutex);
 es_launch_result r=root_result(es_root_disarm(&p->root));
 pthread_mutex_lock(&p->mutex);p->disarm_active=0;
 if(!r)p->disarm_done=1;else{p->inconclusive=1;fail(p,r);}
 /* Ended-window OK is independent of a prior owner refusal. */
 --p->users;pthread_cond_broadcast(&p->changed);pthread_mutex_unlock(&p->mutex);settle(p);return r;
}
es_launch_result es_launch_correlate(es_launch *p){
 if(!valid(p))return ES_LAUNCH_INVALID;
 pthread_mutex_lock(&p->mutex);++p->users;
 if(!p->receiver_bound||!pthread_equal(p->receiver,pthread_self())||p->correlate_entered)return leave(p,ES_LAUNCH_PROTOCOL);
 p->correlate_entered=1;es_launch_result r;
 while(!(r=guard(p))&&(!p->register_done||!p->disarm_done))pause_locked(p,p->startup_deadline);
 if(r)return leave(p,r);
 if(p->root_active||p->disarm_active||p->phase!=ES_LAUNCH_PHASE_REGISTERED)return leave(p,ES_LAUNCH_PROTOCOL);
 p->root_active=1;pthread_mutex_unlock(&p->mutex);
 uint64_t token=0;es_peer_identity id={0};
 r=listener_result(es_listener_accept(&p->listener,&token));
 if(!r)r=checkpoint(p);
 if(!r)r=connection_result(es_connection_open(&p->connection,&p->listener,token));
 if(!r)r=checkpoint(p);
 if(!r)r=connection_result(es_connection_prepare(&p->connection,&id));
 if(!r)r=checkpoint(p);
 if(!r)r=root_result(es_root_match(&p->root,&p->connection,&id));
 wipe(&id,sizeof(id));pthread_mutex_lock(&p->mutex);p->root_active=0;
 if(r==ES_LAUNCH_ROOT_CORRELATED){es_launch_result g=guard(p);if(g)r=g;else p->phase=ES_LAUNCH_PHASE_CORRELATED;}
 return leave(p,r);
}
es_launch_result es_launch_status_read(es_launch *p,es_launch_status *out){
 if(!out)return ES_LAUNCH_INVALID;
 if(p&&overlaps(p,sizeof(*p),out,sizeof(*out)))return ES_LAUNCH_INVALID;
 wipe(out,sizeof(*out));if(!valid(p))return ES_LAUNCH_INVALID;
 pthread_mutex_lock(&p->mutex);out->phase=p->phase;out->failure=p->failure;out->close_state=p->closing;out->disarm_completed=p->disarm_done;
 out->calls_quiescent=!p->users&&!p->signals&&!p->root_active&&!p->disarm_active&&p->closing!=ES_LAUNCH_CLOSE_SETTLING;
 out->cleanup_inconclusive=p->inconclusive;pthread_mutex_unlock(&p->mutex);return ES_LAUNCH_OK;
}
es_launch_result es_launch_cancel(es_launch *p){
 if(!valid(p))return ES_LAUNCH_INVALID;
 pthread_mutex_lock(&p->mutex);
 p->cancelled=1;if(p->closing!=ES_LAUNCH_CLOSE_SETTLED)fail(p,ES_LAUNCH_CANCELLED);
 int fd=-1;if(p->closing<ES_LAUNCH_CLOSE_SETTLING&&p->cancel_fd>=0){fd=p->cancel_fd;++p->signals;}
 pthread_mutex_unlock(&p->mutex);es_launch_result r=ES_LAUNCH_OK;
 if(fd>=0){uint64_t one=1;ssize_t n=write(fd,&one,sizeof(one));if(n!=(ssize_t)sizeof(one)&&!(n<0&&errno==EAGAIN))r=ES_LAUNCH_IO;
  pthread_mutex_lock(&p->mutex);--p->signals;if(r)fail(p,r);pthread_cond_broadcast(&p->changed);pthread_mutex_unlock(&p->mutex);}
 settle(p);return r;
}
es_launch_cleanup es_launch_close(es_launch *p,uint64_t left){
 if(!valid(p))return ES_LAUNCH_CLOSE_INVALID;
 pthread_mutex_lock(&p->mutex);
 if(p->closing==ES_LAUNCH_CLOSE_SETTLED){es_launch_cleanup r=p->inconclusive?ES_LAUNCH_CLOSED_INCONCLUSIVE:ES_LAUNCH_CLOSED_COMPLETE;pthread_mutex_unlock(&p->mutex);return r;}
 uint64_t n=now();int ok=n&&left&&left<=NS10&&left<=UINT64_MAX-n;uint64_t d=ok?n+left:n;
 if(!ok)p->inconclusive=1;
 if(p->closing==ES_LAUNCH_CLOSE_NONE){p->closing=ES_LAUNCH_CLOSE_REQUESTED;p->cleanup_deadline=d;}else if(d<p->cleanup_deadline)p->cleanup_deadline=d;
 pthread_cond_broadcast(&p->changed);pthread_mutex_unlock(&p->mutex);
 (void)es_launch_cancel(p);settle(p);
 pthread_mutex_lock(&p->mutex);
 while(p->closing!=ES_LAUNCH_CLOSE_SETTLED){n=now();if(!n||n>=p->cleanup_deadline){p->inconclusive=1;break;}pause_locked(p,p->cleanup_deadline);}
 es_launch_cleanup r=p->closing==ES_LAUNCH_CLOSE_SETTLED&&!p->inconclusive?ES_LAUNCH_CLOSED_COMPLETE:ES_LAUNCH_CLOSED_INCONCLUSIVE;
 pthread_mutex_unlock(&p->mutex);return r;
}

static es_launch_result maps_result(es_maps_result result){
 switch(result){
 case ES_MAPS_OK:return ES_LAUNCH_OK;case ES_MAPS_INVALID:return ES_LAUNCH_INVALID;
 case ES_MAPS_PLATFORM:return ES_LAUNCH_PLATFORM;case ES_MAPS_RESOURCE:return ES_LAUNCH_RESOURCE;
 case ES_MAPS_IDENTITY:return ES_LAUNCH_IDENTITY;case ES_MAPS_FORMAT:return ES_LAUNCH_PROTOCOL;
 case ES_MAPS_DEADLINE:return ES_LAUNCH_DEADLINE;case ES_MAPS_CANCELLED:return ES_LAUNCH_CANCELLED;
 case ES_MAPS_CLEANUP:return ES_LAUNCH_CLEANUP;default:return ES_LAUNCH_IO;
 }
}
es_launch_result es_launch_maps(es_launch *p,es_maps_snapshot *out){
 if(!out)return ES_LAUNCH_INVALID;
 if(p&&overlaps(p,sizeof(*p),out,sizeof(*out)))return ES_LAUNCH_INVALID;
 wipe(out,sizeof(*out));if(!valid(p))return ES_LAUNCH_INVALID;
 pthread_mutex_lock(&p->mutex);++p->users;
 es_launch_result result=guard(p);
 if(result)return leave(p,result);
 if(!p->receiver_bound||!pthread_equal(p->receiver,pthread_self())||p->maps_entered
    ||p->phase!=ES_LAUNCH_PHASE_CORRELATED||p->root_active||p->disarm_active)return leave(p,ES_LAUNCH_PROTOCOL);
 p->maps_entered=1;p->root_active=1;pthread_mutex_unlock(&p->mutex);
 result=maps_result(es_maps_sample(&p->connection.peer,out));
 pthread_mutex_lock(&p->mutex);p->root_active=0;
 if(result==ES_LAUNCH_CLEANUP)p->inconclusive=1;
 if(!result)result=guard(p);
 if(result)wipe(out,sizeof(*out));
 return leave(p,result);
}

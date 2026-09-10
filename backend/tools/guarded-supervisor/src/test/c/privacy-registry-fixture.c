#define _GNU_SOURCE
/* Same production registry translation unit, included only to observe bounded
   retained bookkeeping. No production test API or policy setter is exported. */
#include "../../main/c/privacy-registry.c"
#include <jni.h>
#include <stdio.h>
static es_launch held_owner;
static pthread_t holder;
static pthread_mutex_t fixture_lock=PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t fixture_changed=PTHREAD_COND_INITIALIZER;
static int hold_ready,hold_release,hold_failed,disarm_fault;
static void *hold_window(void *unused){
 (void)unused;char path[ES_LISTENER_PATH_BYTES];
 es_launch_result r=es_launch_open(&held_owner,invocation.parent,invocation.mechanism->parent_path,5000000000ULL,path);
 if(r==ES_LAUNCH_OK)r=es_launch_arm(&held_owner);
 pthread_mutex_lock(&fixture_lock);hold_failed=r!=ES_LAUNCH_OK;hold_ready=1;pthread_cond_broadcast(&fixture_changed);
 while(!hold_release)pthread_cond_wait(&fixture_changed,&fixture_lock);
 pthread_mutex_unlock(&fixture_lock);
 if(r==ES_LAUNCH_OK&&es_launch_disarm(&held_owner)!=ES_LAUNCH_OK)hold_failed=1;
 if(es_launch_close(&held_owner,1000000000ULL)!=ES_LAUNCH_CLOSED_COMPLETE)hold_failed=1;
 return NULL;
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_hold(JNIEnv *e,jclass c,jboolean start){
 (void)e;(void)c;if(start){if(pthread_create(&holder,NULL,hold_window,NULL))return 1;pthread_mutex_lock(&fixture_lock);while(!hold_ready)pthread_cond_wait(&fixture_changed,&fixture_lock);pthread_mutex_unlock(&fixture_lock);return hold_failed;}
 pthread_mutex_lock(&fixture_lock);hold_release=1;pthread_cond_broadcast(&fixture_changed);pthread_mutex_unlock(&fixture_lock);if(pthread_join(holder,NULL))return 1;return hold_failed;
}
es_root_result __real_es_root_disarm(es_root *);
es_root_result __wrap_es_root_disarm(es_root *p){return disarm_fault?ES_ROOT_CLEANUP:__real_es_root_disarm(p);}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_disarmFault(JNIEnv *e,jclass c){(void)e;(void)c;disarm_fault=1;}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_metrics(JNIEnv *e,jclass c){
 (void)e;(void)c;unsigned refs=0,live=0,retired=0,bad=0;
 pthread_mutex_lock(&invocation.mutex);for(unsigned i=0;i<invocation.issued;i++){es_registry_entry *p=&entries[i];refs+=p->refs;if(p->retired){retired++;if(p->complete&&p->owner.initialized){es_launch *o=&p->owner;
   if(o->cancel_fd>=0||p->finally_owed||p->event_active||o->users||o->signals)bad=1;
   if(o->listener.state&&(o->listener.directory_fd>=0||o->listener.socket_path_fd>=0||o->listener.listen_fd>=0||o->listener.live))bad=1;
   for(unsigned j=0;j<o->listener.accepted;j++)if(o->listener.peers[j].fd>=0)bad=1;
   if(o->root.fork.state&&(o->root.fork.receive_fd>=0||o->root.fork.send_fd>=0||atomic_load(&o->root.fork.armed)))bad=1;
   if(o->root.fork.peer.state&&o->root.fork.peer.pidfd>=0)bad=1;
   if(o->connection.peer.state&&o->connection.peer.pidfd>=0)bad=1;
   if(o->connection.wire.state&&o->connection.wire.fd>=0)bad=1;
  }}else live++;}
 unsigned issued=invocation.issued;pthread_mutex_unlock(&invocation.mutex);
 printf("OWNER_BYTES=%zu REGISTRY_BYTES=%zu ISSUED=%u LIVE=%u RETIRED=%u REFERENCES=%u\n",sizeof(es_registry_entry),sizeof(invocation)+sizeof(entries),issued,live,retired,refs);
 return bad||refs||live;
}
#include <stdarg.h>
static __thread const struct JNINativeInterface_ *original_jni;
static struct JNINativeInterface_ fault_jni;
static __thread int allocation_fault,expire_root;
static __thread jobject refusal_throwable;
static int refusal_expected,refusal_injected;
static JavaVM *event_vm;
static int event_fault,event_injected;
static int allocation_held,allocation_released;
static uint64_t held_startup,held_operation;
static int held_descriptors[4];
static jobject faulty_object(JNIEnv *e,jclass type,jmethodID constructor,...){
 int fail_refusal=0;
 if(allocation_fault){int mode=allocation_fault;allocation_fault=0;*e=original_jni;
  if(mode==3||mode==4){pthread_mutex_lock(&fixture_lock);allocation_held=1;pthread_cond_broadcast(&fixture_changed);while(!allocation_released)pthread_cond_wait(&fixture_changed,&fixture_lock);pthread_mutex_unlock(&fixture_lock);fail_refusal=mode==4;}
  else if(mode==5){
   /* Fail only the refusal construction, after the production publication gate
      latched expiry. The reference must still protect the unpublished owner. */
   jclass refused=(*e)->FindClass(e,"studio/environment/supervisor/PrivacyBridge$OpenFailed");
   int exact=refused&&(*e)->IsSameObject(e,type,refused);if(refused)(*e)->DeleteLocalRef(e,refused);
   pthread_mutex_lock(&invocation.mutex);es_registry_entry *p=&entries[invocation.issued-1];
   exact=exact&&p->refs==1&&!p->published&&!p->retired&&p->failure==ES_BRIDGE_DEADLINE;
   pthread_mutex_unlock(&invocation.mutex);
   if(exact&&refusal_throwable&&(*e)->Throw(e,refusal_throwable)==JNI_OK)refusal_injected=1;
   if(refusal_throwable)(*e)->DeleteGlobalRef(e,refusal_throwable);
   refusal_throwable=NULL;return NULL;
  }
  else{jclass oom=(*e)->FindClass(e,"java/lang/OutOfMemoryError");if(oom){
    int thrown=(*e)->ThrowNew(e,oom,"invented allocation fault");(*e)->DeleteLocalRef(e,oom);
    if(mode==6&&thrown==JNI_OK){pthread_mutex_lock(&fixture_lock);event_injected=1;pthread_mutex_unlock(&fixture_lock);}
   }return NULL;}
 }
 va_list args;va_start(args,constructor);jobject result=original_jni->NewObjectV(e,type,constructor,args);va_end(args);
 if(fail_refusal){allocation_fault=5;*e=&fault_jni;}
 return result;
}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_allocationFault(JNIEnv *e,jclass c,jint mode){
 (void)c;original_jni=*e;fault_jni=**e;fault_jni.NewObject=faulty_object;allocation_fault=mode;expire_root=mode==2;*e=&fault_jni;
}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_refusalAllocationFault(JNIEnv *e,jclass c,jthrowable throwable){
 refusal_expected=1;refusal_throwable=(*e)->NewGlobalRef(e,throwable);
 if(refusal_throwable)Java_studio_environment_supervisor_PrivacyBridgeProbe_allocationFault(e,c,4);
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_eventAllocationFault(JNIEnv *e,jclass c){
 (void)c;JavaVM *vm=NULL;if((*e)->GetJavaVM(e,&vm)!=JNI_OK)return 1;
 pthread_mutex_lock(&fixture_lock);event_vm=vm;event_fault=1;pthread_mutex_unlock(&fixture_lock);return 0;
}
es_bridge_result __real_es_registry_event(es_registry_ref *);
es_bridge_result __wrap_es_registry_event(es_registry_ref *ref){
 /* Run the real capture/correlation first. Only JNI construction on its
    original Java coordinator thread is faulted; no event is fabricated. */
 es_bridge_result result=__real_es_registry_event(ref);
 pthread_mutex_lock(&fixture_lock);int inject=event_fault&&result.failure==ES_BRIDGE_INSTALLATION&&!result.closed;
 JavaVM *vm=event_vm;if(inject)event_fault=0;pthread_mutex_unlock(&fixture_lock);
 if(inject&&vm){JNIEnv *e=NULL;if((*vm)->GetEnv(vm,(void**)&e,JNI_VERSION_1_8)==JNI_OK)
   Java_studio_environment_supervisor_PrivacyBridgeProbe_allocationFault(e,NULL,6);}
 return result;
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_eventAllocationObserved(JNIEnv *e,jclass c,jlong token){
 (void)e;(void)c;unsigned number=(unsigned)((uint64_t)token&511U);if(!number||number>LIMIT)return 1;
 pthread_mutex_lock(&fixture_lock);int bad=!event_injected||event_fault;pthread_mutex_unlock(&fixture_lock);
 pthread_mutex_lock(&invocation.mutex);es_registry_entry *p=&entries[number-1];
 bad=bad||p->token!=(uint64_t)token||!p->published||p->failure!=ES_BRIDGE_INSTALLATION||p->refs
  ||!p->event_entered||p->event_active||p->finally_owed;
 pthread_mutex_unlock(&invocation.mutex);return bad;
}
es_root_result __real_es_root_arm(es_root *,int,uint64_t,const uint8_t *);
es_root_result __wrap_es_root_arm(es_root *p,int cancel,uint64_t deadline,const uint8_t *id){if(expire_root){expire_root=0;deadline=es_registry_now()-1;}return __real_es_root_arm(p,cancel,deadline,id);}
JNIEXPORT jlong JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_heldToken(JNIEnv *e,jclass c){
 (void)e;(void)c;pthread_mutex_lock(&fixture_lock);while(!allocation_held)pthread_cond_wait(&fixture_changed,&fixture_lock);pthread_mutex_unlock(&fixture_lock);
 pthread_mutex_lock(&invocation.mutex);es_registry_entry *p=&entries[invocation.issued-1];uint64_t token=p->token;
 held_startup=p->owner.startup_deadline;held_operation=p->owner.operation_deadline;
 held_descriptors[0]=p->owner.cancel_fd;held_descriptors[1]=p->owner.listener.directory_fd;
 held_descriptors[2]=p->owner.listener.socket_path_fd;held_descriptors[3]=p->owner.listener.listen_fd;
 pthread_mutex_unlock(&invocation.mutex);return (jlong)token;
}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_releaseAllocation(JNIEnv *e,jclass c){(void)e;(void)c;pthread_mutex_lock(&fixture_lock);allocation_released=1;pthread_cond_broadcast(&fixture_changed);pthread_mutex_unlock(&fixture_lock);}
#include <errno.h>
/* Independently invented native-construction schedules. Underlying syscalls stay
   real; only their construction completion/failure boundary is perturbed. */
#include <sys/random.h>
static int open_fault,open_expired,open_observed,open_descriptors[4],open_fd_count;
static uint64_t open_startup,open_operation,open_released,open_cleanup_left;
static unsigned open_close_calls;
static void native_open_boundary(void){
 es_launch *p=&entries[invocation.issued-1].owner;
 open_startup=p->startup_deadline;open_operation=p->operation_deadline;open_fd_count=0;
 if(p->cancel_fd>=0)open_descriptors[open_fd_count++]=p->cancel_fd;
 if(p->listener.state){int fds[]={p->listener.directory_fd,p->listener.socket_path_fd,p->listener.listen_fd};
  for(unsigned i=0;i<3;i++)if(fds[i]>=0)open_descriptors[open_fd_count++]=fds[i];}
 uint64_t end=open_expired?open_startup+150000000ULL:es_registry_now()+20000000ULL;
 struct timespec until={(time_t)(end/1000000000ULL),(long)(end%1000000000ULL)};
 int status;do{status=clock_nanosleep(CLOCK_MONOTONIC,TIMER_ABSTIME,&until,NULL);}while(status==EINTR);
 if(status)abort();
 open_released=es_registry_now();open_observed=1;
}
es_listener_result __real_es_listener_path(es_listener *,char *);
es_listener_result __wrap_es_listener_path(es_listener *p,char *out){
 es_listener_result r=__real_es_listener_path(p,out);if(open_fault==1&&r==ES_LISTENER_OK){open_fault=0;native_open_boundary();}return r;
}
es_listener_result __real_es_listener_open(es_listener *,int,const char *,int,uint64_t,unsigned);
es_listener_result __wrap_es_listener_open(es_listener *p,int fd,const char *path,int cancel,uint64_t deadline,unsigned width){
 es_listener_result r=__real_es_listener_open(p,fd,path,cancel,deadline,width);
 if(open_fault==3&&r==ES_LISTENER_OK){open_fault=0;native_open_boundary();return ES_LISTENER_IO;}return r;
}
ssize_t __real_getrandom(void *,size_t,unsigned);
ssize_t __wrap_getrandom(void *p,size_t n,unsigned flags){
 ssize_t r=__real_getrandom(p,n,flags);if(open_fault==2){open_fault=0;native_open_boundary();errno=EIO;return -1;}return r;
}
int __real_eventfd(unsigned,int);
int __wrap_eventfd(unsigned value,int flags){
 if(open_fault==4){open_fault=0;int fd=__real_eventfd(value,flags);if(fd<0||close(fd))abort();native_open_boundary();errno=EMFILE;return -1;}
 return __real_eventfd(value,flags);
}
es_launch_cleanup __real_es_launch_close(es_launch *,uint64_t);
es_launch_cleanup __wrap_es_launch_close(es_launch *p,uint64_t left){
 if(open_observed&&p==&entries[0].owner&&open_close_calls++==0)open_cleanup_left=left;
 return __real_es_launch_close(p,left);
}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_nativeOpenFault(JNIEnv *e,jclass c,jint mode,jboolean expired){
 (void)e;(void)c;open_fault=mode;open_expired=expired;
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_nativeOpenOutcome(JNIEnv *e,jclass c,jint fault){
 (void)e;(void)c;pthread_mutex_lock(&invocation.mutex);es_registry_entry *p=&entries[0];es_launch *o=&p->owner;
 if(!fault){int bad=invocation.issued!=1||invocation.live||!p->retired||!p->settled||p->refs||p->published||p->uncertain||!p->complete
   ||p->failure!=ES_BRIDGE_DEADLINE||o->initialized||o->startup_deadline||o->operation_deadline;
  const unsigned char *bytes=(const unsigned char*)o;for(size_t i=0;i<sizeof(*o);i++)if(bytes[i])bad=1;
  pthread_mutex_unlock(&invocation.mutex);return bad;}
 es_bridge_failure expected=fault==1?ES_BRIDGE_DEADLINE:fault==3?ES_BRIDGE_PLATFORM:ES_BRIDGE_RESOURCE;
 es_launch_result original=fault==1?ES_LAUNCH_DEADLINE:fault==3?ES_LAUNCH_IO:ES_LAUNCH_RESOURCE;
 int bad=!open_observed||p->published||!p->retired||!p->settled||p->refs||p->finally_owed||p->event_active||invocation.live
  ||p->failure!=expected||o->startup_deadline!=open_startup||o->operation_deadline!=open_operation
  ||o->failure!=original||o->closing!=ES_LAUNCH_CLOSE_SETTLED||!open_close_calls
  ||(o->listener.state&&o->listener.startup_deadline_ns!=open_startup);
 if(open_expired)bad|=open_cleanup_left!=0||!p->uncertain||p->complete||!o->inconclusive||o->cleanup_deadline<open_released;
 else bad|=!open_cleanup_left||open_released>=open_startup||open_cleanup_left>open_startup-open_released||p->uncertain||!p->complete||o->inconclusive;
 uint64_t excess=o->cleanup_deadline>open_startup?o->cleanup_deadline-open_startup:0;
 printf("NATIVE_OPEN_REMAINING=%llu CLEANUP_AFTER_STARTUP=%llu COMPLETE=%u UNCERTAIN=%u\n",(unsigned long long)open_cleanup_left,(unsigned long long)excess,p->complete,p->uncertain);
 pthread_mutex_unlock(&invocation.mutex);
 for(int i=0;i<open_fd_count;i++){errno=0;if(fcntl(open_descriptors[i],F_GETFD)!=-1||errno!=EBADF)bad=1;}
 return bad;
}
/* Read-only observation of the reserved entry after the original open caller
   joined. Production token lookup must never expose this unpublished owner. */
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_unpublishedOutcome(JNIEnv *e,jclass c,jlong token){
 (void)e;(void)c;unsigned number=(unsigned)((uint64_t)token&511U);if(!number||number>LIMIT)return 1;
 pthread_mutex_lock(&invocation.mutex);es_registry_entry *p=&entries[number-1];
 int bad=(refusal_expected&&!refusal_injected)||p->token!=(uint64_t)token||p->published||!p->retired||!p->settled||p->refs||p->complete||!p->uncertain
  ||p->failure!=ES_BRIDGE_DEADLINE||p->finally_owed||p->event_active
  ||p->owner.closing!=ES_LAUNCH_CLOSE_SETTLED||!p->owner.inconclusive
  ||p->owner.startup_deadline!=held_startup||p->owner.operation_deadline!=held_operation
  ||p->owner.listener.startup_deadline_ns!=held_startup;
 pthread_mutex_unlock(&invocation.mutex);
 for(unsigned i=0;i<4;i++){errno=0;if(held_descriptors[i]<0||fcntl(held_descriptors[i],F_GETFD)!=-1||errno!=EBADF)bad=1;}
 return bad;
}
#include <sys/prctl.h>
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_controls(JNIEnv *e,jclass c){
 (void)e;(void)c;if(prctl(PR_GET_DUMPABLE,0,0,0,0)!=0)return 1;
 errno=0;if(prctl(PR_SET_DUMPABLE,1,0,0,0)!=-1||errno!=EPERM)return 2;
 errno=0;if(prctl(PR_SET_DUMPABLE,2,0,0,0)!=-1||errno!=EPERM)return 3;return 0;
}
static int delay_record;
const es_compiled_chain *__real_es_compiled_find_chain(int);
const es_compiled_chain *__wrap_es_compiled_find_chain(int ordinal){if(delay_record){delay_record=0;struct timespec pause={0,50000000};(void)nanosleep(&pause,NULL);}return __real_es_compiled_find_chain(ordinal);}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_delayRecord(JNIEnv *e,jclass c){(void)e;(void)c;delay_record=1;}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_clocks(JNIEnv *e,jclass c,jlong token){
 (void)e;(void)c;es_registry_ref ref=es_registry_acquire(token);if(!ref.entry)return 1;es_launch *p=&ref.entry->owner;
 uint64_t n=es_registry_now();int bad=p->operation_deadline-p->startup_deadline!=2000000000ULL||p->startup_deadline>n+9960000000ULL
  ||p->listener.startup_deadline_ns!=p->startup_deadline;es_registry_release(&ref);return bad;
}
static int reference_held,reference_released;
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_holdReference(JNIEnv *e,jclass c,jlong token){
 (void)e;(void)c;es_registry_ref ref=es_registry_acquire(token);pthread_mutex_lock(&fixture_lock);reference_held=ref.entry!=NULL;pthread_cond_broadcast(&fixture_changed);
 while(!reference_released)pthread_cond_wait(&fixture_changed,&fixture_lock);
 pthread_mutex_unlock(&fixture_lock);es_registry_release(&ref);
}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_referenceControl(JNIEnv *e,jclass c,jboolean release){
 (void)e;(void)c;pthread_mutex_lock(&fixture_lock);if(release){reference_released=1;pthread_cond_broadcast(&fixture_changed);}else while(!reference_held)pthread_cond_wait(&fixture_changed,&fixture_lock);pthread_mutex_unlock(&fixture_lock);
}
static int fail_close;
int __real_close(int);
int __wrap_close(int fd){int result=__real_close(fd);if(fail_close){fail_close=0;errno=EINTR;return -1;}return result;}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_closeFault(JNIEnv *e,jclass c){(void)e;(void)c;fail_close=1;}

/* Reoccupy the exact retired cancellation descriptor with a real eventfd. Late
   JNI control calls must leave that independent object open and unsignalled. */
#include <sys/eventfd.h>
static int reused_fd=-1;
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_reuseDescriptor(JNIEnv *e,jclass c,jlong token,jint stage){
 (void)e;(void)c;
 if(stage==0){es_registry_ref ref=es_registry_acquire(token);if(!ref.entry)return 1;reused_fd=ref.entry->owner.cancel_fd;es_registry_release(&ref);return reused_fd<0;}
 if(stage==1){if(fcntl(reused_fd,F_GETFD)!=-1||errno!=EBADF)return 2;int fresh=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);if(fresh<0)return 3;
  if(fresh!=reused_fd){if(dup3(fresh,reused_fd,O_CLOEXEC)<0){close(fresh);return 4;}if(close(fresh))return 5;}return 0;}
 uint64_t value;errno=0;int bad=read(reused_fd,&value,sizeof(value))!=-1||errno!=EAGAIN||fcntl(reused_fd,F_GETFD)<0;
 if(close(reused_fd))bad=1;
 reused_fd=-1;return bad;
}

JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_awaitCoordinator(JNIEnv *e,jclass c,jlong token){
 (void)e;(void)c;es_registry_ref ref=es_registry_acquire(token);if(!ref.entry)return 1;uint64_t end=es_registry_now()+1000000000ULL;unsigned active=0;
 do {pthread_mutex_lock(&invocation.mutex);active=ref.entry->event_active;pthread_mutex_unlock(&invocation.mutex);if(active)break;struct timespec delay={0,1000000};(void)nanosleep(&delay,NULL);}while(es_registry_now()<end);
 es_registry_release(&ref);return !active;
}

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
 pthread_mutex_lock(&invocation.mutex);for(unsigned i=0;i<invocation.issued;i++){es_registry_entry *p=&entries[i];refs+=p->refs;if(p->retired){retired++;if(p->complete){es_launch *o=&p->owner;
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
static int allocation_held,allocation_released;
static uint64_t held_startup,held_operation;
static int held_descriptors[4];
static jobject faulty_object(JNIEnv *e,jclass type,jmethodID constructor,...){
 if(allocation_fault){int mode=allocation_fault;allocation_fault=0;*e=original_jni;
  if(mode==3){pthread_mutex_lock(&fixture_lock);allocation_held=1;pthread_cond_broadcast(&fixture_changed);while(!allocation_released)pthread_cond_wait(&fixture_changed,&fixture_lock);pthread_mutex_unlock(&fixture_lock);}
  else{jclass oom=(*e)->FindClass(e,"java/lang/OutOfMemoryError");if(oom){(*e)->ThrowNew(e,oom,"invented allocation fault");(*e)->DeleteLocalRef(e,oom);}return NULL;}
 }
 va_list args;va_start(args,constructor);jobject result=original_jni->NewObjectV(e,type,constructor,args);va_end(args);return result;
}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_allocationFault(JNIEnv *e,jclass c,jint mode){
 (void)c;original_jni=*e;fault_jni=**e;fault_jni.NewObject=faulty_object;allocation_fault=mode;expire_root=mode==2;*e=&fault_jni;
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
/* Read-only observation of the reserved entry after the original open caller
   joined. Production token lookup must never expose this unpublished owner. */
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyBridgeProbe_unpublishedOutcome(JNIEnv *e,jclass c,jlong token){
 (void)e;(void)c;unsigned number=(unsigned)((uint64_t)token&511U);if(!number||number>LIMIT)return 1;
 pthread_mutex_lock(&invocation.mutex);es_registry_entry *p=&entries[number-1];
 int bad=p->token!=(uint64_t)token||p->published||!p->retired||!p->settled||p->refs||p->complete||!p->uncertain
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

#define _GNU_SOURCE
#include "privacy-root.h"
#include <limits.h>
#include <stddef.h>
#include <string.h>
#include <time.h>
static void wipe(void *v,size_t n){volatile unsigned char *p=v;while(n--)*p++=0;}
static int overlap(const void *a,size_t an,const void *b,size_t bn){
 uintptr_t x=(uintptr_t)a,y=(uintptr_t)b;return x<=y?y-x<an:x-y<bn;
}
static es_root_result fork_result(es_fork_result r){
 switch(r){case ES_FORK_OK:return ES_ROOT_OK;case ES_FORK_CAPTURED:return ES_ROOT_CAPTURED;
 case ES_FORK_INVALID:return ES_ROOT_INVALID;case ES_FORK_UNSUPPORTED:return ES_ROOT_PLATFORM;
 case ES_FORK_PROTOCOL:return ES_ROOT_PROTOCOL;case ES_FORK_DEAD:return ES_ROOT_DEAD;
 case ES_FORK_IDENTITY:return ES_ROOT_IDENTITY;case ES_FORK_DEADLINE:return ES_ROOT_DEADLINE;
 case ES_FORK_CANCELLED:return ES_ROOT_CANCELLED;case ES_FORK_CLEANUP:return ES_ROOT_CLEANUP;
 default:return ES_ROOT_IO;}
}
static es_root_result connection_result(es_connection_result r){
 switch(r){case ES_CONNECTION_PREPARE_RECEIVED:return ES_ROOT_OK;case ES_CONNECTION_INVALID:return ES_ROOT_INVALID;
 case ES_CONNECTION_PLATFORM:return ES_ROOT_PLATFORM;case ES_CONNECTION_IDENTITY:return ES_ROOT_IDENTITY;
 case ES_CONNECTION_DEAD:return ES_ROOT_DEAD;case ES_CONNECTION_PROTOCOL:return ES_ROOT_PROTOCOL;
 case ES_CONNECTION_DEADLINE:return ES_ROOT_DEADLINE;case ES_CONNECTION_CANCELLED:return ES_ROOT_CANCELLED;
 case ES_CONNECTION_CLEANUP:return ES_ROOT_CLEANUP;default:return ES_ROOT_IO;}
}
static es_root_result fail(es_root *p,es_root_result r){if(p->terminal==ES_ROOT_OK)p->terminal=r;return p->terminal;}
static es_root_result guard(es_root *p){
 if(!p||!p->state)return ES_ROOT_INVALID;
 unsigned async=atomic_load_explicit(&p->disarm_failure,memory_order_acquire);
 if(async)fail(p,(es_root_result)async);
 if(p->terminal)return p->terminal;
 if(p->state==5||p->cleanup_started)return fail(p,ES_ROOT_INVALID);
 if(p->generation!=p->fork.generation||p->deadline_ns!=p->fork.deadline_ns||p->cancel_fd!=p->fork.cancel_fd)return fail(p,ES_ROOT_INVALID);
 return ES_ROOT_OK;
}
static int output(es_root *p,es_connection *c,es_peer_identity *out){
 if(!out)return 0;
 if((p&&overlap(out,sizeof(*out),p,sizeof(*p)))||(c&&overlap(out,sizeof(*out),c,sizeof(*c))))return 0;
 if(c&&c->listener&&overlap(out,sizeof(*out),c->listener,sizeof(*c->listener)))return 0;
 wipe(out,sizeof(*out));return 1;
}
es_root_result es_root_arm(es_root *p,int cancel,uint64_t deadline,const uint8_t id[16]){
 if(!p)return ES_ROOT_INVALID;
 if(p->state)return p->state==5?ES_ROOT_INVALID:fail(p,ES_ROOT_INVALID);
 if(!id||cancel<0)return ES_ROOT_INVALID;
 if(overlap(id,16,p,sizeof(*p)))return ES_ROOT_INVALID;
 p->state=1;p->launcher=pthread_self();p->deadline_ns=deadline;p->cancel_fd=cancel;
 atomic_init(&p->disarmed,0);atomic_init(&p->disarm_failure,0);
 if(!atomic_is_lock_free(&p->disarmed)||!atomic_is_lock_free(&p->disarm_failure)){p->arm_failed_unowned=1;return fail(p,ES_ROOT_PLATFORM);}
 es_root_result r=fork_result(es_fork_arm(&p->fork,cancel,deadline,id));p->generation=p->fork.generation;
 /* Fork arm initializes state/generation immediately after acquiring its window.
    Only its completed pre-initialization INVALID refusal proves no ownership. */
 p->arm_failed_unowned=r==ES_ROOT_INVALID&&!p->fork.state&&!p->fork.generation;
 p->arm_failed_ended=r!=ES_ROOT_OK&&p->generation&&atomic_load_explicit(&p->fork.armed,memory_order_acquire)==0;
 return r==ES_ROOT_OK?r:fail(p,r);
}
es_root_result es_root_capture(es_root *p,es_peer_identity *out){
 int valid=output(p,NULL,out);es_root_result r=guard(p);if(r)return r;
 if(!valid||p->state!=1)return fail(p,ES_ROOT_INVALID);
 es_peer_identity identity={0};r=fork_result(es_fork_capture(&p->fork,&identity));
 if(r!=ES_ROOT_CAPTURED){wipe(&identity,sizeof(identity));return fail(p,r);}
 r=guard(p);if(r){wipe(&identity,sizeof(identity));return r;}
 p->state=2;*out=identity;wipe(&identity,sizeof(identity));return ES_ROOT_CAPTURED;
}
es_root_result es_root_register(es_root *p,uint64_t pid){
 es_root_result r=guard(p);if(r)return r;
 if(p->state!=2||!pthread_equal(p->launcher,pthread_self())||atomic_load_explicit(&p->disarmed,memory_order_acquire))return fail(p,ES_ROOT_INVALID);
 if(!pid||pid>INT_MAX)return fail(p,ES_ROOT_IDENTITY);
 es_peer_identity identity={0};r=fork_result(es_fork_read(&p->fork,&identity));
 if(r!=ES_ROOT_CAPTURED){wipe(&identity,sizeof(identity));return fail(p,r);}
 int equal=pid==identity.pid;wipe(&identity,sizeof(identity));if(!equal)return fail(p,ES_ROOT_IDENTITY);
 p->state=3;return ES_ROOT_REGISTERED;
}
static void disarm_fail(es_root *p,es_root_result r){
 unsigned expected=0;if(r==ES_ROOT_CLEANUP)atomic_store_explicit(&p->disarm_failure,r,memory_order_release);
 else (void)atomic_compare_exchange_strong_explicit(&p->disarm_failure,&expected,r,memory_order_release,memory_order_relaxed);
}
es_root_result es_root_disarm(es_root *p){
 /* No receiver-owned state/terminal access: this path may overlap capture. */
 if(!p||!p->generation)return ES_ROOT_INVALID;
 if(!pthread_equal(p->launcher,pthread_self())){
  disarm_fail(p,ES_ROOT_INVALID);
  return ES_ROOT_INVALID;
 }
 if(p->generation!=p->fork.generation||atomic_load_explicit(&p->disarmed,memory_order_acquire)){disarm_fail(p,ES_ROOT_INVALID);return ES_ROOT_INVALID;}
 es_root_result r=p->arm_failed_ended?ES_ROOT_OK:fork_result(es_fork_disarm(&p->fork));
 if(r==ES_ROOT_OK)atomic_store_explicit(&p->disarmed,1,memory_order_release);
 else disarm_fail(p,r);
 return r;
}
static int equal(es_peer_identity a,es_peer_identity b){return a.pid&&a.start_ticks&&a.pid==b.pid&&a.start_ticks==b.start_ticks&&a.uid==b.uid&&a.gid==b.gid;}
es_root_result es_root_match(es_root *p,es_connection *c,es_peer_identity *out){
 int valid=output(p,c,out);es_root_result r=guard(p);if(r)return r;
 if(!valid||!c||p->state!=3||!atomic_load_explicit(&p->disarmed,memory_order_acquire))return fail(p,ES_ROOT_INVALID);
 if(!c->listener||c->listener->cancel_fd!=p->cancel_fd||c->peer.cancel_fd!=p->cancel_fd||c->wire.cancel_fd!=p->cancel_fd
    ||c->listener->startup_deadline_ns!=p->deadline_ns||c->peer.deadline_ns!=p->deadline_ns||c->wire.deadline_ns!=p->deadline_ns)return fail(p,ES_ROOT_INVALID);
 es_peer_identity root_before={0},peer_before={0},root_after={0},peer_after={0};
 r=fork_result(es_fork_read(&p->fork,&root_before));if(r==ES_ROOT_CAPTURED)r=connection_result(es_connection_read(c,&peer_before));
 if(r==ES_ROOT_OK&&!equal(root_before,peer_before))r=ES_ROOT_IDENTITY;
 if(r==ES_ROOT_OK){r=fork_result(es_fork_read(&p->fork,&root_after));if(r==ES_ROOT_CAPTURED)r=connection_result(es_connection_read(c,&peer_after));}
 if(r==ES_ROOT_OK&&(!equal(root_before,root_after)||!equal(peer_before,peer_after)||!equal(root_after,peer_after)))r=ES_ROOT_IDENTITY;
 if(r==ES_ROOT_OK){p->state=4;*out=root_after;}
 wipe(&root_before,sizeof(root_before));wipe(&root_after,sizeof(root_after));wipe(&peer_before,sizeof(peer_before));wipe(&peer_after,sizeof(peer_after));
 return r==ES_ROOT_OK?ES_ROOT_CORRELATED:fail(p,r);
}
static int clock_now(uint64_t *out){
 struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<0||t.tv_nsec<0||t.tv_nsec>=1000000000L)return 0;
 if((uint64_t)t.tv_sec>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL)return 0;
 *out=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;return 1;
}
es_root_cleanup es_root_close(es_root *p,uint64_t deadline){
 if(!p||!p->state)return ES_ROOT_CLOSE_INVALID;
 if(p->state==5)return p->cleanup;
 uint64_t now=0;int valid=clock_now(&now)&&deadline>now&&deadline-now<=10000000000ULL;
 if(!p->cleanup_started){p->cleanup_started=1;p->cleanup_deadline_ns=deadline;}
 else if(deadline<p->cleanup_deadline_ns)p->cleanup_deadline_ns=deadline;
 if(!valid||now>=p->cleanup_deadline_ns)p->cleanup=ES_ROOT_CLOSED_INCONCLUSIVE;
 if(p->fork.state){
  es_fork_result r=es_fork_close(&p->fork);
  if(r==ES_FORK_CLEANUP)p->cleanup=ES_ROOT_CLOSED_INCONCLUSIVE;
  if(atomic_load_explicit(&p->fork.armed,memory_order_acquire)){p->cleanup=ES_ROOT_CLOSED_INCONCLUSIVE;fail(p,ES_ROOT_CLEANUP);return p->cleanup;}
 }
 if(atomic_load_explicit(&p->disarm_failure,memory_order_acquire)==ES_ROOT_CLEANUP)p->cleanup=ES_ROOT_CLOSED_INCONCLUSIVE;
 if(!clock_now(&now)||now>=p->cleanup_deadline_ns)p->cleanup=ES_ROOT_CLOSED_INCONCLUSIVE;
 p->state=5;return p->cleanup;
}

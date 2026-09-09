#ifndef ES_PRIVACY_ROOT_H
#define ES_PRIVACY_ROOT_H
#include "privacy-fork.h"
#include "privacy-connection.h"
typedef enum { ES_ROOT_OK=0,ES_ROOT_CAPTURED=1,ES_ROOT_REGISTERED=2,ES_ROOT_CORRELATED=3,
 ES_ROOT_INVALID=4,ES_ROOT_PLATFORM=5,ES_ROOT_IDENTITY=6,ES_ROOT_DEAD=7,
 ES_ROOT_PROTOCOL=8,ES_ROOT_DEADLINE=9,ES_ROOT_CANCELLED=10,ES_ROOT_IO=11,ES_ROOT_CLEANUP=12 } es_root_result;
typedef enum { ES_ROOT_CLOSED_COMPLETE=0,ES_ROOT_CLOSED_INCONCLUSIVE=1,ES_ROOT_CLOSE_INVALID=2 } es_root_cleanup;
typedef struct {
 es_fork fork;unsigned state,cleanup_started,arm_failed_ended;uint64_t generation,deadline_ns,cleanup_deadline_ns;
 int cancel_fd;pthread_t launcher;_Atomic unsigned disarmed,disarm_failure;
 es_root_result terminal;es_root_cleanup cleanup;
} es_root;
/* Stable fresh zeroed native storage, never copied or reused. Owns fork only.
   Arm/register/disarm are launcher-thread-only. Caller serializes capture,
   register, match and close and safely publishes capture completion. Disarm
   may overlap capture: only its atomic completion/failure fields are shared;
   caller joins/publishes disarm completion before match or close. Cancellation
   eventfd is borrowed and only its signalling may otherwise be concurrent.
   Exact returned PID must originate from PrivacyLaunchOwner; C cannot prove
   Java provenance. No image, ancestry, privacy or runtime admission follows.
   Borrowed connection remains alive and separately owned throughout match.
   Its listener/peer/wire must share this exact immutable cancellation descriptor
   and startup deadline; a foreign or internally inconsistent scope refuses.
   Original startup and independent <=10s cleanup clocks cannot be renewed.
   Complete close tombstones persist; uncertain closes never retry FD numbers. */
es_root_result es_root_arm(es_root *,int,uint64_t,const uint8_t[16]);
es_root_result es_root_capture(es_root *,es_peer_identity *);
es_root_result es_root_register(es_root *,uint64_t);
es_root_result es_root_disarm(es_root *);
es_root_result es_root_match(es_root *,es_connection *,es_peer_identity *);
es_root_cleanup es_root_close(es_root *,uint64_t);
#endif

#ifndef ES_PRIVACY_FORK_H
#define ES_PRIVACY_FORK_H
#include "privacy-peer.h"
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
typedef enum { ES_FORK_OK=0,ES_FORK_CAPTURED=1,ES_FORK_INVALID=2,
 ES_FORK_UNSUPPORTED=3,ES_FORK_PROTOCOL=4,ES_FORK_DEAD=5,
 ES_FORK_IDENTITY=6,ES_FORK_DEADLINE=7,ES_FORK_CANCELLED=8,
 ES_FORK_IO=9,ES_FORK_CLEANUP=10 } es_fork_result;
typedef struct {
 unsigned state;uint64_t generation,deadline_ns;
 int receive_fd,send_fd,cancel_fd;pthread_t launcher;
 _Atomic unsigned hook_done,hook_failed,armed;
 uint8_t record[24];es_peer peer;es_fork_result terminal;
} es_fork;
/* Private prerequisite only: CAPTURED proves a retained kernel sender pin,
   not Java Process registration, executable/ancestry identity or admission.
   The fixed library must remain loaded through every handler and object lifetime.
   Fresh zeroed object; stable native storage, no copying/reinitialization. One
   launcher thread owns arm/disarm. One serialized owner calls capture/read/close;
   it may be a different thread, but must not race these calls with each other.
   Only eventfd signalling is permitted concurrently for cancellation. The
   launcher may disarm concurrently with capture: disarm never touches receiver
   or retained pin. Close/storage release waits for disarm to return and for
   caller-established thread completion; close must not race disarm itself.
   state/terminal/receive_fd/peer belong exclusively to that
   serialized receive owner; launcher/generation/record/deadline/cancel are
   immutable after arm publication. send_fd belongs only to the launcher and its
   parent hook until armed becomes zero with release/acquire ordering. Hook
   status and armed are lock-free atomics, checked before arm. Disarm reads no
   receive-owner state. No close while the launcher window remains armed.
   arm serializes all windows process-wide without waiting; initial-exec TLS is
   bound to this generation. The caller must establish self privacy before arm,
   use the qualified FORK platform thread and exactly one ProcessBuilder start,
   then disarm in finally. These Java/settings prerequisites are not implemented
   by this primitive. The random launch ID is trusted native-owned input, not a
   credential or peer/caller-selected correlation policy.
   Deadline is the original absolute CLOCK_MONOTONIC bound, <=10s remaining.
   cancel eventfd remains borrowed, stable, unconsumed and never closed here. */
es_fork_result es_fork_arm(es_fork *,int cancel_fd,uint64_t deadline_ns,const uint8_t launch_id[16]);
/* Receive exactly one fixed kernel-credentialled packet and EOF. The original
   pin stays owned until close; outputs are zeroed on every refusal. Never acquire
   a pidfd by numeric lookup. Capture can precede Java start return solely to
   support later correlation/cleanup; it cannot register an unreturned Process.
   Invalid ordering on a live object latches failure; it cannot be retried into
   successful capture. A closed object cannot be reinitialized. */
es_fork_result es_fork_capture(es_fork *,es_peer_identity *);
es_fork_result es_fork_read(es_fork *,es_peer_identity *);
/* Same launcher thread, after its fork/start call returned or threw. A missing
   hook closes the residual sender but cannot create CAPTURED evidence. */
es_fork_result es_fork_disarm(es_fork *);
/* Close once after disarm. An armed window latches cleanup uncertainty and keeps
   descriptors quarantined until disarm proves handlers cannot touch them.
   Later close can release them but cannot erase that sticky uncertainty. */
es_fork_result es_fork_close(es_fork *);
#endif

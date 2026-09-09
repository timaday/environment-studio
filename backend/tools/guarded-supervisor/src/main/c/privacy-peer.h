#ifndef ES_PRIVACY_PEER_H
#define ES_PRIVACY_PEER_H
#include <stdint.h>
typedef enum { ES_PEER_OK=0, ES_PEER_INVALID=1, ES_PEER_UNSUPPORTED=2,
 ES_PEER_DEAD=3, ES_PEER_IDENTITY=4, ES_PEER_DEADLINE=5,
 ES_PEER_CANCELLED=6, ES_PEER_IO=7, ES_PEER_CLEANUP=8 } es_peer_result;
typedef struct { uint32_t pid,uid,gid; uint64_t start_ticks; } es_peer_identity;
typedef struct {
 int pidfd; int cancel_fd; unsigned state; uint64_t deadline_ns;
 es_peer_result terminal; es_peer_identity identity;
} es_peer;
/* Fresh zeroed object, one native owner. Socket and cancel eventfd are borrowed:
   their owner keeps both stable/live throughout calls. No socket I/O occurs.
   Only an acquired pidfd is retained here; temporary procfs read descriptors
   are also owned and closed once. The pin remains until close/failure.
   Deadline is the caller's original absolute CLOCK_MONOTONIC launch bound,
   with no more than ten seconds remaining; excessive future bounds refuse.
   A kernel process identity is NOT image, ancestry, Java-root or admission proof.
   Reads use verified local procfs, <=16 KiB scratch and checks between syscalls;
   a stalled kernel syscall is not made interruptible by this deadline.
   PID/start ticks must be positive; zero UID/GID are valid exact identities. */
es_peer_result es_peer_open(es_peer *,int socket_fd,int cancel_fd,uint64_t deadline_ns);
/* Recheck original pinned process, never reopen a pidfd by numeric PID.
   On failure output is zeroed and terminal cleanup is sticky. */
es_peer_result es_peer_read(es_peer *,es_peer_identity *);
es_peer_result es_peer_close(es_peer *);
#endif

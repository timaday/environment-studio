#ifndef ES_PRIVACY_LISTENER_H
#define ES_PRIVACY_LISTENER_H
#include <stdint.h>
#include <sys/types.h>
#define ES_LISTENER_PATH_BYTES 104
#define ES_LISTENER_MAX_PEERS 16
#define ES_LISTENER_MAX_ACCEPTS 64
typedef enum { ES_LISTENER_OK=0,ES_LISTENER_ACCEPTED=1,
 ES_LISTENER_INVALID=2,ES_LISTENER_PLATFORM=3,ES_LISTENER_IDENTITY=4,
 ES_LISTENER_DEADLINE=5,ES_LISTENER_CANCELLED=6,ES_LISTENER_CAPACITY=7,
 ES_LISTENER_IO=8,ES_LISTENER_FAILED=9 } es_listener_result;
typedef enum { ES_LISTENER_CLOSED_COMPLETE=0,
 ES_LISTENER_CLOSED_INCONCLUSIVE=1,ES_LISTENER_CLOSE_INVALID=2 } es_listener_cleanup;
typedef struct {int fd;unsigned state,transferred;es_listener_cleanup cleanup;} es_listener_slot;
typedef struct {
 unsigned state,width,live,accepted;uint32_t generation;
 int parent_fd,cancel_fd,directory_fd,socket_path_fd,listen_fd;
 uint64_t startup_deadline_ns,cleanup_deadline_ns;
 dev_t parent_device,directory_device,socket_device;
 ino_t parent_inode,directory_inode,socket_inode;
 char parent_path[ES_LISTENER_PATH_BYTES],directory_name[36],path[ES_LISTENER_PATH_BYTES];
 unsigned directory_created,socket_created,cleanup_started;
 es_listener_result terminal;es_listener_cleanup cleanup;
 es_listener_slot peers[ES_LISTENER_MAX_ACCEPTS];
} es_listener;
/* Private native socket ownership only, never process/image/ancestry admission.
   No raw descriptor may cross JNI. Fresh zeroed, stable object; one serialized
   native owner calls all methods. Only signalling the borrowed cancel eventfd
   may be concurrent. Parent/cancel descriptors remain borrowed, stable and live.
   Parent must be a previously admitted CLOEXEC directory owned by effective UID,
   mode0700, with a stable canonical absolute UTF-8 pathname. The primitive
   repeats these checks, walks every component without following symlinks and
   compares the last inode/device with the parent descriptor. Root/ancestor and
   filesystem admission remain caller duties. No cwd or global umask mutation.
   The admitted namespace owner must guarantee exclusive mutation of this parent
   and newly owned child namespace throughout their lifetime. Mode0700 and UID
   checks do not establish that against a hostile same-UID process. Establishing
   this caller precondition remains an unqualified coordinator/runtime gate.
   Linux offers no conditional-unlink-by-pinned-inode guarantee; the primitive
   cannot claim immunity to a malicious concurrent namespace owner.
   Native entropy creates a fresh child0700/control.sock0600, at most103 path
   bytes plus NUL. Pinned chmod must not follow a substituted path. Unsupported
   pinned-operation support refuses, with no pathname-following fallback.
   Deadline is the original absolute CLOCK_MONOTONIC startup clock, <=10s left.
   width1..16 bounds live accepted owners and the requested listen backlog
   separately; it does NOT certify the kernel queued+accepted pending bound. */
es_listener_result es_listener_open(es_listener *,int parent_fd,const char *parent_path,
 int cancel_fd,uint64_t startup_deadline_ns,unsigned width);
es_listener_result es_listener_path(es_listener *,char out[ES_LISTENER_PATH_BYTES]);
/* At most64 accepted tokens per launch; closed records retain tombstones.
   Tokens carry an object generation and ordinal. The listener owns each accepted
   nonblocking CLOEXEC socket; borrowing it never transfers close ownership.
   No receive, peer proof or wire protocol is performed here. */
es_listener_result es_listener_accept(es_listener *,uint64_t *token);
es_listener_result es_listener_borrow(es_listener *,uint64_t token,int *fd);
es_listener_cleanup es_listener_close_peer(es_listener *,uint64_t token);
/* Native-only transfer to the single active connection owner. Output storage
   must not alias this listener. Successful take moves fd to *owned_fd and clears
   the listener FD slot; token/capacity remain outstanding until finish_transfer.
   A second transfer, stale token or another active transfer refuses. No raw FD
   may cross JNI. Borrow/close_peer cannot act on a transferred descriptor.
   Listener close preserves outstanding transfer uncertainty and never closes a
   transferred number; receiver must still close and settle its original token.
   finish_transfer accepts only the trusted recipient's actual close outcome;
   it cannot erase earlier listener uncertainty. Repeat settlement preserves its
   tombstone. These calls share the listener's single serialized native owner. */
es_listener_result es_listener_take(es_listener *,uint64_t token,int *owned_fd);
es_listener_result es_listener_transfer_live(es_listener *,uint64_t token);
es_listener_cleanup es_listener_finish_transfer(es_listener *,uint64_t token,es_listener_cleanup);
/* Close has its own already-running absolute cleanup deadline. First close fixes
   <=10s remaining; subsequent calls cannot renew it. Expired/invalid allowance
   still attempts bounded descriptor shutdown and stays INCONCLUSIVE. Cancellation
   never prevents owned close attempts. Exact owned names/identities are checked;
   detected substitutions are preserved and cleanup remains INCONCLUSIVE. Linux
   close/unlink uncertainty is sticky and never retried against a reused name or
   descriptor. Completed close tombstones retain the exact original outcome.
   Operational refusal stays latched even when cleanup itself completes. */
es_listener_cleanup es_listener_close(es_listener *,uint64_t cleanup_deadline_ns);
#endif

#ifndef ES_PRIVACY_CONNECTION_H
#define ES_PRIVACY_CONNECTION_H
#include "privacy-listener.h"
#include "privacy-peer.h"
#include "privacy-wire.h"
typedef enum {
 ES_CONNECTION_OK=0, ES_CONNECTION_PREPARE_RECEIVED=1,
 ES_CONNECTION_INVALID=2, ES_CONNECTION_PLATFORM=3,
 ES_CONNECTION_IDENTITY=4, ES_CONNECTION_DEAD=5,
 ES_CONNECTION_PROTOCOL=6, ES_CONNECTION_DEADLINE=7,
 ES_CONNECTION_CANCELLED=8, ES_CONNECTION_IO=9,
 ES_CONNECTION_CLEANUP=10
} es_connection_result;
typedef enum {
 ES_CONNECTION_CLOSED_COMPLETE=0, ES_CONNECTION_CLOSED_INCONCLUSIVE=1,
 ES_CONNECTION_CLOSE_INVALID=2
} es_connection_cleanup;
typedef struct {
 unsigned state,transferred,cleanup_started;
 es_listener *listener;uint64_t token,cleanup_deadline_ns;
 es_peer peer;es_wire wire;
 es_connection_result terminal;es_connection_cleanup cleanup;
} es_connection;
/* Private native coordinator prerequisite only. PREPARE_RECEIVED means exactly
   the initial frame plus a live same-socket kernel process pin, never Java-root,
   ancestry, executable/image, suppression, final handshake or runtime admission.
   No CHALLENGE/ACK is sent. No descriptors or these objects may cross JNI.

   Fresh zeroed stable object; one serialized native owner operates this object,
   its listener and all peer/wire members. Only signalling the listener's borrowed
   cancellation eventfd may be concurrent. Keep listener storage, its admitted
   parent and borrowed cancellation descriptor alive through connection close.
   The existing exclusive-namespace admission precondition remains unchanged.

   open takes an already accepted listener token. It acquires a process pin from
   the exact borrowed accepted socket, then transfers that socket once to its
   fresh wire owner. The listener retains the capacity/cleanup obligation until
   actual receiver close is settled; transfer alone never releases capacity.
   At most one transfer/active handshake exists for a listener. Invalid fresh
   object/output/token prechecks leave the existing owner untouched; subsequent
   refusal closes acquired resources once and latches terminal uncertainty.
   No socket duplicate or numeric process-pin acquisition is used.
   Startup/cancellation are inherited from the original listener, not peer input.
   Kernel syscalls that stall are not made interruptible by these clock checks. */
es_connection_result es_connection_open(es_connection *,es_listener *,uint64_t token);
/* Receive PREPARE exactly once, rechecking the live pin before/after reception.
   Distinct caller identity output is zeroed on every refusal. Output overlapping
   this connection or its listener is refused before writing and closes the live
   connection safely. Null output on a live connection
   is a sticky refusal with owned cleanup. Read only after PREPARE rechecks the
   existing pin without receiving another frame or granting more authority. */
es_connection_result es_connection_prepare(es_connection *,es_peer_identity *);
es_connection_result es_connection_read(es_connection *,es_peer_identity *);
/* Independent original cleanup deadline, <=10s remaining. First close fixes it;
   repeats cannot renew it. Expiry/cancellation never skip owned descriptor close.
   Settle transferred socket closure only after the wire owner has actually
   closed once; propagate uncertain close to the listener. Closing the listener
   first cannot touch the transferred FD and cannot report complete cleanup.
   Repeated complete close preserves its tombstone; no reused FD is retried. */
es_connection_cleanup es_connection_close(es_connection *,uint64_t cleanup_deadline_ns);
#endif

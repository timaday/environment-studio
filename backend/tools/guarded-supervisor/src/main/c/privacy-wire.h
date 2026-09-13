#ifndef ES_PRIVACY_WIRE_H
#define ES_PRIVACY_WIRE_H
#include <stddef.h>
#include <stdint.h>
#define ES_WIRE_MAX_FRAME 108
#define ES_WIRE_TUPLE_SIZE 84
#define ES_WIRE_PROOF_SIZE 12
#define ES_WIRE_PREPARE 0x01
#define ES_WIRE_CHALLENGE 0x02
#define ES_WIRE_ESTABLISHED 0x03
#define ES_WIRE_ACK 0x04
#define ES_WIRE_ABORT 0x7e
#define ES_WIRE_REFUSED 0x7f
/* Connection results are never executable identity or runtime admission. */
typedef enum {
    ES_WIRE_OK=0, ES_WIRE_COMPLETE=1, ES_WIRE_PROTOCOL=2,
    ES_WIRE_DEADLINE=3, ES_WIRE_CANCELLED=4, ES_WIRE_IO=5,
    ES_WIRE_ABORTED=6, ES_WIRE_PEER_REFUSED=7, ES_WIRE_CLEANUP=8
} es_wire_result;
typedef enum { ES_WIRE_PARENT=1, ES_WIRE_CHILD=2 } es_wire_role;
typedef struct {
    uint8_t type;
    uint8_t tuple[ES_WIRE_TUPLE_SIZE];
    uint8_t proof[ES_WIRE_PROOF_SIZE];
    uint16_t refusal;
} es_wire_frame;
typedef struct {
    int fd;
    int cancel_fd; /* Borrowed eventfd; never consumed or closed here. */
    uint64_t deadline_ns; /* Fixed local CLOCK_MONOTONIC bound; never peer input. */
    es_wire_role role;
    unsigned state;
    es_wire_result terminal;
    uint8_t tuple[ES_WIRE_TUPLE_SIZE];
} es_wire;
/* Fixed output buffers are wiped on failure. The codec allocates nothing. */
es_wire_result es_wire_encode(const es_wire_frame *, uint8_t [ES_WIRE_MAX_FRAME], size_t *);
es_wire_result es_wire_decode(const uint8_t *, size_t, es_wire_frame *);
/* One native owner calls this API; concurrent calls are not supported.
   Other threads may only signal the borrowed cancellation eventfd.
   The parent supplies its original shared launch deadline on every connection.
   A child-local bound limits only its own wait, never parent admission. Wire
   frames contain no deadline. Neither role renews its bound between phases.
   Initialize a fresh zeroed object. Ownership of fd transfers on this call;
   it must be a nonblocking CLOEXEC AF_UNIX stream. cancel_fd stays borrowed.
   A descriptor alias or previously initialized object refuses without adopting
   or closing either supplied descriptor. */
es_wire_result es_wire_init(es_wire *, int fd, int cancel_fd, uint64_t deadline_ns, es_wire_role);
es_wire_result es_wire_send(es_wire *, const es_wire_frame *);
es_wire_result es_wire_receive(es_wire *, es_wire_frame *);
/* Parent requires EOF after ACK. Child calls finish immediately after ACK.
   Completion closes this connection only; no admission is granted. */
es_wire_result es_wire_finish(es_wire *);
/* Close once, wipe retained correlation, preserve a sticky terminal result. */
es_wire_result es_wire_close(es_wire *);
#endif

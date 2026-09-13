#ifndef ES_PRIVACY_HASH_H
#define ES_PRIVACY_HASH_H
#include <stdint.h>
typedef enum {ES_HASH_OK=0,ES_HASH_INVALID=1,ES_HASH_PLATFORM=2,
 ES_HASH_CANCELLED=3,ES_HASH_DEADLINE=4,ES_HASH_RESOURCE=5,
 ES_HASH_FILE=6,ES_HASH_CRYPTO=7,ES_HASH_IO=8,ES_HASH_CLEANUP=9} es_hash_result;
typedef struct {uint64_t device,inode,size;unsigned char sha256[32];} es_hash_identity;
typedef struct {
 int cancel_fd;uint64_t deadline_ns,bytes;uint32_t objects;unsigned state;
 es_hash_result terminal,cleanup;void *library,*provider,*algorithm;
} es_hash;
/* Fresh zeroed owner in stable storage; never copy after initialization.
   Caller serialized; controls/file fd remain borrowed.
   Measures content only, never trusted file/image/closure or process admission.
   Failed initialized owners must still close; no reopen or budget reset. */
es_hash_result es_hash_open(es_hash *,int cancel_fd,uint64_t deadline_ns);
es_hash_result es_hash_file(es_hash *,int borrowed_file,es_hash_identity *);
/* Closes acquired crypto resources once; preserves earlier sticky refusal.
   OK means this owner's resources released, not operation success. */
es_hash_result es_hash_close(es_hash *);
#endif

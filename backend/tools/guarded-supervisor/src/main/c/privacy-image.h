#ifndef ES_PRIVACY_IMAGE_H
#define ES_PRIVACY_IMAGE_H
#include "privacy-peer.h"
#include "privacy-file.h"
#include "privacy-hash.h"
typedef enum { ES_IMAGE_OK=0, ES_IMAGE_INVALID=1, ES_IMAGE_PLATFORM=2,
 ES_IMAGE_CANCELLED=3, ES_IMAGE_DEADLINE=4, ES_IMAGE_IDENTITY=5,
 ES_IMAGE_RESOURCE=6, ES_IMAGE_IO=7, ES_IMAGE_CLEANUP=8 } es_image_result;
/* Borrowed stable, distinct caller-serialized owners, same original controls.
   Expected digest belongs to a compiled installation record, never peer input.
   Invalid owner/descriptor/scope preflight does not mutate borrowed owners.
   Output aliasing an owner or expected digest is invalid and is not overwritten;
   otherwise refusal output is zeroed. Peer/hash retain their own failure/cleanup
   semantics. The caller must latch any refusal; no admission retry is permitted.
   Peer DEAD/IDENTITY and hash FILE or identity mismatch map to IDENTITY;
   unsupported maps PLATFORM, hash CRYPTO maps IO. Cancellation/deadline/resource
   remain distinct. Any temporary close uncertainty overrides earlier outcomes.
   Success is current measured executable association only, not exec generation,
   script/loader/ancestry, privacy, CHALLENGE or runtime admission. */
es_image_result es_image_check(es_peer *,const es_file *,es_hash *,
 const unsigned char expected_sha256[32],es_hash_identity *out);
#endif

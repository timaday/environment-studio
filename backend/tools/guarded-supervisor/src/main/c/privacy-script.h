#ifndef ES_PRIVACY_SCRIPT_H
#define ES_PRIVACY_SCRIPT_H
#include <stddef.h>
#include "privacy-peer.h"
#include "privacy-file.h"
#include "privacy-hash.h"
typedef enum { ES_SCRIPT_OK=0, ES_SCRIPT_INVALID=1, ES_SCRIPT_PLATFORM=2,
 ES_SCRIPT_CANCELLED=3, ES_SCRIPT_DEADLINE=4, ES_SCRIPT_IDENTITY=5,
 ES_SCRIPT_RESOURCE=6, ES_SCRIPT_IO=7, ES_SCRIPT_CLEANUP=8 } es_script_result;
typedef struct {
 const char *script_path;size_t script_path_length;
 const unsigned char *shebang;size_t shebang_length;
 const unsigned char *arguments;size_t arguments_length;unsigned script_index;
 unsigned char script_sha256[32],interpreter_sha256[32];
} es_script_expected;
typedef struct {es_hash_identity script,interpreter;} es_script_identity;
/* Stable distinct caller-serialized borrowed owners and immutable compiled
   expected record/spans. All owners share original launch controls. No input
   may overlap a mutable owner/output. Invalid aliased output stays untouched;
   otherwise refusal output is zero. Existing borrowed failure/cleanup states
   persist; caller latches every refusal and may not retry admission.
   Only no option or exact -e shebang forms; canonical interpreter file is
   independently compiled, never discovered from the literal argv token.
   Five successful hash charges, two independent argv calls, no offset changes.
   Peer death/file trust/content mismatch -> IDENTITY, crypto -> IO; cleanup
   uncertainty overrides all outcomes. No consumed-script, constructor, loader,
   generation, privacy, JNI or runtime admission proof. */
es_script_result es_script_check(es_peer *,const es_file *,const es_file *,es_hash *,
 const es_script_expected *,es_script_identity *);
#endif

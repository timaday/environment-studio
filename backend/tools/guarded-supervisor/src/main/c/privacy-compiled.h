#ifndef ES_PRIVACY_COMPILED_H
#define ES_PRIVACY_COMPILED_H
#include <stdint.h>
/* Link-time installation tables only. No setter/provider or Java policy input. */
typedef struct {int ordinal;const char *java_runtime;const char *parent_path;uint32_t version;} es_compiled_mechanism;
typedef struct {int ordinal,mechanism;uint32_t version,width,identity_version;uint64_t lifetime;unsigned char installation[32],chain[32];} es_compiled_chain;
const es_compiled_mechanism *es_compiled_find_mechanism(int);
const es_compiled_chain *es_compiled_find_chain(int);
#endif

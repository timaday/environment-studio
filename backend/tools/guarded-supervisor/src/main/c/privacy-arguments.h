#ifndef ES_PRIVACY_ARGUMENTS_H
#define ES_PRIVACY_ARGUMENTS_H
#include <stddef.h>
#include "privacy-peer.h"
/* Exact bounded observed argv equality only. No image/chain/admission authority.
   Expected NUL-separated bytes and peer remain caller-owned and serialized. */
es_peer_result es_arguments_check(es_peer *,const unsigned char *,size_t);
#endif

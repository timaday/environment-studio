#ifndef ES_PRIVACY_MAPS_H
#define ES_PRIVACY_MAPS_H
#include "privacy-peer.h"
#define ES_MAPS_RECORDS 4096U
#define ES_MAPS_BYTES 262144U
#define ES_MAPS_LINE_BYTES 8192U
typedef enum {ES_MAPS_OK=0,ES_MAPS_INVALID=1,ES_MAPS_PLATFORM=2,ES_MAPS_RESOURCE=3,
 ES_MAPS_IDENTITY=4,ES_MAPS_FORMAT=5,ES_MAPS_DEADLINE=6,ES_MAPS_CANCELLED=7,
 ES_MAPS_IO=8,ES_MAPS_CLEANUP=9} es_maps_result;
typedef struct {
 uint64_t start,end,offset,inode;
 uint32_t device_major,device_minor;
 uint32_t read,write,execute,shared;
 uint32_t label_offset,label_length;
} es_maps_record;
typedef struct {
 es_peer_identity identity;uint32_t count,byte_length;
 es_maps_record records[ES_MAPS_RECORDS];unsigned char bytes[ES_MAPS_BYTES];
} es_maps_snapshot;
_Static_assert(sizeof(es_maps_snapshot)<=576U*1024U,"bounded maps output");
/* Borrowed live kernel peer and original controls. Complete non-atomic sampled
   evidence only; opaque labels are never trusted filenames. Caller owns stable
   nonoverlapping output and wipes it after use. No memFD or admission. */
es_maps_result es_maps_sample(es_peer *,es_maps_snapshot *);
#endif

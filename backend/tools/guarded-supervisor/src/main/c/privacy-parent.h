#ifndef ES_PRIVACY_PARENT_H
#define ES_PRIVACY_PARENT_H
#include "privacy-peer.h"
/* Caller-serialized existing pins in one launch scope. OK proves only one live
   direct parent edge at checked boundaries, never ancestry/exec/admission.
   No borrowed descriptor is closed here; failed peer rechecks own their cleanup.
   Temporary close uncertainty must be latched by the calling coordinator. */
es_peer_result es_parent_check(es_peer *child,es_peer *parent);
#endif

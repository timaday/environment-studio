#ifndef ES_PRIVACY_FILE_H
#define ES_PRIVACY_FILE_H
#include <stdint.h>
#include <stddef.h>
typedef enum { ES_FILE_OK=0, ES_FILE_INVALID=1, ES_FILE_PLATFORM=2,
 ES_FILE_CANCELLED=3, ES_FILE_DEADLINE=4, ES_FILE_TRUST=5,
 ES_FILE_IO=6, ES_FILE_CLEANUP=7 } es_file_result;
typedef struct {int cancel_fd,fd;uint64_t deadline_ns;unsigned state;es_file_result terminal,cleanup;} es_file;
/* Fresh zeroed, stable, noncopyable owner. Caller serializes all access.
   Path bytes come from tool-owned installation metadata, never a peer.
   File fd is borrowed by callers until close; cancellation stays borrowed.
   Open OK establishes file policy only, never expected content/process identity. */
es_file_result es_file_open(es_file *,int cancel_fd,uint64_t deadline_ns,const char *path,size_t length);
es_file_result es_file_close(es_file *);
#endif

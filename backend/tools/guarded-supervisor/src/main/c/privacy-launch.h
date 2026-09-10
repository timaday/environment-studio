#ifndef ES_PRIVACY_LAUNCH_H
#define ES_PRIVACY_LAUNCH_H
#include "privacy-root.h"
#include "privacy-maps.h"
#include "privacy-elf.h"
#include <pthread.h>
typedef enum { ES_LAUNCH_OK=0,ES_LAUNCH_ROOT_CORRELATED=1,ES_LAUNCH_INVALID=2,
 ES_LAUNCH_PLATFORM=3,ES_LAUNCH_RESOURCE=4,ES_LAUNCH_IDENTITY=5,ES_LAUNCH_PROTOCOL=6,
 ES_LAUNCH_DEADLINE=7,ES_LAUNCH_CANCELLED=8,ES_LAUNCH_IO=9,ES_LAUNCH_CLEANUP=10 } es_launch_result;
typedef enum { ES_LAUNCH_PHASE_OPEN=0,ES_LAUNCH_PHASE_ARMED=1,ES_LAUNCH_PHASE_CAPTURED=2,
 ES_LAUNCH_PHASE_REGISTERED=3,ES_LAUNCH_PHASE_CORRELATED=4,ES_LAUNCH_PHASE_FAILED=5 } es_launch_phase;
typedef enum { ES_LAUNCH_CLOSE_NONE=0,ES_LAUNCH_CLOSE_REQUESTED=1,
 ES_LAUNCH_CLOSE_SETTLING=2,ES_LAUNCH_CLOSE_SETTLED=3 } es_launch_close_state;
typedef enum { ES_LAUNCH_CLOSED_COMPLETE=0,ES_LAUNCH_CLOSED_INCONCLUSIVE=1,
 ES_LAUNCH_CLOSE_INVALID=2 } es_launch_cleanup;
typedef struct {es_launch_phase phase;es_launch_result failure;es_launch_close_state close_state;
 unsigned disarm_completed,calls_quiescent,cleanup_inconclusive;} es_launch_status;
typedef struct { unsigned char path[4096];uint32_t path_length;unsigned char expected_sha256[32]; } es_launch_image_record;
typedef struct es_launch {
 unsigned initialized;
 pthread_mutex_t mutex;pthread_cond_t changed;
 es_launch_phase phase;es_launch_result failure;es_launch_close_state closing;
 unsigned inconclusive,cancelled,users,signals,root_active,disarm_active;
 unsigned launcher_bound,receiver_bound,arm_entered,arm_done,arm_unowned,capture_entered,capture_done;
 unsigned register_entered,register_done,disarm_entered,disarm_done,correlate_entered,maps_entered,image_entered;
 pthread_t launcher,receiver;
 int cancel_fd;uint64_t startup_deadline,operation_deadline,cleanup_deadline;
 uint8_t launch_id[16];
 es_root root;es_listener listener;es_connection connection;es_hash hash;
} es_launch;
/* Stable zeroed native owner. No copy/reinit/free until all external callers are
 joined and every native call/TLS window has ended. Parent directory is borrowed.
 See frozen ABI for single launcher/receiver, output aliases, clocks and sticky
 cleanup. No production JNI token, CHALLENGE or privacy/runtime admission. */
es_launch_result es_launch_open(es_launch *,int,const char *,uint64_t,char[ES_LISTENER_PATH_BYTES]);
/* Trusted native entry origin only; no Java/peer epoch or caller policy. */
es_launch_result es_launch_open_started(es_launch *,int,const char *,uint64_t,uint64_t,char[ES_LISTENER_PATH_BYTES]);
es_launch_result es_launch_arm(es_launch *);
es_launch_result es_launch_capture(es_launch *);
es_launch_result es_launch_register(es_launch *,uint64_t);
es_launch_result es_launch_disarm(es_launch *);
es_launch_result es_launch_correlate(es_launch *);
es_launch_result es_launch_maps(es_launch *,es_maps_snapshot *);
/* Once-only correlated receiver, native installation metadata only. Original
   scope and one retained hash budget; structural dynamic ELF evidence is not
   loader, exec-generation or admission proof. Output aliases refuse untouched;
   distinct failed output is wiped. File cleanup stays inside the active call. */
es_launch_result es_launch_image(es_launch *,const es_launch_image_record *,es_elf_layout *);
es_launch_result es_launch_status_read(es_launch *,es_launch_status *);
es_launch_result es_launch_cancel(es_launch *);
es_launch_cleanup es_launch_close(es_launch *,uint64_t);
#endif

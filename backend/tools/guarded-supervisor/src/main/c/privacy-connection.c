#define _GNU_SOURCE
#include "privacy-connection.h"
#include <time.h>
static void wipe(void *v,size_t n){volatile unsigned char *p=v;while(n--)*p++=0;}
static int clock_ns(uint64_t *out){
 struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<0||t.tv_nsec<0||t.tv_nsec>=1000000000L)return 0;
 if((uint64_t)t.tv_sec>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL)return 0;
 *out=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;return 1;
}
static es_connection_result from_listener(es_listener_result r){
 switch(r){case ES_LISTENER_OK:return ES_CONNECTION_OK;case ES_LISTENER_PLATFORM:return ES_CONNECTION_PLATFORM;
 case ES_LISTENER_IDENTITY:return ES_CONNECTION_IDENTITY;case ES_LISTENER_DEADLINE:return ES_CONNECTION_DEADLINE;
 case ES_LISTENER_CANCELLED:return ES_CONNECTION_CANCELLED;case ES_LISTENER_IO:return ES_CONNECTION_IO;
 default:return ES_CONNECTION_INVALID;}
}
static es_connection_result from_peer(es_peer_result r){
 switch(r){case ES_PEER_OK:return ES_CONNECTION_OK;case ES_PEER_UNSUPPORTED:return ES_CONNECTION_PLATFORM;
 case ES_PEER_DEAD:return ES_CONNECTION_DEAD;case ES_PEER_IDENTITY:return ES_CONNECTION_IDENTITY;
 case ES_PEER_DEADLINE:return ES_CONNECTION_DEADLINE;case ES_PEER_CANCELLED:return ES_CONNECTION_CANCELLED;
 case ES_PEER_CLEANUP:return ES_CONNECTION_CLEANUP;case ES_PEER_IO:return ES_CONNECTION_IO;
 default:return ES_CONNECTION_INVALID;}
}
static es_connection_result from_wire(es_wire_result r){
 switch(r){case ES_WIRE_OK:return ES_CONNECTION_OK;case ES_WIRE_DEADLINE:return ES_CONNECTION_DEADLINE;
 case ES_WIRE_CANCELLED:return ES_CONNECTION_CANCELLED;case ES_WIRE_IO:return ES_CONNECTION_IO;
 case ES_WIRE_CLEANUP:return ES_CONNECTION_CLEANUP;default:return ES_CONNECTION_PROTOCOL;}
}
static void release(es_connection *p){
 if(p->state==3)return;
 int socket_uncertain=0;
 if(p->wire.state&&es_wire_close(&p->wire)==ES_WIRE_CLEANUP)socket_uncertain=1;
 if(p->peer.state&&es_peer_close(&p->peer)==ES_PEER_CLEANUP)p->cleanup=ES_CONNECTION_CLOSED_INCONCLUSIVE;
 es_listener_cleanup settled=p->transferred
  ?es_listener_finish_transfer(p->listener,p->token,socket_uncertain?ES_LISTENER_CLOSED_INCONCLUSIVE:ES_LISTENER_CLOSED_COMPLETE)
  :es_listener_close_peer(p->listener,p->token);
 if(socket_uncertain||settled!=ES_LISTENER_CLOSED_COMPLETE||p->listener->cleanup==ES_LISTENER_CLOSED_INCONCLUSIVE)p->cleanup=ES_CONNECTION_CLOSED_INCONCLUSIVE;
 p->state=3;
}
static es_connection_result fail(es_connection *p,es_connection_result result){
 if(p->terminal==ES_CONNECTION_OK)p->terminal=result;
 if(result==ES_CONNECTION_CLEANUP)p->cleanup=ES_CONNECTION_CLOSED_INCONCLUSIVE;
 release(p);return p->terminal;
}
es_connection_result es_connection_open(es_connection *p,es_listener *listener,uint64_t token){
 if(!p||p->state||!listener)return ES_CONNECTION_INVALID;
 int fd=-1;es_listener_result borrowed=es_listener_borrow(listener,token,&fd);
 if(borrowed!=ES_LISTENER_OK)return from_listener(borrowed);
 p->listener=listener;p->token=token;p->state=1;
 es_connection_result result=from_peer(es_peer_open(&p->peer,fd,listener->cancel_fd,listener->startup_deadline_ns));
 if(result)return fail(p,result);
 int owned=-1;result=from_listener(es_listener_take(listener,token,&owned));
 if(result)return fail(p,result);
 p->transferred=1;
 /* Fresh wire, distinct stable cancel FD and accepted socket: the wire adopts
    on every subsequent result, including setup failure. No duplicate is made. */
 result=from_wire(es_wire_init(&p->wire,owned,listener->cancel_fd,listener->startup_deadline_ns,ES_WIRE_PARENT));
 return result?fail(p,result):ES_CONNECTION_OK;
}
static es_connection_result check(es_connection *p,es_peer_identity *identity){
 es_connection_result result=from_listener(es_listener_transfer_live(p->listener,p->token));
 return result?result:from_peer(es_peer_read(&p->peer,identity));
}
static int overlaps(const void *left,size_t left_size,const void *right,size_t right_size){
 uintptr_t a=(uintptr_t)left,b=(uintptr_t)right;
 return left&&right&&(a>=b?a-b<right_size:b-a<left_size);
}
static int output_alias(es_connection *p,es_peer_identity *out){
 return p&&out&&(overlaps(out,sizeof(*out),p,sizeof(*p))
  ||(p->state&&overlaps(out,sizeof(*out),p->listener,sizeof(*p->listener))));
}
es_connection_result es_connection_prepare(es_connection *p,es_peer_identity *out){
 if(output_alias(p,out))return p->state&&p->state!=3?fail(p,ES_CONNECTION_INVALID):ES_CONNECTION_INVALID;
 if(out)wipe(out,sizeof(*out));
 if(!p||!p->state)return ES_CONNECTION_INVALID;
 if(p->state==3)return p->terminal?p->terminal:ES_CONNECTION_INVALID;
 if(!out||p->state!=1)return fail(p,ES_CONNECTION_INVALID);
 es_peer_identity identity={0};es_connection_result result=check(p,&identity);
 es_wire_frame frame={0};
 if(!result)result=from_wire(es_wire_receive(&p->wire,&frame));
 if(!result&&frame.type!=ES_WIRE_PREPARE)result=ES_CONNECTION_PROTOCOL;
 wipe(&frame,sizeof(frame));
 if(!result)result=check(p,&identity);
 if(result){wipe(&identity,sizeof(identity));return fail(p,result);}
 p->state=2;*out=identity;wipe(&identity,sizeof(identity));return ES_CONNECTION_PREPARE_RECEIVED;
}
es_connection_result es_connection_read(es_connection *p,es_peer_identity *out){
 if(output_alias(p,out))return p->state&&p->state!=3?fail(p,ES_CONNECTION_INVALID):ES_CONNECTION_INVALID;
 if(out)wipe(out,sizeof(*out));
 if(!p||!p->state)return ES_CONNECTION_INVALID;
 if(p->state==3)return p->terminal?p->terminal:ES_CONNECTION_INVALID;
 if(!out||p->state!=2)return fail(p,ES_CONNECTION_INVALID);
 es_connection_result result=check(p,out);
 if(result){wipe(out,sizeof(*out));return fail(p,result);}
 return ES_CONNECTION_PREPARE_RECEIVED;
}
es_connection_cleanup es_connection_close(es_connection *p,uint64_t deadline){
 if(!p||!p->state)return ES_CONNECTION_CLOSE_INVALID;
 if(p->cleanup_started&&p->state==3)return p->cleanup;
 uint64_t now=0;
 if(!clock_ns(&now)||deadline<=now||deadline-now>10000000000ULL){p->cleanup=ES_CONNECTION_CLOSED_INCONCLUSIVE;p->cleanup_deadline_ns=now;}
 else if(!p->cleanup_started||deadline<p->cleanup_deadline_ns)p->cleanup_deadline_ns=deadline;
 p->cleanup_started=1;
 release(p);
 if(!clock_ns(&now)||now>=p->cleanup_deadline_ns)p->cleanup=ES_CONNECTION_CLOSED_INCONCLUSIVE;
 return p->cleanup;
}

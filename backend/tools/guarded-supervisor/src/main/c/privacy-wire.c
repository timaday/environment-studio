#define _GNU_SOURCE
#include "privacy-wire.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

enum { P_PREPARE=1, P_CHALLENGE, P_ESTABLISHED, P_ACK, P_EOF,
       C_PREPARE, C_CHALLENGE, C_ESTABLISHED, C_ACK, C_FINISH, TERMINAL };
static const uint8_t magic[8]={'E','S','P','R','V','0','0','1'};
static const uint8_t required_proof[12]={0,1,1,0,0,1,0,1,0xc0,0,0,0x3e};
static void wipe(void *memory,size_t length) {
    volatile uint8_t *p=memory;
    while(length--) *p++=0;
}
static size_t frame_size(uint8_t type) {
    switch(type) {
        case ES_WIRE_PREPARE: case ES_WIRE_ABORT:return 12;
        case ES_WIRE_CHALLENGE: case ES_WIRE_ACK:return 96;
        case ES_WIRE_ESTABLISHED:return 108;
        case ES_WIRE_REFUSED:return 16;
        default:return 0;
    }
}
static int tuple_valid(const uint8_t *tuple) {
    return tuple[16]==0 && tuple[17]==0 && tuple[18]==0
        && tuple[19]>=1 && tuple[19]<=64;
}
es_wire_result es_wire_decode(const uint8_t *bytes,size_t length,es_wire_frame *frame) {
    if(!frame)return ES_WIRE_PROTOCOL;
    wipe(frame,sizeof(*frame));
    if(!bytes || length<12 || memcmp(bytes,magic,8) || bytes[9] || bytes[10] || bytes[11]
        || !frame_size(bytes[8]) || frame_size(bytes[8])!=length)return ES_WIRE_PROTOCOL;
    uint8_t type=bytes[8];
    if(type==ES_WIRE_CHALLENGE || type==ES_WIRE_ESTABLISHED || type==ES_WIRE_ACK) {
        if(!tuple_valid(bytes+12))return ES_WIRE_PROTOCOL;
        if(type==ES_WIRE_ESTABLISHED && memcmp(bytes+96,required_proof,12))return ES_WIRE_PROTOCOL;
        memcpy(frame->tuple,bytes+12,84);
        if(type==ES_WIRE_ESTABLISHED)memcpy(frame->proof,bytes+96,12);
    } else if(type==ES_WIRE_REFUSED) {
        if(bytes[12] || bytes[13]<1 || bytes[13]>6 || bytes[14] || bytes[15])return ES_WIRE_PROTOCOL;
        frame->refusal=bytes[13];
    }
    frame->type=type;
    return ES_WIRE_OK;
}
es_wire_result es_wire_encode(const es_wire_frame *frame,uint8_t bytes[ES_WIRE_MAX_FRAME],size_t *length) {
    if(length)*length=0;
    if(!bytes)return ES_WIRE_PROTOCOL;
    wipe(bytes,ES_WIRE_MAX_FRAME);
    if(!frame || !length || !frame_size(frame->type))return ES_WIRE_PROTOCOL;
    size_t size=frame_size(frame->type);
    if(frame->type==ES_WIRE_CHALLENGE || frame->type==ES_WIRE_ESTABLISHED || frame->type==ES_WIRE_ACK) {
        if(!tuple_valid(frame->tuple))return ES_WIRE_PROTOCOL;
        if(frame->type==ES_WIRE_ESTABLISHED && memcmp(frame->proof,required_proof,12))return ES_WIRE_PROTOCOL;
        memcpy(bytes+12,frame->tuple,84);
        if(frame->type==ES_WIRE_ESTABLISHED)memcpy(bytes+96,frame->proof,12);
    } else if(frame->type==ES_WIRE_REFUSED) {
        if(frame->refusal<1 || frame->refusal>6)return ES_WIRE_PROTOCOL;
        bytes[13]=(uint8_t)frame->refusal;
    }
    memcpy(bytes,magic,8);bytes[8]=frame->type;*length=size;
    return ES_WIRE_OK;
}
static int monotonic(uint64_t *value) {
    struct timespec t;
    if(clock_gettime(CLOCK_MONOTONIC,&t) || t.tv_sec<0 || t.tv_nsec<0 || t.tv_nsec>=1000000000L
        || (uint64_t)t.tv_sec>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL)return 0;
    *value=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;return 1;
}
static es_wire_result end(es_wire *wire,es_wire_result result) {
    if(wire->state==TERMINAL)return wire->terminal;
    int fd=wire->fd;wire->fd=-1;wire->state=TERMINAL;wire->terminal=result;
    wipe(wire->tuple,sizeof(wire->tuple));
    /* Linux may have released the number even when close reports an error. */
    if(fd>=0 && close(fd)!=0)wire->terminal=ES_WIRE_CLEANUP;
    return wire->terminal;
}
es_wire_result es_wire_init(es_wire *wire,int fd,int cancel_fd,uint64_t deadline,es_wire_role role) {
    if(!wire || wire->state || fd==cancel_fd)return ES_WIRE_PROTOCOL;
    wire->fd=fd;wire->cancel_fd=cancel_fd;wire->deadline_ns=deadline;wire->role=role;
    wire->state=role==ES_WIRE_PARENT?P_PREPARE:C_PREPARE;
    wipe(wire->tuple,sizeof(wire->tuple));
    uint64_t current;
    if(!monotonic(&current))return end(wire,ES_WIRE_IO);
    if(deadline<=current)return end(wire,ES_WIRE_DEADLINE);
    if(deadline-current>10000000000ULL || (role!=ES_WIRE_PARENT && role!=ES_WIRE_CHILD)
        || fd<0 || cancel_fd<0)return end(wire,ES_WIRE_PROTOCOL);
    int domain=0,type=0;socklen_t length=sizeof(int);
    int flags=fcntl(fd,F_GETFL),descriptor_flags=fcntl(fd,F_GETFD);
    int cancel_flags=fcntl(cancel_fd,F_GETFL),cancel_descriptor_flags=fcntl(cancel_fd,F_GETFD);
    if(flags<0 || descriptor_flags<0 || cancel_flags<0 || cancel_descriptor_flags<0
        || !(flags&O_NONBLOCK) || !(descriptor_flags&FD_CLOEXEC)
        || !(cancel_flags&O_NONBLOCK) || !(cancel_descriptor_flags&FD_CLOEXEC)
        || getsockopt(fd,SOL_SOCKET,SO_DOMAIN,&domain,&length) || length!=sizeof(int)
        || domain!=AF_UNIX)return end(wire,ES_WIRE_PROTOCOL);
    length=sizeof(int);
    if(getsockopt(fd,SOL_SOCKET,SO_TYPE,&type,&length) || length!=sizeof(int) || type!=SOCK_STREAM)
        return end(wire,ES_WIRE_PROTOCOL);
    return ES_WIRE_OK;
}
static es_wire_result live(es_wire *wire) {
    if(wire->state==TERMINAL)return wire->terminal;
    if(!wire->state)return ES_WIRE_PROTOCOL;
    for(;;) {
        uint64_t current;
        if(!monotonic(&current))return ES_WIRE_IO;
        if(current>=wire->deadline_ns)return ES_WIRE_DEADLINE;
        struct pollfd cancel={wire->cancel_fd,POLLIN,0};
        int result=poll(&cancel,1,0);
        if(result<0){if(errno==EINTR)continue;return ES_WIRE_IO;}
        if(cancel.revents&POLLIN)return ES_WIRE_CANCELLED;
        if(cancel.revents&(POLLERR|POLLHUP|POLLNVAL))return ES_WIRE_IO;
        return ES_WIRE_OK;
    }
}
static es_wire_result incoming_terminal(es_wire *wire);
static es_wire_result ready(es_wire *wire,short events) {
    for(;;) {
        es_wire_result check=live(wire);if(check!=ES_WIRE_OK)return check;
        uint64_t current;if(!monotonic(&current))return ES_WIRE_IO;
        if(current>=wire->deadline_ns)return ES_WIRE_DEADLINE;
        uint64_t remaining=wire->deadline_ns-current;
        struct timespec timeout={(time_t)(remaining/1000000000ULL),(long)(remaining%1000000000ULL)};
        short watched=(short)(events|(events==POLLOUT?POLLIN:0));
        struct pollfd descriptors[2]={{wire->cancel_fd,POLLIN,0},{wire->fd,watched,0}};
        int result=ppoll(descriptors,2,&timeout,NULL);
        if(result<0){if(errno==EINTR)continue;return ES_WIRE_IO;}
        if(!result)continue;
        if(descriptors[0].revents&POLLIN)return ES_WIRE_CANCELLED;
        if(descriptors[0].revents&(POLLERR|POLLHUP|POLLNVAL) || descriptors[1].revents&POLLNVAL)return ES_WIRE_IO;
        /* A valid terminal frame must interrupt backpressured writes too. */
        if(events==POLLOUT && (descriptors[1].revents&POLLIN))return incoming_terminal(wire);
        if(descriptors[1].revents&(events|POLLHUP|POLLERR))return live(wire);
    }
}
/* Ancillary messages never belong to this protocol. Close all delivered rights,
   including when MSG_CTRUNC says additional rights were discarded by the kernel. */
static es_wire_result receive_once(int fd,uint8_t *bytes,size_t length,ssize_t *received) {
    union { struct cmsghdr alignment;uint8_t bytes[CMSG_SPACE(16*sizeof(int))]; } control={0};
    struct iovec vector={bytes,length};struct msghdr message={0};
    message.msg_iov=&vector;message.msg_iovlen=1;message.msg_control=control.bytes;message.msg_controllen=sizeof(control.bytes);
    *received=recvmsg(fd,&message,MSG_DONTWAIT|MSG_CMSG_CLOEXEC);
    int saved_errno=errno;es_wire_result result=ES_WIRE_OK;
    if(*received>=0) {
        for(struct cmsghdr *c=CMSG_FIRSTHDR(&message);c;c=CMSG_NXTHDR(&message,c)) {
            if(result!=ES_WIRE_CLEANUP)result=ES_WIRE_PROTOCOL;
            if(c->cmsg_level==SOL_SOCKET && c->cmsg_type==SCM_RIGHTS && c->cmsg_len>=CMSG_LEN(0)) {
                size_t count=(c->cmsg_len-CMSG_LEN(0))/sizeof(int);
                for(size_t index=0;index<count;index++) {
                    int received_fd;memcpy(&received_fd,CMSG_DATA(c)+index*sizeof(int),sizeof(int));
                    if(close(received_fd)!=0)result=ES_WIRE_CLEANUP;
                }
            }
        }
        if(message.msg_flags&(MSG_CTRUNC|MSG_TRUNC)) {
            if(result!=ES_WIRE_CLEANUP)result=ES_WIRE_PROTOCOL;
        }
    }
    wipe(&control,sizeof(control));errno=saved_errno;return result;
}
static es_wire_result read_exact(es_wire *wire,uint8_t *bytes,size_t length) {
    size_t offset=0;
    while(offset<length) {
        es_wire_result result=ready(wire,POLLIN);if(result!=ES_WIRE_OK)return result;
        ssize_t received;result=receive_once(wire->fd,bytes+offset,length-offset,&received);
        if(result!=ES_WIRE_OK)return result;
        if(received<0){if(errno==EINTR || errno==EAGAIN || errno==EWOULDBLOCK)continue;return ES_WIRE_IO;}
        if(!received)return ES_WIRE_PROTOCOL;
        offset+=(size_t)received;
    }
    return live(wire);
}
static es_wire_result no_extra(es_wire *wire,int require_eof) {
    for(;;) {
        es_wire_result result=require_eof?ready(wire,POLLIN):live(wire);
        if(result!=ES_WIRE_OK)return result;
        uint8_t byte=0;ssize_t received;result=receive_once(wire->fd,&byte,1,&received);wipe(&byte,1);
        if(result!=ES_WIRE_OK)return result;
        if(received>0)return ES_WIRE_PROTOCOL;
        if(received==0)return live(wire);
        if(errno==EINTR)continue;
        if(errno==EAGAIN || errno==EWOULDBLOCK){if(require_eof)continue;return live(wire);}
        return ES_WIRE_IO;
    }
}
static es_wire_result read_frame(es_wire *wire,es_wire_frame *frame) {
    uint8_t bytes[108]={0};
    es_wire_result result=read_exact(wire,bytes,12);size_t length=0;
    if(result==ES_WIRE_OK) {
        length=frame_size(bytes[8]);
        if(!length || memcmp(bytes,magic,8) || bytes[9] || bytes[10] || bytes[11])result=ES_WIRE_PROTOCOL;
        else if(length>12)result=read_exact(wire,bytes+12,length-12);
    }
    if(result==ES_WIRE_OK)result=es_wire_decode(bytes,length,frame);
    if(result==ES_WIRE_OK)result=no_extra(wire,0);
    wipe(bytes,sizeof(bytes));
    if(result!=ES_WIRE_OK)wipe(frame,sizeof(*frame));
    return result;
}
static es_wire_result incoming_terminal(es_wire *wire) {
    es_wire_frame frame={0};es_wire_result result=read_frame(wire,&frame);
    if(result==ES_WIRE_OK) {
        if(wire->role==ES_WIRE_CHILD && frame.type==ES_WIRE_ABORT)result=ES_WIRE_ABORTED;
        else if(wire->role==ES_WIRE_PARENT && frame.type==ES_WIRE_REFUSED)result=ES_WIRE_PEER_REFUSED;
        else result=ES_WIRE_PROTOCOL;
    }
    wipe(&frame,sizeof(frame));return result;
}
es_wire_result es_wire_send(es_wire *wire,const es_wire_frame *frame) {
    if(!wire || !wire->state)return ES_WIRE_PROTOCOL;
    if(wire->state==TERMINAL)return wire->terminal;
    uint8_t bytes[108]={0};size_t length=0;es_wire_result result=es_wire_encode(frame,bytes,&length);
    unsigned next=0;int terminal=0;
    if(result==ES_WIRE_OK) {
        if(wire->role==ES_WIRE_PARENT && frame->type==ES_WIRE_ABORT)terminal=1;
        else if(wire->role==ES_WIRE_CHILD && frame->type==ES_WIRE_REFUSED)terminal=1;
        else if(wire->state==P_CHALLENGE && frame->type==ES_WIRE_CHALLENGE)next=P_ESTABLISHED;
        else if(wire->state==P_ACK && frame->type==ES_WIRE_ACK)next=P_EOF;
        else if(wire->state==C_PREPARE && frame->type==ES_WIRE_PREPARE)next=C_CHALLENGE;
        else if(wire->state==C_ESTABLISHED && frame->type==ES_WIRE_ESTABLISHED)next=C_ACK;
        else result=ES_WIRE_PROTOCOL;
        if(result==ES_WIRE_OK && (next==P_EOF || next==C_ACK) && memcmp(frame->tuple,wire->tuple,84))result=ES_WIRE_PROTOCOL;
    }
    size_t offset=0;
    while(result==ES_WIRE_OK && offset<length) {
        result=ready(wire,POLLOUT);if(result!=ES_WIRE_OK)break;
        ssize_t sent=send(wire->fd,bytes+offset,length-offset,MSG_DONTWAIT|MSG_NOSIGNAL);
        if(sent<0){if(errno==EINTR || errno==EAGAIN || errno==EWOULDBLOCK)continue;result=ES_WIRE_IO;break;}
        if(!sent){result=ES_WIRE_IO;break;}offset+=(size_t)sent;
    }
    if(result==ES_WIRE_OK)result=live(wire);
    wipe(bytes,sizeof(bytes));
    if(result!=ES_WIRE_OK)return end(wire,result);
    if(terminal)return end(wire,ES_WIRE_ABORTED);
    if(next==P_ESTABLISHED)memcpy(wire->tuple,frame->tuple,84);
    wire->state=next;return ES_WIRE_OK;
}
es_wire_result es_wire_receive(es_wire *wire,es_wire_frame *frame) {
    if(frame)wipe(frame,sizeof(*frame));
    if(!wire || !wire->state)return ES_WIRE_PROTOCOL;
    if(wire->state==TERMINAL)return wire->terminal;
    if(!frame)return end(wire,ES_WIRE_PROTOCOL);
    if(wire->state!=P_PREPARE && wire->state!=P_ESTABLISHED && wire->state!=C_CHALLENGE && wire->state!=C_ACK)
        return end(wire,ES_WIRE_PROTOCOL);
    es_wire_frame decoded={0};
    es_wire_result result=read_frame(wire,&decoded);
    unsigned next=0;
    if(result==ES_WIRE_OK) {
        if(wire->role==ES_WIRE_CHILD && decoded.type==ES_WIRE_ABORT)result=ES_WIRE_ABORTED;
        else if(wire->role==ES_WIRE_PARENT && decoded.type==ES_WIRE_REFUSED)result=ES_WIRE_PEER_REFUSED;
        else if(wire->state==P_PREPARE && decoded.type==ES_WIRE_PREPARE)next=P_CHALLENGE;
        else if(wire->state==P_ESTABLISHED && decoded.type==ES_WIRE_ESTABLISHED)next=P_ACK;
        else if(wire->state==C_CHALLENGE && decoded.type==ES_WIRE_CHALLENGE)next=C_ESTABLISHED;
        else if(wire->state==C_ACK && decoded.type==ES_WIRE_ACK)next=C_FINISH;
        else result=ES_WIRE_PROTOCOL;
        if(result==ES_WIRE_OK && (next==P_ACK || next==C_FINISH) && memcmp(decoded.tuple,wire->tuple,84))result=ES_WIRE_PROTOCOL;
    }
    if(result==ES_WIRE_OK) {
        if(next==C_ESTABLISHED)memcpy(wire->tuple,decoded.tuple,84);
        wire->state=next;*frame=decoded;
    }
    wipe(&decoded,sizeof(decoded));
    return result==ES_WIRE_OK?result:end(wire,result);
}
es_wire_result es_wire_finish(es_wire *wire) {
    if(!wire || !wire->state)return ES_WIRE_PROTOCOL;
    if(wire->state==TERMINAL)return wire->terminal;
    if(wire->state!=P_EOF && wire->state!=C_FINISH)return end(wire,ES_WIRE_PROTOCOL);
    es_wire_result result=no_extra(wire,wire->state==P_EOF);
    return end(wire,result==ES_WIRE_OK?ES_WIRE_COMPLETE:result);
}
es_wire_result es_wire_close(es_wire *wire) {
    if(!wire || !wire->state)return ES_WIRE_PROTOCOL;
    return end(wire,ES_WIRE_ABORTED);
}

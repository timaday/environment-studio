#define _GNU_SOURCE
#include "privacy-maps.h"
#include <errno.h>
#include <fcntl.h>
#include <linux/magic.h>
#include <stdio.h>
#include <string.h>
#include <sys/vfs.h>
#include <unistd.h>

static void wipe(void *value,size_t size){volatile unsigned char *p=value;while(size--)*p++=0;}
static int overlap(const void *a,size_t an,const void *b,size_t bn){
    uintptr_t x=(uintptr_t)a,y=(uintptr_t)b;return x<=y?y-x<an:x-y<bn;
}
static es_maps_result peer_result(es_peer_result result){
    switch(result){
        case ES_PEER_OK:return ES_MAPS_OK;
        case ES_PEER_INVALID:return ES_MAPS_INVALID;
        case ES_PEER_UNSUPPORTED:return ES_MAPS_PLATFORM;
        case ES_PEER_DEAD:case ES_PEER_IDENTITY:return ES_MAPS_IDENTITY;
        case ES_PEER_DEADLINE:return ES_MAPS_DEADLINE;
        case ES_PEER_CANCELLED:return ES_MAPS_CANCELLED;
        case ES_PEER_CLEANUP:return ES_MAPS_CLEANUP;
        default:return ES_MAPS_IO;
    }
}
static es_maps_result live(es_peer *peer){
    es_peer_identity identity={0};es_maps_result result=peer_result(es_peer_read(peer,&identity));
    wipe(&identity,sizeof(identity));return result;
}
static es_maps_result combine(es_maps_result first,es_maps_result next){
    return next==ES_MAPS_CLEANUP?next:first==ES_MAPS_OK?next:first;
}
/* One open/fstatfs/close attempt; at most32 EINTR retries across both streams.
   Every attempt also consumes the same original peer deadline. */
static es_maps_result stream(es_peer *peer,es_maps_snapshot *out,int second,unsigned *interrupts){
    char path[64];unsigned char scratch[8192];int fd=-1;size_t used=0;
    es_maps_result result=live(peer);
    if(result)goto done;
    int size=snprintf(path,sizeof(path),"/proc/%u/maps",peer->identity.pid);
    if(size<0||(size_t)size>=sizeof(path)){result=ES_MAPS_INVALID;goto done;}
    fd=open(path,O_RDONLY|O_CLOEXEC|O_NONBLOCK|O_NOFOLLOW);
    result=combine(fd<0?ES_MAPS_IO:ES_MAPS_OK,live(peer));
    if(result)goto finish;
    struct statfs filesystem;
    if(fstatfs(fd,&filesystem))result=ES_MAPS_IO;
    else if(filesystem.f_type!=PROC_SUPER_MAGIC)result=ES_MAPS_PLATFORM;
    result=combine(result,live(peer));
    while(!result){
        size_t capacity=second?sizeof(scratch):ES_MAPS_BYTES-used;
        if(capacity>sizeof(scratch))capacity=sizeof(scratch);
        if(!capacity)capacity=1; /* Exact maximum still requires independent EOF. */
        unsigned char *destination=second||used==ES_MAPS_BYTES?scratch:out->bytes+used;
        result=live(peer);if(result)break;
        ssize_t count=read(fd,destination,capacity);int error=errno;
        result=combine(result,live(peer));if(result)break;
        if(count<0){
            if(error==EINTR&&++*interrupts<=32)continue;
            result=ES_MAPS_IO;break;
        }
        if(!count){
            if(second){if(used!=out->byte_length)result=ES_MAPS_IDENTITY;}
            else if(!used)result=ES_MAPS_FORMAT;
            else out->byte_length=(uint32_t)used;
            break;
        }
        if((size_t)count>capacity){result=ES_MAPS_IO;break;}
        if(second){
            if(used>out->byte_length||(size_t)count>out->byte_length-used||memcmp(scratch,out->bytes+used,(size_t)count)){
                result=ES_MAPS_IDENTITY;break;
            }
        }else if((size_t)count>ES_MAPS_BYTES-used){result=ES_MAPS_RESOURCE;break;}
        used+=(size_t)count;
    }
finish:
    if(fd>=0){int closing=fd;fd=-1;if(close(closing))result=ES_MAPS_CLEANUP;}
    result=combine(result,live(peer));
done:
    wipe(path,sizeof(path));wipe(scratch,sizeof(scratch));return result;
}
static int number(const unsigned char *bytes,size_t end,size_t *position,unsigned radix,uint64_t *value){
    size_t begin=*position;uint64_t n=0;
    while(*position<end){
        unsigned char c=bytes[*position];unsigned digit;
        if(c>='0'&&c<='9')digit=c-'0';
        else if(radix==16&&c>='a'&&c<='f')digit=c-'a'+10;
        else break;
        if(digit>=radix||n>(UINT64_MAX-digit)/radix)return 0;
        n=n*radix+digit;++*position;
        if(radix==16&&*position-begin>16)return 0;
    }
    if(*position==begin)return 0;
    *value=n;return 1;
}
static int take(const unsigned char *bytes,size_t end,size_t *position,unsigned char expected){
    if(*position>=end||bytes[*position]!=expected)return 0;
    ++*position;return 1;
}
static es_maps_result parse(es_peer *peer,es_maps_snapshot *out){
    size_t begin=0;
    while(begin<out->byte_length){
        es_maps_result status=live(peer);if(status)return status;
        size_t end=begin;
        while(end<out->byte_length&&out->bytes[end]!='\n'){
            if(!out->bytes[end])return ES_MAPS_FORMAT;
            if(end-begin>=ES_MAPS_LINE_BYTES-1)return ES_MAPS_RESOURCE;
            ++end;
        }
        if(end==out->byte_length)return ES_MAPS_FORMAT;
        if(out->count==ES_MAPS_RECORDS)return ES_MAPS_RESOURCE;
        size_t position=begin;es_maps_record record={0};uint64_t major=0,minor=0;
        if(!number(out->bytes,end,&position,16,&record.start)||!take(out->bytes,end,&position,'-')
                ||!number(out->bytes,end,&position,16,&record.end)||!take(out->bytes,end,&position,' '))return ES_MAPS_FORMAT;
        if(end-position<4)return ES_MAPS_FORMAT;
        const unsigned char *permissions=out->bytes+position;
        if((permissions[0]!='r'&&permissions[0]!='-')||(permissions[1]!='w'&&permissions[1]!='-')
                ||(permissions[2]!='x'&&permissions[2]!='-')||(permissions[3]!='p'&&permissions[3]!='s'))return ES_MAPS_FORMAT;
        record.read=permissions[0]=='r';record.write=permissions[1]=='w';record.execute=permissions[2]=='x';record.shared=permissions[3]=='s';position+=4;
        if(!take(out->bytes,end,&position,' ')||!number(out->bytes,end,&position,16,&record.offset)
                ||!take(out->bytes,end,&position,' ')||!number(out->bytes,end,&position,16,&major)
                ||!take(out->bytes,end,&position,':')||!number(out->bytes,end,&position,16,&minor)
                ||!take(out->bytes,end,&position,' ')||!number(out->bytes,end,&position,10,&record.inode))return ES_MAPS_FORMAT;
        if(major>UINT32_MAX||minor>UINT32_MAX||record.start>=record.end
                ||(out->count&&out->records[out->count-1].end>record.start))return ES_MAPS_FORMAT;
        if(position<end&&out->bytes[position]!=' ')return ES_MAPS_FORMAT;
        record.device_major=(uint32_t)major;record.device_minor=(uint32_t)minor;
        record.label_offset=(uint32_t)position;record.label_length=(uint32_t)(end-position);
        out->records[out->count++]=record;begin=end+1;
    }
    return ES_MAPS_OK;
}
es_maps_result es_maps_sample(es_peer *peer,es_maps_snapshot *out){
    if(!out)return ES_MAPS_INVALID;
    if(peer&&overlap(peer,sizeof(*peer),out,sizeof(*out)))return ES_MAPS_INVALID;
    wipe(out,sizeof(*out));
    if(!peer||peer->state!=1||peer->pidfd<0)return ES_MAPS_INVALID;
    es_maps_result result=peer_result(es_peer_read(peer,&out->identity));unsigned interrupts=0;
    if(!result)result=stream(peer,out,0,&interrupts);
    if(!result)result=parse(peer,out);
    if(!result)result=stream(peer,out,1,&interrupts);
    result=combine(result,live(peer));
    if(result)wipe(out,sizeof(*out));
    return result;
}

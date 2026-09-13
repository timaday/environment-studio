#define _GNU_SOURCE
#include "privacy-image.h"
#include <fcntl.h>
#include <linux/magic.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/vfs.h>
#include <unistd.h>

static void wipe(void *memory,size_t length) {
    volatile unsigned char *bytes=memory;
    while(length--)*bytes++=0;
}
static int span(const void *pointer,size_t length) {
    return pointer&&length&&(uintptr_t)pointer<=UINTPTR_MAX-(length-1);
}
static int overlap(const void *a,size_t an,const void *b,size_t bn) {
    if(!a||!b)return 0;
    uintptr_t x=(uintptr_t)a,y=(uintptr_t)b;
    return x>=y?x-y<bn:y-x<an;
}
static es_image_result peer_result(es_peer_result result) {
    switch(result) {
        case ES_PEER_OK:return ES_IMAGE_OK;
        case ES_PEER_INVALID:return ES_IMAGE_INVALID;
        case ES_PEER_UNSUPPORTED:return ES_IMAGE_PLATFORM;
        case ES_PEER_DEAD:case ES_PEER_IDENTITY:return ES_IMAGE_IDENTITY;
        case ES_PEER_DEADLINE:return ES_IMAGE_DEADLINE;
        case ES_PEER_CANCELLED:return ES_IMAGE_CANCELLED;
        case ES_PEER_CLEANUP:return ES_IMAGE_CLEANUP;
        case ES_PEER_IO:return ES_IMAGE_IO;
    }
    return ES_IMAGE_IO;
}
static es_image_result hash_result(es_hash_result result) {
    switch(result) {
        case ES_HASH_OK:return ES_IMAGE_OK;
        case ES_HASH_INVALID:return ES_IMAGE_INVALID;
        case ES_HASH_PLATFORM:return ES_IMAGE_PLATFORM;
        case ES_HASH_CANCELLED:return ES_IMAGE_CANCELLED;
        case ES_HASH_DEADLINE:return ES_IMAGE_DEADLINE;
        case ES_HASH_RESOURCE:return ES_IMAGE_RESOURCE;
        case ES_HASH_FILE:return ES_IMAGE_IDENTITY;
        case ES_HASH_CLEANUP:return ES_IMAGE_CLEANUP;
        case ES_HASH_CRYPTO:case ES_HASH_IO:return ES_IMAGE_IO;
    }
    return ES_IMAGE_IO;
}
static es_image_result live(es_peer *peer) {
    es_peer_identity identity={0};
    es_image_result result=peer_result(es_peer_read(peer,&identity));
    wipe(&identity,sizeof identity);
    return result;
}
static void release(int *owned,int *uncertain) {
    int fd=*owned;*owned=-1;
    if(fd>=0&&close(fd))*uncertain=1;
}
static es_image_result descriptor(es_peer *peer,int fd,int directory) {
    struct stat metadata={0};struct statfs filesystem={0};
    es_image_result result=live(peer);
    if(result)goto done;
    int flags=fcntl(fd,F_GETFD);result=live(peer);if(result)goto done;
    if(flags<0||!(flags&FD_CLOEXEC)){result=ES_IMAGE_IO;goto done;}
    flags=fcntl(fd,F_GETFL);result=live(peer);if(result)goto done;
    if(flags<0||(flags&O_ACCMODE)!=O_RDONLY||(flags&O_PATH)){result=ES_IMAGE_IO;goto done;}
    int status=fstat(fd,&metadata);result=live(peer);if(result)goto done;
    if(status){result=ES_IMAGE_IO;goto done;}
    if(directory?!S_ISDIR(metadata.st_mode):!S_ISREG(metadata.st_mode)){result=ES_IMAGE_IDENTITY;goto done;}
    if(directory) {
        status=fstatfs(fd,&filesystem);result=live(peer);if(result)goto done;
        if(status){result=ES_IMAGE_IO;goto done;}
        if(filesystem.f_type!=PROC_SUPER_MAGIC)result=ES_IMAGE_PLATFORM;
    }
 done:
    wipe(&metadata,sizeof metadata);wipe(&filesystem,sizeof filesystem);return result;
}
static int equal(const es_hash_identity *a,const es_hash_identity *b) {
    return a->device==b->device&&a->inode==b->inode&&a->size==b->size&&!memcmp(a->sha256,b->sha256,32);
}
static es_image_result acquisition(es_peer *peer,es_hash *hash,const es_hash_identity *expected) {
    int proc=-1,pid=-1,exe=-1,uncertain=0;
    char number[16]={0};es_hash_identity actual={0};
    es_image_result result=live(peer);
    if(result)goto done;
    proc=open("/proc",O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_NONBLOCK|O_CLOEXEC);
    result=live(peer);if(result)goto done;
    if(proc<0){result=ES_IMAGE_IO;goto done;}
    result=descriptor(peer,proc,1);if(result)goto done;
    int length=snprintf(number,sizeof number,"%u",peer->identity.pid);
    if(length<=0||(size_t)length>=sizeof number){result=ES_IMAGE_IDENTITY;goto done;}
    result=live(peer);if(result)goto done;
    pid=openat(proc,number,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_NONBLOCK|O_CLOEXEC);
    result=live(peer);if(result)goto done;
    if(pid<0){result=ES_IMAGE_IDENTITY;goto done;}
    result=descriptor(peer,pid,1);if(result)goto done;
    result=live(peer);if(result)goto done;
    /* Only the fixed magic link of this verified pinned process directory follows. */
    exe=openat(pid,"exe",O_RDONLY|O_NONBLOCK|O_CLOEXEC|O_NOCTTY);
    result=live(peer);if(result)goto done;
    if(exe<0){result=ES_IMAGE_IDENTITY;goto done;}
    result=descriptor(peer,exe,0);if(result)goto done;
    result=hash_result(es_hash_file(hash,exe,&actual));
    if(!result&&!equal(expected,&actual))result=ES_IMAGE_IDENTITY;
 done:
    release(&exe,&uncertain);release(&pid,&uncertain);release(&proc,&uncertain);
    es_image_result final=live(peer);
    if(result!=ES_IMAGE_CLEANUP&&final)result=final;
    if(uncertain||result==ES_IMAGE_CLEANUP)result=ES_IMAGE_CLEANUP;
    wipe(number,sizeof number);wipe(&actual,sizeof actual);return result;
}
es_image_result es_image_check(es_peer *peer,const es_file *file,es_hash *hash,
        const unsigned char expected[32],es_hash_identity *out) {
    if(!span(out,sizeof(*out))||overlap(out,sizeof(*out),peer,sizeof(*peer))
            ||overlap(out,sizeof(*out),file,sizeof(*file))||overlap(out,sizeof(*out),hash,sizeof(*hash))
            ||overlap(out,sizeof(*out),expected,32))return ES_IMAGE_INVALID;
    wipe(out,sizeof(*out));
    if(!span(peer,sizeof(*peer))||!span(file,sizeof(*file))||!span(hash,sizeof(*hash))||!span(expected,32)
            ||overlap(peer,sizeof(*peer),file,sizeof(*file))||overlap(peer,sizeof(*peer),hash,sizeof(*hash))
            ||overlap(file,sizeof(*file),hash,sizeof(*hash))||overlap(expected,32,peer,sizeof(*peer))
            ||overlap(expected,32,file,sizeof(*file))||overlap(expected,32,hash,sizeof(*hash)))return ES_IMAGE_INVALID;
    if(peer->state!=1||peer->terminal!=ES_PEER_OK||peer->pidfd<0||!peer->identity.pid||!peer->identity.start_ticks
            ||file->state!=1||file->terminal!=ES_FILE_OK||file->cleanup!=ES_FILE_OK||file->fd<0
            ||hash->state!=1||hash->terminal!=ES_HASH_OK||hash->cleanup!=ES_HASH_OK
            ||!hash->library||!hash->provider||!hash->algorithm||peer->cancel_fd<0
            ||peer->cancel_fd!=file->cancel_fd||peer->cancel_fd!=hash->cancel_fd
            ||peer->deadline_ns!=file->deadline_ns||peer->deadline_ns!=hash->deadline_ns
            ||peer->pidfd==file->fd||peer->pidfd==peer->cancel_fd||file->fd==peer->cancel_fd)return ES_IMAGE_INVALID;
    es_hash_identity trusted={0};es_image_result result=live(peer);
    if(result)goto done;
    result=hash_result(es_hash_file(hash,file->fd,&trusted));if(result)goto done;
    result=live(peer);if(result)goto done;
    if(memcmp(trusted.sha256,expected,32)){result=ES_IMAGE_IDENTITY;goto done;}
    result=acquisition(peer,hash,&trusted);if(result)goto done;
    result=acquisition(peer,hash,&trusted);
 done:
    es_image_result final=live(peer);
    if(result!=ES_IMAGE_CLEANUP&&final)result=final;
    if(!result)*out=trusted;
    wipe(&trusted,sizeof trusted);return result;
}

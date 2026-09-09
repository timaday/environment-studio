#define _GNU_SOURCE
#include "privacy-elf.h"
#include <errno.h>
#include <fcntl.h>
#include <linux/magic.h>
#include <poll.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/vfs.h>
#include <sys/xattr.h>
#include <time.h>
#include <unistd.h>

static void wipe(void *memory,size_t length) {volatile unsigned char *p=memory;while(length--)*p++=0;}
static int span(const void *p,size_t n) {return p&&n&&(uintptr_t)p<=UINTPTR_MAX-(n-1);}
static int overlap(const void *a,size_t an,const void *b,size_t bn) {
 if(!a||!b)return 0;
 uintptr_t x=(uintptr_t)a,y=(uintptr_t)b;return x>=y?x-y<bn:y-x<an;
}
static es_elf_result hashed(es_hash_result result) {
 switch(result) {
  case ES_HASH_OK:return ES_ELF_OK;
  case ES_HASH_INVALID:return ES_ELF_INVALID;
  case ES_HASH_PLATFORM:return ES_ELF_PLATFORM;
  case ES_HASH_CANCELLED:return ES_ELF_CANCELLED;
  case ES_HASH_DEADLINE:return ES_ELF_DEADLINE;
  case ES_HASH_RESOURCE:return ES_ELF_RESOURCE;
  case ES_HASH_FILE:return ES_ELF_IDENTITY;
  case ES_HASH_CRYPTO:case ES_HASH_IO:return ES_ELF_IO;
  case ES_HASH_CLEANUP:return ES_ELF_CLEANUP;
 }
 return ES_ELF_PLATFORM;
}
static es_elf_result guard(const es_file *file) {
 struct pollfd cancel={.fd=file->cancel_fd,.events=POLLIN};
 if(poll(&cancel,1,0)<0)return ES_ELF_IO;
 if(cancel.revents&POLLIN)return ES_ELF_CANCELLED;
 if(cancel.revents)return ES_ELF_IO;
 struct timespec now;
 if(clock_gettime(CLOCK_MONOTONIC,&now)||now.tv_sec<0||now.tv_nsec<0||now.tv_nsec>=1000000000L)return ES_ELF_IO;
 if((uint64_t)now.tv_sec>(UINT64_MAX-(uint64_t)now.tv_nsec)/1000000000)return ES_ELF_IO;
 uint64_t ns=(uint64_t)now.tv_sec*1000000000+(uint64_t)now.tv_nsec;
 if(ns>=file->deadline_ns)return ES_ELF_DEADLINE;
 return file->deadline_ns-ns>UINT64_C(10000000000)?ES_ELF_INVALID:ES_ELF_OK;
}
static es_elf_result descriptor(const es_file *file,struct stat *metadata) {
 es_elf_result result=guard(file);if(result)return result;
 int flags=fcntl(file->fd,F_GETFL),fdflags=fcntl(file->fd,F_GETFD);result=guard(file);if(result)return result;
 if(flags<0||fdflags<0||(flags&O_ACCMODE)!=O_RDONLY||(flags&O_PATH)||!(fdflags&FD_CLOEXEC))return ES_ELF_IDENTITY;
 int status=fstat(file->fd,metadata);result=guard(file);if(result)return result;
 if(status)return ES_ELF_IO;
 if(getuid()!=geteuid())return ES_ELF_PLATFORM;
 if(!S_ISREG(metadata->st_mode)||metadata->st_size<0||(metadata->st_mode&06022)
       ||(metadata->st_uid!=0&&metadata->st_uid!=getuid()))return ES_ELF_IDENTITY;
 struct statfs fs={0};status=fstatfs(file->fd,&fs);result=guard(file);
 if(!result&&status)result=ES_ELF_IO;
 if(!result&&fs.f_type!=EXT4_SUPER_MAGIC&&fs.f_type!=TMPFS_MAGIC&&fs.f_type!=OVERLAYFS_SUPER_MAGIC)result=ES_ELF_PLATFORM;
 wipe(&fs,sizeof(fs));if(result)return result;
 ssize_t caps=fgetxattr(file->fd,"security.capability",NULL,0);int error=errno;result=guard(file);if(result)return result;
 if(caps>=0)return ES_ELF_IDENTITY;
 return error==ENODATA?ES_ELF_OK:error==ENOTSUP?ES_ELF_PLATFORM:ES_ELF_IO;
}
static int unchanged(const struct stat *a,const struct stat *b) {
 return a->st_dev==b->st_dev&&a->st_ino==b->st_ino&&a->st_size==b->st_size&&a->st_mode==b->st_mode
  &&a->st_uid==b->st_uid&&a->st_gid==b->st_gid&&a->st_mtim.tv_sec==b->st_mtim.tv_sec
  &&a->st_mtim.tv_nsec==b->st_mtim.tv_nsec&&a->st_ctim.tv_sec==b->st_ctim.tv_sec&&a->st_ctim.tv_nsec==b->st_ctim.tv_nsec;
}
static int same(const es_hash_identity *a,const es_hash_identity *b) {
 return a->device==b->device&&a->inode==b->inode&&a->size==b->size&&!memcmp(a->sha256,b->sha256,32);
}
static es_elf_result read_exact(const es_file *file,uint64_t offset,unsigned char *out,size_t size) {
 size_t used=0;
 while(used<size) {
  es_elf_result result=guard(file);if(result)return result;
  ssize_t count=pread(file->fd,out+used,size-used,(off_t)(offset+used));int error=errno;
  result=guard(file);if(result)return result;
  if(count<0){if(error==EINTR)continue;return ES_ELF_IO;}
  if(!count||(size_t)count>size-used)return ES_ELF_FORMAT;
  used+=(size_t)count;
 }
 return ES_ELF_OK;
}
static uint16_t u16(const unsigned char *p) {return (uint16_t)((uint16_t)p[0]|(uint16_t)p[1]<<8);}
static uint32_t u32(const unsigned char *p) {return (uint32_t)p[0]|(uint32_t)p[1]<<8|(uint32_t)p[2]<<16|(uint32_t)p[3]<<24;}
static uint64_t u64(const unsigned char *p) {return (uint64_t)u32(p)|(uint64_t)u32(p+4)<<32;}
static es_elf_result header(const unsigned char *b,es_elf_layout *layout) {
 if(memcmp(b,"\177ELF",4)||b[4]!=2||b[5]!=1||b[6]!=1||(b[7]!=0&&b[7]!=3)||b[8])return ES_ELF_FORMAT;
 for(unsigned i=9;i<16;i++)if(b[i])return ES_ELF_FORMAT;
 memcpy(layout->ident,b,16);layout->type=u16(b+16);layout->machine=u16(b+18);layout->version=u32(b+20);
 layout->entry=u64(b+24);layout->phoff=u64(b+32);layout->shoff=u64(b+40);layout->flags=u32(b+48);
 layout->ehsize=u16(b+52);layout->phentsize=u16(b+54);layout->phnum=u16(b+56);
 layout->shentsize=u16(b+58);layout->shnum=u16(b+60);layout->shstrndx=u16(b+62);
 if((layout->type!=2&&layout->type!=3)||layout->machine!=62||layout->version!=1||layout->flags
    ||layout->ehsize!=64||layout->phentsize!=56||!layout->phnum||layout->phnum==UINT16_MAX)return ES_ELF_FORMAT;
 if(layout->phnum>128)return ES_ELF_RESOURCE;
 if(layout->phoff<64||layout->phoff>layout->file.size||(uint64_t)layout->phnum*56>layout->file.size-layout->phoff)return ES_ELF_FORMAT;
 return ES_ELF_OK;
}
static int singleton(uint32_t type) {
 switch(type) {
  case 2:return 0;case 3:return 1;case 6:return 2;case 7:return 3;
  case 0x6474e550:return 4;case 0x6474e551:return 5;case 0x6474e552:return 6;case 0x6474e553:return 7;
  default:return -1;
 }
}
static int covered(const es_elf_program *p,const es_elf_program *load) {
 if(p->offset<load->offset||p->vaddr<load->vaddr)return 0;
 uint64_t delta=p->offset-load->offset;
 return delta==p->vaddr-load->vaddr&&delta<=load->filesz&&p->filesz<=load->filesz-delta
  &&delta<=load->memsz&&p->memsz<=load->memsz-delta;
}
static es_elf_result programs(const es_file *file,const unsigned char *table,es_elf_layout *layout) {
 unsigned seen=0;uint64_t previous=0,last_end=0;int have_load=0,have_nonempty=0;
 for(unsigned i=0;i<layout->phnum;i++) {
  es_elf_result result=guard(file);if(result)return result;
  const unsigned char *raw=table+i*56;es_elf_program *p=&layout->programs[i];
  p->type=u32(raw);p->flags=u32(raw+4);p->offset=u64(raw+8);p->vaddr=u64(raw+16);p->paddr=u64(raw+24);
  p->filesz=u64(raw+32);p->memsz=u64(raw+40);p->align=u64(raw+48);
  if(!p->type)continue;
  int one=singleton(p->type);
  if(p->type!=1&&p->type!=4&&one<0)return ES_ELF_FORMAT;
  if(one>=0){unsigned bit=1u<<(unsigned)one;if(seen&bit)return ES_ELF_FORMAT;seen|=bit;}
  if(p->flags&~7u||p->filesz>p->memsz||p->offset>layout->file.size||p->filesz>layout->file.size-p->offset
     ||p->memsz>UINT64_MAX-p->vaddr||p->memsz>UINT64_MAX-p->paddr
     ||(p->align>1&&(p->align&(p->align-1))))return ES_ELF_FORMAT;
  if((p->type==1||p->type==7)&&p->align>1&&p->vaddr%p->align!=p->offset%p->align)return ES_ELF_FORMAT;
  if(p->type==1) {
   if(++layout->load_count>32)return ES_ELF_RESOURCE;
   if((p->flags&3u)==3u||(p->vaddr&4095)!=(p->offset&4095)||(have_load&&p->vaddr<previous))return ES_ELF_FORMAT;
   if(p->memsz){if(have_nonempty&&p->vaddr<last_end)return ES_ELF_FORMAT;last_end=p->vaddr+p->memsz;have_nonempty=1;}
   previous=p->vaddr;have_load=1;
  }
  if((p->type==3||p->type==6)&&have_load)return ES_ELF_FORMAT;
  if(p->type==6&&(p->offset!=layout->phoff||p->filesz!=(uint64_t)layout->phnum*56||p->filesz!=p->memsz))return ES_ELF_FORMAT;
  if(p->type==0x6474e551&&((p->flags&1)||p->offset||p->vaddr||p->paddr||p->filesz||p->memsz))return ES_ELF_FORMAT;
 }
 if(!layout->load_count)return ES_ELF_FORMAT;
 for(unsigned i=0;i<layout->phnum;i++) {
  es_elf_result result=guard(file);if(result)return result;
  const es_elf_program *p=&layout->programs[i];
  if(p->type==0||p->type==1||p->type==4||p->type==0x6474e551||(!p->filesz&&!p->memsz))continue;
  int found=0;
  for(unsigned j=0;j<layout->phnum;j++)if(layout->programs[j].type==1&&covered(p,&layout->programs[j])){found=1;break;}
  if(!found)return ES_ELF_FORMAT;
 }
 return ES_ELF_OK;
}
es_elf_result es_elf_check(const es_file *file,es_hash *hash,const unsigned char expected[32],es_elf_layout *out) {
 if(!span(out,sizeof(*out))||overlap(out,sizeof(*out),file,sizeof(*file))||overlap(out,sizeof(*out),hash,sizeof(*hash))
    ||overlap(out,sizeof(*out),expected,32))return ES_ELF_INVALID;
 wipe(out,sizeof(*out));
 if(!span(file,sizeof(*file))||!span(hash,sizeof(*hash))||!span(expected,32)
    ||overlap(file,sizeof(*file),hash,sizeof(*hash))||overlap(file,sizeof(*file),expected,32)||overlap(hash,sizeof(*hash),expected,32))return ES_ELF_INVALID;
 if(file->state!=1||file->terminal!=ES_FILE_OK||file->cleanup!=ES_FILE_OK||file->fd<0||file->cancel_fd<0
    ||hash->state!=1||hash->terminal!=ES_HASH_OK||hash->cleanup!=ES_HASH_OK||!hash->library||!hash->provider||!hash->algorithm
    ||file->cancel_fd!=hash->cancel_fd||file->deadline_ns!=hash->deadline_ns||file->fd==file->cancel_fd)return ES_ELF_INVALID;
 unsigned char head[64]={0},table[7168]={0};es_elf_layout layout={0};es_hash_identity after={0};struct stat initial={0},final={0};
 wipe(&layout,sizeof(layout));
 es_elf_result result=descriptor(file,&initial);if(result)goto done;
 result=hashed(es_hash_file(hash,file->fd,&layout.file));if(result)goto done;
 if(memcmp(layout.file.sha256,expected,32)){result=ES_ELF_IDENTITY;goto done;}
 if(layout.file.size<sizeof(head)){result=ES_ELF_FORMAT;goto done;}
 result=read_exact(file,0,head,sizeof(head));if(result)goto done;
 result=header(head,&layout);if(result)goto done;
 result=read_exact(file,layout.phoff,table,(size_t)layout.phnum*56);if(result)goto done;
 result=programs(file,table,&layout);if(result)goto done;
 result=hashed(es_hash_file(hash,file->fd,&after));if(result)goto done;
 if(!same(&layout.file,&after)||memcmp(after.sha256,expected,32)){result=ES_ELF_IDENTITY;goto done;}
 done:;
 es_elf_result last=descriptor(file,&final);
 if(result!=ES_ELF_CLEANUP&&last)result=last;
 if(!result&&!unchanged(&initial,&final))result=ES_ELF_IDENTITY;
 if(hash->cleanup==ES_HASH_CLEANUP||hash->terminal==ES_HASH_CLEANUP||file->cleanup==ES_FILE_CLEANUP)result=ES_ELF_CLEANUP;
 if(!result)memcpy(out,&layout,sizeof(layout));
 wipe(head,sizeof(head));wipe(table,sizeof(table));wipe(&layout,sizeof(layout));wipe(&after,sizeof(after));wipe(&initial,sizeof(initial));wipe(&final,sizeof(final));return result;
}

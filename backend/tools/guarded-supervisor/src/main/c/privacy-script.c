#define _GNU_SOURCE
#include "privacy-script.h"
#include "privacy-arguments.h"
#include "privacy-image.h"
#include <errno.h>
#include <string.h>
#include <unistd.h>

static void wipe(void *memory,size_t length){volatile unsigned char *p=memory;while(length--)*p++=0;}
static int span(const void *p,size_t n){return p&&n&&(uintptr_t)p<=UINTPTR_MAX-(n-1);}
static int overlaps(const void *a,size_t an,const void *b,size_t bn){
 if(!a||!b||!an||!bn)return 0;
 uintptr_t x=(uintptr_t)a,y=(uintptr_t)b;return x>=y?x-y<bn:y-x<an;
}
static es_script_result peer_result(es_peer_result r){
 switch(r){
 case ES_PEER_OK:return ES_SCRIPT_OK;case ES_PEER_INVALID:return ES_SCRIPT_INVALID;
 case ES_PEER_UNSUPPORTED:return ES_SCRIPT_PLATFORM;case ES_PEER_DEAD:case ES_PEER_IDENTITY:return ES_SCRIPT_IDENTITY;
 case ES_PEER_DEADLINE:return ES_SCRIPT_DEADLINE;case ES_PEER_CANCELLED:return ES_SCRIPT_CANCELLED;
 case ES_PEER_IO:return ES_SCRIPT_IO;case ES_PEER_CLEANUP:return ES_SCRIPT_CLEANUP;
 }return ES_SCRIPT_IO;
}
static es_script_result hash_result(es_hash_result r){
 switch(r){
 case ES_HASH_OK:return ES_SCRIPT_OK;case ES_HASH_INVALID:return ES_SCRIPT_INVALID;
 case ES_HASH_PLATFORM:return ES_SCRIPT_PLATFORM;case ES_HASH_CANCELLED:return ES_SCRIPT_CANCELLED;
 case ES_HASH_DEADLINE:return ES_SCRIPT_DEADLINE;case ES_HASH_RESOURCE:return ES_SCRIPT_RESOURCE;
 case ES_HASH_FILE:return ES_SCRIPT_IDENTITY;case ES_HASH_CRYPTO:case ES_HASH_IO:return ES_SCRIPT_IO;
 case ES_HASH_CLEANUP:return ES_SCRIPT_CLEANUP;
 }return ES_SCRIPT_IO;
}
static es_script_result file_result(es_file_result r){
 switch(r){
 case ES_FILE_OK:return ES_SCRIPT_OK;case ES_FILE_INVALID:return ES_SCRIPT_INVALID;
 case ES_FILE_PLATFORM:return ES_SCRIPT_PLATFORM;case ES_FILE_CANCELLED:return ES_SCRIPT_CANCELLED;
 case ES_FILE_DEADLINE:return ES_SCRIPT_DEADLINE;case ES_FILE_TRUST:return ES_SCRIPT_IDENTITY;
 case ES_FILE_IO:return ES_SCRIPT_IO;case ES_FILE_CLEANUP:return ES_SCRIPT_CLEANUP;
 }return ES_SCRIPT_IO;
}
static es_script_result image_result(es_image_result r){
 switch(r){
 case ES_IMAGE_OK:return ES_SCRIPT_OK;case ES_IMAGE_INVALID:return ES_SCRIPT_INVALID;
 case ES_IMAGE_PLATFORM:return ES_SCRIPT_PLATFORM;case ES_IMAGE_CANCELLED:return ES_SCRIPT_CANCELLED;
 case ES_IMAGE_DEADLINE:return ES_SCRIPT_DEADLINE;case ES_IMAGE_IDENTITY:return ES_SCRIPT_IDENTITY;
 case ES_IMAGE_RESOURCE:return ES_SCRIPT_RESOURCE;case ES_IMAGE_IO:return ES_SCRIPT_IO;
 case ES_IMAGE_CLEANUP:return ES_SCRIPT_CLEANUP;
 }return ES_SCRIPT_IO;
}
static es_script_result live(es_peer *peer){
 es_peer_identity identity={0};es_script_result r=peer_result(es_peer_read(peer,&identity));wipe(&identity,sizeof identity);return r;
}
/* Lexical trusted-file path contract only; es_file_open establishes filesystem trust. */
static int path_valid(const char *p,size_t n){
 if(n<2||n>1024||p[0]!='/'||p[n-1]=='/')return 0;
 size_t start=1;
 for(size_t i=0;i<n;){
  unsigned char c=(unsigned char)p[i];if(!c||c=='\r'||c=='\n')return 0;
  if(c=='/'&&i){size_t width=i-start;if(!width||(width==1&&p[start]=='.')||(width==2&&p[start]=='.'&&p[start+1]=='.'))return 0;start=i+1;}
  if(c<128){i++;continue;}
  unsigned count;uint32_t value,minimum;
  if(c>=0xc2&&c<=0xdf){count=2;value=c&31;minimum=0x80;}
  else if(c>=0xe0&&c<=0xef){count=3;value=c&15;minimum=0x800;}
  else if(c>=0xf0&&c<=0xf4){count=4;value=c&7;minimum=0x10000;}
  else return 0;
  if(count>n-i)return 0;
  for(unsigned j=1;j<count;j++){unsigned char next=(unsigned char)p[i+j];if((next&0xc0)!=0x80)return 0;value=(value<<6)|(next&63);}
  if(value<minimum||value>0x10ffff||(value>=0xd800&&value<=0xdfff))return 0;
  i+=count;
 }
 size_t width=n-start;return width&&!(width==1&&p[start]=='.')&&!(width==2&&p[start]=='.'&&p[start+1]=='.');
}
static int expected_valid(const es_script_expected *e){
 if(!path_valid(e->script_path,e->script_path_length)||e->shebang_length<5||e->shebang_length>255
    ||e->arguments_length>16384||e->shebang[0]!='#'||e->shebang[1]!='!'||e->shebang[2]!='/'
    ||e->shebang[e->shebang_length-1]!='\n')return 0;
 size_t end=e->shebang_length-1,token=end;
 for(size_t i=2;i<end;i++){
  unsigned char c=e->shebang[i];if(!c||c=='\r'||c=='\n'||c=='\t')return 0;
  if(c==' '){token=i;break;}
 }
 int option=token!=end;
 if(token<=3||(option&&(end-token!=3||memcmp(e->shebang+token," -e",3))))return 0;
 if(!path_valid((const char*)e->shebang+2,token-2)||e->script_index!=(unsigned)(option?2:1))return 0;
 size_t start=0;unsigned count=0;int found=0;
 for(size_t i=0;i<e->arguments_length;i++){
  if(e->arguments[i]){if(i-start>=1024)return 0;continue;}
  size_t width=i-start;if(++count>128)return 0;
  if(count==1&&(width!=token-2||memcmp(e->arguments+start,e->shebang+2,width)))return 0;
  if(option&&count==2&&(width!=2||memcmp(e->arguments+start,"-e",2)))return 0;
  if(count-1==e->script_index){if(width!=e->script_path_length||memcmp(e->arguments+start,e->script_path,width))return 0;found=1;}
  start=i+1;
 }
 return start==e->arguments_length&&found;
}
static int preflight(es_peer *peer,const es_file *script,const es_file *interpreter,es_hash *hash,
 const es_script_expected *expected,es_script_identity *out){
 const void *p[8]={peer,script,interpreter,hash,expected,NULL,NULL,NULL};
 size_t n[8]={sizeof(*peer),sizeof(*script),sizeof(*interpreter),sizeof(*hash),sizeof(*expected),0,0,0};
 if(!span(out,sizeof(*out)))return 0;
 for(unsigned i=0;i<5;i++)if(overlaps(out,sizeof(*out),p[i],n[i]))return 0;
 if(span(expected,sizeof(*expected))){
  p[5]=expected->script_path;n[5]=expected->script_path_length;
  p[6]=expected->shebang;n[6]=expected->shebang_length;
  p[7]=expected->arguments;n[7]=expected->arguments_length;
  for(unsigned i=5;i<8;i++)if(overlaps(out,sizeof(*out),p[i],n[i]))return 0;
 }
 wipe(out,sizeof(*out));
 for(unsigned i=0;i<8;i++)if(!span(p[i],n[i]))return 0;
 for(unsigned i=0;i<4;i++)for(unsigned j=i+1;j<8;j++)if(overlaps(p[i],n[i],p[j],n[j]))return 0;
 if(peer->state!=1||peer->terminal!=ES_PEER_OK||peer->pidfd<0||peer->cancel_fd<0||!peer->identity.pid||!peer->identity.start_ticks
    ||script->state!=1||script->terminal!=ES_FILE_OK||script->cleanup!=ES_FILE_OK||script->fd<0
    ||interpreter->state!=1||interpreter->terminal!=ES_FILE_OK||interpreter->cleanup!=ES_FILE_OK||interpreter->fd<0
    ||hash->state!=1||hash->terminal!=ES_HASH_OK||hash->cleanup!=ES_HASH_OK||!hash->library||!hash->provider||!hash->algorithm)return 0;
 if(script->cancel_fd!=peer->cancel_fd||interpreter->cancel_fd!=peer->cancel_fd||hash->cancel_fd!=peer->cancel_fd
    ||script->deadline_ns!=peer->deadline_ns||interpreter->deadline_ns!=peer->deadline_ns||hash->deadline_ns!=peer->deadline_ns)return 0;
 int fds[]={peer->pidfd,peer->cancel_fd,script->fd,interpreter->fd};
 for(unsigned i=0;i<4;i++)for(unsigned j=i+1;j<4;j++)if(fds[i]==fds[j])return 0;
 return expected_valid(expected);
}
static int identity_equal(const es_hash_identity *a,const es_hash_identity *b){
 return a->device==b->device&&a->inode==b->inode&&a->size==b->size&&!memcmp(a->sha256,b->sha256,32);
}
es_script_result es_script_check(es_peer *peer,const es_file *script,const es_file *interpreter,es_hash *hash,
 const es_script_expected *expected,es_script_identity *out){
 if(!preflight(peer,script,interpreter,hash,expected,out))return ES_SCRIPT_INVALID;
 unsigned char prefix[256]={0};es_script_identity measured={0};es_hash_identity reopened={0};es_file current={0};
 es_script_result result=live(peer);size_t used=0;
 if(result)goto done;
 result=peer_result(es_arguments_check(peer,expected->arguments,expected->arguments_length));if(result)goto done;
 while(used<expected->shebang_length){
  result=live(peer);if(result)goto done;
  ssize_t count=pread(script->fd,prefix+used,expected->shebang_length-used,(off_t)used);int error=errno;
  result=live(peer);if(result)goto done;
  if(count<0){if(error==EINTR)continue;result=ES_SCRIPT_IO;goto done;}
  if(!count){result=ES_SCRIPT_IDENTITY;goto done;}used+=(size_t)count;
 }
 if(memcmp(prefix,expected->shebang,expected->shebang_length)){result=ES_SCRIPT_IDENTITY;goto done;}
 result=hash_result(es_hash_file(hash,script->fd,&measured.script));if(result)goto done;
 if(memcmp(measured.script.sha256,expected->script_sha256,32)){result=ES_SCRIPT_IDENTITY;goto done;}
 result=image_result(es_image_check(peer,interpreter,hash,expected->interpreter_sha256,&measured.interpreter));if(result)goto done;
 result=file_result(es_file_open(&current,peer->cancel_fd,peer->deadline_ns,expected->script_path,expected->script_path_length));if(result)goto done;
 result=hash_result(es_hash_file(hash,current.fd,&reopened));if(result)goto done;
 if(!identity_equal(&measured.script,&reopened)){result=ES_SCRIPT_IDENTITY;goto done;}
 result=peer_result(es_arguments_check(peer,expected->arguments,expected->arguments_length));
 done:
 if(current.state&&es_file_close(&current)!=ES_FILE_OK)result=ES_SCRIPT_CLEANUP;
 es_script_result final=live(peer);if(result!=ES_SCRIPT_CLEANUP&&final)result=final;
 if(!result)*out=measured;
 wipe(prefix,sizeof prefix);wipe(&measured,sizeof measured);wipe(&reopened,sizeof reopened);wipe(&current,sizeof current);
 return result;
}

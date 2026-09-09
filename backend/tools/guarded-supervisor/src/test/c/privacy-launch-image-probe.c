#define _GNU_SOURCE
#include "privacy-launch.h"
#include "privacy-image.h"
#include <sys/stat.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>
#define CHECK(x) do{if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}}while(0)
static es_launch owner;static es_launch_image_record record;static es_elf_layout output;
static const char *mode="actual";static unsigned file_closes,hash_closes;static int last_file=-1;static pthread_t receiver_thread;static unsigned hash_closed_on_receiver;
static pthread_mutex_t hold_mutex=PTHREAD_MUTEX_INITIALIZER;static pthread_cond_t hold_changed=PTHREAD_COND_INITIALIZER;static int held,released;
static int is(const char *s){return !strcmp(mode,s);}
static char directory[]="/tmp/es-image-owner-XXXXXX";static int parent=-1;static pid_t child=-1;
__attribute__((constructor)) static void held_constructor(void){
 const char *path=getenv("ES_IMAGE_TEST_ENDPOINT");if(!path)return;
 int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un a={.sun_family=AF_UNIX};
 if(fd<0||strlen(path)>=sizeof(a.sun_path))_exit(41);
 memcpy(a.sun_path,path,strlen(path)+1);
 if(connect(fd,(struct sockaddr*)&a,sizeof(a)))_exit(42);
 unsigned char prepare[12]={'E','S','P','R','V','0','0','1',1,0,0,0};if(write(fd,prepare,sizeof(prepare))!=sizeof(prepare))_exit(43);
 char byte;ssize_t n=read(fd,&byte,1);if(n!=0)_exit(44);if(close(fd))_exit(45);
}
static void cleanup(void){
 if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);child=-1;}
 if(owner.initialized)(void)es_launch_close(&owner,1000000000ULL);
 if(parent>=0)close(parent);
 rmdir(directory);
 volatile unsigned char *p=(volatile unsigned char*)&output;for(size_t i=0;i<sizeof(output);i++)p[i]=0;
}
es_hash_result __real_es_hash_open(es_hash*,int,uint64_t);
es_hash_result __wrap_es_hash_open(es_hash *h,int fd,uint64_t d){
 es_hash_result r=__real_es_hash_open(h,fd,d);if(!r){
  if(is("count507"))h->objects=507;
  if(is("count508"))h->objects=508;
  if(is("bytes-limit"))h->bytes=2ULL*1024*1024*1024;
  if(is("partial-hash")){h->state=2;h->terminal=ES_HASH_CRYPTO;return ES_HASH_CRYPTO;}
 }return r;
}
es_hash_result __real_es_hash_close(es_hash*);
es_hash_result __wrap_es_hash_close(es_hash *h){hash_closes++;hash_closed_on_receiver=pthread_equal(pthread_self(),receiver_thread);es_hash_result r=__real_es_hash_close(h);if(is("hash-close")){h->cleanup=ES_HASH_CLEANUP;return ES_HASH_CLEANUP;}return r;}
es_file_result __real_es_file_close(es_file*);
es_file_result __wrap_es_file_close(es_file *f){
 if(owner.image_entered&&f->state){file_closes++;last_file=f->fd;}
 es_file_result r=__real_es_file_close(f);
 if(owner.image_entered&&(is("close-uncertain")||is("digest-close"))){f->cleanup=ES_FILE_CLEANUP;return ES_FILE_CLEANUP;}
 if(owner.image_entered&&is("close-deadline"))owner.startup_deadline=1;
 if(owner.image_entered&&is("close-cancel"))(void)es_launch_cancel(&owner);
 return r;
}
es_image_result __real_es_image_check(es_peer*,const es_file*,es_hash*,const unsigned char*,es_hash_identity*);
es_image_result __wrap_es_image_check(es_peer*p,const es_file*f,es_hash*h,const unsigned char*d,es_hash_identity*out){
 if(is("held")||is("shortened")){pthread_mutex_lock(&hold_mutex);held=1;pthread_cond_broadcast(&hold_changed);while(!released)pthread_cond_wait(&hold_changed,&hold_mutex);pthread_mutex_unlock(&hold_mutex);}
 es_image_result r=__real_es_image_check(p,f,h,d,out);
 if(!r&&is("image-cancel"))(void)es_launch_cancel(&owner);
 if(!r&&is("image-deadline"))owner.startup_deadline=1;
 if(!r&&is("image-death")){CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=-1;}
 return r;
}
es_elf_result __real_es_elf_check(const es_file*,es_hash*,const unsigned char*,es_elf_layout*);
es_elf_result __wrap_es_elf_check(const es_file*f,es_hash*h,const unsigned char*d,es_elf_layout*out){
 es_elf_result r=__real_es_elf_check(f,h,d,out);
 if(!r&&(is("empty-dynamic")||is("duplicate-interpreter"))){
  unsigned count=out->phnum;
  for(unsigned i=0;i<count;i++){
   if(is("empty-dynamic")&&out->programs[i].type==2)out->programs[i].filesz=0;
   if(is("duplicate-interpreter")&&out->programs[i].type==3){CHECK(out->phnum<128);out->programs[out->phnum++]=out->programs[i];}
  }
 }return r;
}
static void *closing(void *value){*(es_launch_cleanup*)value=es_launch_close(&owner,1000000000ULL);return NULL;}
static void *receive(void *result){
 receiver_thread=pthread_self();es_launch_result r=es_launch_capture(&owner);if(!r)r=es_launch_correlate(&owner);
 if(r==ES_LAUNCH_ROOT_CORRELATED){
  if(is("scope"))owner.connection.peer.deadline_ns--;
  r=es_launch_image(&owner,&record,&output);
  if(is("double")&&!r)r=es_launch_image(&owner,&record,&output);
 }
 *(es_launch_result*)result=r;return NULL;
}
int main(int argc,char **argv){
 if(getenv("ES_IMAGE_TEST_ENDPOINT")){CHECK(write(1,"INVENTED-OUT\n",13)==13);CHECK(write(2,"INVENTED-ERR\n",13)==13);return 0;}
 CHECK(argc>=2&&strlen(argv[1])==64);if(argc>2)mode=argv[2];const char *selected=argc>3?argv[3]:argv[0];
 CHECK(strlen(selected)<sizeof(record.path));record.path_length=(uint32_t)strlen(selected);memcpy(record.path,selected,record.path_length+1);
 for(unsigned i=0;i<32;i++){unsigned v;CHECK(sscanf(argv[1]+2*i,"%2x",&v)==1);record.expected_sha256[i]=(unsigned char)v;}
 if(is("digest")||is("digest-close"))record.expected_sha256[0]^=1;
 if(is("tail-bytes"))memset(record.path+record.path_length+1,0xff,sizeof(record.path)-record.path_length-1);
 CHECK(mkdtemp(directory));CHECK(!atexit(cleanup));parent=open(directory,O_PATH|O_DIRECTORY|O_CLOEXEC);CHECK(parent>=0);
 char endpoint[ES_LISTENER_PATH_BYTES];CHECK(es_launch_open(&owner,parent,directory,5000000000ULL,endpoint)==ES_LAUNCH_OK);
 if(!is("writable")&&!is("symlink")&&!is("missing")){es_file preflight={0};CHECK(es_file_open(&preflight,owner.cancel_fd,owner.startup_deadline,(const char*)record.path,record.path_length)==ES_FILE_OK);CHECK(es_file_close(&preflight)==ES_FILE_OK);}
 if(is("invalid")){
  es_launch_image_record before=record;memset(&output,0xa5,sizeof(output));CHECK(es_launch_image(NULL,&record,&output)==ES_LAUNCH_INVALID);
  for(size_t i=0;i<sizeof(output);i++)CHECK(!((unsigned char*)&output)[i]);
  CHECK(es_launch_image(&owner,&record,NULL)==ES_LAUNCH_INVALID);CHECK(es_launch_image(&owner,NULL,&output)==ES_LAUNCH_INVALID);
  union {es_launch_image_record r;es_elf_layout layout;} alias;memset(&alias,0xa5,sizeof(alias));unsigned char saved[sizeof(alias)];memcpy(saved,&alias,sizeof(alias));
  CHECK(es_launch_image(&owner,&alias.r,&alias.layout)==ES_LAUNCH_INVALID&&!memcmp(saved,&alias,sizeof(alias)));
  record.path_length=4096;CHECK(es_launch_image(&owner,&record,&output)==ES_LAUNCH_INVALID);record=before;
  record.path[record.path_length]='x';CHECK(es_launch_image(&owner,&record,&output)==ES_LAUNCH_INVALID);record=before;
  record.path[1]=0;CHECK(es_launch_image(&owner,&record,&output)==ES_LAUNCH_INVALID);record=before;
  CHECK(!owner.image_entered&&!owner.hash.state&&owner.failure==ES_LAUNCH_OK);CHECK(es_launch_image(&owner,&record,&output)==ES_LAUNCH_PROTOCOL);
  CHECK(es_launch_close(&owner,1000000000ULL)==ES_LAUNCH_CLOSED_COMPLETE);return 0;
 }
 pthread_t receiver;es_launch_result result=ES_LAUNCH_INVALID;CHECK(!pthread_create(&receiver,NULL,receive,&result));CHECK(es_launch_arm(&owner)==ES_LAUNCH_OK);
 child=fork();CHECK(child>=0);if(!child){CHECK(!setenv("ES_IMAGE_TEST_ENDPOINT",endpoint,1));execl(is("static")?selected:argv[0],is("static")?selected:argv[0],"child",(char*)NULL);_exit(46);}
 CHECK(es_launch_register(&owner,(uint64_t)child)==ES_LAUNCH_OK);CHECK(es_launch_disarm(&owner)==ES_LAUNCH_OK);
 if(is("held")||is("shortened")){
  pthread_mutex_lock(&hold_mutex);while(!held)pthread_cond_wait(&hold_changed,&hold_mutex);pthread_mutex_unlock(&hold_mutex);
  pthread_t closer;es_launch_cleanup closed=ES_LAUNCH_CLOSE_INVALID;
  if(is("shortened")){CHECK(!pthread_create(&closer,NULL,closing,&closed));for(;;){pthread_mutex_lock(&owner.mutex);int ready=owner.closing!=ES_LAUNCH_CLOSE_NONE;pthread_mutex_unlock(&owner.mutex);if(ready)break;usleep(1000);}}
  int borrowed=owner.cancel_fd;CHECK(es_launch_close(&owner,10000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);CHECK(fcntl(borrowed,F_GETFD)>=0&&hash_closes==0);
  if(is("shortened")){CHECK(!pthread_join(closer,NULL));CHECK(closed==ES_LAUNCH_CLOSED_INCONCLUSIVE);}
  pthread_mutex_lock(&hold_mutex);released=1;pthread_cond_broadcast(&hold_changed);pthread_mutex_unlock(&hold_mutex);
  CHECK(!pthread_join(receiver,NULL));CHECK(result==ES_LAUNCH_CANCELLED);CHECK(hash_closes==1&&hash_closed_on_receiver);
  for(size_t i=0;i<sizeof(output);i++)CHECK(!((unsigned char*)&output)[i]);
  CHECK(es_launch_close(&owner,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
  CHECK(fcntl(borrowed,F_GETFD)<0&&errno==EBADF);return 0;
 }
 CHECK(!pthread_join(receiver,NULL));
 if(is("wrong-thread"))result=es_launch_image(&owner,&record,&output);
 es_launch_result expected=ES_LAUNCH_OK;
 if(is("digest")||is("digest-close")||is("different-inode")||is("writable")||is("image-death"))expected=ES_LAUNCH_IDENTITY;
 if(is("symlink")||is("scope"))expected=ES_LAUNCH_INVALID;
 if(is("count508")||is("bytes-limit"))expected=ES_LAUNCH_RESOURCE;
 if(is("partial-hash")||is("missing"))expected=ES_LAUNCH_IO;
 if(is("close-uncertain"))expected=ES_LAUNCH_CLEANUP;
 if(is("close-cancel")||is("image-cancel"))expected=ES_LAUNCH_CANCELLED;
 if(is("double")||is("static")||is("wrong-thread")||is("empty-dynamic")||is("duplicate-interpreter"))expected=ES_LAUNCH_PROTOCOL;
 if(is("image-deadline")||is("close-deadline"))expected=ES_LAUNCH_DEADLINE;
 CHECK(result==expected);
 if(expected){
  for(size_t i=0;i<sizeof(output);i++)CHECK(!((unsigned char*)&output)[i]);
  int uncertain=is("close-uncertain")||is("digest-close");int dummy=-1;
  if(uncertain){CHECK(owner.inconclusive&&last_file>=0);dummy=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(dummy>=0);if(dummy!=last_file){CHECK(dup3(dummy,last_file,O_CLOEXEC)==last_file);CHECK(!close(dummy));}dummy=last_file;}
  CHECK(es_launch_close(&owner,1000000000ULL)==(uncertain?ES_LAUNCH_CLOSED_INCONCLUSIVE:ES_LAUNCH_CLOSED_COMPLETE));
  if(dummy>=0){CHECK(fcntl(dummy,F_GETFD)>=0);CHECK(es_launch_close(&owner,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);CHECK(fcntl(dummy,F_GETFD)>=0);CHECK(!close(dummy));}
  if(is("count508"))CHECK(owner.hash.objects==512);
  if(owner.hash.state)CHECK(hash_closes==1&&owner.hash.state==3);
  return 0;
 }
 CHECK(owner.hash.objects==(is("count507")?512:5));CHECK(output.type==3&&output.machine==62&&output.phentsize==56&&output.phnum>0&&output.load_count>0);
 CHECK(!memcmp(output.file.sha256,record.expected_sha256,32));
 CHECK(es_launch_close(&owner,1000000000ULL)==(is("hash-close")?ES_LAUNCH_CLOSED_INCONCLUSIVE:ES_LAUNCH_CLOSED_COMPLETE));
 if(is("hash-close"))CHECK(es_launch_close(&owner,1000000000ULL)==ES_LAUNCH_CLOSED_INCONCLUSIVE);
 CHECK(hash_closes==1&&owner.hash.state==3&&!hash_closed_on_receiver);
 int status;CHECK(waitpid(child,&status,0)==child);child=-1;CHECK(WIFEXITED(status)&&WEXITSTATUS(status)==0);return 0;
}

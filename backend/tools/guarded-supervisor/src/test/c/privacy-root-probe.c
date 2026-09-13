#define _GNU_SOURCE
#include "privacy-root.h"
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#define CHECK(x) do{if(!(x)){fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__);exit(40);}}while(0)
static es_root root;static es_listener listener;static es_connection connection;
static int cancel=-1,parent=-1,inherited=-1,foreign_cancel=-1;static pid_t child=-1,other=-1;static const char *mode;
static int is(const char *s){return !strcmp(mode,s);}static char path[]="/tmp/es-root-XXXXXX";
static int uncertain_fd=-1,uncertain_done,matching,match_reads;
int __real_close(int);
int __wrap_close(int fd){int r=__real_close(fd);if(fd==uncertain_fd&&!uncertain_done){uncertain_done=1;errno=EINTR;return -1;}return r;}
es_fork_result __real_es_fork_read(es_fork *,es_peer_identity *);
es_fork_result __wrap_es_fork_read(es_fork *p,es_peer_identity *out){
 es_fork_result r=__real_es_fork_read(p,out);
 if(matching&&is("death-during-match")&&child>0){CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=-1;}
 return r;
}
es_connection_result __real_es_connection_read(es_connection *,es_peer_identity *);
es_connection_result __wrap_es_connection_read(es_connection *p,es_peer_identity *out){
 if(matching&&is("cancel-during-match")&&++match_reads==2){uint64_t one=1;CHECK(write(cancel,&one,sizeof(one))==sizeof(one));}
 es_connection_result r=__real_es_connection_read(p,out);
 if(matching&&r==ES_CONNECTION_PREPARE_RECEIVED){if(is("death-after-peer")&&child>0){CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=-1;}if(is("wrong-start"))out->start_ticks++;if(is("wrong-uid"))out->uid++;if(is("wrong-gid"))out->gid++;}
 return r;
}
static uint64_t now(void){struct timespec t;CHECK(!clock_gettime(CLOCK_MONOTONIC,&t));return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
static void cleanup(void){
 if(root.state){(void)es_root_disarm(&root);(void)es_root_close(&root,now()+1000000000ULL);}
 if(connection.state)(void)es_connection_close(&connection,now()+1000000000ULL);
 if(listener.state)(void)es_listener_close(&listener,now()+1000000000ULL);
 if(child>0){kill(child,SIGKILL);waitpid(child,NULL,0);}
 if(other>0){kill(other,SIGKILL);waitpid(other,NULL,0);}
 if(inherited>=0)close(inherited);
 if(parent>=0)close(parent);
 if(cancel>=0)close(cancel);
 if(foreign_cancel>=0)close(foreign_cancel);
 rmdir(path);
}
static void prepare_wait(const char *endpoint){
 int fd=inherited>=0?inherited:socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un a={.sun_family=AF_UNIX};memcpy(a.sun_path,endpoint,strlen(endpoint)+1);
 if(fd<0||(inherited<0&&connect(fd,(struct sockaddr*)&a,sizeof(a))))_exit(41);
 unsigned char prepare[12]={'E','S','P','R','V','0','0','1',1,0,0,0};
 if(send(fd,prepare,12,MSG_NOSIGNAL)!=12)_exit(42);
 char b;if(read(fd,&b,1)!=0)_exit(43);close(fd);
}
static void child_run(const char *endpoint){prepare_wait(endpoint);_exit(0);}
static int constructor_completed;
/* Test fixture only: PREPARE blocks the actual ELF constructor before main. */
__attribute__((constructor)) static void test_constructor(void){const char *endpoint=getenv("ES_ROOT_TEST_ENDPOINT");if(endpoint){prepare_wait(endpoint);constructor_completed=1;}}
static void *wrong_thread(void *arg){es_root_result *r=arg;*r=is("wrong-disarm")?es_root_disarm(&root):es_root_register(&root,(uint64_t)child);return NULL;}
static void *capture_thread(void *arg){es_root_result *r=arg;es_peer_identity out;*r=es_root_capture(&root,&out);CHECK(!out.pid&&!out.start_ticks);return NULL;}
static void *capture_success(void *arg){es_root_result *r=arg;es_peer_identity out;*r=es_root_capture(&root,&out);CHECK(*r==ES_ROOT_CAPTURED&&out.pid==(uint32_t)child);return NULL;}
static void signal_cancel(void){uint64_t one=1;CHECK(write(cancel,&one,sizeof(one))==sizeof(one));}
int main(int argc,char **argv){
 if(argc==3&&!strcmp(argv[1],"owned-child")){if(write(1,"INVENTED-OUT\n",13)!=13||write(2,"INVENTED-ERR\n",13)!=13)return 50;if(constructor_completed)return 0;child_run(argv[2]);}
 CHECK(argc==2);mode=argv[1];if(is("fresh")){es_peer_identity o={1,1,1,1};CHECK(es_root_disarm(&root)==ES_ROOT_INVALID);CHECK(es_root_capture(&root,&o)==ES_ROOT_INVALID&&!o.pid&&!o.start_ticks);CHECK(es_root_register(&root,1)==ES_ROOT_INVALID);CHECK(es_root_close(&root,now()+1000000000ULL)==ES_ROOT_CLOSE_INVALID);return 0;}CHECK(mkdtemp(path));CHECK(!atexit(cleanup));
 parent=open(path,O_PATH|O_DIRECTORY|O_CLOEXEC);cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);CHECK(parent>=0&&cancel>=0);
 uint64_t deadline=now()+(is("capture-deadline")?100000000ULL:3000000000ULL);if(is("foreign-cancel")){foreign_cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);CHECK(foreign_cancel>=0);}
 CHECK(es_listener_open(&listener,parent,path,foreign_cancel>=0?foreign_cancel:cancel,deadline+(is("foreign-deadline")?1000000ULL:0),1)==ES_LISTENER_OK);
 char endpoint[104];CHECK(es_listener_path(&listener,endpoint)==ES_LISTENER_OK);const uint8_t id[16]={1,3,5,7};
 if(is("expired-arm")){CHECK(es_root_arm(&root,cancel,now()-1,id)==ES_ROOT_DEADLINE);CHECK(es_root_disarm(&root)==ES_ROOT_OK);CHECK(es_root_register(&root,1)==ES_ROOT_DEADLINE);CHECK(es_root_disarm(&root)==ES_ROOT_INVALID);return 0;}
 if(is("cancel-arm")){signal_cancel();CHECK(es_root_arm(&root,cancel,deadline,id)==ES_ROOT_CANCELLED);CHECK(es_root_disarm(&root)==ES_ROOT_OK);return 0;}
 if(is("inherited")){inherited=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un a={.sun_family=AF_UNIX};memcpy(a.sun_path,endpoint,strlen(endpoint)+1);CHECK(inherited>=0&&!connect(inherited,(struct sockaddr*)&a,sizeof(a)));}
 CHECK(es_root_arm(&root,cancel,deadline,id)==ES_ROOT_OK);
 if(is("busy-arm")){es_root busy={0};CHECK(es_root_arm(&busy,cancel,deadline,id)==ES_ROOT_INVALID);CHECK(!busy.generation);CHECK(es_root_disarm(&busy)==ES_ROOT_INVALID);CHECK(es_root_close(&busy,now()+1000000000ULL)==ES_ROOT_CLOSED_COMPLETE);CHECK(es_root_disarm(&root)==ES_ROOT_OK);return 0;}
 if(is("capture-cancel")||is("capture-deadline")){pthread_t thread;es_root_result result;CHECK(!pthread_create(&thread,NULL,capture_thread,&result));if(is("capture-cancel")){usleep(20000);signal_cancel();}CHECK(!pthread_join(thread,NULL));CHECK(result==(is("capture-cancel")?ES_ROOT_CANCELLED:ES_ROOT_DEADLINE));CHECK(es_root_disarm(&root)==ES_ROOT_OK);CHECK(es_root_register(&root,1)==result);CHECK(es_root_close(&root,now()+1000000000ULL)==ES_ROOT_CLOSED_COMPLETE);CHECK(fcntl(cancel,F_GETFD)>=0);if(is("capture-cancel")){uint64_t value=0;CHECK(read(cancel,&value,sizeof(value))==sizeof(value)&&value==1);}return 0;}
 if(is("before-capture")){CHECK(es_root_register(&root,1)==ES_ROOT_INVALID);CHECK(es_root_disarm(&root)==ES_ROOT_OK);CHECK(es_root_register(&root,1)==ES_ROOT_INVALID);return 0;}
 if(is("missing-hook")){CHECK(es_root_disarm(&root)==ES_ROOT_OK);es_peer_identity o={0};CHECK(es_root_capture(&root,&o)==ES_ROOT_PROTOCOL);CHECK(es_root_register(&root,1)==ES_ROOT_PROTOCOL);return 0;}
 if(is("rearm")){CHECK(es_root_arm(&root,cancel,deadline,id)==ES_ROOT_INVALID);es_peer_identity o={0};CHECK(es_root_capture(&root,&o)==ES_ROOT_INVALID);CHECK(es_root_disarm(&root)==ES_ROOT_OK);return 0;}

 child=fork();CHECK(child>=0);if(!child){if(is("unrelated"))for(;;)pause();child_run(endpoint);}
 if(is("disarm-overlap")){pthread_t thread;es_root_result result;CHECK(!pthread_create(&thread,NULL,capture_success,&result));CHECK(es_root_disarm(&root)==ES_ROOT_OK);CHECK(!pthread_join(thread,NULL));CHECK(result==ES_ROOT_CAPTURED);CHECK(es_root_register(&root,(uint64_t)child)==ES_ROOT_INVALID);CHECK(es_root_close(&root,now()+1000000000ULL)==ES_ROOT_CLOSED_COMPLETE);return 0;}
 if(is("close-armed")){CHECK(es_root_close(&root,now()+1000000000ULL)==ES_ROOT_CLOSED_INCONCLUSIVE);CHECK(es_root_disarm(&root)==ES_ROOT_OK);CHECK(es_root_close(&root,now()+1000000000ULL)==ES_ROOT_CLOSED_INCONCLUSIVE);return 0;}
 es_peer_identity out={0};CHECK(es_root_capture(&root,&out)==ES_ROOT_CAPTURED);CHECK(out.pid==(uint32_t)child);
 if(is("wrong-pid")||is("zero-pid")||is("wide-pid")){uint64_t pid=is("zero-pid")?0:is("wide-pid")?UINT64_MAX:(uint64_t)getpid();CHECK(es_root_register(&root,pid)==ES_ROOT_IDENTITY);CHECK(es_root_register(&root,(uint64_t)child)==ES_ROOT_IDENTITY);CHECK(es_root_disarm(&root)==ES_ROOT_OK);return 0;}
 if(is("wrong-thread")||is("wrong-disarm")){pthread_t thread;es_root_result result;CHECK(!pthread_create(&thread,NULL,wrong_thread,&result));CHECK(!pthread_join(thread,NULL));CHECK(result==ES_ROOT_INVALID);CHECK(es_root_register(&root,(uint64_t)child)==ES_ROOT_INVALID);CHECK(es_root_disarm(&root)==ES_ROOT_OK);return 0;}
 if(is("stale-generation")){root.generation++;CHECK(es_root_register(&root,(uint64_t)child)==ES_ROOT_INVALID);CHECK(es_root_disarm(&root)==ES_ROOT_INVALID);root.generation--;CHECK(es_root_disarm(&root)==ES_ROOT_OK);return 0;}
 if(is("cancel-register")){signal_cancel();CHECK(es_root_register(&root,(uint64_t)child)==ES_ROOT_CANCELLED);CHECK(es_root_disarm(&root)==ES_ROOT_OK);return 0;}
 if(is("dead-register")){CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);uint64_t pid=(uint64_t)child;child=-1;CHECK(es_root_register(&root,pid)==ES_ROOT_DEAD);CHECK(es_root_disarm(&root)==ES_ROOT_OK);return 0;}
 CHECK(es_root_register(&root,(uint64_t)child)==ES_ROOT_REGISTERED);
 if(is("duplicate-register")){CHECK(es_root_register(&root,(uint64_t)child)==ES_ROOT_INVALID);CHECK(es_root_disarm(&root)==ES_ROOT_OK);return 0;}
 if(!is("before-disarm"))CHECK(es_root_disarm(&root)==ES_ROOT_OK);
 if(is("unrelated")){other=fork();CHECK(other>=0);if(!other)child_run(endpoint);}

 uint64_t token;CHECK(es_listener_accept(&listener,&token)==ES_LISTENER_ACCEPTED);CHECK(es_connection_open(&connection,&listener,token)==ES_CONNECTION_OK);
 CHECK(es_connection_prepare(&connection,&out)==ES_CONNECTION_PREPARE_RECEIVED);
 matching=1;
 if(is("peer-cancel")||is("wire-cancel")||is("peer-deadline")||is("wire-deadline")){
  if(is("peer-cancel")||is("wire-cancel")){foreign_cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);CHECK(foreign_cancel>=0);if(is("peer-cancel"))connection.peer.cancel_fd=foreign_cancel;else connection.wire.cancel_fd=foreign_cancel;}
  else if(is("peer-deadline"))connection.peer.deadline_ns++;else connection.wire.deadline_ns++;
  CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_INVALID);CHECK(!out.pid&&!out.start_ticks);CHECK(connection.wire.fd>=0&&listener.live==1);return 0;
 }
 if(is("cancel-during-match")){CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_CANCELLED);CHECK(!out.pid&&!out.start_ticks);return 0;}

 if(is("foreign-cancel")||is("foreign-deadline")){CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_INVALID);CHECK(!out.pid&&!out.start_ticks);CHECK(connection.wire.fd>=0&&listener.live==1);return 0;}
 if(is("unrelated")||is("inherited")||is("wrong-start")||is("wrong-uid")||is("wrong-gid")){memset(&out,1,sizeof(out));CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_IDENTITY);CHECK(!out.pid&&!out.start_ticks);CHECK(connection.wire.fd>=0&&listener.live==1);return 0;}
 if(is("before-disarm")){CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_INVALID);CHECK(es_root_disarm(&root)==ES_ROOT_OK);CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_INVALID);return 0;}
 if(is("null")||is("root-alias")||is("connection-alias")){es_peer_identity *o=is("null")?NULL:is("root-alias")?(es_peer_identity*)&root:(es_peer_identity*)&connection;int rootpin=root.fork.peer.pidfd;uint64_t forkgen=root.fork.generation;CHECK(es_root_match(&root,&connection,o)==ES_ROOT_INVALID);CHECK(root.state&&root.fork.state&&root.fork.generation==forkgen&&root.fork.peer.pidfd==rootpin&&connection.state&&connection.wire.fd>=0);return 0;}
 if(is("death-during-match")||is("death-after-peer")){CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_DEAD);CHECK(!out.pid&&!out.start_ticks);return 0;}
 if(is("dead-match")){CHECK(!kill(child,SIGKILL));CHECK(waitpid(child,NULL,0)==child);child=-1;CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_DEAD);CHECK(!out.pid&&!out.start_ticks);return 0;}
 if(is("cancel-match")){signal_cancel();CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_CANCELLED);CHECK(!out.pid&&!out.start_ticks);return 0;}
 CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_CORRELATED);CHECK(out.pid==(uint32_t)child&&out.start_ticks);
 if(is("duplicate-match")){CHECK(es_root_match(&root,&connection,&out)==ES_ROOT_INVALID);CHECK(!out.pid&&!out.start_ticks);}
 if(is("expired-close")){CHECK(es_root_close(&root,0)==ES_ROOT_CLOSED_INCONCLUSIVE);CHECK(es_root_close(&root,now()+1000000000ULL)==ES_ROOT_CLOSED_INCONCLUSIVE);return 0;}
 if(is("uncertain-close")){uncertain_fd=root.fork.peer.pidfd;CHECK(es_root_close(&root,now()+1000000000ULL)==ES_ROOT_CLOSED_INCONCLUSIVE);CHECK(uncertain_done&&fcntl(uncertain_fd,F_GETFD)==-1);int fd=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(fd>=0);if(fd!=uncertain_fd){CHECK(dup2(fd,uncertain_fd)==uncertain_fd);CHECK(!close(fd));fd=uncertain_fd;}CHECK(fd==uncertain_fd&&fcntl(fd,F_GETFD)>=0);CHECK(es_root_close(&root,now()+1000000000ULL)==ES_ROOT_CLOSED_INCONCLUSIVE);CHECK(fcntl(fd,F_GETFD)>=0);close(fd);return 0;}
 int oldpin=root.fork.peer.pidfd;CHECK(es_root_close(&root,now()+1000000000ULL)==ES_ROOT_CLOSED_COMPLETE);CHECK(fcntl(oldpin,F_GETFD)==-1);int replacement=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(replacement>=0);if(replacement!=oldpin){CHECK(dup2(replacement,oldpin)==oldpin);CHECK(!close(replacement));replacement=oldpin;}CHECK(replacement==oldpin&&fcntl(replacement,F_GETFD)>=0);CHECK(es_root_close(&root,0)==ES_ROOT_CLOSED_COMPLETE);CHECK(fcntl(replacement,F_GETFD)>=0);close(replacement);CHECK(connection.wire.fd>=0&&listener.live==1);
 CHECK(es_connection_close(&connection,now()+1000000000ULL)==ES_CONNECTION_CLOSED_COMPLETE);
 int status;CHECK(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0);child=-1;
 CHECK(es_root_close(&root,0)==ES_ROOT_CLOSED_COMPLETE);return 0;
}

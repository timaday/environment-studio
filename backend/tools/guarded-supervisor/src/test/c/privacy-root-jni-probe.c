#define _GNU_SOURCE
#include "privacy-root.h"
#include "privacy-controls.h"
#include <jni.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sys/eventfd.h>
#include <time.h>
#include <unistd.h>
static es_root root;static es_listener listener;static es_connection connection;
static int parent=-1,cancel=-1;static char path[]="/tmp/es-root-jni-XXXXXX";static uint64_t deadline;
static uint64_t now(void){struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t))return 0;return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;}
JNIEXPORT jstring JNICALL Java_studio_environment_supervisor_PrivacyRootProbe_prepare(JNIEnv *env,jclass type){
 (void)type;if(es_privacy_establish()!=ES_PRIVACY_OK||!mkdtemp(path))return NULL;
 parent=open(path,O_PATH|O_DIRECTORY|O_CLOEXEC);cancel=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);deadline=now()+1000000000ULL;
 if(parent<0||cancel<0||es_listener_open(&listener,parent,path,cancel,deadline,1)!=ES_LISTENER_OK)return NULL;
 char endpoint[104];if(es_listener_path(&listener,endpoint)!=ES_LISTENER_OK)return NULL;
 return (*env)->NewStringUTF(env,endpoint);
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyRootProbe_arm(JNIEnv *env,jclass type){(void)env;(void)type;const uint8_t id[16]={2,4,6,8};return es_root_arm(&root,cancel,deadline,id);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyRootProbe_register(JNIEnv *env,jclass type,jlong pid){
 (void)env;(void)type;es_peer_identity identity;es_root_result r=es_root_capture(&root,&identity);return r==ES_ROOT_CAPTURED?es_root_register(&root,(uint64_t)pid):r;
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyRootProbe_disarm(JNIEnv *env,jclass type){(void)env;(void)type;return es_root_disarm(&root);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyRootProbe_match(JNIEnv *env,jclass type){
 (void)env;(void)type;uint64_t token;es_peer_identity identity;
 if(es_listener_accept(&listener,&token)!=ES_LISTENER_ACCEPTED)return 30;
 if(es_connection_open(&connection,&listener,token)!=ES_CONNECTION_OK)return 31;
 if(es_connection_prepare(&connection,&identity)!=ES_CONNECTION_PREPARE_RECEIVED)return 32;
 return es_root_match(&root,&connection,&identity);
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyRootProbe_releaseConnection(JNIEnv *env,jclass type){(void)env;(void)type;return es_connection_close(&connection,now()+1000000000ULL);}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyRootProbe_cancel(JNIEnv *env,jclass type){(void)env;(void)type;uint64_t one=1;if(cancel>=0){ssize_t n=write(cancel,&one,sizeof(one));(void)n;}}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyRootProbe_finish(JNIEnv *env,jclass type,jlong remaining){
 (void)env;(void)type;uint64_t cleanup=now()+(remaining>0?(uint64_t)remaining:0);int bad=0;
 if(root.state&&es_root_close(&root,cleanup)!=ES_ROOT_CLOSED_COMPLETE)bad=1;
 if(connection.state&&es_connection_close(&connection,cleanup)!=ES_CONNECTION_CLOSED_COMPLETE)bad=1;
 if(listener.state&&es_listener_close(&listener,cleanup)!=ES_LISTENER_CLOSED_COMPLETE)bad=1;
 if(parent>=0){int fd=parent;parent=-1;if(close(fd))bad=1;}
 if(cancel>=0){int fd=cancel;cancel=-1;if(close(fd))bad=1;}
 if(rmdir(path))bad=1;
 return bad;
}

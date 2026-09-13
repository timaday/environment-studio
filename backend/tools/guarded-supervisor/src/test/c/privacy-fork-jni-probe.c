#define _GNU_SOURCE
#include "privacy-fork.h"
#include "privacy-controls.h"
#include <jni.h>
#include <sys/eventfd.h>
#include <time.h>
#include <unistd.h>
static es_fork capture;
static int cancel_fd=-1;
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyForkProbe_prepare(JNIEnv *env,jclass type){
 (void)env;(void)type;
 if(es_privacy_establish()!=ES_PRIVACY_OK)return 30;
 cancel_fd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);if(cancel_fd<0)return 31;
 struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t))return 32;
 uint64_t deadline=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec+5000000000ULL;
 const uint8_t id[16]={2,4,6,8,10,12,14,16};
 return es_fork_arm(&capture,cancel_fd,deadline,id);
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyForkProbe_disarm(JNIEnv *env,jclass type){(void)env;(void)type;return es_fork_disarm(&capture);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyForkProbe_capture(JNIEnv *env,jclass type,jlong expected){
 (void)env;(void)type;es_peer_identity out={0};es_fork_result r=es_fork_capture(&capture,&out);
 if(r==ES_FORK_CAPTURED&&((uint64_t)expected!=out.pid||!out.start_ticks))return 33;
 return r;
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyForkProbe_finish(JNIEnv *env,jclass type){
 (void)env;(void)type;int r=es_fork_close(&capture);if(cancel_fd>=0){int fd=cancel_fd;cancel_fd=-1;if(close(fd))return 34;}return r;
}

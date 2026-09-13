#define _GNU_SOURCE
#include "privacy-launch.h"
#include "privacy-controls.h"
#include <jni.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>
static es_launch owner;static int parent=-1;static char directory[]="/tmp/es-launch-jni-XXXXXX";
JNIEXPORT jstring JNICALL Java_studio_environment_supervisor_PrivacyNativeLaunchProbe_prepare(JNIEnv *env,jclass type){
 (void)type;if(es_privacy_establish()!=ES_PRIVACY_OK||!mkdtemp(directory))return NULL;
 parent=open(directory,O_PATH|O_DIRECTORY|O_CLOEXEC);if(parent<0)return NULL;
 char path[ES_LISTENER_PATH_BYTES];if(es_launch_open(&owner,parent,directory,3000000000ULL,path)!=ES_LAUNCH_OK)return NULL;
 return (*env)->NewStringUTF(env,path);
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyNativeLaunchProbe_arm(JNIEnv *e,jclass c){(void)e;(void)c;return es_launch_arm(&owner);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyNativeLaunchProbe_captureAndCorrelate(JNIEnv *e,jclass c){(void)e;(void)c;es_launch_result r=es_launch_capture(&owner);return r==ES_LAUNCH_OK?es_launch_correlate(&owner):r;}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyNativeLaunchProbe_register(JNIEnv *e,jclass c,jlong pid){(void)e;(void)c;return es_launch_register(&owner,(uint64_t)pid);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyNativeLaunchProbe_disarm(JNIEnv *e,jclass c){(void)e;(void)c;return es_launch_disarm(&owner);}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyNativeLaunchProbe_cancel(JNIEnv *e,jclass c){(void)e;(void)c;(void)es_launch_cancel(&owner);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyNativeLaunchProbe_finish(JNIEnv *e,jclass c,jlong left){(void)e;(void)c;return es_launch_close(&owner,left>0?(uint64_t)left:0);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyNativeLaunchProbe_releaseParent(JNIEnv *e,jclass c){
 (void)e;(void)c;int bad=0;if(parent>=0){int fd=parent;parent=-1;if(close(fd))bad=1;}if(rmdir(directory))bad=1;return bad;
}

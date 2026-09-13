#define _GNU_SOURCE
#include "privacy-launch.h"
#include "privacy-controls.h"
#include <jni.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
static es_launch owner;static es_launch_image_record record;static es_elf_layout layout;static int parent=-1;static char directory[]="/tmp/es-launch-jni-XXXXXX";
JNIEXPORT jstring JNICALL Java_studio_environment_supervisor_PrivacyLaunchImageProbe_prepare(JNIEnv *env,jclass type,jstring executable,jstring digest){
 const char *pathText=(*env)->GetStringUTFChars(env,executable,NULL);const char *hashText=(*env)->GetStringUTFChars(env,digest,NULL);
 if(!pathText||!hashText){if(pathText)(*env)->ReleaseStringUTFChars(env,executable,pathText);if(hashText)(*env)->ReleaseStringUTFChars(env,digest,hashText);return NULL;}
 size_t n=strlen(pathText);int bad=n>4095||strlen(hashText)!=64;
 if(bad){(*env)->ReleaseStringUTFChars(env,executable,pathText);(*env)->ReleaseStringUTFChars(env,digest,hashText);return NULL;}
 record.path_length=(uint32_t)n;memcpy(record.path,pathText,n+1);for(unsigned i=0;i<32;i++){unsigned value;if(sscanf(hashText+2*i,"%2x",&value)!=1){bad=1;break;}record.expected_sha256[i]=(unsigned char)value;}
 (*env)->ReleaseStringUTFChars(env,executable,pathText);(*env)->ReleaseStringUTFChars(env,digest,hashText);if(bad)return NULL;
 (void)type;if(es_privacy_establish()!=ES_PRIVACY_OK||!mkdtemp(directory))return NULL;
 parent=open(directory,O_PATH|O_DIRECTORY|O_CLOEXEC);if(parent<0)return NULL;
 char path[ES_LISTENER_PATH_BYTES];if(es_launch_open(&owner,parent,directory,3000000000ULL,path)!=ES_LAUNCH_OK)return NULL;
 return (*env)->NewStringUTF(env,path);
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyLaunchImageProbe_arm(JNIEnv *e,jclass c){(void)e;(void)c;return es_launch_arm(&owner);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyLaunchImageProbe_captureAndCorrelate(JNIEnv *e,jclass c){(void)e;(void)c;es_launch_result r=es_launch_capture(&owner);if(r==ES_LAUNCH_OK)r=es_launch_correlate(&owner);return r==ES_LAUNCH_ROOT_CORRELATED?es_launch_image(&owner,&record,&layout):r;}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyLaunchImageProbe_register(JNIEnv *e,jclass c,jlong pid){(void)e;(void)c;return es_launch_register(&owner,(uint64_t)pid);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyLaunchImageProbe_disarm(JNIEnv *e,jclass c){(void)e;(void)c;return es_launch_disarm(&owner);}
JNIEXPORT void JNICALL Java_studio_environment_supervisor_PrivacyLaunchImageProbe_cancel(JNIEnv *e,jclass c){(void)e;(void)c;(void)es_launch_cancel(&owner);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyLaunchImageProbe_finish(JNIEnv *e,jclass c,jlong left){(void)e;(void)c;return es_launch_close(&owner,left>0?(uint64_t)left:0);}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyLaunchImageProbe_releaseParent(JNIEnv *e,jclass c){
 (void)e;(void)c;int bad=0;volatile unsigned char *wipe=(volatile unsigned char*)&layout;for(size_t i=0;i<sizeof(layout);i++)wipe[i]=0;if(parent>=0){int fd=parent;parent=-1;if(close(fd))bad=1;}if(rmdir(directory))bad=1;return bad;
}

JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyLaunchImageProbe_passes(JNIEnv *e,jclass c){(void)e;(void)c;return (jint)owner.hash.objects;}

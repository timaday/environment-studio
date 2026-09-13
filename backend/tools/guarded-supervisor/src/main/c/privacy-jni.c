#define _POSIX_C_SOURCE 200809L
#include <jni.h>
#include <stdint.h>
#include <string.h>
#include "privacy-registry.h"
#define PREFIX "studio/environment/supervisor/PrivacyBridge$"
#define TYPE(name) "L" PREFIX name ";"
static jclass bridge,system_class,failed[6],opened,final_admitted;
static jmethodID constructors[6],open_constructor,final_constructor,get_property;
static jobject failures[11],ownership[2],success[4],statuses[4],close_results[2],closed_event;
static jobject globals[64];static unsigned global_count;
static jclass keep_class(JNIEnv *e,const char *name){jclass local=(*e)->FindClass(e,name);if(!local)return NULL;jclass global=(*e)->NewGlobalRef(e,local);(*e)->DeleteLocalRef(e,local);if(global)globals[global_count++]=global;return global;}
static jobject constant(JNIEnv *e,const char *type,const char *name,const char *signature){jclass c=(*e)->FindClass(e,type);if(!c)return NULL;
 jfieldID id=(*e)->GetStaticFieldID(e,c,name,signature);jobject local=id?(*e)->GetStaticObjectField(e,c,id):NULL;(*e)->DeleteLocalRef(e,c);if(!local)return NULL;
 jobject global=(*e)->NewGlobalRef(e,local);(*e)->DeleteLocalRef(e,local);if(global)globals[global_count++]=global;return global;}
static jobject failure(JNIEnv *e,unsigned kind,es_bridge_failure code,unsigned no_window){
 if(code<0||code>ES_BRIDGE_CLEANUP)code=ES_BRIDGE_PROTOCOL;
 return kind==2||kind==4?(*e)->NewObject(e,failed[kind],constructors[kind],failures[code],ownership[no_window?0:1])
 :(*e)->NewObject(e,failed[kind],constructors[kind],failures[code]);
}
static jobject finish(JNIEnv *e,es_registry_ref *ref,jobject object,unsigned unpublished){
 if((*e)->ExceptionCheck(e)||!object){es_registry_result_failed(ref);if(unpublished)es_registry_unpublished(ref);}
 else es_registry_result_delivered(ref);
 es_registry_release(ref);return object;
}
/* Standard UTF-8 -> UTF-16, not JNI modified UTF-8. Native listener already
   validates this string, but conversion remains bounded and rejects corruption. */
static jstring path_string(JNIEnv *e,const char *path){
 jchar out[ES_LISTENER_PATH_BYTES]={0};jstring result=NULL;unsigned used=0;size_t n=strnlen(path,ES_LISTENER_PATH_BYTES),at=0;
 if(!n||n>=ES_LISTENER_PATH_BYTES)goto done;
 while(at<n){uint32_t c=(unsigned char)path[at++],value;unsigned extra;
  if(c<128){value=c;extra=0;}else if(c>=194&&c<=223){value=c&31;extra=1;}else if(c>=224&&c<=239){value=c&15;extra=2;}else if(c>=240&&c<=244){value=c&7;extra=3;}else goto done;
  if(extra>n-at)goto done;
  for(unsigned i=0;i<extra;i++){c=(unsigned char)path[at++];if((c&192)!=128)goto done;value=(value<<6)|(c&63);}
  if((extra==1&&value<128)||(extra==2&&value<2048)||(extra==3&&value<65536)||value>0x10ffff||(value>=0xd800&&value<=0xdfff))goto done;
  if(value>65535){value-=65536;out[used++]=(jchar)(0xd800+(value>>10));out[used++]=(jchar)(0xdc00+(value&1023));}else out[used++]=(jchar)value;
 }
 result=(*e)->NewString(e,out,(jsize)used);
 done:;volatile jchar *wipe=out;for(unsigned i=0;i<ES_LISTENER_PATH_BYTES;i++)wipe[i]=0;return result;
}
static int property(JNIEnv *e,const char *name,const char *expected){
 jstring key=(*e)->NewStringUTF(e,name);if(!key)return 0;jstring value=(*e)->CallStaticObjectMethod(e,system_class,get_property,key);(*e)->DeleteLocalRef(e,key);
 if((*e)->ExceptionCheck(e)||!value)return 0;
 const char *text=(*e)->GetStringUTFChars(e,value,NULL);int equal=text&&expected&&!strcmp(text,expected);
 if(text)(*e)->ReleaseStringUTFChars(e,value,text);
 (*e)->DeleteLocalRef(e,value);return equal;
}
static int caller(JNIEnv *e,jclass c){return (*e)->IsSameObject(e,c,bridge);}
static jobject self(JNIEnv *e,jclass c,jint ordinal){
 if(!caller(e,c))return failure(e,0,ES_BRIDGE_PROTOCOL,0);
 const es_compiled_mechanism *record=es_compiled_find_mechanism(ordinal);
 if(record&&(!property(e,"jdk.lang.Process.launchMechanism","FORK")||!property(e,"java.runtime.version",record->java_runtime)))record=NULL;
 es_bridge_failure result=es_registry_self(record);if((*e)->ExceptionCheck(e))return NULL;
 return result==ES_BRIDGE_NONE?success[0]:failure(e,0,result,0);
}
static jobject open_launch(JNIEnv *e,jclass c,jint ordinal,jlong left){
 uint64_t entered=es_registry_now();
 if(!caller(e,c))return failure(e,1,ES_BRIDGE_PROTOCOL,0);
 char path[ES_LISTENER_PATH_BYTES];
 es_registry_ref ref=es_registry_open(ordinal,left,entered,path);jobject result;
 if(ref.failure!=ES_BRIDGE_NONE)result=failure(e,1,ref.failure,0);
 else{jstring text=path_string(e,path);result=text?(*e)->NewObject(e,opened,open_constructor,(jlong)ref.token,text):NULL;if(text)(*e)->DeleteLocalRef(e,text);
  if(!(*e)->ExceptionCheck(e)&&(!result||!es_registry_publish(&ref))){
   es_bridge_result state=es_registry_status(&ref);
   if(result)(*e)->DeleteLocalRef(e,result);
   result=failure(e,1,state.failure==ES_BRIDGE_NONE?ES_BRIDGE_PROTOCOL:state.failure,0);es_registry_unpublished(&ref);}}
 memset(path,0,sizeof(path));return finish(e,&ref,result,ref.failure==ES_BRIDGE_NONE);
}
static jobject arm(JNIEnv *e,jclass c,jlong token){
 if(!caller(e,c))return failure(e,2,ES_BRIDGE_PROTOCOL,0);
 es_registry_ref ref=es_registry_acquire(token);es_bridge_result r=es_registry_arm(&ref);
 return finish(e,&ref,r.failure==ES_BRIDGE_NONE?success[1]:failure(e,2,r.failure,r.no_window),0);
}
static jobject register_root(JNIEnv *e,jclass c,jlong token,jlong pid){
 if(!caller(e,c))return failure(e,3,ES_BRIDGE_PROTOCOL,0);
 es_registry_ref ref=es_registry_acquire(token);es_bridge_result r=es_registry_register(&ref,pid);
 return finish(e,&ref,r.failure==ES_BRIDGE_NONE?success[2]:failure(e,3,r.failure,0),0);
}
static jobject disarm(JNIEnv *e,jclass c,jlong token){
 if(!caller(e,c))return failure(e,4,ES_BRIDGE_PROTOCOL,0);
 es_registry_ref ref=es_registry_acquire(token);es_bridge_result r=es_registry_disarm(&ref);
 return finish(e,&ref,r.failure==ES_BRIDGE_NONE?success[3]:failure(e,4,r.failure,r.no_window),0);
}
static jobject event(JNIEnv *e,jclass c,jlong token){
 if(!caller(e,c))return failure(e,5,ES_BRIDGE_PROTOCOL,0);
 es_registry_ref ref=es_registry_acquire(token);es_bridge_result r=es_registry_event(&ref);
 return finish(e,&ref,r.closed?closed_event:failure(e,5,r.failure,0),0);
}
static jobject status(JNIEnv *e,jclass c,jlong token){
 if(!caller(e,c))return statuses[2];
 es_registry_ref ref=es_registry_acquire(token);es_bridge_result r=es_registry_status(&ref);
 return finish(e,&ref,statuses[r.closed?3:r.failure==ES_BRIDGE_NONE?0:2],0);
}
static void cancel(JNIEnv *e,jclass c,jlong token){if(!caller(e,c))return;
 es_registry_ref ref=es_registry_acquire(token);es_registry_cancel(&ref);es_registry_release(&ref);}
static jobject close_launch(JNIEnv *e,jclass c,jlong token,jlong left){
 if(!caller(e,c))return close_results[1];
 es_registry_ref ref=es_registry_acquire(token);unsigned complete=es_registry_close(&ref,left);
 return finish(e,&ref,close_results[complete?0:1],0);
}
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm,void *reserved){
 (void)reserved;unsigned registration_attempted=0;JNIEnv *e=NULL;if((*vm)->GetEnv(vm,(void**)&e,JNI_VERSION_1_8)!=JNI_OK)return JNI_ERR;
 bridge=keep_class(e,"studio/environment/supervisor/PrivacyBridge");if(!bridge)goto bad;
 system_class=keep_class(e,"java/lang/System");if(!system_class)goto bad;
 get_property=(*e)->GetStaticMethodID(e,system_class,"getProperty","(Ljava/lang/String;)Ljava/lang/String;");if(!get_property)goto bad;
 const char *fnames[]={"PLATFORM","INSTALLATION","SELF_PRIVACY","THREAD_SYNC","RESOURCE","IDENTITY","CHAIN","PROTOCOL","DEADLINE","CANCELLED","CLEANUP"};
 for(unsigned i=0;i<11;i++)if(!(failures[i]=constant(e,PREFIX "Failure",fnames[i],TYPE("Failure"))))goto bad;
 ownership[0]=constant(e,PREFIX "ArmOwnership","NO_WINDOW_ACQUIRED",TYPE("ArmOwnership"));ownership[1]=constant(e,PREFIX "ArmOwnership","DISARM_REQUIRED_OR_UNKNOWN",TYPE("ArmOwnership"));if(!ownership[0]||!ownership[1])goto bad;
 const char *names[]={PREFIX "SelfFailed",PREFIX "OpenFailed",PREFIX "ForkFailed",PREFIX "RootFailed",PREFIX "DisarmFailed",PREFIX "EventFailed"};
 for(unsigned i=0;i<6;i++){failed[i]=keep_class(e,names[i]);if(!failed[i])goto bad;
  constructors[i]=(*e)->GetMethodID(e,failed[i],"<init>",i==2||i==4?"(" TYPE("Failure") TYPE("ArmOwnership") ")V":"(" TYPE("Failure") ")V");if(!constructors[i])goto bad;}
 opened=keep_class(e,PREFIX "Opened");final_admitted=keep_class(e,PREFIX "FinalAdmitted");if(!opened||!final_admitted)goto bad;
 open_constructor=(*e)->GetMethodID(e,opened,"<init>","(JLjava/lang/String;)V");final_constructor=(*e)->GetMethodID(e,final_admitted,"<init>","(J)V");if(!open_constructor||!final_constructor)goto bad;
 success[0]=constant(e,PREFIX "SelfEstablished","ESTABLISHED",TYPE("SelfEstablished"));success[1]=constant(e,PREFIX "ForkArmed","ARMED",TYPE("ForkArmed"));
 success[2]=constant(e,PREFIX "RootRegistered","REGISTERED",TYPE("RootRegistered"));success[3]=constant(e,PREFIX "ForkDisarmed","DISARMED",TYPE("ForkDisarmed"));for(unsigned i=0;i<4;i++)if(!success[i])goto bad;
 const char *snames[]={"STARTING","ADMITTED","FAILED","CLOSED"};for(unsigned i=0;i<4;i++)if(!(statuses[i]=constant(e,PREFIX "Status",snames[i],TYPE("Status"))))goto bad;
 close_results[0]=constant(e,PREFIX "CloseResult","COMPLETE",TYPE("CloseResult"));close_results[1]=constant(e,PREFIX "CloseResult","INCONCLUSIVE",TYPE("CloseResult"));closed_event=constant(e,PREFIX "EventClosed","CLOSED",TYPE("EventClosed"));if(!close_results[0]||!close_results[1]||!closed_event)goto bad;
 JNINativeMethod methods[]={
  {"establishSelf","(I)" TYPE("SelfResult"),(void*)self},{"openLaunch","(IJ)" TYPE("OpenResult"),(void*)open_launch},
  {"armFork","(J)" TYPE("ForkResult"),(void*)arm},{"registerRoot","(JJ)" TYPE("RootResult"),(void*)register_root},
  {"disarmFork","(J)" TYPE("DisarmResult"),(void*)disarm},{"nextEvent","(J)" TYPE("Event"),(void*)event},
  {"status","(J)" TYPE("Status"),(void*)status},{"cancel","(J)V",(void*)cancel},{"closeLaunch","(JJ)" TYPE("CloseResult"),(void*)close_launch}};
 jmethodID claim=(*e)->GetStaticMethodID(e,bridge,"claimNativeRegistration","()Z");if(!claim)goto bad;
 if(!(*e)->CallStaticBooleanMethod(e,bridge,claim)||(*e)->ExceptionCheck(e))goto bad;
 registration_attempted=1;
 if((*e)->RegisterNatives(e,bridge,methods,9))goto bad;
 return JNI_VERSION_1_8;
 bad:if(bridge&&registration_attempted){jthrowable pending=(*e)->ExceptionOccurred(e);if(pending)(*e)->ExceptionClear(e);
  (void)(*e)->UnregisterNatives(e,bridge);if((*e)->ExceptionCheck(e))(*e)->ExceptionClear(e);
  if(pending){(*e)->Throw(e,pending);(*e)->DeleteLocalRef(e,pending);}}
 while(global_count)(*e)->DeleteGlobalRef(e,globals[--global_count]);
 return JNI_ERR;
}

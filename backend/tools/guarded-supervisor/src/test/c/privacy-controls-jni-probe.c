#define _GNU_SOURCE
#include "privacy-controls.h"
#include <jni.h>
#include <errno.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyControlsJvmProbe_establish(JNIEnv *env,jclass cls) {
    (void)env; (void)cls; return es_privacy_establish();
}
JNIEXPORT jint JNICALL Java_studio_environment_supervisor_PrivacyControlsJvmProbe_check(JNIEnv *env,jclass cls) {
    (void)env; (void)cls;
    if (prctl(PR_GET_DUMPABLE) != 0 || prctl(PR_GET_NO_NEW_PRIVS,0,0,0,0) != 1) return 31;
    unsigned long values[] = {1,2,1UL<<32,(1UL<<32)|1};
    for (unsigned int i=0;i<sizeof(values)/sizeof(values[0]);i++) {
        errno=0;
        if (syscall(SYS_prctl,PR_SET_DUMPABLE,values[i],0UL,0UL,0UL) != -1 || errno != EPERM) {
            prctl(PR_SET_DUMPABLE,0UL,0UL,0UL,0UL);
            return 32;
        }
    }
    return 0;
}

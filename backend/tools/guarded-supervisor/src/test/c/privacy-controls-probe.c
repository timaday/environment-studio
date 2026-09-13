#define _GNU_SOURCE
#include "privacy-controls.h"
#include <errno.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <pthread.h>
#include <stdint.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>
static int denied(unsigned long value) {
    errno = 0;
    return syscall(SYS_prctl, PR_SET_DUMPABLE, value, 0UL, 0UL, 0UL) == -1 && errno == EPERM;
}
static int checks(void) {
    return prctl(PR_GET_DUMPABLE) == 0 && prctl(PR_GET_NO_NEW_PRIVS,0,0,0,0) == 1
        && prctl(PR_SET_DUMPABLE,0UL,0UL,0UL,0UL) == 0
        && denied(1) && denied(2) && denied(1UL<<32) && denied((1UL<<32)|1)
        && denied(~0UL);
}
static pthread_barrier_t barrier;
static void *diverge(void *unused) {
    (void)unused;
    struct sock_filter code[] = { BPF_STMT(BPF_RET|BPF_K,SECCOMP_RET_ALLOW) };
    struct sock_fprog program = {1,code};
    if (prctl(PR_SET_NO_NEW_PRIVS,1,0,0,0) || syscall(SYS_seccomp,SECCOMP_SET_MODE_FILTER,0,&program)) _exit(80);
    pthread_barrier_wait(&barrier);
    pthread_barrier_wait(&barrier);
    return NULL;
}
int main(int argc, char **argv) {
    struct rlimit core = {0,0};
    if (setrlimit(RLIMIT_CORE,&core) || argc != 2) return 90;
    if (!strcmp(argv[1],"diverged")) {
        pthread_t thread;
        if (pthread_barrier_init(&barrier,NULL,2) || pthread_create(&thread,NULL,diverge,NULL)) return 91;
        pthread_barrier_wait(&barrier);
        int result = es_privacy_establish();
        pthread_barrier_wait(&barrier);
        pthread_join(thread,NULL);
        return result == ES_PRIVACY_THREAD_SYNC ? 0 : 21;
    }
    if (es_privacy_establish() != ES_PRIVACY_OK) return 22;
    if (!strcmp(argv[1],"healthy")) return checks() ? 0 : 23;
    if (!strcmp(argv[1],"option-high-word")) {
        errno = 0;
        long result = syscall(SYS_prctl,(1UL<<32)|PR_SET_DUMPABLE,1UL,0UL,0UL,0UL);
        return result == -1 && errno == EPERM && prctl(PR_GET_DUMPABLE) == 0 ? 0 : 27;
    }
    if (!strcmp(argv[1],"fork")) {
        pid_t child = fork();
        if (child < 0) return 92;
        if (!child) _exit(checks() ? 0 : 24);
        int status;
        if (waitpid(child,&status,0) != child) return 93;
        return WIFEXITED(status) ? WEXITSTATUS(status) : 94;
    }
    if (!strcmp(argv[1],"x32")) {
        errno = 0;
        long result = syscall(SYS_prctl|0x40000000UL,PR_SET_DUMPABLE,1UL,0UL,0UL,0UL);
        return result == -1 && errno == EPERM ? 0 : 25;
    }
    if (!strcmp(argv[1],"compat")) {
#if defined(__x86_64__)
        long result;
        __asm__ volatile("int $0x80" : "=a"(result) : "0"(172L), "b"(4L), "c"(1L) : "memory");
        (void)result;
        return 26;
#else
        return 95;
#endif
    }
    return 96;
}

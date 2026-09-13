#define _GNU_SOURCE
#include "privacy-controls.h"

#if defined(__linux__) && defined(__x86_64__) && !defined(__ILP32__)
#include <errno.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <stddef.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

static int reset_denied(unsigned long value) {
    errno = 0;
    return syscall(SYS_prctl, PR_SET_DUMPABLE, value, 0UL, 0UL, 0UL) == -1
        && errno == EPERM;
}

es_privacy_result es_privacy_establish(void) {
    /* A partial failure remains a refusal: never undo suppression or retry
       without TSYNC. seccomp's positive offending TID is also a failure. */
    if (prctl(PR_SET_DUMPABLE, 0UL, 0UL, 0UL, 0UL) != 0
        || prctl(PR_GET_DUMPABLE) != 0
        || prctl(PR_SET_NO_NEW_PRIVS, 1UL, 0UL, 0UL, 0UL) != 0
        || prctl(PR_GET_NO_NEW_PRIVS, 0UL, 0UL, 0UL, 0UL) != 1)
        return ES_PRIVACY_SUPPRESSION;

    struct sock_filter filter[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_X86_64, 1, 0),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
        /* x32 shares the audit architecture but uses a different syscall ABI. */
        BPF_JUMP(BPF_JMP | BPF_JSET | BPF_K, 0x40000000U, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, SYS_prctl, 0, 7),
        /* Linux consumes the prctl option as int, ignoring its high word. */
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[0])),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, PR_SET_DUMPABLE, 0, 5),
        /* The value is unsigned long: deny every nonzero 64-bit encoding. */
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[1]) + 4),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0, 0, 2),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[1])),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0, 1, 0),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW)
    };
    struct sock_fprog program = {
        (unsigned short)(sizeof(filter) / sizeof(filter[0])), filter
    };
    if (syscall(SYS_seccomp, SECCOMP_SET_MODE_FILTER,
                SECCOMP_FILTER_FLAG_TSYNC, &program) != 0)
        return ES_PRIVACY_THREAD_SYNC;
    if (!reset_denied(1UL) || !reset_denied(2UL)
        || !reset_denied(1UL << 32) || prctl(PR_GET_DUMPABLE) != 0)
        return ES_PRIVACY_RESET_CONTROL;
    return ES_PRIVACY_OK;
}
#else
es_privacy_result es_privacy_establish(void) { return ES_PRIVACY_PLATFORM; }
#endif

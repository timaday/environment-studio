#define _GNU_SOURCE
#include "privacy-wire.h"
#include <errno.h>
#include <dirent.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#ifdef ES_WIRE_TEST_PARTIAL_SEND
/* Test-only link shim: cap each actual AF_UNIX send, rather than synthesize I/O. */
ssize_t __real_send(int fd,const void *data,size_t length,int flags);
ssize_t __wrap_send(int fd,const void *data,size_t length,int flags) {
    return __real_send(fd,data,length>3?3:length,flags);
}
#endif
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"PROBE_ASSERT_LINE_%d\n",__LINE__); exit(40); } } while(0)
static uint64_t now(void) { struct timespec t; CHECK(!clock_gettime(CLOCK_MONOTONIC,&t)); return (uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec; }
static void pause_ns(long n) { struct timespec t={0,n}; while(nanosleep(&t,&t) && errno==EINTR) {} }
static int zero(const void *data,size_t n) { const unsigned char *p=data; for(size_t i=0;i<n;i++)if(p[i])return 0;return 1; }
/* Independently specified bytes; production encoding never creates expected data. */
static const unsigned char tuple[84]={
  1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,0,0,0,1,
  21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,
  37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,
  53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,
  69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84};
static const unsigned char proof[12]={0,1,1,0,0,1,0,1,0xc0,0,0,0x3e};
static size_t golden(unsigned type,unsigned char b[109]) {
    memset(b,0,109);memcpy(b,"ESPRV001",8);b[8]=(unsigned char)type;
    if(type==ES_WIRE_PREPARE || type==ES_WIRE_ABORT)return 12;
    if(type==ES_WIRE_REFUSED){b[13]=6;return 16;}
    memcpy(b+12,tuple,84);
    if(type==ES_WIRE_ESTABLISHED){memcpy(b+96,proof,12);return 108;}
    return 96;
}
static es_wire_frame frame(unsigned type) {
    es_wire_frame f={0};f.type=(uint8_t)type;
    if(type==ES_WIRE_CHALLENGE || type==ES_WIRE_ESTABLISHED || type==ES_WIRE_ACK)memcpy(f.tuple,tuple,84);
    if(type==ES_WIRE_ESTABLISHED)memcpy(f.proof,proof,12);
    if(type==ES_WIRE_REFUSED)f.refusal=6;
    return f;
}
static void codec(void) {
    const unsigned types[]={1,2,3,4,0x7e,0x7f};
    for(size_t i=0;i<sizeof(types)/sizeof(types[0]);i++) {
        unsigned char expected[109],actual[108];size_t n=golden(types[i],expected),written=99;
        es_wire_frame input=frame(types[i]),output;
        CHECK(es_wire_encode(&input,actual,&written)==ES_WIRE_OK && written==n && !memcmp(actual,expected,n));
        CHECK(es_wire_decode(expected,n,&output)==ES_WIRE_OK && output.type==input.type);
        CHECK(!memcmp(output.tuple,input.tuple,84) && !memcmp(output.proof,input.proof,12) && output.refusal==input.refusal);
        for(size_t cut=0;cut<n;cut++) {
            memset(&output,0xa5,sizeof(output));CHECK(es_wire_decode(expected,cut,&output)==ES_WIRE_PROTOCOL && zero(&output,sizeof(output)));
        }
        CHECK(es_wire_decode(expected,n+1,&output)==ES_WIRE_PROTOCOL && zero(&output,sizeof(output)));
        for(int reserved=9;reserved<12;reserved++){expected[reserved]=1;CHECK(es_wire_decode(expected,n,&output)==ES_WIRE_PROTOCOL);expected[reserved]=0;}
    }
    unsigned char b[109],out[108];es_wire_frame f;size_t n=golden(3,b),written;
    for(int i=96;i<108;i++){b[i]^=1;CHECK(es_wire_decode(b,n,&f)==ES_WIRE_PROTOCOL);b[i]^=1;}
    b[31]=0;CHECK(es_wire_decode(b,n,&f)==ES_WIRE_PROTOCOL);b[31]=65;CHECK(es_wire_decode(b,n,&f)==ES_WIRE_PROTOCOL);
    golden(0x7f,b);b[13]=7;CHECK(es_wire_decode(b,16,&f)==ES_WIRE_PROTOCOL);b[13]=0;CHECK(es_wire_decode(b,16,&f)==ES_WIRE_PROTOCOL);
    f=frame(3);f.proof[0]=1;memset(out,0xa5,108);CHECK(es_wire_encode(&f,out,&written)==ES_WIRE_PROTOCOL && written==0 && zero(out,108));
    golden(1,b);b[0]='X';CHECK(es_wire_decode(b,12,&f)==ES_WIRE_PROTOCOL);golden(1,b);b[8]=0x55;CHECK(es_wire_decode(b,12,&f)==ES_WIRE_PROTOCOL);
}
static void pair(int s[2]) { CHECK(!socketpair(AF_UNIX,SOCK_STREAM|SOCK_NONBLOCK|SOCK_CLOEXEC,0,s)); }
static void raw_write(int fd,const unsigned char *p,size_t n) {
    uint64_t end=now()+3000000000ULL;
    while(n){ssize_t used=send(fd,p,n,MSG_NOSIGNAL);if(used>0){p+=used;n-=(size_t)used;continue;}CHECK(used<0&&(errno==EAGAIN||errno==EINTR));CHECK(now()<end);struct pollfd f={fd,POLLOUT,0};CHECK(poll(&f,1,50)>=0);}
}
static void raw_read(int fd,unsigned char *p,size_t n) {
    uint64_t end=now()+3000000000ULL;
    while(n){ssize_t used=recv(fd,p,n,0);if(used>0){p+=used;n-=(size_t)used;continue;}CHECK(used<0&&(errno==EAGAIN||errno==EINTR));CHECK(now()<end);struct pollfd f={fd,POLLIN,0};CHECK(poll(&f,1,50)>=0);}
}
static void split_write(int fd,unsigned type,size_t split) {
    unsigned char b[109];size_t n=golden(type,b);size_t first=split<n?split:n-1;
    raw_write(fd,b,first);pause_ns(100000);raw_write(fd,b+first,n-first);
}
static void join(pid_t child) { int status;CHECK(waitpid(child,&status,0)==child && WIFEXITED(status) && WEXITSTATUS(status)==0); }
static void fragments(int parent_side) {
    for(size_t split=1;split<108;split++) {
        int s[2];pair(s);int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);CHECK(cancel>=0);
        pid_t child=fork();CHECK(child>=0);
        if(!child){close(s[0]);unsigned char b[108];
            if(parent_side){split_write(s[1],1,split);raw_read(s[1],b,96);CHECK(!memcmp(b+12,tuple,84));split_write(s[1],3,split);raw_read(s[1],b,96);CHECK(b[8]==4);}
            else {raw_read(s[1],b,12);CHECK(b[8]==1);split_write(s[1],2,split);raw_read(s[1],b,108);CHECK(b[8]==3);split_write(s[1],4,split);}
            close(s[1]);close(cancel);_exit(0);
        }
        close(s[1]);es_wire w={0};es_wire_frame f;
        CHECK(es_wire_init(&w,s[0],cancel,now()+2000000000ULL,parent_side?ES_WIRE_PARENT:ES_WIRE_CHILD)==ES_WIRE_OK);
        if(parent_side){CHECK(es_wire_receive(&w,&f)==ES_WIRE_OK&&f.type==1);f=frame(2);CHECK(es_wire_send(&w,&f)==ES_WIRE_OK);CHECK(es_wire_receive(&w,&f)==ES_WIRE_OK&&f.type==3);f=frame(4);CHECK(es_wire_send(&w,&f)==ES_WIRE_OK);}
        else {f=frame(1);CHECK(es_wire_send(&w,&f)==ES_WIRE_OK);CHECK(es_wire_receive(&w,&f)==ES_WIRE_OK&&f.type==2);f=frame(3);CHECK(es_wire_send(&w,&f)==ES_WIRE_OK);CHECK(es_wire_receive(&w,&f)==ES_WIRE_OK&&f.type==4);}
        CHECK(es_wire_finish(&w)==ES_WIRE_COMPLETE && w.fd==-1 && zero(w.tuple,84));
        CHECK(es_wire_close(&w)==ES_WIRE_COMPLETE);close(cancel);join(child);
    }
}
static void malformed(void) {
    for(int variant=0;variant<5;variant++) {
        int s[2];pair(s);int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);es_wire w={0};es_wire_frame f;unsigned char b[109];
        CHECK(es_wire_init(&w,s[0],cancel,now()+1000000000ULL,ES_WIRE_PARENT)==ES_WIRE_OK);
        size_t n=golden(1,b);
        if(variant==0)b[8]=2; /* Reordered frame; complete wrong-direction body. */
        if(variant==1)b[9]=1;
        if(variant==2)b[0]='X';
        if(variant==3){b[n++]=1;} /* Bytes after PREPARE. */
        if(variant==4)n=11;
        raw_write(s[1],b,n);CHECK(!shutdown(s[1],SHUT_WR));memset(&f,0xa5,sizeof(f));
        CHECK(es_wire_receive(&w,&f)==ES_WIRE_PROTOCOL && zero(&f,sizeof(f)) && w.fd==-1 && zero(w.tuple,84));
        CHECK(es_wire_close(&w)==ES_WIRE_PROTOCOL);close(s[1]);close(cancel);
    }
}
static void correlation(void) {
    for(int variant=0;variant<3;variant++) {
        int s[2];pair(s);int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);es_wire w={0};es_wire_frame f;unsigned char b[109];
        CHECK(es_wire_init(&w,s[0],cancel,now()+1000000000ULL,ES_WIRE_PARENT)==ES_WIRE_OK);
        raw_write(s[1],b,golden(1,b));CHECK(es_wire_receive(&w,&f)==ES_WIRE_OK);f=frame(2);CHECK(es_wire_send(&w,&f)==ES_WIRE_OK);raw_read(s[1],b,96);
        size_t n=golden(3,b);if(variant==0)b[12]^=1;if(variant==1)b[40]^=1;if(variant==2)b[97]=0;raw_write(s[1],b,n);
        CHECK(es_wire_receive(&w,&f)==ES_WIRE_PROTOCOL && zero(&f,sizeof(f)) && zero(w.tuple,84));close(s[1]);close(cancel);
    }
}
static int fd_count(void) {DIR *d=opendir("/proc/self/fd");CHECK(d);int n=0;while(readdir(d))n++;closedir(d);return n;}
static void ancillary(void) {
    for(int count=1;count<=32;count+=31) {
        int s[2];pair(s);int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC),file=open("/dev/null",O_RDONLY|O_CLOEXEC);CHECK(file>=0);es_wire w={0};es_wire_frame f;
        CHECK(es_wire_init(&w,s[0],cancel,now()+1000000000ULL,ES_WIRE_PARENT)==ES_WIRE_OK);int before=fd_count();
        unsigned char b[109];size_t n=golden(1,b);union{struct cmsghdr align;unsigned char bytes[CMSG_SPACE(32*sizeof(int))];} control={0};
        struct iovec iov={b,n};struct msghdr msg={0};msg.msg_iov=&iov;msg.msg_iovlen=1;msg.msg_control=control.bytes;msg.msg_controllen=CMSG_SPACE((size_t)count*sizeof(int));
        struct cmsghdr *c=CMSG_FIRSTHDR(&msg);c->cmsg_level=SOL_SOCKET;c->cmsg_type=SCM_RIGHTS;c->cmsg_len=CMSG_LEN((size_t)count*sizeof(int));for(int i=0;i<count;i++)memcpy(CMSG_DATA(c)+i*sizeof(int),&file,sizeof(file));
        CHECK(sendmsg(s[1],&msg,MSG_NOSIGNAL)==(ssize_t)n);
        CHECK(es_wire_receive(&w,&f)==ES_WIRE_PROTOCOL && zero(&f,sizeof(f)));CHECK(fd_count()==before-1);
        close(file);close(cancel);close(s[1]);
    }
}
static volatile sig_atomic_t signals;
static void interrupted(int signo){(void)signo;signals++;}
static void *cancel_later(void *arg){pause_ns(20000000);uint64_t one=1;CHECK(write(*(int*)arg,&one,8)==8);return NULL;}
static void *abort_later(void *arg){pause_ns(20000000);unsigned char b[109];size_t n=golden(ES_WIRE_ABORT,b);raw_write(*(int*)arg,b,n);return NULL;}
static void waits(const char *mode) {
    int s[2];pair(s);int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);es_wire w={0};es_wire_frame f;uint64_t begin=now();
    CHECK(es_wire_init(&w,s[0],cancel,begin+70000000ULL,ES_WIRE_CHILD)==ES_WIRE_OK);
    int aborting=!strcmp(mode,"abort-write"),backpressure=!strcmp(mode,"backpressure")||aborting,cancelling=!strcmp(mode,"cancel");pthread_t thread;
    if(backpressure){unsigned char fill[4096]={0};while(send(s[0],fill,sizeof(fill),MSG_NOSIGNAL)>0){}CHECK(errno==EAGAIN);}
    if(cancelling)CHECK(!pthread_create(&thread,NULL,cancel_later,&cancel));
    if(aborting)CHECK(!pthread_create(&thread,NULL,abort_later,&s[1]));
    struct sigaction action={0};action.sa_handler=interrupted;CHECK(!sigaction(SIGALRM,&action,NULL));
    struct itimerval timer={{0,1000},{0,1000}};if(!strcmp(mode,"eintr"))CHECK(!setitimer(ITIMER_REAL,&timer,NULL));
    f=frame(1);es_wire_result result=es_wire_send(&w,&f);
    if(!backpressure){CHECK(result==ES_WIRE_OK);result=es_wire_receive(&w,&f);CHECK(zero(&f,sizeof(f)));}
    timer=(struct itimerval){{0,0},{0,0}};CHECK(!setitimer(ITIMER_REAL,&timer,NULL));
    CHECK(result==(cancelling?ES_WIRE_CANCELLED:aborting?ES_WIRE_ABORTED:ES_WIRE_DEADLINE));CHECK(now()-begin<500000000ULL);CHECK(w.fd==-1&&zero(w.tuple,84));
    if(cancelling){pthread_join(thread,NULL);uint64_t value;CHECK(read(cancel,&value,8)==8&&value==1);}
    if(aborting)pthread_join(thread,NULL);
    if(!strcmp(mode,"eintr"))CHECK(signals>1);
    close(cancel);close(s[1]);
}
static void terminal_frames(void) {
    for(int child=0;child<2;child++) {
        int s[2];pair(s);int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);es_wire w={0};es_wire_frame f;unsigned char b[109];
        CHECK(es_wire_init(&w,s[0],cancel,now()+1000000000ULL,child?ES_WIRE_CHILD:ES_WIRE_PARENT)==ES_WIRE_OK);
        if(child){f=frame(1);CHECK(es_wire_send(&w,&f)==ES_WIRE_OK);}
        size_t n=golden(child?ES_WIRE_ABORT:ES_WIRE_REFUSED,b);raw_write(s[1],b,n);
        CHECK(es_wire_receive(&w,&f)==(child?ES_WIRE_ABORTED:ES_WIRE_PEER_REFUSED)&&zero(&f,sizeof(f))&&w.fd==-1);
        close(cancel);close(s[1]);
    }
}
static void final_eof(int trailing) {
    int s[2];pair(s);int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);es_wire w={0};es_wire_frame f;unsigned char b[109];
    CHECK(es_wire_init(&w,s[0],cancel,now()+70000000ULL,ES_WIRE_PARENT)==ES_WIRE_OK);
    raw_write(s[1],b,golden(1,b));CHECK(es_wire_receive(&w,&f)==ES_WIRE_OK);f=frame(2);CHECK(es_wire_send(&w,&f)==ES_WIRE_OK);raw_read(s[1],b,96);
    raw_write(s[1],b,golden(3,b));CHECK(es_wire_receive(&w,&f)==ES_WIRE_OK);f=frame(4);CHECK(es_wire_send(&w,&f)==ES_WIRE_OK);raw_read(s[1],b,96);
    if(trailing)raw_write(s[1],b,golden(1,b));
    CHECK(es_wire_finish(&w)==(trailing?ES_WIRE_PROTOCOL:ES_WIRE_DEADLINE));CHECK(zero(w.tuple,84)&&w.fd==-1);
    close(s[1]);close(cancel);
}
static void trickle(void) {
    int s[2];pair(s);int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);pid_t child=fork();CHECK(child>=0);
    if(!child){close(s[0]);unsigned char b[109];size_t n=golden(1,b);for(size_t i=0;i<n;i++){if(send(s[1],b+i,1,MSG_NOSIGNAL)!=1)break;pause_ns(15000000);}close(s[1]);close(cancel);_exit(0);}
    close(s[1]);es_wire w={0};es_wire_frame f;uint64_t start=now();
    CHECK(es_wire_init(&w,s[0],cancel,start+70000000ULL,ES_WIRE_PARENT)==ES_WIRE_OK);
    CHECK(es_wire_receive(&w,&f)==ES_WIRE_DEADLINE&&zero(&f,sizeof(f)));CHECK(now()-start<300000000ULL);
    close(cancel);join(child);
}
int main(int argc,char **argv) {
    CHECK(argc==2);
    if(!strcmp(argv[1],"alias")) {
        int cancel=eventfd(0,EFD_NONBLOCK|EFD_CLOEXEC);CHECK(cancel>=0);es_wire w={0};
        CHECK(es_wire_init(&w,cancel,cancel,now()+1000000000ULL,ES_WIRE_PARENT)==ES_WIRE_PROTOCOL);
        CHECK(fcntl(cancel,F_GETFD)>=0);CHECK(!close(cancel));
    }
    else if(!strcmp(argv[1],"codec"))codec();
    else if(!strcmp(argv[1],"parent-fragments"))fragments(1);
    else if(!strcmp(argv[1],"child-fragments"))fragments(0);
    else if(!strcmp(argv[1],"malformed"))malformed();
    else if(!strcmp(argv[1],"correlation"))correlation();
    else if(!strcmp(argv[1],"ancillary"))ancillary();
    else if(!strcmp(argv[1],"terminals"))terminal_frames();
    else if(!strcmp(argv[1],"final-stall"))final_eof(0);
    else if(!strcmp(argv[1],"final-trailing"))final_eof(1);
    else if(!strcmp(argv[1],"trickle"))trickle();
    else waits(argv[1]);
    return 0;
}

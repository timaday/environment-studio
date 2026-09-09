#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>

/* Private, platform-pinned ABI. No dynamic commands, allocation or diagnostic values. */
static int tty=-1, wake[2]={-1,-1}, captured=0, changed=0, restore_requested=0;
static volatile sig_atomic_t interrupted=0;
static struct termios original;
static unsigned char snapshot[512],account[129],secret[4097];
static size_t snapshot_size;
static uint64_t tty_device,device,inode;
static pid_t session,group,parent;
static int64_t now_ms(void){struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t))return -1;return (int64_t)t.tv_sec*1000+t.tv_nsec/1000000;}
static void wipe(void *p,size_t n){volatile unsigned char *v=p;while(n--)*v++=0;}
static void caught(int signal_number){int saved=errno;interrupted=signal_number;unsigned char b=1;if(wake[1]>=0){ssize_t n=write(wake[1],&b,1);(void)n;}errno=saved;}
static void put32(unsigned char *p,uint32_t v){for(int i=3;i>=0;i--){p[i]=(unsigned char)v;v>>=8;}}
static uint32_t get32(const unsigned char *p){uint32_t n=0;for(int i=0;i<4;i++)n=n*256+p[i];return n;}
static void put64(unsigned char *p,uint64_t v){for(int i=7;i>=0;i--){p[i]=(unsigned char)v;v>>=8;}}
static uint64_t get64(const unsigned char *p){uint64_t n=0;for(int i=0;i<8;i++)n=n*256+p[i];return n;}
static int ready(int fd,short events,int64_t end,int check_parent){
    for(;;){int64_t left=end-now_ms();if(left<=0||interrupted)return -1;struct pollfd f[3]={{fd,events,0},{wake[0],POLLIN,0},{STDIN_FILENO,0,0}};
        int n=poll(f,check_parent?3:2,left>1000?1000:(int)left);if(n<0){if(errno==EINTR)continue;return -1;}if(f[1].revents||(check_parent&&(f[2].revents&(POLLHUP|POLLERR|POLLNVAL))))return -1;
        if(f[0].revents&events)return 0;
        if(f[0].revents&(POLLHUP|POLLERR|POLLNVAL))return -1;
    }
}
static int send_bytes(int fd,const unsigned char *p,size_t n,int64_t end){while(n){if(ready(fd,POLLOUT,end,fd!=STDIN_FILENO))return -1;ssize_t sent=write(fd,p,n);if(sent<0){if(errno==EAGAIN||errno==EINTR)continue;return -1;}if(!sent)return -1;p+=sent;n-=(size_t)sent;}return 0;}
static int receive(unsigned char *p,size_t n,int64_t end){while(n){if(ready(STDIN_FILENO,POLLIN,end,0))return -1;ssize_t got=read(STDIN_FILENO,p,n);if(got<0){if(errno==EINTR||errno==EAGAIN)continue;return -1;}if(!got)return -1;p+=got;n-=(size_t)got;}return 0;}
static int frame(unsigned char kind,const unsigned char *p,size_t n,int64_t end){unsigned char header[13]={'E','S','T','T','Y','0','0','1',0,0,0,0,0};header[8]=kind;put32(header+9,(uint32_t)n);return send_bytes(STDOUT_FILENO,header,sizeof header,end)||send_bytes(STDOUT_FILENO,p,n,end);}
static int identity(void){struct stat st;unsigned int actual=0;return fstat(tty,&st)||ioctl(tty,TIOCGDEV,&actual)||tty_device!=actual||device!=(uint64_t)st.st_dev||inode!=(uint64_t)st.st_ino||tcgetsid(tty)!=session||getsid(0)!=session||getpgrp()!=group||tcgetpgrp(tty)!=group?-1:0;}
static int restore(void){
    if(!captured||!changed)return 0;
    /* Restoration is synchronous; cancellation flags must not prevent it. */
    if(identity()||tcflush(tty,TCIFLUSH)||tcsetattr(tty,TCSANOW,&original))return -1;
    struct termios actual;memset(&actual,0,sizeof actual);if(tcgetattr(tty,&actual)||memcmp(&actual,&original,sizeof actual))return -1;changed=0;return 0;
}
static int capture(void){
    struct stat st;unsigned int actual=0;memset(&original,0,sizeof original);
    if(!isatty(tty)||fstat(tty,&st)||ioctl(tty,TIOCGDEV,&actual)||tcgetattr(tty,&original))return -1;
    session=getsid(0);group=getpgrp();parent=getppid();tty_device=actual;device=(uint64_t)st.st_dev;inode=(uint64_t)st.st_ino;
    if(session<0||group<0||parent<=1||getsid(parent)!=session||getpgid(parent)!=group||identity())return -1;
    if(sizeof original>sizeof snapshot-52)return -1;
    memcpy(snapshot,"LNXAMD64",8);put32(snapshot+8,1);put64(snapshot+12,tty_device);put64(snapshot+20,device);put64(snapshot+28,inode);put32(snapshot+36,(uint32_t)session);put32(snapshot+40,(uint32_t)group);put32(snapshot+44,(uint32_t)parent);put32(snapshot+48,sizeof original);memcpy(snapshot+52,&original,sizeof original);snapshot_size=52+sizeof original;captured=1;return 0;
}
static int fallback(int64_t end){
    unsigned char length[4];if(receive(length,4,end))return -1;uint32_t n=get32(length);if(n!=52+sizeof original||n>sizeof snapshot||receive(snapshot,n,end))return -1;
    if(memcmp(snapshot,"LNXAMD64",8)||get32(snapshot+8)!=1||get32(snapshot+48)!=sizeof original)return -1;
    tty_device=get64(snapshot+12);device=get64(snapshot+20);inode=get64(snapshot+28);session=(pid_t)get32(snapshot+36);group=(pid_t)get32(snapshot+40);parent=(pid_t)get32(snapshot+44);
    if(parent!=getppid()||identity())return -1;
    memcpy(&original,snapshot+52,sizeof original);captured=1;changed=1;return restore();
}
static int line(unsigned char *buffer,size_t limit,size_t *length,int64_t end){
    size_t used=0;
    for(;;){if(interrupted||now_ms()>=end)return -1;struct pollfd f[3]={{tty,POLLIN,0},{STDIN_FILENO,POLLIN,0},{wake[0],POLLIN,0}};int64_t left=end-now_ms();if(left<=0)return -1;
        int n=poll(f,3,left>1000?1000:(int)left);if(n<0){if(errno==EINTR)continue;return -1;}if(f[1].revents){unsigned char command;if(read(STDIN_FILENO,&command,1)==1&&command==4)restore_requested=1;return -1;}if(f[2].revents)return -1;if(f[0].revents&(POLLHUP|POLLERR|POLLNVAL))return -1;
        if(f[0].revents&POLLIN){unsigned char b;ssize_t got=read(tty,&b,1);if(got<0&&(errno==EAGAIN||errno==EINTR))continue;if(got!=1)return -1;
            if(b=='\r'||b=='\n'){if(tcflush(tty,TCIFLUSH))return -1;*length=used;return 0;}if(b==0||used==limit)return -1;buffer[used++]=b;
        }
    }
}
static int account_valid(size_t n,int pg){if(!n||n>(pg?63u:128u))return 0;for(size_t i=0;i<n;i++){unsigned char c=account[i];int letter=(c>='A'&&c<='Z')||(pg&&c>='a'&&c<='z');if(i==0){if(!letter&&!(pg&&c=='_'))return 0;}else if(!letter&&!(c>='0'&&c<='9')&&c!='_'&&c!='$'&&(pg||c!='#'))return 0;}return 1;}
static int utf8_valid(size_t n){size_t i=0,count=0;while(i<n){uint32_t v;unsigned char c=secret[i++];int extra;if(c<0x80){v=c;extra=0;}else if(c>=0xc2&&c<=0xdf){v=c&31;extra=1;}else if(c>=0xe0&&c<=0xef){v=c&15;extra=2;}else if(c>=0xf0&&c<=0xf4){v=c&7;extra=3;}else return 0;int width=extra;while(extra--){if(i>=n||(secret[i]&0xc0)!=0x80)return 0;v=(v<<6)|(secret[i++]&63);}if((width==1&&v<0x80)||(width==2&&v<0x800)||(width==3&&v<0x10000)||v>0x10ffff||(v>=0xd800&&v<=0xdfff)||v==0||v==10||v==13||++count>1024)return 0;}return 1;}
static int entry(int pg,int64_t end){
    struct termios mode=original;mode.c_lflag&=~(ECHO|ECHONL|ICANON|IEXTEN|ISIG);mode.c_iflag&=~(ICRNL|INLCR|IGNCR|IXON|IXOFF|ISTRIP|PARMRK);mode.c_cc[VMIN]=1;mode.c_cc[VTIME]=0;
    if(identity())return -1;
    changed=1;if(tcsetattr(tty,TCSAFLUSH,&mode))return -1;
    const char *prompt=pg?"Account is visible in native process arguments. Enter to finish; entry expires after 120s.\nAccount: ":"Enter to finish; entry expires after 120s.\nAccount: ";
    if(send_bytes(tty,(const unsigned char *)prompt,strlen(prompt),end))return -1;
    size_t n=0;
    if(line(account,pg?63:128,&n,end)||!account_valid(n,pg)||frame(2,account,n,end))return -1;
    wipe(account,sizeof account);
    if(tcflush(tty,TCIFLUSH)||send_bytes(tty,(const unsigned char *)"\nPassword: ",11,end)||line(secret,4096,&n,end)||!utf8_valid(n))return -1;
    if(tcflush(tty,TCIFLUSH)||restore()||frame(3,secret,n,end))return -1;
    wipe(secret,sizeof secret);return frame(4,NULL,0,end);
}
int main(int argc,char **argv){
    (void)argv;int result=77;int64_t start=now_ms(),end=start+120000;unsigned char command=0;
    if(argc!=1||start<0||pipe2(wake,O_NONBLOCK|O_CLOEXEC))goto done;
    struct sigaction action;memset(&action,0,sizeof action);action.sa_handler=caught;sigemptyset(&action.sa_mask);
    int signals[]={SIGINT,SIGTERM,SIGHUP,SIGTSTP,SIGPIPE};for(size_t i=0;i<sizeof signals/sizeof signals[0];i++)if(sigaction(signals[i],&action,NULL))goto done;
    parent=getppid();if(parent<=1||prctl(PR_SET_PDEATHSIG,SIGTERM)||getppid()!=parent)goto done;
    if(fcntl(STDIN_FILENO,F_SETFL,O_NONBLOCK)||fcntl(STDOUT_FILENO,F_SETFL,O_NONBLOCK))goto done;
    tty=open("/dev/tty",O_RDWR|O_CLOEXEC|O_NOCTTY|O_NONBLOCK);if(tty<0||receive(&command,1,end))goto done;
    if(command==5){end=now_ms()+10000;if(!fallback(end)&&!frame(4,NULL,0,end))result=0;goto done;}
    if(command!=1||capture()||frame(1,snapshot,snapshot_size,end)||receive(&command,1,end))goto done;
    if(command==4){if(!restore()&&!frame(4,NULL,0,end))result=0;goto done;}
    if((command==2||command==3)&&!entry(command==2,end))result=0;
done:
    wipe(account,sizeof account);wipe(secret,sizeof secret);
    if(restore())result=78;
    else if(restore_requested){interrupted=0;if(!frame(4,NULL,0,now_ms()+10000))result=0;}
    if(result){unsigned char reason=(unsigned char)result;interrupted=0;(void)frame(0x7f,&reason,1,now_ms()+1000);}
    wipe(snapshot,sizeof snapshot);wipe(&original,sizeof original);
    if(tty>=0)close(tty);
    if(wake[0]>=0)close(wake[0]);
    if(wake[1]>=0)close(wake[1]);
    return result;
}

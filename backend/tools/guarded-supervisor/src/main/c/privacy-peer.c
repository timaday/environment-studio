#define _GNU_SOURCE
#include "privacy-peer.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/vfs.h>
#include <linux/magic.h>
#include <time.h>
#include <unistd.h>
#ifndef SO_PEERPIDFD
#define SO_PEERPIDFD 77
#endif
static void wipe(void *v,size_t n){volatile unsigned char *p=v;while(n--)*p++=0;}
static es_peer_result end(es_peer *p,es_peer_result r){
 if(p->state==2)return p->terminal;
 int fd=p->pidfd;p->pidfd=-1;
 if(fd>=0&&close(fd))r=ES_PEER_CLEANUP;
 wipe(&p->identity,sizeof(p->identity));p->terminal=r;p->state=2;return r;
}
static es_peer_result guard(es_peer *p){
 struct pollfd cancel={.fd=p->cancel_fd,.events=POLLIN};
 int r=poll(&cancel,1,0);if(r<0)return ES_PEER_IO;
 if(cancel.revents&POLLIN)return ES_PEER_CANCELLED;
 if(cancel.revents)return ES_PEER_IO;
 struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<0||t.tv_nsec<0||t.tv_nsec>=1000000000L)return ES_PEER_IO;
 if((uint64_t)t.tv_sec>(UINT64_MAX-(uint64_t)t.tv_nsec)/1000000000ULL)return ES_PEER_IO;
 uint64_t current=(uint64_t)t.tv_sec*1000000000ULL+(uint64_t)t.tv_nsec;
 if(current>=p->deadline_ns)return ES_PEER_DEADLINE;
 if(p->deadline_ns-current>10000000000ULL)return ES_PEER_INVALID;
 if(p->pidfd>=0){struct pollfd pin={.fd=p->pidfd,.events=POLLIN};r=poll(&pin,1,0);if(r<0)return ES_PEER_IO;if(pin.revents&(POLLIN|POLLHUP))return ES_PEER_DEAD;if(pin.revents)return ES_PEER_IO;}
 return ES_PEER_OK;
}
/* Procfs is local and opened nonblocking; fixed caps include the EOF witness.
   No path/content is returned in errors. Close uncertainty never retries. */
static es_peer_result file(es_peer *p,const char *path,char *out,size_t cap){
 es_peer_result r=guard(p);if(r)return r;
 int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK);
 if(fd<0){r=guard(p);return r?r:ES_PEER_IDENTITY;}
 struct statfs filesystem;
 if(fstatfs(fd,&filesystem)||filesystem.f_type!=PROC_SUPER_MAGIC){return close(fd)?ES_PEER_CLEANUP:ES_PEER_IDENTITY;}
 size_t n=0;int complete=0;
 while(n<cap-1){r=guard(p);if(r)break;ssize_t count=read(fd,out+n,cap-1-n);if(count==0){complete=1;break;}if(count<0){if(errno==EINTR)continue;r=ES_PEER_IO;break;}n+=(size_t)count;}
 if(close(fd))r=ES_PEER_CLEANUP;
 if(!r&&!complete)r=ES_PEER_IDENTITY;
 if(!r&&memchr(out,0,n))r=ES_PEER_IDENTITY;
 out[n]=0;
 if(r!=ES_PEER_CLEANUP){es_peer_result final=guard(p);if(final)r=final;}
 if(r)wipe(out,cap);
 return r;
}
static int decimal(const char **text,uint64_t *value){
 const char *s=*text;uint64_t v=0;int count=0;
 while(*s>='0'&&*s<='9'){unsigned digit=(unsigned)(*s-'0');if(v>(UINT64_MAX-digit)/10)return 0;v=v*10+digit;s++;count++;}
 if(!count)return 0;
 *text=s;*value=v;return 1;
}
static const char *line(const char *s,const char *name){
 size_t n=strlen(name);const char *found=NULL;
 while(*s){if(!strncmp(s,name,n)){if(found)return NULL;found=s+n;}const char *next=strchr(s,'\n');if(!next)break;s=next+1;}
 return found;
}
static void spaces(const char **s){while(**s==' '||**s=='\t')++*s;}
static int single(const char *s,uint64_t *value){if(!s)return 0;spaces(&s);if(!decimal(&s,value))return 0;return *s=='\n'||*s==0;}
static int effective(const char *s,uint32_t expected){
 if(!s)return 0;
 uint64_t v=0;
 for(int i=0;i<4;i++){spaces(&s);if(!decimal(&s,&v)||v>UINT32_MAX)return 0;if(i==1&&v!=expected)return 0;if(i<3&&*s!=' '&&*s!='\t')return 0;}
 return *s=='\n';
}
static es_peer_result identity(es_peer *p,es_peer_identity *out){
 char path[64],data[16384];uint64_t value=0;es_peer_result r;
 int n=snprintf(path,sizeof(path),"/proc/self/fdinfo/%d",p->pidfd);if(n<0||(size_t)n>=sizeof(path))return ES_PEER_IDENTITY;
 r=file(p,path,data,sizeof(data));if(r)goto done;
 if(!single(line(data,"Pid:"),&value)||value!=p->identity.pid){r=ES_PEER_IDENTITY;goto done;}
 n=snprintf(path,sizeof(path),"/proc/%u/status",p->identity.pid);if(n<0||(size_t)n>=sizeof(path)){r=ES_PEER_IDENTITY;goto done;}
 r=file(p,path,data,sizeof(data));if(r)goto done;
 if(!single(line(data,"Pid:"),&value)||value!=p->identity.pid||!effective(line(data,"Uid:"),p->identity.uid)||!effective(line(data,"Gid:"),p->identity.gid)){r=ES_PEER_IDENTITY;goto done;}
 n=snprintf(path,sizeof(path),"/proc/%u/stat",p->identity.pid);if(n<0||(size_t)n>=sizeof(path)){r=ES_PEER_IDENTITY;goto done;}
 r=file(p,path,data,sizeof(data));if(r)goto done;
 const char *s=data;if(!decimal(&s,&value)||value!=p->identity.pid||s[0]!=' '||s[1]!='('){r=ES_PEER_IDENTITY;goto done;}
 s=strrchr(s,')');if(!s||s[1]!=' '){r=ES_PEER_IDENTITY;goto done;}s+=2;
 /* comm may contain spaces, newlines and ')'; remaining stat fields cannot. */
 for(int field=3;field<22;field++){const char *next=strchr(s,' ');if(!next||next==s){r=ES_PEER_IDENTITY;goto done;}s=next+1;}
 if(!decimal(&s,&value)||!value||*s!=' '||(p->identity.start_ticks&&value!=p->identity.start_ticks)){r=ES_PEER_IDENTITY;goto done;}
 r=guard(p);if(!r){*out=p->identity;out->start_ticks=value;}
 done:wipe(data,sizeof(data));return r;
}
es_peer_result es_peer_open(es_peer *p,int socket_fd,int cancel_fd,uint64_t deadline_ns){
 if(!p||p->state||socket_fd<0||cancel_fd<0||socket_fd==cancel_fd)return ES_PEER_INVALID;
 p->pidfd=-1;p->cancel_fd=cancel_fd;p->deadline_ns=deadline_ns;p->state=1;
 es_peer_result r=guard(p);if(r)return end(p,r);
 int type=0,domain=0;socklen_t n=sizeof(type);
 if(getsockopt(socket_fd,SOL_SOCKET,SO_TYPE,&type,&n)||n!=sizeof(type)||type!=SOCK_STREAM)return end(p,ES_PEER_INVALID);
 n=sizeof(domain);if(getsockopt(socket_fd,SOL_SOCKET,SO_DOMAIN,&domain,&n)||n!=sizeof(domain)||domain!=AF_UNIX)return end(p,ES_PEER_INVALID);
 struct ucred credentials={0};n=sizeof(credentials);
 if(getsockopt(socket_fd,SOL_SOCKET,SO_PEERCRED,&credentials,&n)||n!=sizeof(credentials)||credentials.pid<=0)return end(p,ES_PEER_IDENTITY);
 int fd=-1;n=sizeof(fd);
 int acquired=getsockopt(socket_fd,SOL_SOCKET,SO_PEERPIDFD,&fd,&n);
 /* Even a malformed successful option may have installed an owned fd. */
 if(acquired==0&&fd>=0&&fd!=socket_fd&&fd!=cancel_fd)p->pidfd=fd;
 if(acquired)return end(p,errno==ENOPROTOOPT||errno==EINVAL?ES_PEER_UNSUPPORTED:ES_PEER_IO);
 if(n!=sizeof(fd)||fd<0||fd==socket_fd||fd==cancel_fd)return end(p,ES_PEER_IDENTITY);
 int flags=fcntl(fd,F_GETFD);if(flags<0||!(flags&FD_CLOEXEC))return end(p,ES_PEER_IDENTITY);
 p->identity=(es_peer_identity){.pid=(uint32_t)credentials.pid,.uid=(uint32_t)credentials.uid,.gid=(uint32_t)credentials.gid};
 es_peer_identity id={0};r=identity(p,&id);if(r)return end(p,r);p->identity=id;return ES_PEER_OK;
}
es_peer_result es_peer_read(es_peer *p,es_peer_identity *out){
 if(out)wipe(out,sizeof(*out));
 if(!p||!p->state)return ES_PEER_INVALID;
 if(p->state==2)return p->terminal==ES_PEER_OK?ES_PEER_INVALID:p->terminal;
 if(!out)return end(p,ES_PEER_INVALID);
 es_peer_result r=identity(p,out);return r?end(p,r):ES_PEER_OK;
}
es_peer_result es_peer_close(es_peer *p){if(!p||!p->state)return ES_PEER_INVALID;return end(p,ES_PEER_OK);}

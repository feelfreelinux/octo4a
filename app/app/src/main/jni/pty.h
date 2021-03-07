#ifdef __cplusplus
extern "C" {
#endif
#ifndef _PTY_H
#define _PTY_H	1

#include <features.h>

#include <termios.h>
#include <sys/ioctl.h>

/* Create pseudo tty master slave pair with NAME and set terminal
   attributes according to TERMP and WINP and return handles for both
   ends in AMASTER and ASLAVE.  */
int openpty (int *__amaster, int *__aslave, char *__name,
		    struct termios *__termp, struct winsize *__winp);

#ifdef __cplusplus
}
#endif
#endif	/* pty.h */
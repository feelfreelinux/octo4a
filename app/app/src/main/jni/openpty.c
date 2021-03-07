#include "pty.h"

#include <fcntl.h>
#include <stdlib.h>
#include "util.h"

#define HAVE_openpty
int openpty(int *amaster, int *aslave, char *name, struct termios *termp, struct winsize *winp)
{
       char buf[512];
       int master, slave;

       master = open("/dev/ptmx", O_RDWR);
       if (master == -1) return -1;
       if (grantpt(master) || unlockpt(master) || ptsname_r(master, buf, sizeof buf)) goto fail;

       slave = open(buf, O_RDWR | O_NOCTTY);
       if (slave == -1) goto fail;

       /* XXX Should we ignore errors here?  */
       if (termp) tcsetattr(slave, TCSAFLUSH, termp);
       if (winp) ioctl(slave, TIOCSWINSZ, winp);

       *amaster = master;
       *aslave = slave;
       if (name != NULL) strcpy(name, buf);
       return 0;

fail:
       close(master);
       return -1;
}
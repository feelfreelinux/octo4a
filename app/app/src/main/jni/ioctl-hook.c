#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <stdio.h>
#include <dlfcn.h>
#include <termios.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <signal.h>

#ifndef RTLD_NEXT
#define RTLD_NEXT ((void *)-1l)
#endif

#define REAL_LIBC RTLD_NEXT

static char fifoPath[] = "/data/data/com.octo4a/files/home/eventPipe";
static char eventJsonStart[] = "{\"eventType\": \"";
static char eventJsonEnd[] = "\"}\n";


int writeEventToPipe(char* event, int len) {
    int eventFifoFd = open(fifoPath, O_WRONLY);
    write(eventFifoFd, eventJsonStart, sizeof(eventJsonStart)-1);
    write(eventFifoFd, event, len);
    write(eventFifoFd, eventJsonEnd, sizeof(eventJsonEnd)-1);
    close(eventFifoFd);
}

int ioctl(int fd, int request, ...)
{
    static int (*funcIoctl)(int, request_t, void *) = NULL;
    va_list args;
    void *argp;

    if (!funcIoctl)
        funcIoctl = (int (*)(int, request_t, void *))dlsym(REAL_LIBC, "ioctl");
    va_start(args, request);
    argp = va_arg(args, void *);
    va_end(args);

    if (request == TIOCMBIS)
    {
        writeEventToPipe("rtsDts", 6);
        return 0;
    }

    if (request == 0x802c542a || request == 0x402C542B)
    {
        writeEventToPipe("customBaud", 10);
        return 0;
    }

    return funcIoctl(fd, request, argp);
}
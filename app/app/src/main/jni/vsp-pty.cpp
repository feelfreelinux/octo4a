#include <string.h>
#include <jni.h>
#include <string>
#include <android/log.h>
#include <stdio.h>
#include <pty.h>
#include <pthread.h>
#include <termios.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/types.h>
#include <stddef.h>
#include <sys/wait.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <thread>

#define BUFSIZE 512
int master;
static JavaVM *jvm = nullptr;
JNIEnv *storedEnv;
jmethodID stringCallback;
jweak storeWeakListener;
jclass serialDataClass;
jmethodID serialDataConstructor;
pthread_t ptyThreadHandle = 0;

static jint getBaudrate(speed_t baudrate)
{
    switch (baudrate)
    {
    case B0:
        return 0;
    case B50:
        return 50;
    case B75:
        return 75;
    case B110:
        return 110;
    case B134:
        return 134;
    case B150:
        return 150;
    case B200:
        return 200;
    case B300:
        return 300;
    case B600:
        return 600;
    case B1200:
        return 1200;
    case B1800:
        return 1800;
    case B2400:
        return 2400;
    case B4800:
        return 4800;
    case B9600:
        return 9600;
    case B19200:
        return 19200;
    case B38400:
        return 38400;
    case B57600:
        return 57600;
    case B115200:
        return 115200;
    case B230400:
        return 230400;
    case B460800:
        return 460800;
    case B500000:
        return 500000;
    case B576000:
        return 576000;
    case B921600:
        return 921600;
    case B1000000:
        return 1000000;
    case B1152000:
        return 1152000;
    case B1500000:
        return 1500000;
    case B2000000:
        return 2000000;
    case B2500000:
        return 2500000;
    case B3000000:
        return 3000000;
    case B3500000:
        return 3500000;
    case B4000000:
        return 4000000;
    default:
        return 250000;
    }
}

extern "C"
{

    void passReceivedData(unsigned char *val, size_t dataSize, speed_t baudrate, tcflag_t cIflag, tcflag_t cOflag, tcflag_t cCflag, tcflag_t CLflag)
    {
        __android_log_print(ANDROID_LOG_VERBOSE, "GetEnv:", " start Callback to JNL\n");
        JNIEnv *gEnv;

        if (nullptr == jvm)
        {
            __android_log_print(ANDROID_LOG_ERROR, "GetEnv:", "  No VM  \n");
            return;
        }

        JavaVMAttachArgs args;
        args.version = JNI_VERSION_1_6; // set your JNI version
        args.name = nullptr;
        args.group = nullptr;

        int getEnvStat = jvm->GetEnv(reinterpret_cast<void **>(&gEnv), JNI_VERSION_1_6);

        if (getEnvStat == JNI_EDETACHED)
        {
            __android_log_print(ANDROID_LOG_ERROR, "GetEnv:", " not attached\n");
            if (jvm->AttachCurrentThread(&gEnv, &args) != 0)
            {
                __android_log_print(ANDROID_LOG_ERROR, "GetEnv:", " Failed to attach\n");
            }
        }
        else if (getEnvStat == JNI_OK)
        {
            __android_log_print(ANDROID_LOG_VERBOSE, "GetEnv:", " JNI_OK\n");
        }
        else if (getEnvStat == JNI_EVERSION)
        {
            __android_log_print(ANDROID_LOG_ERROR, "GetEnv:", " version not supported\n");
        }

        jbyteArray serialDataArr = gEnv->NewByteArray(dataSize);
        gEnv->SetByteArrayRegion(serialDataArr, 0, dataSize, (jbyte *)val);
        jobject object = gEnv->NewObject(serialDataClass, serialDataConstructor, serialDataArr, static_cast<jint>(baudrate));

        gEnv->CallVoidMethod(storeWeakListener, stringCallback, object);
        gEnv->DeleteLocalRef(object);

        if (gEnv->ExceptionCheck())
            gEnv->ExceptionDescribe();

        jvm->DetachCurrentThread();
    }
}

[[noreturn]] static void* ptyThread(void* irrelevant)
{

    int slave;
    __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "OK GETTIN READY");

    char name[256];
    openpty(&master, &slave, name, nullptr, nullptr);
    __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "CORN");

    unlink("/data/data/com.octo4a/files/serialpipe");
    symlink(name, "/data/data/com.octo4a/files/serialpipe");
    __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "SYMLIKED");

    // Prepare fds
    fd_set rfds, xfds;
    int retval, nread, status = 0, nonzero = 1;
    unsigned char buf[BUFSIZE];
    ioctl(master, TIOCPKT, &nonzero); // Packet mode go brr
    while (true)
    {
        //        setbuf(master, NULL); // gtfu buffer

        FD_ZERO(&rfds);
        FD_SET(master, &rfds);
        FD_ZERO(&xfds);
        FD_SET(master, &xfds);

        select(1 + master, &rfds, nullptr, &xfds, nullptr);

        const char *r_text = (FD_ISSET(master, &rfds) ? "master ready for reading" : "- ");
        const char *x_text = (FD_ISSET(master, &xfds) ? "exception on master" : "- ");

        __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "rfds: %s, xfds: %s\n", r_text, x_text);
        if ((nread = read(master, buf, BUFSIZE - 1)) < 0)
            __android_log_print(ANDROID_LOG_ERROR, "TAG", "read error");
        else
        {
            if (*buf == TIOCPKT_DATA)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "TIOCPKT_DATA\n");

            if (*buf == TIOCPKT_START)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "TIOCPKT_START\n");

            if (*buf == TIOCPKT_STOP)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "TIOCPKT_STOP\n");

            if (*buf == TIOCPKT_IOCTL)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "IOCTL\n");

            if (*buf == TIOCPKT_FLUSHREAD)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "TIOCPKT_FLUSHREAD\n");

            if (*buf == TIOCPKT_FLUSHWRITE)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "TIOCPKT_FLUSHWRITE\n");

            if (*buf == TIOCPKT_DOSTOP)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "TIOCPKT_DOSTOP\n");

            if (*buf == TIOCPKT_NOSTOP)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "TIOCPKT_NOSTOP\n");

            struct termios tio={};
            tcgetattr(master, &tio);
            __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "Baudrate: %d\n", cfgetospeed(&tio));
            if ((tio.c_cflag & CSIZE) == CS8)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "8 b i t \n");

            if ((tio.c_cflag & CSIZE) == CS7)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "7 b i t \n");

            if ((tio.c_cflag & CSIZE) == CS6)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "6 b i t \n");

            if ((tio.c_cflag & CSIZE) == CS5)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "5 b i t \n");

            if (tio.c_cflag & CSTOPB)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "2 stop bits\n");

            if (tio.c_cflag & PARENB)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "Got parity\n");

            if (tio.c_cflag & PARODD)
                __android_log_print(ANDROID_LOG_VERBOSE, "TAG", "Odd parity\n");

            passReceivedData(buf, nread, cfgetospeed(&tio), tio.c_iflag, tio.c_oflag, tio.c_cflag, tio.c_lflag);
        }
    }
}

extern "C"
{

    JNIEXPORT void JNICALL Java_com_octo4a_serial_VSPPty_writeData(JNIEnv *env, jobject obj, jbyteArray data)
    {
        jsize numBytes = env->GetArrayLength(data);
        jbyte *lib = env->GetByteArrayElements(data, 0);
        write(master, lib, numBytes);
        //    fflush(master);
    }

    JNIEXPORT jint JNICALL Java_com_octo4a_serial_VSPPty_getBaudrate(JNIEnv *env, jobject obj, jint data)
    {
        return getBaudrate(data);
    }

    JNIEXPORT void JNICALL Java_com_octo4a_serial_VSPPty_setVSPListener(JNIEnv *env, jobject instance, jobject listener)
    {
        env->GetJavaVM(&jvm);
        storedEnv = env;

        storeWeakListener = env->NewWeakGlobalRef(listener);
        serialDataClass = (jclass) env->NewGlobalRef(env->FindClass("com/octo4a/serial/SerialData"));
        serialDataConstructor = env->GetMethodID(serialDataClass, "<init>", "([BIIIII)V");
        jclass clazz = env->GetObjectClass(storeWeakListener);
        stringCallback = env->GetMethodID(clazz, "onDataReceived", "(Lcom/octo4a/serial/SerialData;)V");

        __android_log_print(ANDROID_LOG_VERBOSE, "GetEnv:", " Subscribe to Listener  OK \n");
        if (nullptr == stringCallback)
            return;
    }

    JNIEXPORT void JNICALL Java_com_octo4a_serial_VSPPty_runPtyThread(JNIEnv *env, jobject instance)
    {
        if (ptyThreadHandle == 0) {
            pthread_create(&ptyThreadHandle, nullptr, &ptyThread, nullptr);
        }
    }
}
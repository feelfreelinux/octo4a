// from https://stackoverflow.com/questions/43642111/android-renderscript-to-convert-nv12-yuv-to-rgb

#pragma version(1)
#pragma rs java_package_name(com.octo4a.camera)
#pragma rs_fp_relaxed

rs_allocation Yplane;
uint32_t Yline;
uint32_t UVline;
rs_allocation Uplane;
rs_allocation Vplane;
rs_allocation NV21;
uint32_t Width;
uint32_t Height;

uchar4 __attribute__((kernel)) YUV420toRGB(uint32_t x, uint32_t y)
{
    short Y = rsGetElementAt_uchar(Yplane, x + y * Yline);
    short V = rsGetElementAt_uchar(Vplane, (x & ~1) + y/2 * UVline) - 128;
    short U = rsGetElementAt_uchar(Uplane, (x & ~1) + y/2 * UVline) - 128;
    // https://en.wikipedia.org/wiki/YCbCr#JPEG_conversion
    short R = Y + (512           + 1436 * V) / 1024; //             1.402
    short G = Y + (512 -  352 * U - 731 * V) / 1024; // -0.344136  -0.714136
    short B = Y + (512 + 1815 * U          ) / 1024; //  1.772
    if (R < 0) R = 0; else if (R > 255) R = 255;
    if (G < 0) G = 0; else if (G > 255) G = 255;
    if (B < 0) B = 0; else if (B > 255) B = 255;
    return (uchar4){R, G, B, 255};
}

uchar4 __attribute__((kernel)) YUV420toRGB_180(uint32_t x, uint32_t y)
{
    return YUV420toRGB(Width - 1 - x, Height - 1 - y);
}

uchar4 __attribute__((kernel)) YUV420toRGB_90(uint32_t x, uint32_t y)
{
    return YUV420toRGB(y, Width - x - 1);
}

uchar4 __attribute__((kernel)) YUV420toRGB_270(uint32_t x, uint32_t y)
{
    return YUV420toRGB(Height - 1 - y, x);
}

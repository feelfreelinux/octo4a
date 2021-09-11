#include <string.h>
#include <jni.h>
#include <string>
#include <android/log.h>
#include <stdio.h>
#include <sys/select.h>
#include <sys/types.h>
#include <stddef.h>
#include <sys/wait.h>
#include <unistd.h>
#include <stdlib.h>

namespace {

void yuv420toNv21(int image_width, int image_height, const int8_t* y_buffer,
                  const int8_t* u_buffer, const int8_t* v_buffer, int y_pixel_stride,
                  int uv_pixel_stride, int y_row_stride, int uv_row_stride,
                  int8_t *nv21) {
  for(int y = 0; y < image_height; ++y) {
    int destOffset = image_width * y;
    int yOffset = y * y_row_stride;
    memcpy(nv21 + destOffset, y_buffer + yOffset, image_width);
  }

  int idUV = image_width * image_height;
  int uv_width = image_width / 2;
  int uv_height = image_height / 2;
  for(int y = 0; y < uv_height; ++y) {
    int uvOffset = y * uv_row_stride;
    for (int x = 0; x < uv_width; ++x) {
      int bufferIndex = uvOffset + (x * uv_pixel_stride);
      // V channel.
      nv21[idUV++] = v_buffer[bufferIndex];
      // U channel.
      nv21[idUV++] = u_buffer[bufferIndex];
    }
  }
}

}  // nampespace

extern "C" {

jboolean Java_com_octo4a_camera_NativeCameraUtils_yuv420toNv21(
    JNIEnv *env, jclass clazz,
    jint image_width, jint image_height, jobject y_byte_buffer,
    jobject u_byte_buffer, jobject v_byte_buffer, jint y_pixel_stride,
    jint uv_pixel_stride, jint y_row_stride, jint uv_row_stride,
    jbyteArray nv21_array) {

    auto y_buffer = static_cast<jbyte*>(env->GetDirectBufferAddress(y_byte_buffer));
    auto u_buffer = static_cast<jbyte*>(env->GetDirectBufferAddress(u_byte_buffer));
    auto v_buffer = static_cast<jbyte*>(env->GetDirectBufferAddress(v_byte_buffer));

    jbyte* nv21 = env->GetByteArrayElements(nv21_array, nullptr);
    if (nv21 == nullptr || y_buffer == nullptr || u_buffer == nullptr
        || v_buffer == nullptr) {
        // Log this.
        return false;
    }

    yuv420toNv21(image_width, image_height, y_buffer, u_buffer, v_buffer,
               y_pixel_stride, uv_pixel_stride, y_row_stride, uv_row_stride,
               nv21);

    env->ReleaseByteArrayElements(nv21_array, nv21, 0);
    return true;
}

}  // extern "C"
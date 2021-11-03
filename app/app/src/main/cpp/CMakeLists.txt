project(Octo4a)
cmake_minimum_required(VERSION 3.4.1)

add_library(vsp-pty SHARED
            vsp-pty.cpp openpty.c)

target_link_libraries(vsp-pty
                      android
                      log)

add_library(yuv2rgb SHARED
            yuv2rgb.cpp)

target_link_libraries(yuv2rgb
                      android)

add_library(ioctl-hook STATIC
            ioctl-hook.c)

target_link_libraries(ioctl-hook
                      android)
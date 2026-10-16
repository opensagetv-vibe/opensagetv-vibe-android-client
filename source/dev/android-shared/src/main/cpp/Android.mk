LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := vibe_gpu_fence
LOCAL_SRC_FILES := vibe_gpu_fence.c
LOCAL_CFLAGS := -std=c11 -Wall -Werror -fvisibility=hidden
LOCAL_LDFLAGS := -Wl,--no-undefined -Wl,-z,max-page-size=16384
LOCAL_LDLIBS := -lEGL -lGLESv2
include $(BUILD_SHARED_LIBRARY)

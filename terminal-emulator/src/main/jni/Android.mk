LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
# MODIFIED (moshVR): align PT_LOAD segments for 16 KB Android page sizes.
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_MODULE:= libtermux
LOCAL_SRC_FILES:= termux.c
include $(BUILD_SHARED_LIBRARY)

cmake_minimum_required(VERSION 3.10.2)

project("edgeviewer")

add_library(
        native-lib
        SHARED
        native-lib.cpp)

# ✅ SET OPENCV PATH HERE (IMPORTANT!)
set(OpenCV_DIR "/opencv-4.12.0-android-sdk/OpenCV-android-sdk/sdk/native/jni")

find_package(OpenCV REQUIRED)

include_directories(${OpenCV_INCLUDE_DIRS})

find_library(
        log-lib
        log)

target_link_libraries(
        native-lib
        ${log-lib}
        ${OpenCV_LIBS})

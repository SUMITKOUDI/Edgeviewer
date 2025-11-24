#include <jni.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>

#define LOG_TAG "NativeEdge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static int gWidth = 640;
static int gHeight = 480;

extern "C"
JNIEXPORT void JNICALL
Java_com_example_edgeviewer_NativeBridge_init(JNIEnv *env, jclass clazz, jint width, jint height) {
    gWidth = width;
    gHeight = height;
    LOGD("Init native with %dx%d", gWidth, gHeight);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_example_edgeviewer_NativeBridge_processFrame(JNIEnv *env, jclass clazz,
                                                      jbyteArray grayInput,
                                                      jbyteArray rgbaOutput) {

    jbyte* grayPtr = env->GetByteArrayElements(grayInput, nullptr);
    jbyte* rgbaPtr = env->GetByteArrayElements(rgbaOutput, nullptr);

    cv::Mat gray(gHeight, gWidth, CV_8UC1, (unsigned char*)grayPtr);
    cv::Mat edges;

    auto start = std::chrono::high_resolution_clock::now();

    LOGD("Using OpenCV Canny");
    cv::Canny(gray, edges, 50, 150);

    cv::Mat rgba(gHeight, gWidth, CV_8UC4, (unsigned char*)rgbaPtr);
    cv::cvtColor(edges, rgba, cv::COLOR_GRAY2RGBA);

    auto end = std::chrono::high_resolution_clock::now();
    float ms = std::chrono::duration<float, std::milli>(end - start).count();

    env->ReleaseByteArrayElements(grayInput, grayPtr, JNI_ABORT);
    env->ReleaseByteArrayElements(rgbaOutput, rgbaPtr, 0);

    return ms;
}

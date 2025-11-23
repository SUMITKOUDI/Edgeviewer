#include <jni.h>

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_example_edgeviewer_NativeBridge_processFrame(
        JNIEnv *env,
        jclass clazz,
        jbyteArray grayInput,
        jbyteArray rgbaOutput) {

    // No processing yet, just return 0 ms
    return 0.0f;
}

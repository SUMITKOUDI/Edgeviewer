package com.example.edgeviewer;

public class NativeBridge {

    static {
        System.loadLibrary("native-lib");
    }

    public static native void init(int width, int height);

    public static native float processFrame(byte[] grayInput, byte[] rgbaOutput);
}

package com.example.edgeviewer;

public class NativeBridge {

    static {
        System.loadLibrary("native-lib");
    }

    public static native float processFrame(byte[] gray, byte[] rgba);
}

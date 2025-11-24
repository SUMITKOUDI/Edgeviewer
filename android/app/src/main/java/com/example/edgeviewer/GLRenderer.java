package com.example.edgeviewer;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.SurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer implements GLSurfaceView.Renderer {

    private final SurfaceView surfaceView;
    private int[] textureHandle = new int[1];
    private byte[] frameData;
    private boolean hasFrame = false;

    private static final float[] QUAD = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
    };

    private FloatBuffer vertexBuffer;

    public GLRenderer(SurfaceView sv) {
        this.surfaceView = sv;
        vertexBuffer = ByteBuffer.allocateDirect(QUAD.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(QUAD).position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glGenTextures(1, textureHandle, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!hasFrame) return;

        ByteBuffer buf = ByteBuffer.wrap(frameData);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0,
                GLES20.GL_RGBA, 640, 480, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    public void updateFrame(byte[] rgba) {
        this.frameData = rgba;
        this.hasFrame = true;
    }
}

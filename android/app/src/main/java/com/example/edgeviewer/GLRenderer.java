package com.example.edgeviewer;

import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class GLRenderer implements SurfaceHolder.Callback, Runnable {

    private final SurfaceHolder surfaceHolder;
    private int surfaceWidth = 0;
    private int surfaceHeight = 0;
    private FloatBuffer vertexBufferScaled;

    private int textureId = -1;
    private Bitmap frameBitmap;
    private final Object frameLock = new Object();

    private int width = 640;
    private int height = 480;
    private boolean running = false;

    public volatile float lastProcessingMs = 0f;
    public volatile boolean showEdges = true;

    public GLRenderer(SurfaceView surfaceView) {
        this.surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        frameBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
    }

    public void updateFrame(byte[] rgbaBytes, float processingMs) {
        synchronized (frameLock) {
            frameBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rgbaBytes));
            lastProcessingMs = processingMs;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        new Thread(this).start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // No-op
        surfaceWidth = width;
        surfaceHeight = height;
        updateVerticesForAspectRatio();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
    }

    @Override
    public void run() {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        egl.eglInitialize(display, version);

        int[] configAttribs = {
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfig = new int[1];
        egl.eglChooseConfig(display, configAttribs, configs, 1, numConfig);
        EGLConfig config = configs[0];

        int[] attribList = {
                0x3098, 2,
                EGL10.EGL_NONE
        };
        EGLContext context = egl.eglCreateContext(display, config,
                EGL10.EGL_NO_CONTEXT, attribList);

        EGLSurface surface = egl.eglCreateWindowSurface(display, config, surfaceHolder, null);
        egl.eglMakeCurrent(display, surface, surface, context);

        initGL();

        while (running) {
            drawFrame();
            egl.eglSwapBuffers(display, surface);
        }

        egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        egl.eglDestroySurface(display, surface);
        egl.eglDestroyContext(display, context);
        egl.eglTerminate(display);
    }

    private int program;
    private int positionHandle;
    private int texCoordHandle;

    private FloatBuffer vertexBuffer;
    private FloatBuffer texBuffer;

    private final float[] vertices = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
    };

    private final float[] texCoords = {
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
    };

    private void initGL() {
        String vertexShaderCode =
                "attribute vec4 aPosition;" +
                "attribute vec2 aTexCoord;" +
                "varying vec2 vTexCoord;" +
                "void main() {" +
                "  gl_Position = aPosition;" +
                "  vTexCoord = aTexCoord;" +
                "}";

        String fragmentShaderCode =
                "precision mediump float;" +
                "varying vec2 vTexCoord;" +
                "uniform sampler2D uTexture;" +
                "void main() {" +
                "  gl_FragColor = texture2D(uTexture, vTexCoord);" +
                "}";

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord");

        vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        texBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        texBuffer.put(texCoords);
        texBuffer.position(0);

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    }

    private int loadShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        return shader;
    }
    private void updateVerticesForAspectRatio() {
        float screenRatio = (float) surfaceWidth / surfaceHeight;
        float imageRatio = (float) width / height;

        float xScale = 1f;
        float yScale = 1f;

        if (imageRatio > screenRatio) {
            yScale = screenRatio / imageRatio;
        } else {
            xScale = imageRatio / screenRatio;
        }

        float[] scaledVertices = {
                -xScale, -yScale,
                xScale, -yScale,
                -xScale,  yScale,
                xScale,  yScale
        };

        vertexBufferScaled = ByteBuffer.allocateDirect(scaledVertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBufferScaled.put(scaledVertices).position(0);
    }


    private void drawFrame() {
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);

        synchronized (frameLock) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frameBitmap, 0);
        }

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBufferScaled);


        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }
}

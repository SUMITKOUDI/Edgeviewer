package com.example.edgeviewer;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MainActivity extends AppCompatActivity {

    private SurfaceView glSurface;
    private GLRenderer glRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        glSurface = findViewById(R.id.glSurface);

        glRenderer = new GLRenderer(glSurface);

        glSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {}

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {}
        });

        // TEST: send a black frame
        byte[] testFrame = new byte[640 * 480 * 4];
        glRenderer.updateFrame(testFrame);
    }
}

package com.example.edgeviewer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "EdgeViewer";
    private static final int CAMERA_REQUEST_CODE = 1001;

    // Target processing size
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    private SurfaceView glSurfaceView;
    private GLRenderer glRenderer;

    private Button btnToggle;
    private TextView tvStats;

    private byte[] grayBuffer = new byte[WIDTH * HEIGHT];
    private byte[] rgbaBuffer = new byte[WIDTH * HEIGHT * 4];

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private CameraManager cameraManager;
    private String backCameraId;

    private volatile boolean running = true;
    private long lastFpsTime = 0;
    private int frameCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        glSurfaceView = findViewById(R.id.glSurface);
        btnToggle = findViewById(R.id.btnToggle);
        tvStats = findViewById(R.id.tvStats);

        glRenderer = new GLRenderer(glSurfaceView);

        NativeBridge.init(WIDTH, HEIGHT);

        btnToggle.setOnClickListener(v -> {
            glRenderer.showEdges = !glRenderer.showEdges;
            btnToggle.setText(glRenderer.showEdges ? "Show Raw" : "Show Edges");
        });

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        findBackCamera();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
            } else {
                startCamera();
            }
        } else {
            startCamera();
        }
    }

    private void findBackCamera() {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = id;
                    return;
                }
            }
            // fallback: first camera
            if (cameraManager.getCameraIdList().length > 0) {
                backCameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "findBackCamera: ", e);
        }
    }

    private void startCamera() {
        if (backCameraId == null) {
            Log.e(TAG, "No camera found");
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            cameraManager.openCamera(backCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    setupImageReader();
                    createCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    Log.e(TAG, "Camera error: " + error);
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "startCamera: ", e);
        }
    }

    private void setupImageReader() {
        Size size = new Size(WIDTH, HEIGHT); // request fixed processing size
        imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;
                processImage(image);
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, null);
    }

    private void createCaptureSession() {
        try {
            Surface surface = imageReader.getSurface();
            final CaptureRequest.Builder builder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);

            cameraDevice.createCaptureSession(
                    Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                builder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                captureSession.setRepeatingRequest(builder.build(), null, null);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "setRepeatingRequest: ", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Camera capture session configuration failed");
                        }
                    },
                    null
            );
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCaptureSession: ", e);
        }
    }

    private void processImage(Image image) {
        // YUV_420_888: use Y plane as grayscale
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        int yRowStride = planes[0].getRowStride();

        // Copy Y plane to our WIDTH*HEIGHT grayBuffer
        if (grayBuffer.length != WIDTH * HEIGHT) {
            grayBuffer = new byte[WIDTH * HEIGHT];
        }

        int pos = 0;
        byte[] rowData = new byte[yRowStride];
        for (int row = 0; row < HEIGHT; row++) {
            int bytesToRead = Math.min(yRowStride, yBuffer.remaining());
            yBuffer.get(rowData, 0, bytesToRead);
            System.arraycopy(rowData, 0, grayBuffer, pos, WIDTH);
            pos += WIDTH;
        }

        long start = System.currentTimeMillis();
        float ms = NativeBridge.processFrame(grayBuffer, rgbaBuffer);
        long end = System.currentTimeMillis();

        glRenderer.updateFrame(rgbaBuffer, ms);

        frameCount++;
        long now = System.currentTimeMillis();
        if (lastFpsTime == 0) lastFpsTime = now;
        if (now - lastFpsTime >= 1000) {
            final int fps = frameCount;
            frameCount = 0;
            lastFpsTime = now;
            runOnUiThread(() ->
                    tvStats.setText("FPS: " + fps +
                            " | " + WIDTH + "x" + HEIGHT +
                            " | native: " + ms + " ms"
                    )
            );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // permission will be requested in onCreate only; here we just skip if missing
        } else {
            startCamera();
        }
    }

    private void stopCamera() {
        running = false;
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                captureSession.close();
            } catch (CameraAccessException | IllegalStateException e) {
                Log.e(TAG, "stopCamera: ", e);
            }
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            }
        }
    }
}

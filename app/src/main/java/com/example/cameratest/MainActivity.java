package com.example.cameratest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;

// source : https://inducesmile.com/android/android-camera2-api-example-tutorial/
public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Button takePictureBtn;
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    // 사용가능한 Camera
    protected CameraDevice cameraDevice;
    // createCaptureSession 함수로 생성됨.surface와 연결되어있다
    protected CameraCaptureSession cameraCaptureSessions;
    // 이미지 캡쳐에 필요한 Param을 가지고 있다
    protected CaptureRequest captureRequest;
    // surface와 연결되어 있다
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = (TextureView) findViewById(R.id.texture_view);
        assert textureView != null;
        // texture view와 surface view 사용이 가능할 때 알람을 받는다
        textureView.setSurfaceTextureListener(textureListener);
        takePictureBtn = (Button) findViewById(R.id.takepicture_btn);
        assert takePictureBtn != null;
        takePictureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        // Texture의 SurfaceTexture가 사용준비가 되었을 때 호출
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface
                                            , int width     /* surface width */
                                            , int height    /* surface height */) {
            // open your camera here
            // TODO
            // openCamera();
        }

        // SurfaceTexture의 버퍼 사이즈가 변경되었을 때 호출
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface
                                                , int width
                                                , int height) {
            // Transform you image captured size accroding to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        // SurfaceTexture가 updateTexImage() 함수를 통해 업데이트 될 때 호출
        // updateTextImage는 image stream으로 부터 제일 가까운 frame을 업데이트 한다
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            // This is called when the camera is open
            Log.d(TAG, "onOpened");
            cameraDevice = camera;
            // TODO
            //createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        // 이미지 캡쳐가 완전히 완료되었을 때 호출
        @Override
        public void onCaptureCompleted(CameraCaptureSession session
                                        , CaptureRequest request
                                        , TotalCaptureResult result/* 카메라 시스템 상태, 캡쳐 정보, not null*/) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(MainActivity.this, "Saved:"+file, Toast.LENGTH_SHORT).show();
            // TODO
            // createCameraPreview();
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }

        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSize = null;
            if (characteristics != null) {
                jpegSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSize != null && 0 < jpegSize.length ) {
                width = jpegSize[0].getWidth();
                height = jpegSize[0].getHeight();
            }


        } catch (CameraAccessException e) {

        }
    }
}

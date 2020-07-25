package com.example.cameratest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    // TODO : 이거 사용되나??
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    // TODO : 이거 사용되나??
    private boolean mFlashSupported;
    // TODO : Handler 왜 사용하는건지???
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
            openCamera();
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
            Log.d(TAG, "[CameraDevice.StateCallback] onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.d(TAG, "[CameraDevice.StateCallback] onDisconnected");
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.d(TAG, "[CameraDevice.StateCallback] onError");
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
            Log.d(TAG, "[CameraCaptureSession.CaptureCallback]");

            Toast.makeText(MainActivity.this, "Saved:"+file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    protected void startBackgroundThread() {
        Log.d(TAG, "[startBackgroundThread]");

        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        Log.d(TAG, "[stopBackgroundThread]");

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
        Log.d(TAG, "[takePicture]");

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

            ImageReader imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> surfaces = new ArrayList<>(2);
            // TODO : 두개의 SURFACE를 사용하는 이유는??
            surfaces.add(imageReader.getSurface());
            surfaces.add(new Surface(textureView.getSurfaceTexture()));
            // 타겟을 사용하기 위해 초기화, 새로운 CAPTURE REQUEST를 위해 만든다
            // 다른 카메라 끼리 재사용은 추천X
            // TEMPLATE_STILL_CAPTURE : 이미지 캡쳐를 위한 적합한 요청 생성
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            // CONTROL_MODE_AUTO는 3A 설정을 사용
            // 3A란 Auto Exposure, Auto FucusAuto, White Balance
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            String saveDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()+ "/Camera/pic.jpg";
            final File file = new File(saveDir);
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                // ImageReader로부터 새로운 이미지를 사용할 수 있을 때 불러진다
                public void onImageAvailable(ImageReader reader) {
                    Log.d(TAG, "[takePicture]onImageAvailable");

                    Image image = null;
                    OutputStream output = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }

                        if (null != output) {
                            try {
                                output.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            };
            // ImageReder로부터 새로운 이미지 사용이 가능하게 되면 적용
            // handler : 리스너를 호출할 핸들러
            imageReader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session
                                                , CaptureRequest request
                                                , TotalCaptureResult result) {
                    Log.d(TAG, "[captureListener]onCaptureCompleted");

                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved:"+file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback(){
                // 가케라 기기 설정을 스스로 마치고 capture requests 프로세스를 시작할 수 있을 때 호출
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.d(TAG, "[cameraDevice.createCaptureSession]onConfigured");

                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        Log.d(TAG, "[createCameraPreview]");
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize((int)(imageDimension.getWidth()*0.5),(int)(imageDimension.getHeight()*0.5));
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    // The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying
                    cameraCaptureSessions = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }

            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        Log.d(TAG, "[openCamera]");
        // 모든 카메라 정보를 가진 manager
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "os camera open");

        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            // 사용할 수 있는 camera debice stream 속성
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                                != PackageManager.PERMISSION_GRANTED
                                                && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA
                                                , Manifest.permission.WRITE_EXTERNAL_STORAGE}
                                                , REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        Log.d(TAG, "[updatePreview]");

        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            // caputreRequest의 처리 반복(계속 preview는 업데이트 되어야 하므로)
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
}

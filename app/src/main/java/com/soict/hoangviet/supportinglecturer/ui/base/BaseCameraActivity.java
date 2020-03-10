package com.soict.hoangviet.supportinglecturer.ui.base;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.soict.hoangviet.supportinglecturer.R;
import com.soict.hoangviet.supportinglecturer.customview.AutoFitTextureView;
import com.soict.hoangviet.supportinglecturer.customview.SonnyJackDragView;
import com.soict.hoangviet.supportinglecturer.utils.DeviceUtil;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public abstract class BaseCameraActivity extends BaseActivity {
    @BindView(R.id.textureView)
    protected AutoFitTextureView textureView;
    @BindView(R.id.drawView)
    protected RelativeLayout drawView;
    @Nullable
    @BindView(R.id.rl_camera)
    protected RelativeLayout rlCamera;
    private SonnyJackDragView mSonnyJackDragView;
    private CameraDevice.StateCallback stateCallback;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSessions;
    private Size imageDimension;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private static final int CAMERA_BACK = 0;
    private static final int CAMERA_FONT = 1;
    TextureView.SurfaceTextureListener textureListener;
    private String cameraId;
    CameraCharacteristics characteristics;
    static final int REQUEST_CAMERA_PERMISSION = 1009;
    private int lastAction;
    private float dX;
    private float dY;
    private int screenHight;
    private int screenWidth;
    int effect = 0;

    @Override
    protected void initView() {
        initStateCallback();
        initTextureListener();
    }

    @Override
    protected void initListener() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            textureView.setOnClickListener(view -> {
                if (effect == 12) {
                    effect = 0;
                }
                captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, effect++);
                try {
                    cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            });
        } else {
            initOnTouch();
        }
    }

    @Override
    protected void initData() {

    }

    private void initOnTouch() {
        textureView.setOnClickListener(view -> {

        });
        mSonnyJackDragView = new SonnyJackDragView.Builder()
                .setActivity(this)
                .setNeedNearEdge(true)
                .setView(textureView)
                .build();
    }

    private void initStateCallback() {
        stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                createCameraPreview();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                cameraDevice.close();
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int i) {
                cameraDevice.close();
                cameraDevice = null;
            }
        };
    }

    private void initTextureListener() {
        textureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                DeviceUtil.transformImage(BaseCameraActivity.this, textureView, width, height);
                openCamera(CAMERA_FONT);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        };

    }

    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_EFFECT_MODE_AQUA);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (cameraDevice == null)
                        return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            DeviceUtil.transformImage(this, textureView, textureView.getWidth(), textureView.getHeight());
            openCamera(CAMERA_FONT);
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void openCamera(int rotate) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[rotate];
            characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //Check realtime permission if run higher API 23
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Vui lòng cấp quyền truy cập camera", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

//    protected void showTextureViewSmall() {
//        RelativeLayout.LayoutParams layoutParams = createLayoutParams(
//                DeviceUtil.convertDpToPx(this, 180),
//                DeviceUtil.convertDpToPx(this, 230)
//        );
//        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
//        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
//        removeViewParent(textureView);
//        drawView.addView(textureView, layoutParams);
//        mSonnyJackDragView = new SonnyJackDragView.Builder()
//                .setActivity(this)
//                .setNeedNearEdge(true)
//                .setView(textureView)
//                .build();
//    }

//    @SuppressLint("CheckResult")
//    protected void showTextureViewBig() {
//        RelativeLayout.LayoutParams layoutParams = createLayoutParams(
//                WindowManager.LayoutParams.MATCH_PARENT,
//                DeviceUtil.convertDpToPx(this, 450)
//        );
//        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
//        textureView.setOnTouchListener(null);
//        removeViewParent(textureView);
//        Completable.timer(400, TimeUnit.MILLISECONDS)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .doOnSubscribe(disposable -> {
//                    camLoading.setVisibility(View.VISIBLE);
//                })
//                .doFinally(()->{
//                    camLoading.setVisibility(View.GONE);
//                })
//                .subscribe(() -> {
//                    rlCamera.addView(textureView, layoutParams);
//                });
//    }

    private RelativeLayout.LayoutParams createLayoutParams(int width, int height) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(width, height);
        return layoutParams;
    }

    private void removeViewParent(AutoFitTextureView view) {
        if (view.getParent() != null) {
            ((RelativeLayout) view.getParent()).endViewTransition(view);
            ((RelativeLayout) view.getParent()).removeView(view);
        }
    }
}

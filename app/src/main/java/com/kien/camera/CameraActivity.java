package com.kien.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.kien.camera.custom.ImageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CAMAPI2";

    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int mCaptureState = STATE_PREVIEW;

    private TextureView mTextureView;
    ImageView mResultPicture, mSwitch, ivPic1;
    //Image Classifier
    private Classifier detector;
    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detectv2.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

    //Text
    int preWidth = 0, preHeight = 0;
    private boolean checkBack = false;

    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize;
    private Size mImageSize;
    private ImageReader mImageReader;

    private int mTotalRotation;
    private CameraCaptureSession mPreviewCaptureSession;

    boolean hello = true;
    //Text
    private static final float TEXT_SIZE_DIP = 5;
    private float textSizePx;
    private Paint fgPaint;
    int sizeWidth;
    int sizeHeight;
    private final int REQUEST_CODE_FOLDER = 123;
    private final int REQUEST_CODE_CAMERA = 456;
    private boolean mIsCounting;

    //Thread
    MyAsyncTask myAsyncTask;
    MyLooperThread wT;
    private Handler uiHandler;
    private Handler mUiHandler = new Handler();
    private MyHandler2 mWorkerThread;

    private LinearLayout canvasLayout = null;
    CustomSurfaceView customSurfaceView = null;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        initDataTensor();

        mTextureView = findViewById(R.id.textureView);
        ivPic1 = findViewById(R.id.ivPic1);
        mResultPicture = findViewById(R.id.ivPic);
        mSwitch = findViewById(R.id.ivSwitch);
        mTakePicture = findViewById(R.id.cameraImageButton2);
        canvasLayout = findViewById(R.id.customViewLayout);

        ivPic1.setOnClickListener(this);
        mSwitch.setOnClickListener(this);
        mTakePicture.setOnClickListener(this);
        mResultPicture.setOnClickListener(this);

        customSurfaceView = new CustomSurfaceView(getApplicationContext());
        canvasLayout.addView(customSurfaceView);

        getSize();
    }

    private void initDataTensor() {
        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
        } catch (final IOException e) {
            e.printStackTrace();
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
    }

    public void clickTestMsg() {
        Handler workerHandler = wT.getHandlerToMsgQueue();
        // obtain a msg object from global msg pool
        Message m = workerHandler.obtainMessage();
        Bundle b = m.getData();
        b.putParcelable("key", getBitmap());
        Log.i(TAG, "sending msg to worker thread from UI");
        // and pass the msg
        workerHandler.sendMessage(m);
    }

    private Bitmap getBitmap() {
        return mTextureView.getBitmap();
    }

    private static class CustomAsyncTask extends AsyncTask<Object, Object, String> {
        private WeakReference<CameraActivity> activity;

        CustomAsyncTask(CameraActivity activity) {
            // Here we create a WeakReference to the outer activity
            this.activity = new WeakReference<>(activity);
        }

        @SuppressLint("WrongThread")
        @Override
        protected String doInBackground(Object... objects) {
            //do some background operation
            CameraActivity cameraActivity = activity.get();
            TextureView mTextureView = cameraActivity.findViewById(R.id.textureView);
            while (true) {
                Bitmap bitmap = mTextureView.getBitmap();
                Bitmap reBitmap = BitmapUtils.scaleImage(bitmap, 300);
                publishProgress(reBitmap);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            super.onProgressUpdate(values);
            //We get the activity from the WeakReference
            CameraActivity cameraActivity = activity.get();
            //check if it is still alive
            if (cameraActivity != null) {
                //it is alive, update the UI
                cameraActivity.updateBitmap((Bitmap) values[0]);
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
        }
    }

    private void updateBitmap(Bitmap s) {
        final List<Classifier.Recognition> results = detector.recognizeImage(s);
        Log.d("resizedBitmap", results.toString());
        //Draw picture
        final Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);

        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            customSurfaceView.remove();
            if (location != null && result.getConfidence() >= 0.035f) {
                final RectF location1 = new RectF(location.left * (preWidth / TF_OD_API_INPUT_SIZE), location.top * (preWidth / TF_OD_API_INPUT_SIZE),
                        location.right * (preWidth / TF_OD_API_INPUT_SIZE), location.bottom * (preWidth / TF_OD_API_INPUT_SIZE));
                customSurfaceView.setLocation(location1);
                customSurfaceView.setPaint(paint);
                customSurfaceView.drawLocation();
//                Bitmap cutBitmap = Bitmap.createBitmap(s,
//                        (int) (result.getLocation().left), (int) (result.getLocation().top)
//                        , (int) (result.getLocation().right - result.getLocation().left), (int) (result.getLocation().bottom - result.getLocation().top));
//                BitmapUtils.saveTempBitmap(CameraActivity.this, cutBitmap);
                result.setLocation(location1);
            }
        }
    }

    private void startThread() {
        //C4
        new CustomAsyncTask(this).execute();
        //C1: AysncTask
//                MyAsyncTask myAsyncTask = new MyAsyncTask(CameraActivity.this);
//                myAsyncTask.setHello(true);
//                myAsyncTask.execute();
        //C2
//        uiHandler = new Handler() {
//            // this will handle the notification gets from worker thead
//            @Override
//            public void handleMessage(Message msg) {
//                Bundle b = msg.getData();
//                Bitmap bitmap = b.getParcelable("key");
//                Bitmap reBitmap = BitmapUtils.scaleImage(bitmap,300);
//                mResultPicture.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        mResultPicture.setImageBitmap(reBitmap);
//                        final List<Classifier.Recognition> list = detector.recognizeImage(reBitmap);
//                        Log.d("LISST", list.toString());
//                    }
//                });
//                Log.d("AAA", bitmap.getHeight()+":"+(bitmap == null));
//            }
//        };
//
//        // create a seperate thread
//        wT = new MyLooperThread(CameraActivity.this, uiHandler);
//        // starts the thead
//        wT.start();

//        HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
//        handlerThread.start();
//        Handler handler = new Handler(handlerThread.getLooper());
//        handler.post(() -> {
//            mResultPicture.setImageBitmap(mTextureView.getBitmap());
//        });

        //C3
//        mWorkerThread = new MyHandler2("myWorkerThread");
//        Runnable task = () -> {
//            while (true) {
//                Bitmap croppyBitmap = mTextureView.getBitmap();
//                Bitmap croppedBitmap = BitmapUtils.scaleImage(croppyBitmap, TF_OD_API_INPUT_SIZE);
//                mResultPicture.post(() -> {
//                    final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
//                    Log.d("resizedBitmap", results.toString());
//                    final Canvas canvas = new Canvas(croppedBitmap);
//                    mResultPicture.setImageBitmap(croppedBitmap);
//                    //Draw picture
//                    final Paint paint = new Paint();
//                    paint.setColor(Color.RED);
//                    paint.setStyle(Paint.Style.STROKE);
//                    paint.setStrokeWidth(2f);
//                    mResultPicture.getLayoutParams().height = TF_OD_API_INPUT_SIZE;
//                    mResultPicture.getLayoutParams().width = TF_OD_API_INPUT_SIZE;
//                    //Draw text
//                    textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
//                    fgPaint = new Paint();
//                    fgPaint.setTextSize(textSizePx);
//                    fgPaint.setColor(Color.YELLOW);
//                    for (final Classifier.Recognition result : results) {
//                        final RectF location = result.getLocation();
//                        if (location != null && result.getConfidence() >= 0.3f) {
//                            canvas.drawText(result.getConfidence() + "", (location.left), (location.top + 10), fgPaint);
//                            canvas.drawRect(location, paint);
//                            Bitmap cutBitmap = Bitmap.createBitmap(croppedBitmap,
//                                    (int) (result.getLocation().left), (int) (result.getLocation().top)
//                                    , (int) (result.getLocation().right - result.getLocation().left), (int) (result.getLocation().bottom - result.getLocation().top));
//                            BitmapUtils.saveTempBitmap(CameraActivity.this, cutBitmap);
//                            result.setLocation(location);
//                        }
//                    }
//                });
//
//                try {
//                    Thread.sleep(2000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        };
//        mWorkerThread.start();
//        mWorkerThread.prepareHandler();
//        mWorkerThread.postTask(task);


    }

    private void getSize() {
        //Get width and height screen
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        sizeWidth = size.x;
        sizeHeight = size.y;
    }

    private void showImage() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_image);
        final ImageView ivDialog = dialog.findViewById(R.id.ivDialog);
        BitmapDrawable bitmapDrawable = (BitmapDrawable) mResultPicture.getDrawable();
        Bitmap bitmap = bitmapDrawable.getBitmap();
        ivDialog.setImageBitmap(bitmap);
        dialog.show();
    }

    private void scaleImage(Bitmap bitmap) {
        final int maxSize = TF_OD_API_INPUT_SIZE;
        int outWidth;
        int outHeight;
        int inWidth = bitmap.getWidth();
        int inHeight = bitmap.getHeight();
        if (inWidth > inHeight) {
            outWidth = maxSize;
            outHeight = (inHeight * maxSize) / inWidth;
        } else {
            outHeight = maxSize;
            outWidth = (inWidth * maxSize) / inHeight;
        }
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, outWidth, outHeight, false);
        Bitmap workingBitmap = Bitmap.createBitmap(resizedBitmap);
        Bitmap mutableBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true);

        final List<Classifier.Recognition> results = detector.recognizeImage(mutableBitmap);
        Log.d("ressss",results.toString());
        final Canvas canvas = new Canvas(mutableBitmap);
        mResultPicture.setImageBitmap(mutableBitmap);
        //Draw picture
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        mResultPicture.getLayoutParams().height = TF_OD_API_INPUT_SIZE;
        mResultPicture.getLayoutParams().width = TF_OD_API_INPUT_SIZE;
        //Draw text
        textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        fgPaint = new Paint();
        fgPaint.setTextSize(textSizePx);
        fgPaint.setColor(Color.YELLOW);
        for (final Classifier.Recognition result : results) {

            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= 0.3f) {
                canvas.drawText(result.getConfidence() + "", (location.left), (location.top + 10), fgPaint);
                canvas.drawRect(location, paint);
                Bitmap cutBitmap = Bitmap.createBitmap(resizedBitmap,
                        (int) (result.getLocation().left), (int) (result.getLocation().top)
                        , (int) (result.getLocation().right - result.getLocation().left), (int) (result.getLocation().bottom - result.getLocation().top));
                BitmapUtils.saveTempBitmap(CameraActivity.this, cutBitmap);
//                result.setLocation(location);
            }
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            preWidth = width;
            preHeight = height;
            mCameraId = "1";
            connectCamera();
            mIsCounting = true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            System.gc();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
            if (mIsCounting) {
                startThread();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = mImageReader.acquireLatestImage();
                    image.close();
                }
            };

    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult captureResult) {
                    switch (mCaptureState) {
                        case STATE_PREVIEW:
                            break;
                        case STATE_WAIT_LOCK:
                            mCaptureState = STATE_PREVIEW;
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                startStillCaptureRequest();
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    process(result);
                }
            };
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private ImageButton mTakePicture;

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ivPic1:
//                clickTestMsg();
//                hello = false;
                //Folder
//                Intent intent = new Intent(Intent.ACTION_PICK);
//                intent.setType("image/*");
//                startActivityForResult(intent, REQUEST_CODE_FOLDER);

                //Cam
                ActivityCompat.requestPermissions(
                        CameraActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CODE_CAMERA
                );

                break;
            case R.id.ivSwitch:
                closeCamera();
                hello = false;
                setupCamera(preWidth, preHeight);
                if (checkBack) {
                    mCameraId = "0";
                    checkBack = false;
                } else {
                    mCameraId = "1";
                    checkBack = true;
                }
                connectCamera();
                hello = true;
                break;
            case R.id.cameraImageButton2:
                lockFocus();
                Bitmap abc = mTextureView.getBitmap();
                mResultPicture.setImageBitmap(abc);
                scaleImage(abc);
                break;
            case R.id.ivPic:
                showImage();
                break;
            default:
                break;
        }
    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) (lhs.getWidth() * lhs.getHeight()) -
                    (long) (rhs.getWidth() * rhs.getHeight()));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not run without camera services", Toast.LENGTH_SHORT).show();
            }
            if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Application will not have audio on record", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "Permission successfully granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "App needs to save video to run", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_CODE_FOLDER) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_CODE_FOLDER);
            } else {
                Toast.makeText(this, "Ko dc phep", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            } else {
                Toast.makeText(this, "Ko dc phep", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        System.gc();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocas) {
        super.onWindowFocusChanged(hasFocas);
        View decorView = getWindow().getDecorView();
        if (hasFocas) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                mTotalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = mTotalRotation == 90 || mTotalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG), rotatedWidth, rotatedHeight);
                mImageReader = ImageReader.newInstance(mImageSize.getWidth(), mImageSize.getHeight(), ImageFormat.JPEG, 1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(this,
                                "Require Camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[]{android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                    }, REQUEST_CAMERA_PERMISSION_RESULT);
                }

            } else {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigured: startPreview");
                            mPreviewCaptureSession = session;
                            try {
                                mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                        null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed: startPreview");

                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startStillCaptureRequest() {
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mTotalRotation);

            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                        }

                    };
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Camera2VideoImage");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrienatation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrienatation + deviceOrientation + 360) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }

    private void lockFocus() {
        mCaptureState = STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            hello = true;
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                mResultPicture.setImageBitmap(bitmap);
                scaleImage(bitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        if (requestCode == REQUEST_CODE_CAMERA && resultCode == RESULT_OK && data != null) {
            Bitmap bitmap = (Bitmap) data.getExtras().get("data");
            scaleImage(bitmap);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}

package com.kien.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.TextureView;

public class MyLooperThread extends Thread {
    private final static String TAG = MyLooperThread.class.getSimpleName();
    // to send msgs to the msg queue
    private Handler workerHandler;
    // to update the UI thread
    private Handler uiHandler;
    private Activity activity;

    public MyLooperThread(Activity activity, Handler h) {
        this.activity = activity;
        uiHandler = h;
    }

    // what needs to be executed when thread starts
    @Override
    public void run() {
        Looper.prepare();
        workerHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Bundle u = msg.getData();
                Message m = uiHandler.obtainMessage();
                Bundle uB = m.getData();
                uB.putParcelable("key", getBitmap());
                m.setData(uB);
                uiHandler.sendMessage(m);
            }
        };
        Looper.loop();
    }

    public Handler getHandlerToMsgQueue() {
        return workerHandler;
    }

    private Bitmap getBitmap() {
        TextureView mTextureView = activity.findViewById(R.id.textureView);
        return mTextureView.getBitmap();
    }
}
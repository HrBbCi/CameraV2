package com.kien.camera;

import android.app.Activity;
import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.TextureView;
import android.widget.ImageView;

public class MyHandler2 extends HandlerThread {

    private Handler mWorkerHandler;

    public MyHandler2(String name) {
        super(name);
    }

    public void postTask(Runnable task){
        mWorkerHandler.post(task);
    }

    public void prepareHandler(){
        mWorkerHandler = new Handler(getLooper());
    }
}

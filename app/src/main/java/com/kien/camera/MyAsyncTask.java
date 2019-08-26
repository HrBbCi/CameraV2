package com.kien.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.List;

public class MyAsyncTask extends AsyncTask<Void, Object, Void> {

    Activity contextParent;
    boolean hello;
    TextureView mTextureView;
    ImageView mResultImage;

    public MyAsyncTask(Activity contextParent) {
        this.contextParent = contextParent;
        this.hello = hello;
    }

    public boolean isHello() {
        return hello;
    }

    public void setHello(boolean hello) {
        this.hello = hello;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Toast.makeText(contextParent, "Start", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("WrongThread")
    @Override
    protected Void doInBackground(Void... params) {
        mResultImage = contextParent.findViewById(R.id.ivPic);
        mTextureView = contextParent.findViewById(R.id.textureView);
        int i = 0;
        Bitmap mText;
        while (hello) {
            hello = false;
            mText = mTextureView.getBitmap();
            publishProgress(mText, i);
            i++;
            hello = true;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        super.onProgressUpdate(values);
        Integer i = (Integer) values[1];
        Toast.makeText(contextParent, "i" + i, Toast.LENGTH_SHORT).show();
        Bitmap bitmap = (Bitmap) values[0];
        Bitmap reBitmap = BitmapUtils.scaleImage(bitmap,300);
        mResultImage.setImageBitmap(reBitmap);
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        Toast.makeText(contextParent, "Okie, Finished", Toast.LENGTH_SHORT).show();
    }
}
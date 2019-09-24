package com.niuniu.player;

import androidx.appcompat.app.AppCompatActivity;

import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private TextureView mTextureView;
    private VideoDecoderThread mVideoDecoder;
    private AudioDecoderThread mAudioDecoder;
    private String path;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.texture_view);
        mTextureView.setSurfaceTextureListener(this);
        path = "test.mp4";
    }

    private AssetFileDescriptor getFileDesciptor() {
        try {
            AssetFileDescriptor afd = getAssets().openFd(path);
            return afd;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        Surface surface = new Surface(surfaceTexture);
        mVideoDecoder = new VideoDecoderThread(surface);
        mVideoDecoder.setDataSource(getFileDesciptor());
        mVideoDecoder.prepare();
        mAudioDecoder = new AudioDecoderThread();
        mAudioDecoder.setDataSource(getFileDesciptor());
        mAudioDecoder.prepare();
        mVideoDecoder.start();
        mAudioDecoder.start();
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
}

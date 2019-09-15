package com.niuniu.player.decoder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.FileDescriptor;

public abstract class MediaDecoder implements Runnable {

    private static final int TIMEOUT_USEC = 10000;    // 10[msec]

    protected MediaExtractor mMediaExtractor;
    protected MediaCodec mMediaCodec;
    protected MediaFormat mInputFormat;

    private boolean mIsDecoding;
    private MediaCodec.BufferInfo mBufferInfo;

    private DecodeCallback mCallBack;

    public MediaDecoder() {
        mBufferInfo = new MediaCodec.BufferInfo();
    }

    protected abstract MediaFormat prepareExtractor(MediaExtractor mediaExtractor);

    protected abstract MediaCodec prepareCodec(MediaFormat format);


    public boolean setDataSource(String path) {
        try {
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(path);
            mInputFormat = prepareExtractor(mMediaExtractor);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mInputFormat != null;
    }

    public boolean setDataSource(FileDescriptor fd) {
        try {
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(fd);
            mInputFormat = prepareExtractor(mMediaExtractor);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mInputFormat != null;
    }

    // call me after setDataSource
    public MediaFormat getInputFormat() {
        return mInputFormat;
    }

    public abstract boolean isAudio();

    public void setDecodeCallback(DecodeCallback callback) {
        this.mCallBack = callback;
    }

    public boolean prepare() {
        if (mMediaCodec != null) {
            mMediaCodec.release();
        }
        mIsDecoding = false;
        if (mInputFormat != null) {
            notifyInputFormatReceived();
            mMediaCodec = prepareCodec(mInputFormat);
            return mMediaCodec != null;
        }
        return false;
    }

    public boolean reset() {
        if (mMediaCodec != null) {
            mMediaCodec.release();
        }
        if ((mMediaCodec = prepareCodec(mInputFormat)) != null) {
            return true;
        }
        return false;
    }
//
//    public boolean start() {
//
//    }
//
//    public boolean pause() {
//
//    }
//
//    public boolean resume() {
//
//    }
//
//    public boolean stop() {
//
//    }

    @Override
    public void run() {

    }

    public void notifyInputFormatReceived() {
        if (mCallBack != null) {
            mCallBack.onInputFormatReceived(this, isAudio(), mInputFormat);
        }
    }


    public interface DecodeCallback {

        void onDecodeStart(MediaDecoder decoder, boolean isAudio);

        void onDecodeStop(MediaDecoder decoder, boolean isAudio);

        void onDecodeError(MediaDecoder decoder, boolean isAudio, Exception error);

        void onInputFormatReceived(MediaDecoder decoder, boolean isAudio, MediaFormat format);

        void onOutputFormatReceived(MediaDecoder decoder, boolean isAudio, MediaFormat format);

//        void onBufferDecoded(MediaDecoder decoder, boolean isAudio, MediaBuffer buffer);

        void onDecodeReachEOS(MediaDecoder decoder, boolean isAudio);
    }

}

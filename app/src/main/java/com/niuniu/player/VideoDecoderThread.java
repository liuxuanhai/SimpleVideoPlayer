package com.niuniu.player;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoDecoderThread extends Thread {

    private static final String TAG = "video-decoder";
    private static final int TIMEOUT_USEC = 10000;    // 10[msec]

    private MediaCodec mMediaCodec;
    private MediaExtractor mExtractor;
    private MediaFormat mInputFormat;
    private Surface mSurface;

    private DecodeCallback mCallBack;
    private long mFrameStamp;

    private boolean isEOS;


    public VideoDecoderThread(Surface surface) {
        this.mSurface = surface;
    }

    public void setDecodeCallback(DecodeCallback callback) {
        mCallBack = callback;
    }

    public void setDataSource(String path) {
        try {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(path);
            mInputFormat = parserMediaFormat(mExtractor);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void setDataSource(AssetFileDescriptor fd) {
        try {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mInputFormat = parserMediaFormat(mExtractor);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean prepare() {
        if (mInputFormat != null) {
            String mimeType = mInputFormat.getString(MediaFormat.KEY_MIME);
            try {
                mMediaCodec = MediaCodec.createDecoderByType(mimeType);
                mMediaCodec.configure(mInputFormat, mSurface, null, 0);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private MediaFormat parserMediaFormat(MediaExtractor extractor) {
        int trackCount = extractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat mediaFormat = extractor.getTrackFormat(i);
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                mExtractor.selectTrack(i);
                return mediaFormat;
            }
        }
        return null;
    }

    @Override
    public void run() {
        super.run();
        MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();
        mMediaCodec.start();
        ByteBuffer[] inBuffers = mMediaCodec.getInputBuffers();
        while (!isEOS) {
            int inputIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputIndex >= 0) {
                int size = mExtractor.readSampleData(inBuffers[inputIndex], 0);
                if (size < 0) {
                    mMediaCodec.queueInputBuffer(inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    mMediaCodec.queueInputBuffer(inputIndex, 0, size, mExtractor.getSampleTime(), 0);
                    inBuffers[inputIndex].clear();
                    mExtractor.advance();
                }
            }
            int outIndex = mMediaCodec.dequeueOutputBuffer(outBufferInfo, TIMEOUT_USEC);
            if ((outBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                isEOS = true;
                Log.d(TAG, "is EOS BUFFER_FLAG_END_OF_STREAM");
                break;
            }
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    MediaFormat outFormat = mMediaCodec.getOutputFormat();
                    int width = outFormat.getInteger(MediaFormat.KEY_WIDTH);
                    int height = outFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    Log.d(TAG, "New format " + outFormat + "width=" + width + ",height=" + height);
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    mMediaCodec.releaseOutputBuffer(outIndex, true);
                    mFrameStamp = outBufferInfo.presentationTimeUs;
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
        release();


    }

    private void release() {
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mExtractor != null) {
            mExtractor.release();
            mExtractor = null;
        }

        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
    }


    public void notifyFormatChanged(MediaFormat format, int width, int height) {
        mCallBack.onOutputFormatReceived(format, width, height);
    }



    public interface DecodeCallback {

        void onOutputFormatReceived(MediaFormat format, int width, int height);
    }

}

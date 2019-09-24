package com.niuniu.player;

import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.annotation.RequiresApi;

public class AudioDecoderThread extends Thread {

    private static final String TAG = "video-decoder";
    private static final int TIMEOUT_USEC = 10000;    // 10[msec]

    private MediaCodec mMediaCodec;
    private MediaExtractor mExtractor;
    private MediaFormat mInputFormat;
    private AudioTrack mAudioTrack;

    private int mSampleRateInHz;
    private int mChannels;

    private VideoDecoderThread.DecodeCallback mCallBack;
    private long mFrameStamp;

    private boolean isEOS;

    public AudioDecoderThread() {

    }

    public void setDecodeCallback(VideoDecoderThread.DecodeCallback callback) {
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
                mMediaCodec.configure(mInputFormat, null, null, 0);
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
            if (mimeType.startsWith("audio/")) {
                mSampleRateInHz = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                mChannels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                mExtractor.selectTrack(i);
                return mediaFormat;
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        super.run();
        int buffsize = AudioTrack.getMinBufferSize(mSampleRateInHz,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRateInHz,
                AudioFormat.CHANNEL_OUT_STEREO,  AudioFormat.ENCODING_PCM_16BIT,
                buffsize,  AudioTrack.MODE_STREAM);

        mAudioTrack.play();

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
                    mSampleRateInHz = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    mAudioTrack.setPlaybackRate(mSampleRateInHz);
                    Log.d(TAG, "New format " + outFormat + "mSampleRateInHz=" + mSampleRateInHz);
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(TAG, "dequeueOutputBuffer timed out!");
                    break;
                default:
                    mFrameStamp = outBufferInfo.presentationTimeUs;
                    ByteBuffer outBuffer = mMediaCodec.getOutputBuffer(outIndex);
                    final byte[] chunk = new byte[outBufferInfo.size];
                    outBuffer.get(chunk);
                    mAudioTrack.write(chunk, outBufferInfo.offset, outBufferInfo.offset + outBufferInfo.size);
                    mMediaCodec.releaseOutputBuffer(outIndex, true);
                    break;
            }
        }
        release();


    }

    private void release() {
        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
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


}

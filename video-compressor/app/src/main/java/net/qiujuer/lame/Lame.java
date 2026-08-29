package net.qiujuer.lame;

import java.io.Closeable;
import java.security.InvalidParameterException;

/**
 * Patched Java wrapper for the legacy libmp3lame JNI binary.
 * Modern arm64 Android may return a valid native pointer whose signed jlong value is negative.
 * The original 2017 wrapper rejected every value <= 0, causing false initialization failures.
 */
public class Lame implements Closeable {

    public interface LameModel {
        int STEREO = 0;
        int JOINT_STEREO = 1;
        int MONO = 3;
        int AUTO = 5;
    }

    public interface LameQuality {
        int BEST = 0;
        int NEAR_BEST = 2;
        int GOOD = 5;
        int OK = 7;
        int WORST = 9;
    }

    private long mNativeLame;
    private final int mInChannels;

    public Lame(int inSampleRate, int inChannels, int outSampleRate,
                int outBitrate, int quality) {
        this(inSampleRate, inChannels, outSampleRate, outBitrate, LameModel.AUTO, quality);
    }

    public Lame(int inSampleRate, int inChannels, int outSampleRate,
                int outBitrate, int model, int quality) {
        if (outSampleRate > inSampleRate) {
            throw new InvalidParameterException("Output sample rate cannot exceed input sample rate.");
        }
        if (outBitrate > 320 || outBitrate < 8) {
            throw new InvalidParameterException("MP3 bitrate must be between 8 and 320 kbps.");
        }
        if (inChannels > 2 || inChannels < 1) {
            throw new InvalidParameterException("LAME supports one or two input channels here.");
        }
        if (model > 5 || model < 0 || quality > 9 || quality < 0) {
            throw new InvalidParameterException("Invalid LAME mode or quality.");
        }

        long ptr = nInit(inSampleRate, inChannels, outSampleRate, outBitrate, model, quality);

        // nInit returns small negative LAME error codes on failure. On modern arm64,
        // a perfectly valid tagged native pointer can also look negative as a signed jlong.
        // Therefore only zero or a small negative error code is considered a failure.
        if (ptr == 0 || (ptr < 0 && ptr >= -4096)) {
            throw new RuntimeException("Initialize Lame failed:" + ptr);
        }

        mInChannels = inChannels;
        mNativeLame = ptr;
    }

    int getInChannels() {
        return mInChannels;
    }

    private void checkLame() {
        if (mNativeLame == 0) {
            throw new IllegalStateException("Lame was closed.");
        }
    }

    public int getMp3bufferSize(int samples) {
        checkLame();
        return mGetMp3bufferSizeWithSamples(mNativeLame, samples);
    }

    public int encodeInterleaved(short[] bufLR, int samples, byte[] outMp3buf) {
        checkLame();
        return nEncodeShortInterleaved(mNativeLame, bufLR, samples, outMp3buf);
    }

    public int encode(short[] bufL, short[] bufR, int samples, byte[] outMp3buf) {
        checkLame();
        return nEncodeShort(mNativeLame, bufL, bufR, samples, outMp3buf);
    }

    public int flush(byte[] outMp3buf) {
        checkLame();
        return nFlush(mNativeLame, outMp3buf);
    }

    @Override
    public void close() {
        if (mNativeLame != 0) {
            nClose(mNativeLame);
            mNativeLame = 0;
        }
    }

    private static native long nInit(int inSampleRate, int inChannels, int outSampleRate,
                                     int outBitrate, int model, int quality);
    private static native int mGetMp3bufferSizeWithSamples(long lamePtr, int samples);
    private static native int nEncodeShortInterleaved(long lamePtr, short[] bufLR,
                                                       int samples, byte[] outMp3buf);
    private static native int nEncodeShort(long lamePtr, short[] bufL, short[] bufR,
                                            int samples, byte[] outMp3buf);
    private static native int nFlush(long lamePtr, byte[] outBuf);
    private static native void nClose(long lamePtr);

    static {
        System.loadLibrary("mp3lame-lib");
    }
}

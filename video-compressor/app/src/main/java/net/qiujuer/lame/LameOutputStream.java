package net.qiujuer.lame;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

/** Minimal stream wrapper around the patched Lame class. */
public class LameOutputStream implements Closeable, Flushable {
    private final Lame lame;
    private final OutputStream outputStream;
    private final byte[] outBuf;

    public LameOutputStream(Lame lame, OutputStream outputStream, int shortBufferSize) {
        this.lame = lame;
        this.outputStream = outputStream;
        int samples = lame.getInChannels() == 1 ? shortBufferSize : Math.max(1, shortBufferSize / 2);
        int nativeSize;
        try {
            nativeSize = lame.getMp3bufferSize(samples);
        } catch (Throwable ignored) {
            nativeSize = 0;
        }
        int safeSize = Math.max(nativeSize, (int) Math.ceil(1.25 * samples + 7200));
        this.outBuf = new byte[Math.max(safeSize, 16384)];
    }

    public void write(short[] pcm, int len) throws IOException {
        if (len <= 0) return;
        int count;
        if (lame.getInChannels() == 1) {
            count = lame.encode(pcm, pcm, len, outBuf);
        } else {
            count = lame.encodeInterleaved(pcm, len >> 1, outBuf);
        }
        if (count > 0) {
            outputStream.write(outBuf, 0, count);
        } else if (count < 0) {
            throw new IOException("LAME encode error: " + count);
        }
    }

    @Override
    public void flush() throws IOException {
        int count;
        do {
            count = lame.flush(outBuf);
            if (count > 0) outputStream.write(outBuf, 0, count);
            else if (count < 0) throw new IOException("LAME flush error: " + count);
        } while (count > 0);
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            flush();
        } catch (IOException e) {
            failure = e;
        }
        try {
            lame.close();
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                if (failure == null) failure = e;
            }
        }
        if (failure != null) throw failure;
    }
}

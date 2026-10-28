package tw.ajo.record;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** 003: conservative energy-based pause search over 16-kHz mono signed 16-bit PCM.
 * This is NOT an AI VAD, and must not discard quiet speech merely because it sounds soft.
 * All returned byte offsets are even and partition the recorded audio exactly once.
 */
public final class SpeechChunker003 {
    private SpeechChunker003() {}
    static final int BYTES_PER_SEC = 32000;
    static final int FRAME_BYTES = 640;       // 20 ms
    static final long MIN_BYTES = 6L * BYTES_PER_SEC;
    static final long MAX_BYTES = 16L * BYTES_PER_SEC;
    static final long SEARCH_BYTES = 3L * BYTES_PER_SEC;
    static final double PAUSE_RMS = 0.0055; // Only picks pause boundaries; does not remove speech
    static final int PAUSE_FRAMES = 22;     // 440 ms

    /** -1 means wait for more audio. When the recording ends, return the exact last sample. */
    public static long nextEnd(File pcm, long from, long available, boolean stopped) throws IOException {
        long end = available & ~1L;
        if(end <= from) return -1;
        if(stopped) return end;
        long length = end - from;
        if(length < MIN_BYTES) return -1;
        long latest = Math.min(end, from + MAX_BYTES);
        long earliest = Math.max(from + MIN_BYTES, latest - SEARCH_BYTES);
        long pause = latestPause(pcm, earliest, latest);
        if(pause >= 0) return pause;
        if(length >= MAX_BYTES) return latest;
        return -1;
    }

    private static long latestPause(File pcm, long from, long to) throws IOException {
        long alignedFrom = (from + FRAME_BYTES - 1) / FRAME_BYTES * FRAME_BYTES;
        long alignedTo = to / FRAME_BYTES * FRAME_BYTES;
        int quiet = 0;
        long last = -1;
        try(RandomAccessFile r = new RandomAccessFile(pcm, "r")) {
            r.seek(alignedFrom);
            byte[] block = new byte[FRAME_BYTES];
            for(long pos = alignedFrom; pos + FRAME_BYTES <= alignedTo; pos += FRAME_BYTES) {
                r.readFully(block);
                if(frameRms(block) < PAUSE_RMS) {
                    if(++quiet >= PAUSE_FRAMES) last = pos + FRAME_BYTES - (PAUSE_FRAMES/2L)*FRAME_BYTES;
                } else quiet = 0;
            }
        }
        return last;
    }

    static double frameRms(byte[] frame) {
        long sum=0;
        for(int i=0;i+1<frame.length;i+=2) {
            int s=(short)((frame[i]&255)|((frame[i+1]&255)<<8));
            sum+=(long)s*s;
        }
        return Math.sqrt(sum/(double)(frame.length/2))/32768.0;
    }

    /** Skip only electrically near-silent sections. Do NOT reject low but audible speech. */
    public static boolean definitelySilent(File pcm) throws IOException {
        int frames=0;
        try(RandomAccessFile r = new RandomAccessFile(pcm,"r")) {
            byte[] frame = new byte[FRAME_BYTES];
            while(r.getFilePointer()+FRAME_BYTES<=r.length()) {
                r.readFully(frame);
                if(frameRms(frame) > 0.0015) return false;
                frames++;
            }
        }
        return frames >= 20;
    }
}

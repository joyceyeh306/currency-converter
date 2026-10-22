package tw.ajo.autosubtitles;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** 16 kHz/mono/PCM16 clip. All offsets remain referenced to the original video. */
public final class ReviewPcmClip {
    private ReviewPcmClip() {}
    public static void write(File input,File output,long startMs,long endMs) throws IOException {
        if(input==null||output==null||!input.isFile()||endMs<=startMs||startMs<0)
            throw new IOException("Invalid PCM review range");
        if(input.getCanonicalFile().equals(output.getCanonicalFile()))
            throw new IOException("Cannot overwrite source PCM");
        try(RandomAccessFile in=new RandomAccessFile(input,"r");
            java.io.FileOutputStream out=new java.io.FileOutputStream(output)) {
            long a=Math.min(in.length(),Math.multiplyExact(startMs,32));
            long b=Math.min(in.length(),Math.multiplyExact(endMs,32));
            if(b-a<9600)throw new IOException("PCM review interval too short");
            in.seek(a);
            byte[] buffer=new byte[32768];
            long remain=b-a;
            while(remain>0){
                int count=in.read(buffer,0,(int)Math.min(buffer.length,remain));
                if(count<=0)throw new IOException("PCM review truncated");
                out.write(buffer,0,count);remain-=count;
            }
        }
    }
}

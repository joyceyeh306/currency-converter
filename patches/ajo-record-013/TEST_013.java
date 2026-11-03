import tw.ajo.record.*;
import tw.ajo.autosubtitles.Caption;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class TEST_013 {
    static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        File pcm=File.createTempFile("ajo013",".pcm");
        try(RandomAccessFile f=new RandomAccessFile(pcm,"rw")){f.setLength(20L*60*32000);}
        final long[] bytes={0};
        SafeLongWhisper013.Result result=SafeLongWhisper013.transcribe(
            pcm,pcm.getParentFile(),new AtomicBoolean(false),
            (part,retry,base,span)->{
                bytes[0]+=part.length();
                return Arrays.asList(new Caption(0,1000,"這是正常的逐字稿"));
            },(percent,completed)->check(percent<=99,"progress bound"),
            (items,done)->check(done<=pcm.length(),"checkpoint boundary")
        );
        check(bytes[0]==pcm.length(),"all bytes exactly once");
        check(result.chunks>=4 && result.chunks<=6,"20-min split into 4-6 windows");
        check(result.captions.size()==result.chunks,"one caption each");
        check(result.captions.get(result.captions.size()-1).startMs>result.captions.get(0).startMs,"absolute timestamps");
        pcm.delete();
        File retryPcm=File.createTempFile("ajo013retry",".pcm");
        try(RandomAccessFile f=new RandomAccessFile(retryPcm,"rw")){f.setLength(5L*60*32000);}
        final int[] attempts={0};
        SafeLongWhisper013.Result retry=SafeLongWhisper013.transcribe(
            retryPcm,retryPcm.getParentFile(),new AtomicBoolean(false),
            (part,isRetry,base,span)->{
                if(!isRetry)return Arrays.asList(new Caption(0,1000,"那你偷偷笑我幾點就走".repeat(12)));
                attempts[0]++;return Arrays.asList(new Caption(0,1000,"重新辨識成功的內容"));
            },(p,n)->{},null);
        check(retry.retriedChunks>=1 && attempts[0]>=4,"targeted retry");
        retryPcm.delete();
        System.out.println("Record013 tests: contiguous PCM, absolute timing, localized retry PASS");
    }
}
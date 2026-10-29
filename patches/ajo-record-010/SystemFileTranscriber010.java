package tw.ajo.record;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Streams a previously decoded 16-kHz PCM file via Android's optional injected-audio API.
 * The service may ignore the source and open the microphone instead, so ask for consent.
 */
public final class SystemFileTranscriber010 {
    public interface Progress { void update(int percent, String status); }
    private SystemFileTranscriber010() {}
    public static String transcribe(Context context, File pcm, AtomicBoolean cancel, Progress progress) throws Exception {
        if(pcm==null||!pcm.isFile()||pcm.length()<3200)throw new IOException("音檔沒有可辨識的音訊");
        final String[] unsupported={null};
        final SystemSpeech008 speech=new SystemSpeech008(context,new SystemSpeech008.Listener(){
            @Override public void onText(String text){}
            @Override public void onStatus(String status){progress.update(-1,status);}
            @Override public void onUnsupported(String reason){unsupported[0]=reason;}
        });
        try {
            speech.start();
            Thread.sleep(750);
            long total=pcm.length(),sent=0;
            long nextFrame=SystemClock.elapsedRealtime();
            byte[] buf=new byte[4096];
            try(InputStream in=new BufferedInputStream(new FileInputStream(pcm))) {
                int n;
                while((n=in.read(buf))!=-1) {
                    if(cancel.get())throw new InterruptedException("已取消辨識");
                    if(unsupported[0]!=null)throw new IOException(unsupported[0]);
                    while(SystemClock.elapsedRealtime()<nextFrame) {
                        if(cancel.get())throw new InterruptedException("已取消辨識");
                        Thread.sleep(Math.min(40,Math.max(1,nextFrame-SystemClock.elapsedRealtime())));
                    }
                    speech.send(buf,n);
                    sent+=n;
                    progress.update((int)Math.min(98,sent*98L/total),
                        "Android 系統辨識中 · 已送入 "+(sent/32000)+" 秒音訊");
                    nextFrame+=Math.max(1,n*1000L/32000L);
                }
            }
            if(unsupported[0]!=null)throw new IOException(unsupported[0]);
            speech.finishInput();
            long until=SystemClock.elapsedRealtime()+4000L;
            String prev="";long unchanged=SystemClock.elapsedRealtime();
            while(SystemClock.elapsedRealtime()<until) {
                if(cancel.get())throw new InterruptedException("已取消辨識");
                if(unsupported[0]!=null)throw new IOException(unsupported[0]);
                String now=speech.snapshot();
                if(!prev.equals(now)){prev=now;unchanged=SystemClock.elapsedRealtime();}
                if(!now.isBlank()&&SystemClock.elapsedRealtime()-unchanged>1700)break;
                Thread.sleep(120);
            }
            String output=TranscriptText.traditional(speech.snapshot()).trim();
            if(output.isEmpty())throw new IOException("系統辨識沒有回傳文字；OPPO 辨識服務可能不支援匯入音檔");
            if(TranscriptSafety008.runaway(output))throw new IOException("系統辨識結果大量重複，已拒絕覆蓋逐字稿");
            progress.update(100,"Android 辨識完成，尚無逐句時間標記");
            return output;
        } finally {speech.stop();}
    }
}

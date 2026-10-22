import java.io.*;
import java.nio.file.*;
import java.util.*;
import tw.ajo.autosubtitles.*;

/** Offline safety/regression checks for opt-in, review-only Whisper interval recovery. */
public final class Version040Tests {
    private static void ok(boolean pass,String why){if(!pass)throw new AssertionError(why);}
    public static void main(String[] args) throws Exception {
        List<Caption> original=new ArrayList<>();
        Caption a=new Caption(500,2200,"爸爸新年快樂");
        Caption b=new Caption(5600,7800,"媽媽新年快樂");
        original.add(a);original.add(b);
        List<SpeechTiming.Span> speech=Arrays.asList(new SpeechTiming.Span(550,2100),
            new SpeechTiming.Span(2900,3900),new SpeechTiming.Span(5800,7300),
            new SpeechTiming.Span(8500,9450));
        List<SpeechTiming.Span> gaps=SubtitleGapReview.find(speech,original,10000);
        ok(gaps.size()==2,"two truly uncovered voice gaps must be presented");
        ok(gaps.get(0).startMs==2900&&gaps.get(0).endMs==3900,"first gap original clock");
        ok(gaps.get(1).startMs==8500,"last gap");
        ok(a.startMs==500&&a.endMs==2200&&"爸爸新年快樂".equals(a.text),"existing caption unchanged");
        List<SpeechTiming.Span> noGap=SubtitleGapReview.find(
            Arrays.asList(new SpeechTiming.Span(700,1000)),original,10000);
        ok(noGap.isEmpty(),"speech covered by existing captions is never auto-added");
        ok(SubtitleGapReview.find(speech,original,0).isEmpty(),"invalid duration returns no candidates");
        SpeechTiming.Span window=SubtitleGapReview.clip(2900,3900,10000);
        ok(window.startMs==1400&&window.endMs==5400,"speech context must be kept");
        SpeechTiming.Span limited=SubtitleGapReview.clip(2000,42000,50000);
        ok(limited.endMs-limited.startMs<=18000,"bounded CPU work for long clips");
        List<Caption> retry=Arrays.asList(new Caption(0,1700,"爸爸新年快樂"),
            new Caption(1700,2800,"阿公新年快樂"),new Caption(4300,5500,"其他話"));
        String preview=SubtitleGapReview.previewText(retry,1400,2900,3900);
        ok(preview.equals("阿公新年快樂"),"clip-relative times must be mapped before selection");
        ok(SubtitleGapReview.previewText(retry,1400,7800,8800).isEmpty(),"out-of-range hallucinations are not candidates");
        Path input=Files.createTempFile("ajo040-in",".pcm"), output=Files.createTempFile("ajo040-out",".pcm");
        try {
            byte[] data=new byte[32000*4];
            for(int i=0;i<data.length;i++)data[i]=(byte)(i%251);
            Files.write(input,data);
            ReviewPcmClip.write(input.toFile(),output.toFile(),1000,2300);
            byte[] clipped=Files.readAllBytes(output);
            ok(clipped.length==41600,"PCM offset uses 32 bytes/ms");
            ok(clipped[0]==data[32000]&&clipped[clipped.length-1]==data[32000+41600-1],"exact PCM data preserved");
        }finally{Files.deleteIfExists(input);Files.deleteIfExists(output);}
        String main=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/MainActivity.java"));
        ok(main.contains("檢查可能漏句（不改原字幕）"),"discoverable manual gap check");
        ok(main.contains("重辨識這段（先預覽，不覆蓋）"),"editor can retry overlap without rewriting original");
        ok(main.contains("原字幕保持不變")&&main.contains("setNegativeButton(\"保留原文\""),"uncertain result remains optional");
        ok(main.contains("PcmDecoder.decode")&&main.contains("ReviewPcmClip.write")&&main.contains("WhisperNative.run"),"actual local second-pass audio inference");
        ok(main.contains("if(!captions.contains(existing))return;"),"avoid stale editor result");
        ok(Files.readString(Path.of("app/build.gradle")).contains("versionName '040'"),"040 versionName");
        ok(Files.readString(Path.of("app/build.gradle")).contains("versionCode 40"),"040 versionCode");
        System.out.println("Version040Tests passed: gap detection, preservation, segment-offset, PCM crop, opt-in UI and versioning");
    }
}

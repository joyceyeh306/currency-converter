import java.nio.file.*;
public final class Version066Tests {
  static int n=0;
  static void yes(boolean x,String m){n++; if(!x)throw new AssertionError(m);}
  public static void main(String[] z)throws Exception {
    String b=Files.readString(Path.of("app/build.gradle"));
    String root="app/src/main/java/tw/ajo/autosubtitles/";
    String a=Files.readString(Path.of(root+"MainActivity.java"));
    String j=Files.readString(Path.of(root+"WhisperNative.java"));
    String c=Files.readString(Path.of("app/src/main/cpp/ajo_whisper.cpp"));
    yes(b.contains("versionCode 66")&&b.contains("versionName '066'"),"066 version");
    yes(a.contains("WhisperNative.Result recognized=WhisperNative.runFullQuality066("),"full-file beam route");
    yes(a.contains("WhisperNative.Result pass=WhisperNative.run("),"local retry remains greedy");
    yes(a.contains("WhisperRecovery045.accept(initial,candidates,anchors,duration)"),"045 guard");
    yes(a.contains("WhisperClipTiming065.apply(refined,timedReviews)"),"065 native timestamps");
    yes(j.contains("boolean beamSearch, AtomicBoolean cancelled"),"bridge boolean flag");
    yes(j.contains("runInternal(model,pcm,language,cpuThreads,false,cancelled,progress)"),"default greedy");
    yes(j.contains("runInternal(model,pcm,language,cpuThreads,true,cancelled,progress)"),"full beam flag");
    yes(c.contains("jint cpuThreads,jboolean beamSearch,jobject cancelled"),"JNI parity");
    yes(c.contains("WHISPER_SAMPLING_BEAM_SEARCH")&&c.contains("WHISPER_SAMPLING_GREEDY"),"both algorithms retained");
    yes(c.contains("params.beam_search.beam_size=5;"),"width five");
    yes(c.contains("retryParams.strategy=WHISPER_SAMPLING_GREEDY;"),"tail recovery greedy");
    yes(c.contains("p.strategy=WHISPER_SAMPLING_GREEDY;"),"early recovery greedy");
    yes(c.contains("independent.strategy=WHISPER_SAMPLING_GREEDY;"),"059 recheck greedy");
    yes(c.contains("params.no_context=false;")&&c.contains("params.no_timestamps=false;"),"old context and times");
    yes(c.contains("ctxparams.use_gpu=false;"),"CPU-only model");
    yes(a.contains("WhisperRecovery044.plan(anchors,duration)"),"064 full-timeline rechecks");
    yes(a.contains("WhisperClipTiming065.review("),"065 safe split review");
    yes(a.contains("rescue.captions"),"045 result intact");
    yes(Files.readString(Path.of(root+"SpeechGap062.java")).contains("MIN_MISSING_MS=600"),"VAD unchanged");
    String e=Files.readString(Path.of(root+"SubtitleExporter.java"));
    yes(e.contains("experimentalSetMaxFramesInEncoder("),"057 encoder guard");
    yes(e.contains("HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC"),"054 color processing");
    System.out.println("066 PASS "+n+" checks");
  }
}
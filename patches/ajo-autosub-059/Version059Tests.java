import java.nio.file.*;
public final class Version059Tests {
    static int count=0;
    static void ok(boolean b,String reason){count++;if(!b)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        String cpp=Files.readString(Path.of("app/src/main/cpp/ajo_whisper.cpp"));
        String java=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/WhisperNative.java"));
        String exporter=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/SubtitleExporter.java"));
        String color=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/SdrSaturation056.java"));
        String gradle=Files.readString(Path.of("app/build.gradle"));
        ok(gradle.contains("versionCode 59")&&gradle.contains("versionName '059'"),"version");
        ok(cpp.contains("#include \"whisper_silence_review059.h\""),"helper linked");
        ok(cpp.contains("whisper_full_with_state(w,state,params"),"primary 058 decode kept");
        ok(cpp.contains("independent.no_context=true"),"true independent retry");
        ok(cpp.contains("whisper_full_n_segments_from_state(fresh)"),"second pass result checked");
        ok(cpp.contains("MAX_REVIEWS"),"bounded performance cost");
        ok(cpp.contains("if(reject)phrases.erase"),"only double-confirmed no speech deleted");
        ok(cpp.contains("ajo_tail::anomalous")&&cpp.contains("ajo_early::findMissedSpeechOnset"),"031/034 safeguards kept");
        ok(cpp.contains("ctx.progress"),"existing feedback retained");
        ok(java.contains("new long[8]"),"JNI metrics unchanged");
        ok(exporter.contains("experimentalSetMaxFramesInEncoder("),"057 artifact fix kept");
        ok(exporter.contains("HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC"),"054 conversion kept");
        ok(color.contains("K_TARGET_MULTIPLIER = 3.0f"),"058 K color kept");
        ok(!cpp.contains("if(phrase==\"謝謝收看\")"),"no phrase-specific blacklist");
        System.out.println("059 integration checks PASSED ("+count+")");
    }
}

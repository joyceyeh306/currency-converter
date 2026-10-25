import java.nio.file.*;
import java.util.*;
import tw.ajo.autosubtitles.*;
/** 063 regression: the 7-candidate list must actually be selectable, not covered by message. */
public final class Version063Tests {
  static int checks;
  static void ok(boolean yes, String why){checks++;if(!yes)throw new AssertionError(why);}
  public static void main(String[] args) throws Exception {
    String src=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/MainActivity.java"));
    String gradle=Files.readString(Path.of("app/build.gradle"));
    int a=src.indexOf("private void showMissingVoiceList062(");
    int b=src.indexOf("private void showMissingVoiceItem062(",a);
    ok(a>0 && b>a,"found 063 missing voice list handler");
    String list=src.substring(a,b);
    ok(!list.contains(".setMessage("),"list dialog must not hide list with setMessage");
    ok(list.contains(".setItems(labels,"),"candidate list must use Android selectable items");
    ok(list.contains("new String[gaps.size()]"),"all candidate intervals have labels");
    ok(list.contains("showMissingVoiceItem062(source,gaps,i)"),"each item opens correct candidate");
    ok(list.contains("Caption.time(s.startMs,false)")&&list.contains("Caption.time(s.endMs,false)"),"labels show accurate start and end");
    ok(list.contains("返回編輯字幕"),"user can exit the list");
    ok(list.contains("gaps==null || gaps.isEmpty()"),"no empty candidate list");
    String item=src.substring(b,src.indexOf("private void retryCaptionWithWhisper(",b));
    ok(item.contains("試聽這段")&&item.contains("playCaptionRange("),"candidate audio preview is reachable");
    ok(item.contains("Math.max(0,s.startMs-350)")&&item.contains("Math.min(videoDurationMs,s.endMs+350)"),"trial preview bounds are clamped");
    ok(item.contains("新增空白字幕")&&item.contains("new Caption(s.startMs,s.endMs,\"\")"),"add empty caption with candidate start and end");
    ok(item.contains("openCaptionEditor(added)"),"new caption opens text and timing editor");
    ok(item.contains("if(busy||!source.equals(video))return;"),"prevent adding to changed video or while busy");
    ok(item.contains("原有字幕時間和文字都不變"),"warn user that original caption data stays untouched");
    ok(gradle.contains("versionCode 63") && gradle.contains("versionName '063'"),"correct new version");
    String gaps=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/SpeechGap062.java"));
    String silero=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/OnnxSilero062.java"));
    String cloud=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/CloudReview060.java"));
    ok(gaps.contains("MIN_MISSING_MS=600")&&gaps.contains("CAPTION_GRACE_MS=100"),"same gap detection policy");
    ok(silero.contains("silero_vad_062.onnx"),"same verified local VAD model");
    ok(cloud.contains("gpt-4o-transcribe-diarize"),"same paid recognition model");
    List<Caption> original=new ArrayList<>(List.of(new Caption(300,1200,"保留時間")));
    List<SpeechTiming.Span> candidates=SpeechGap062.find(List.of(new SpeechTiming.Span(1600,2750)),original,3400);
    ok(candidates.size()==1 && candidates.get(0).startMs==1600 && candidates.get(0).endMs==2750,"candidate timestamps preserved");
    ok(original.size()==1&&original.get(0).startMs==300&&original.get(0).endMs==1200,"existing captions not edited by detection");
    System.out.println("Version063Tests passed: "+checks);
  }
}

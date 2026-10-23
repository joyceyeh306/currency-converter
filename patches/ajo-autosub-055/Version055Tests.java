import tw.ajo.autosubtitles.SdrColor054;
import java.nio.file.*;
public class Version055Tests {
  static int assertions = 0;
  static void ok(boolean b, String name) { assertions++; if (!b) throw new AssertionError(name); }
  static String read(String p) throws Exception { return Files.readString(Path.of(p)); }
  public static void main(String[] a) throws Exception {
    String gradle=read("app/build.gradle");
    String exporter=read("app/src/main/java/tw/ajo/autosubtitles/SubtitleExporter.java");
    String activity=read("app/src/main/java/tw/ajo/autosubtitles/MainActivity.java");
    String helper=read("app/src/main/java/tw/ajo/autosubtitles/SdrColor054.java");
    ok(gradle.contains("versionCode 55") && gradle.contains("versionName '055'"), "version 055");
    ok(SdrColor054.SATURATION_POINTS == 4f, "exactly +4 saturation only");
    ok(helper.contains("SATURATION_POINTS = 4f"), "one changed color constant");
    ok(SdrColor054.applyToNormalExport(true,false), "HDR iPhone on regular export");
    ok(!SdrColor054.applyToNormalExport(false,false), "SDR footage unchanged");
    ok(!SdrColor054.applyToNormalExport(true,true), "HDR diagnostics unchanged");
    ok(exporter.contains("new HslAdjustment.Builder()") && exporter.contains(".adjustSaturation(SdrColor054.SATURATION_POINTS)"), "same 054 pipeline");
    ok(!exporter.contains(".adjustLightness("), "no lightness changes");
    ok(!exporter.contains(".adjustHue("), "no hue changes");
    ok(exporter.contains("Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC"), "keep stable MediaCodec tone-map");
    ok(exporter.contains("diagnosticMode && HdrExport049.requestHdr(inputHdr,attempt)"), "HDR KEEP_HDR only for diagnostic");
    ok(!exporter.contains("new FrameDropEffect"), "no FPS drop");
    ok(exporter.contains("new CanvasOverlay(true)"), "retain working subtitle rendering");
    ok(activity.contains("正在使用 HDR→SDR 色調映射並加入字幕"), "keep familiar export UI");
    ok(exporter.contains("attempt==HdrExport049.FIRST ? HdrExport049.SDR_COMPAT_720 : -1"), "keep fallback unchanged");
    System.out.println("055 regression checks passed: " + assertions);
  }
}

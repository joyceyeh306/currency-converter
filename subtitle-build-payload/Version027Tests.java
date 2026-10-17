import java.nio.file.Files;
import java.nio.file.Path;
import tw.ajo.autosubtitles.VideoFrameRate;
import java.util.ArrayList;
import java.util.List;

/** 027 quality-preserving video export regression checks, JVM and source assertions. */
public final class Version027Tests {
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
    public static void main(String[] args) throws Exception {
        String src=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/SubtitleExporter.java"));
        String ui=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/MainActivity.java"));
        String build=Files.readString(Path.of("app/build.gradle"));
        check(!src.contains("FrameDropEffect"+".create"), "027 must not force HDR60 to 30");
        check(!src.contains("new HslAdjustment"+".Builder"), "027 must not add tint/lightness");
        check(src.contains("HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC"), "maintain real hardware tone mapping");
        check(src.contains("new OverlayEffect"), "retain subtitle burn-in pipeline");
        check(src.contains("if(!fallback)"), "allow compatibility retry without frame dropping");
        check(!ui.contains("HDR 60 fps 轉 30 fps"), "UI must not promise deliberate FPS drop");
        check(ui.contains("目標維持原片約"), "show target native cadence to user");
        check(build.contains("versionCode 27") && build.contains("versionName '027'"), "027 version numbers");
        List<Long> pts=new ArrayList<>();
        for(int i=0;i<90;i++)pts.add(i*16667L);
        check(VideoFrameRate.choose(-1,-1,pts)==60, "iPhone HDR source detected as 60 fps");
        System.out.println("Version027Tests: OK (HDR pipeline, source FPS, no grading, continuity)");
    }
}

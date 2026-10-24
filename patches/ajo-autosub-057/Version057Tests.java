import java.nio.file.Files;
import java.nio.file.Path;
import tw.ajo.autosubtitles.ExportFrameBudget057;
import tw.ajo.autosubtitles.SdrSaturation056;

public final class Version057Tests {
    private static int checked=0;
    private static void verify(boolean ok,String message){checked++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        String exporter=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/SubtitleExporter.java"));
        String gradle=Files.readString(Path.of("app/build.gradle"));
        String matrix=Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/SdrSaturation056.java"));
        verify(gradle.contains("versionCode 57") && gradle.contains("versionName '057'"),"057 version");
        verify(ExportFrameBudget057.MAX_IN_FLIGHT_FRAMES == 4,"frame-in-flight budget");
        verify(ExportFrameBudget057.useBoundedFrames(true,false),"normal HDR constrained");
        verify(!ExportFrameBudget057.useBoundedFrames(false,false),"SDR untouched");
        verify(!ExportFrameBudget057.useBoundedFrames(true,true),"diagnostic HDR untouched");
        verify(exporter.contains("transformerBuilder.experimentalSetMaxFramesInEncoder("),"frame budget wired");
        verify(exporter.contains("ExportFrameBudget057.MAX_IN_FLIGHT_FRAMES"),"single budget reference");
        verify(exporter.contains("Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC"),"054 MediaCodec tonemap retained");
        verify(exporter.contains("new CanvasOverlay(true)"),"caption overlay unchanged");
        verify(exporter.contains("(RgbMatrix) (presentationTimeUs, useHdr) -> SdrSaturation056.getMatrix()"),"first-056 color wiring unchanged");
        verify(Math.abs(SdrSaturation056.H_TARGET_MULTIPLIER - 2.5f) < 0.00001f,"first-056 2.50 strength retained");
        verify(!matrix.contains("MATRIX_LINEAR_MULTIPLIER"),"do not use later 2.10 rewrite");
        verify(!exporter.contains("new FrameDropEffect"),"no dropped frames");
        verify(exporter.contains("MimeTypes.VIDEO_H264"),"compatible H264 output retained");
        verify(!exporter.contains(".setiFrameIntervalSeconds"),"do not touch GOP or fps");
        verify(!exporter.contains(".setEncoderPerformanceParameters"),"do not alter priority or operating rate");
        verify(exporter.contains("HdrExport049.SDR_COMPAT_720"),"old export fallback preserved");
        System.out.println("057 focused regression PASSED ("+checked+" checks)");
    }
}

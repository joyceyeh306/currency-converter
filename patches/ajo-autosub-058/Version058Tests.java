import java.nio.file.Files;
import java.nio.file.Path;
import tw.ajo.autosubtitles.SdrSaturation056;
import tw.ajo.autosubtitles.ExportFrameBudget057;

public final class Version058Tests {
    private static int checks = 0;
    private static void check(boolean ok, String msg) {
        checks++;
        if (!ok) throw new AssertionError(msg);
    }
    private static boolean eq(float a, float b) { return Math.abs(a-b) < .00002f; }
    private static float[] apply(float[] m, float r, float g, float b) {
        return new float[] {m[0]*r + m[4]*g + m[8]*b, m[1]*r+m[5]*g+m[9]*b,
                            m[2]*r+m[6]*g+m[10]*b};
    }
    public static void main(String[] args) throws Exception {
        String app = Files.readString(Path.of("app/build.gradle"));
        String exporter = Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/SubtitleExporter.java"));
        String matrixSource = Files.readString(Path.of("app/src/main/java/tw/ajo/autosubtitles/SdrSaturation056.java"));
        check(app.contains("versionCode 58") && app.contains("versionName '058'"), "058 version");
        check(eq(SdrSaturation056.H_TARGET_MULTIPLIER, 2.5f), "historical H preserved");
        check(eq(SdrSaturation056.K_TARGET_MULTIPLIER, 3.0f), "selected K +200 target");
        check(matrixSource.contains("float saturation = K_TARGET_MULTIPLIER"), "matrix actually uses K");
        check(SdrSaturation056.applyToNormalExport(true,false), "normal HDR only");
        check(!SdrSaturation056.applyToNormalExport(false,false), "SDR unaffected");
        check(!SdrSaturation056.applyToNormalExport(true,true), "manual HDR diagnostic unaffected");
        float[] m=SdrSaturation056.getMatrix();
        check(m.length==16 && eq(m[15],1f), "matrix 4x4, alpha identity");
        check(eq(m[3],0f)&&eq(m[7],0f)&&eq(m[11],0f)&&eq(m[12],0f)&&eq(m[13],0f)&&eq(m[14],0f),"alpha unchanged");
        for(float v : new float[]{0f,.1f,.4f,.8f,1f}) {
            float[] out=apply(m,v,v,v);
            check(eq(out[0],v)&&eq(out[1],v)&&eq(out[2],v), "neutral gray fixed "+v);
        }
        float[] src = {.2f,.4f,.6f}, out=apply(m,src[0],src[1],src[2]);
        check(eq(out[0]-out[1], 3f*(src[0]-src[1])),"red/green separation x3");
        check(eq(out[1]-out[2], 3f*(src[1]-src[2])),"green/blue separation x3");
        check(eq(.2126f*out[0]+.7152f*out[1]+.0722f*out[2],
                 .2126f*src[0]+.7152f*src[1]+.0722f*src[2]),"BT709 luminance fixed");
        check(ExportFrameBudget057.MAX_IN_FLIGHT_FRAMES==4,"057 stability buffer budget preserved");
        check(ExportFrameBudget057.useBoundedFrames(true,false),"057 stability policy preserved");
        check(exporter.contains("experimentalSetMaxFramesInEncoder("),"057 limit actually wired");
        check(exporter.contains("ExportFrameBudget057.MAX_IN_FLIGHT_FRAMES"),"same 4 frame limit");
        check(exporter.contains("Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC"),"054 HDR SDR stable path");
        check(exporter.contains("(RgbMatrix) (presentationTimeUs, useHdr) -> SdrSaturation056.getMatrix()"),"active K matrix wired");
        check(exporter.contains("new CanvasOverlay(true)"),"subtitles unaffected");
        check(!exporter.contains("new FrameDropEffect"),"frame cadence unchanged");
        System.out.println("058 core regression PASSED ("+checks+" checks)");
    }
}

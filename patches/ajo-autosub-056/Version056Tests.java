import java.nio.file.Files;
import java.nio.file.Path;
import tw.ajo.autosubtitles.SdrSaturation056;

public final class Version056Tests {
    private static int count;
    static void ok(boolean condition, String reason) {
        count++;
        if (!condition) throw new AssertionError(reason);
    }
    static boolean approx(float actual, float expected) {
        return Math.abs(actual-expected) < 0.00015f;
    }
    static String read(String name) throws Exception { return Files.readString(Path.of(name)); }
    private static float[] apply(float[] mat, float red,float green,float blue) {
        return new float[] {
            mat[0]*red + mat[4]*green + mat[8]*blue,
            mat[1]*red + mat[5]*green + mat[9]*blue,
            mat[2]*red + mat[6]*green + mat[10]*blue
        };
    }
    public static void main(String[] args) throws Exception {
        String gradle=read("app/build.gradle");
        String exporter=read("app/src/main/java/tw/ajo/autosubtitles/SubtitleExporter.java");
        String existing=read("app/src/main/java/tw/ajo/autosubtitles/SdrColor054.java");
        ok(gradle.contains("versionCode 56") && gradle.contains("versionName '056'"), "version 056");
        ok(approx(SdrSaturation056.H_TARGET_MULTIPLIER,2.5f), "H 2.5x target");
        ok(SdrSaturation056.applyToNormalExport(true,false), "regular HDR only");
        ok(!SdrSaturation056.applyToNormalExport(false,false), "original SDR untouched");
        ok(!SdrSaturation056.applyToNormalExport(true,true), "manual HDR diagnostics untouched");
        float[] m=SdrSaturation056.getMatrix();
        ok(m.length==16 && m[15]==1f, "full RGB matrix, alpha identity");
        ok(m[3]==0f && m[7]==0f && m[11]==0f && m[12]==0f && m[13]==0f && m[14]==0f, "no offsets or alpha changes");
        for(float grey : new float[]{0f,.12f,.42f,1f}){
            float[] rgb=apply(m,grey,grey,grey);
            ok(approx(rgb[0],grey)&&approx(rgb[1],grey)&&approx(rgb[2],grey), "grayscale unchanged at "+grey);
        }
        float[] input={.2f,.4f,.6f}; float[] output=apply(m,input[0],input[1],input[2]);
        float lumaBefore=.2126f*input[0]+.7152f*input[1]+.0722f*input[2];
        float lumaAfter=.2126f*output[0]+.7152f*output[1]+.0722f*output[2];
        ok(approx(lumaBefore,lumaAfter), "BT709 luminance preserved");
        ok(approx(output[0]-output[1],2.5f*(input[0]-input[1])), "R-G chroma separation x2.5");
        ok(approx(output[1]-output[2],2.5f*(input[1]-input[2])), "G-B chroma separation x2.5");
        ok(exporter.contains("(RgbMatrix) (presentationTimeUs, useHdr) -> SdrSaturation056.getMatrix()"), "new RGB matrix wired into export");
        ok(!exporter.contains(".adjustSaturation("), "no misleading +150 HSL setting");
        ok(exporter.contains("Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC"), "preserve verified MediaCodec HDR-to-SDR");
        ok(exporter.contains("new CanvasOverlay(true)"), "normal subtitle overlay unchanged");
        ok(exporter.contains("drawHdrText = drawSubtitles && diagnosticMode && inputHdr"), "diagnostics only HDR text shader");
        ok(!exporter.contains("new FrameDropEffect"), "no frame drops");
        ok(existing.contains("SATURATION_POINTS = 4f"), "055 helper preserved for archaeology");
        System.out.println("056 core validation PASSED ("+count+" checks)");
    }
}

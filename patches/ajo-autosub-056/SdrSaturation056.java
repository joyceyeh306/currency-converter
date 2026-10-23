package tw.ajo.autosubtitles;

/**
 * 056: SDR-only colour-saturation finishing after the device's proven
 * MediaCodec HDR-to-SDR tone map. The user's H reference is FFmpeg's
 * eq=saturation=2.5 (a +150% increase) on the 054 SDR result.
 *
 * Media3 RgbMatrix works in LINEAR RGB BT.709, not gamma-encoded YUV,
 * so identical output to FFmpeg is not promised. 054 SDR sample comparison
 * against H (12-19 seconds) calibrated the linear RGB strength to 2.10.
 * H remains the user's FFmpeg gamma-YUV 2.5x target, not a Media3 setting.
 *
 * The matrix increases distance from BT.709 neutral grey, while keeping
 * computed luminance and neutral grey unchanged. Exceeding gamut is
 * clipped by the normal SDR pipeline. It never alters source SDR clips.
 */
public final class SdrSaturation056 {
    private SdrSaturation056() {}

    public static final float H_TARGET_MULTIPLIER = 2.5f;
    // 054 SDR vs H FFmpeg sample calibrated at 12-19 seconds, 2 fps, 160x90.
    // Media3 transforms linear RGB rather than FFmpeg gamma YUV.
    public static final float MATRIX_LINEAR_MULTIPLIER = 2.10f;
    private static final float LUMA_R = 0.2126f;
    private static final float LUMA_G = 0.7152f;
    private static final float LUMA_B = 0.0722f;

    public static boolean applyToNormalExport(boolean inputHdr, boolean diagnostic) {
        return inputHdr && !diagnostic;
    }

    /** Column-major OpenGL 4x4; alpha stays unchanged. */
    public static float[] getMatrix() {
        float saturation = MATRIX_LINEAR_MULTIPLIER;
        float inverse = 1f - saturation;
        float r = inverse * LUMA_R;
        float g = inverse * LUMA_G;
        float b = inverse * LUMA_B;
        return new float[] {
            r + saturation, r, r, 0f,
            g, g + saturation, g, 0f,
            b, b, b + saturation, 0f,
            0f, 0f, 0f, 1f
        };
    }
}

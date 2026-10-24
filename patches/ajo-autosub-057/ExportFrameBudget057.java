package tw.ajo.autosubtitles;

/**
 * 057: limit simultaneous surfaces for the proven 054 MediaCodec HDR->SDR
 * pipeline.  Bounded input can reduce in-flight buffer pressure at 60fps;
 * it cannot, by itself, prove or guarantee that hardware artifacting stops.
 * Defaults for original SDR clips and the manual HDR diagnostic are unchanged.
 */
public final class ExportFrameBudget057 {
    private ExportFrameBudget057() {}
    /** Media3 developer-recommended experimental range starts at four. */
    public static final int MAX_IN_FLIGHT_FRAMES = 4;
    public static boolean useBoundedFrames(boolean hdrInput, boolean manualHdrDiagnostic) {
        return hdrInput && !manualHdrDiagnostic;
    }
}

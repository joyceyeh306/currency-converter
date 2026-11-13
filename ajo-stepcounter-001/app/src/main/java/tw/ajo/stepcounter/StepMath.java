package tw.ajo.stepcounter;

/** Side-effect-free delta decision. First sighting and reboot establish a baseline. */
public final class StepMath {
    private StepMath() {}
    public static long newSteps(String previousBoot, long previousCounter,
                                String currentBoot, long currentCounter) {
        if (currentCounter < 0 || previousCounter < 0
                || previousBoot == null || previousBoot.isEmpty()
                || !previousBoot.equals(currentBoot)
                || currentCounter < previousCounter) return 0;
        return currentCounter - previousCounter;
    }
}

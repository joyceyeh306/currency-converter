import tw.ajo.stepcounter.StepMath;
public final class StepMathTest {
  static void eq(long want,long got) {
    if(want != got) throw new AssertionError("Expected "+want+" got "+got);
  }
  public static void main(String[] args) {
    eq(0,StepMath.newSteps("",0,"boot1",900));
    eq(25,StepMath.newSteps("boot1",900,"boot1",925));
    eq(0,StepMath.newSteps("boot1",925,"boot2",7));
    eq(0,StepMath.newSteps("boot1",925,"boot1",5));
    eq(0,StepMath.newSteps("boot2",5,"boot2",5));
    eq(12,StepMath.newSteps("boot2",5,"boot2",17));
    System.out.println("StepMath tests passed");
  }
}

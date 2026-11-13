import tw.ajo.stepcounter.GoalMath;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
public final class GoalMathTest {
  static void eq(String want,List<Integer> got) {
    if(!want.equals(got.toString()))throw new AssertionError(want+" != "+got);
  }
  public static void main(String[] args) {
    List<Integer> levels=Arrays.asList(3000,5000,10000);
    eq("[]",GoalMath.crossed(levels,300,2999,Collections.emptySet()));
    eq("[3000]",GoalMath.crossed(levels,2999,3000,Collections.emptySet()));
    eq("[3000, 5000]",GoalMath.crossed(levels,2900,5600,Collections.emptySet()));
    eq("[]",GoalMath.crossed(levels,5200,5200,Collections.emptySet()));
    eq("[]",GoalMath.crossed(levels,5600,5800,new HashSet<>(Arrays.asList(3000,5000))));
    eq("[5000]",GoalMath.crossed(levels,2999,5800,new HashSet<>(Arrays.asList(3000))));
    eq("[]",GoalMath.crossed(levels,5200,1000,Collections.emptySet()));
    System.out.println("GoalMath tests passed");
  }
}

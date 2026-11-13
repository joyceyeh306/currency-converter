package tw.ajo.stepcounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure calculation: a goal fires when the daily count CROSSES it, once per day. */
public final class GoalMath {
    private GoalMath() {}
    public static List<Integer> crossed(List<Integer> goals,long before,long after,Set<Integer> alreadySent) {
        List<Integer> out=new ArrayList<>();
        if(after<=before)return out;
        for(int goal:goals) {
            if(goal>before && goal<=after && !alreadySent.contains(goal)) out.add(goal);
        }
        return out;
    }
}

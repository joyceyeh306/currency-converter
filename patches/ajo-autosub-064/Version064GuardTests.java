import java.util.*;
import tw.ajo.autosubtitles.*;

/** 064 regression: never select only the first 12 speech anchors in longer dialogue. */
public final class Version064GuardTests {
  static int checks;
  static void check(boolean yes,String what){checks++;if(!yes)throw new AssertionError(what);}
  public static void main(String[] args){
    long[][] intervals={{100,720},{3540,5440},{5600,6180},{9620,9980},{12140,12960},
      {14660,15320},{19320,19760},{21040,21460},{21620,22220},{23800,24620},
      {25060,25860},{26580,27180},{28020,28520},{28600,29980},{32640,33860}};
    ArrayList<SpeechBandActivity.Anchor> anchors=new ArrayList<>();
    for(long[] t:intervals)anchors.add(new SpeechBandActivity.Anchor(t[0],t[1],3,.11));
    final long duration=34267;
    List<WhisperRecovery044.Window> plan=WhisperRecovery044.plan(anchors,duration);
    check(anchors.size()==15,"15 acoustic anchors");
    check(plan.size()==12,"12 inference-pass ceiling unchanged");
    check(plan.get(0).anchor.startMs==100,"first original acoustic anchor retained");
    check(plan.get(plan.size()-1).anchor.startMs==32640,"end of original movie covered");
    check(plan.stream().anyMatch(w->w.anchor.startMs==28600),"late 28.6s segment scheduled");
    for(WhisperRecovery044.Window w:plan)
      check(w.readStartMs>=0 && w.readEndMs<=duration && w.readEndMs>w.readStartMs,"valid clip");
    for(int i=1;i<plan.size();i++)
      check(plan.get(i).readStartMs>=plan.get(i-1).readStartMs,"chronological windows");
    List<SpeechBandActivity.Anchor> shortList=anchors.subList(0,8);
    var normal=WhisperRecovery044.plan(shortList,duration);
    check(normal.size()==8,"old small-clip count");
    for(int i=0;i<8;i++)check(normal.get(i).anchor==shortList.get(i),"old short-video unchanged");
    ArrayList<Caption> original=new ArrayList<>();
    for(int i=0;i<3;i++)original.add(new Caption(i*2200L,i*2200L+1900,"爸爸媽媽你們新年快樂"));
    original.add(new Caption(27200,30100,"一個禮拜你要記得"));
    original.add(new Caption(31300,34000,"我沒有答應你我不管你"));
    ArrayList<Caption> shortGuess=new ArrayList<>();
    ArrayList<SpeechBandActivity.Anchor> guessAnchors=new ArrayList<>();
    for(int i=0;i<12;i++){
      long a=600+i*1900L;
      shortGuess.add(new Caption(a,a+1000,"局部文字"+i));
      guessAnchors.add(new SpeechBandActivity.Anchor(a,a+1000,4,.11));
    }
    check(WhisperRecovery045.needsRescue(original,duration),"old rescue trigger retained");
    check(!WhisperRecovery045.accept(original,shortGuess,guessAnchors,duration),
          "prevent losing original valid 31-34s tail");
    check(original.get(original.size()-1).endMs==34000,"original timestamps unchanged");
    var spanning=List.of(new Caption(17000,33500,"我有一段沒有結束的長話"));
    check(!WhisperRecovery045.accept(spanning,shortGuess,guessAnchors,duration),
          "guard long speech spanning rejected suffix");
    var repeated=List.of(new Caption(28000,29000,"新年快樂"),
        new Caption(30000,31000,"新年快樂"),new Caption(32000,34000,"新年快樂"));
    check(!WhisperRecovery045.accept(repeated,shortGuess,guessAnchors,duration),
          "do not erase legitimate repeated short speech");
    System.out.println("Version064GuardTests passed: "+checks+" conditions");
  }
}
import java.util.*;
import tw.ajo.autosubtitles.Caption;
import tw.ajo.record.*;
public class TEST_006 {
 static void chk(boolean ok,String name){if(!ok)throw new AssertionError(name);}
 public static void main(String[] args){
  String sample="我現在要繼續測試這個逐字稿那我現在在玩那個做壽司的遊戲然後背後有吸塵器掃地機器人在吸東西所以應該會有一點噪音然後我們再繼續試看看雖然感覺辨識的速度。";
  String sample2="還是比較慢如果讓AI自己辨識然後做出標點符號這樣不知道可不可行如果我用講的像逗號問號這樣好像又會很奇怪好了先來讓他辨識看看會不會成功。";
  List<TimedTranscript005.Segment> original=Arrays.asList(new TimedTranscript005.Segment(0,29000,sample),new TimedTranscript005.Segment(29000,59650,sample2));
  List<TimedTranscript005.Segment> split=TranscriptLayout006.splitForReading(original);
  chk(split.size()>=7,"readable many sections");
  StringBuilder joined=new StringBuilder();long prior=-1;
  for(TimedTranscript005.Segment s:split){
   chk(s.text.length()<=38,"no lengthy unreadable block");
   chk(s.startMs>=prior,"monotonic timeline");
   chk(s.endMs>s.startMs,"positive duration");
   prior=s.endMs;joined.append(s.text);
  }
  chk(joined.toString().equals(sample+sample2),"all text including tail preserved");
  chk(split.get(split.size()-1).endMs==59650,"last timestamp retained");
  chk(TranscriptLayout006.splitForReading(split).size()==split.size(),"split idempotent");
  chk(TranscriptLayout006.splitForReading(Collections.emptyList()).isEmpty(),"empty safe");
  String phrases="如果讓AI自己辨識然後做出標點符號，這樣不知道可不可行？";
  List<TimedTranscript005.Segment> shortS=TranscriptLayout006.splitForReading(Arrays.asList(new TimedTranscript005.Segment(0,8000,phrases)));
  StringBuilder reconstruct=new StringBuilder();for(TimedTranscript005.Segment s:shortS)reconstruct.append(s.text);
  chk(reconstruct.toString().equals(phrases),"punctuation & speech preserved");
  List<Caption> input=Arrays.asList(new Caption(0,5000,"我現在要繼續測試這個組織稿"),new Caption(7000,12000,"然後我們再繼續試看看"),new Caption(20000,26000,"最後的句子不要漏掉"));
  List<TimedTranscript005.Segment> output=TimedTranscript005.fromCaptions(input);
  chk(TimedTranscript005.text(output).contains("最後的句子不要漏掉"),"no lost ending");
  System.out.println("TEST_006 PASS: readable short paragraphs, all characters, idempotent, timestamps, no lost tail");
 }
}

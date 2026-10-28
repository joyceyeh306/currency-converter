import java.io.*;
import java.util.*;
import tw.ajo.autosubtitles.Caption;
import tw.ajo.record.TimedTranscript005;
public class TEST_005 {
 static Caption c(long start,long end,String t){return new Caption(start,end,t);}
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 public static void main(String[] args) throws Exception {
  List<Caption> caps=Arrays.asList(
   c(0,3100,"我現在要繼續測試這個組織稿"),
   c(3300,5700,"那我現在在玩那個做壽司的遊戲"),
   c(7100,9200,"然後背後有吸塵器"),
   c(9400,13100,"掃地機器人在吸東西所以應該會有一點噪音"),
   c(15000,17000,"我們再繼續試看看"),
   c(17200,20000,"雖然感覺變適的速度還是比較慢"),
   c(21000,28000,"如果讓AI自己辨識然後做出標點符號這樣不知道可不可行"),
   c(29000,36000,"如果我用講的像兜號問號這樣好像又會很奇怪"),
   c(38000,43000,"好了先來讓他辨識看看會不會成功")
  );
  List<TimedTranscript005.Segment> segs=TimedTranscript005.fromCaptions(caps);
  String text=TimedTranscript005.text(segs);
  check(segs.size()>3,"readable segmentation");
  check(text.contains("逐字稿")&&text.contains("辨識")&&text.contains("逗號"),"known corrections");
  check(text.contains("好了先來讓他辨識看看會不會成功"),"no lost last utterance");
  check(text.contains("掃地機器人在吸東西"),"no truncated middle");
  check(text.contains("\n\n"),"paragraphs");
  check(segs.get(0).startMs==0,"first timestamp");
  check(segs.get(segs.size()-1).endMs==43000,"last timestamp");
  File f=File.createTempFile("record005", ".tsv");
  try {TimedTranscript005.write(f,segs);List<TimedTranscript005.Segment> read=TimedTranscript005.read(f);
   check(read.size()==segs.size(),"timing count persisted");
   check(TimedTranscript005.text(read).equals(text),"timing text roundtrip");
   check(TimedTranscript005.indexAt(read,40000)==read.size()-1,"seek last paragraph");
  }finally{f.delete();}
  StringBuilder longText=new StringBuilder();for(int i=0;i<200;i++)longText.append("記錄");
  List<TimedTranscript005.Segment> longSeg=TimedTranscript005.fromCaptions(Arrays.asList(c(0,20000,longText.toString())));
  check(longSeg.size()>=4,"long caption split");
  check(TimedTranscript005.text(longSeg).replaceAll("[。！？，\n ]","").equals(longText.toString()),"all characters preserved");
  check(TimedTranscript005.fromCaptions(Collections.emptyList()).isEmpty(),"empty transcript");
  System.out.println("TEST_005 PASS: timing, readability, last words, sync positions and TSV persistence");
 }
}
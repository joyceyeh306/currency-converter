import java.util.*;
import tw.ajo.autosubtitles.Caption;
import tw.ajo.record.TranscriptFormatter004;
public class TEST_004 {
  static Caption cap(long a,long b,String t){return new Caption(a,b,t);}
  static void check(boolean yes,String what){if(!yes)throw new AssertionError(what);}
  public static void main(String[] args) {
    List<Caption> sample=Arrays.asList(
      cap(0,2600,"我現在要繼續測試這個組織稿"),
      cap(3800,7500,"那我現在在玩那個做壽司的遊戲"),
      cap(7800,8800,"然後背後有吸塵器"),
      cap(9600,13000,"掃地機器人在吸東西所以應該會有一點噪音"),
      cap(15000,17000,"雖然感覺變適的速度"),
      cap(17200,18000,"還是比較慢"),
      cap(20000,21600,"如果"),
      cap(21700,24000,"讓AI自己辨識"),
      cap(25000,26300,"做出標點符號"),
      cap(27300,29500,"這樣不知道可不可行"),
      cap(32200,35500,"如果我用講的像兜號問號這樣好像又會很奇怪"),
      cap(39000,42200,"好了先來讓他辨識看看會不會成功"));
    String cleaned=TranscriptFormatter004.format(sample);
    check(cleaned.contains("逐字稿"),"known spoken transcript term");
    check(cleaned.contains("辨識的速度"),"contextual term near 速度");
    check(cleaned.contains("逗號問號"),"contextual term near 問號");
    check(cleaned.contains("如果讓AI自己辨識"),"do not separate dangling conjunction");
    check(cleaned.contains("，")&&cleaned.contains("。"),"punctuation");
    check(cleaned.contains("\n\n"),"pause paragraph");
    check(TranscriptFormatter004.format(Arrays.asList(cap(0,1200,"這樣可不可行"))).endsWith("？"),"question");
    String speech=TranscriptFormatter004.format(Arrays.asList(cap(0,300,"嗯"),cap(300,800,"啊"),cap(800,1200,"呀")));
    check(speech.contains("嗯")&&speech.contains("啊")&&speech.contains("呀"),"preserve oral particles");
    check(TranscriptFormatter004.format(Arrays.asList(cap(0,1000,"這份組織稿是研究"))).contains("組織稿"),"no unrelated corrections");
    check(TranscriptFormatter004.format(Collections.emptyList()).isEmpty(),"empty");
    System.out.println("TEST_004 PASS: context, punctuation, pauses, question, oral particles");
  }
}
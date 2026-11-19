package tw.ajo.phototext;
import java.util.*;
public final class FuzzyTextTest {
 static int count=0;static void c(boolean b,String msg){count++;if(!b)throw new AssertionError(msg);}
 static int quality(String h,String q){FuzzyText.Hit x=FuzzyText.best(h,q);return x==null?0:x.quality;}
 public static void main(String[] args){
  c(quality("前文已經完成","已經")==2,"exact");
  c(quality("前文己經完成","已經")==1,"similar");
  c(quality("己","已")==0,"single exact");
  c(quality("台灣電力公司","台灣電利")==1,"substitution");
  c(quality("台灣電力公司","台灣電力公同")==1,"long substitution");
  c(quality("台灣力公司","台灣電力")==1,"missing");
  c(quality("台灣大電力公司","台灣電力")==1,"inserted");
  c(quality("台灣電力","台北")==0,"short unrelated");
  c(quality("abcdef","abcdeg")==1,"latin");
  c(quality("0912-345-678","0912345678")==1,"phone");
  c(quality("2026/11/19","20261119")==1,"date");
  c(quality("O912-345-678".toLowerCase(Locale.ROOT),"0912345678")==1,"letter error");
  c(quality("0912-345-679","0912345678")==0,"wrong digit");
  c(quality("oooo","0000")==0,"letters not numbers");
  c(quality("123","124")==0,"short digits");
  c(FuzzyText.terms("0912 345 678",MatchText::norm,true).size()==1,"numeric as phrase");
  c(FuzzyText.terms("電費 台電",MatchText::norm,true).size()==2,"multi");
  FuzzyText.Score s=FuzzyText.score("台灣電利公司電費",List.of("台灣電力","電費"),false);
  c(s.accepted&&s.approximate(false)&&s.exact==1,"AND mixed");
  c(!FuzzyText.score("台灣電利公司",List.of("台灣電力","電費"),false).accepted,"AND reject");
  s=FuzzyText.score("台灣電利公司電費",List.of("台灣電力","電費"),true);
  c(s.accepted&&!s.approximate(true),"OR exact wins");
  MatchText.Segment a=FuzzyText.full("前文 台灣電利 公司 電費",List.of("台灣電力","電費"),MatchText::norm,true);
  c(a.hits.size()==2&&a.hits.get(0).approximate&&!a.hits.get(1).approximate,"highlight quality");
  c(a.text.substring(a.hits.get(0).start,a.hits.get(0).end).equals("台灣電利"),"original range");
  a=FuzzyText.snippet("很長前文很多很多很多很多很多內容電話 0912-345-678 請聯絡",List.of("0912345678"),MatchText::norm,true);
  c(a.hits.size()==1&&a.hits.get(0).approximate&&a.text.substring(a.hits.get(0).start,a.hits.get(0).end).equals("0912-345-678"),"numeric snippet");
  c(FuzzyText.full("己經",List.of("已經"),MatchText::norm,false).hits.isEmpty(),"off");
  System.out.println("FuzzyText: "+count+" cases passed");
 }
}
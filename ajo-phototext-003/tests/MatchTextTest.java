package tw.ajo.phototext;
public class MatchTextTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void hit(MatchText.Segment s,int i,String expected){MatchText.Range r=s.hits.get(i);check(s.text.substring(r.start,r.end).equals(expected),"unexpected hit "+s.text.substring(r.start,r.end));}
 public static void main(String[] args){
  MatchText.Segment s=MatchText.snippet("前面無關的內容很多很多很多很多很多很多很多。病患姓名：測試甲，測試甲，後續內容","測試甲");check(s.text.startsWith("…"),"prefix");hit(s,0,"測試甲");hit(s,1,"測試甲");check(s.hits.get(0).start<=7,"match near start");
  s=MatchText.full("前文 ＡＢＣ\n１２３ 後文","abc123");hit(s,0,"ＡＢＣ\n１２３");
  s=MatchText.snippet("其他內容\n台\n電 繳款","台電");hit(s,0,"台 電");
  s=MatchText.full("Cafe\u0301 CAFÉ","café");hit(s,0,"Cafe\u0301");hit(s,1,"CAFÉ");
  s=MatchText.full("甲甲甲","甲甲");hit(s,0,"甲甲甲");
  s=MatchText.snippet("🍀🍀🍀🍀🍀🍀🍀🍀目標文字","目標");hit(s,0,"目標");check(!Character.isLowSurrogate(s.text.charAt(1)),"surrogate");
  check(MatchText.full("abc","").hits.isEmpty(),"empty");check(MatchText.full("abc","zz").hits.isEmpty(),"absent");
  s=MatchText.full("前文 ﬃ 後文","ffi");hit(s,0,"ﬃ");
  System.out.println("MatchText: 9 keyword context, normalization and range cases passed");
 }
}
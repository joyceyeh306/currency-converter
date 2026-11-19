package tw.ajo.phototext;
import java.util.*;
import java.util.function.UnaryOperator;
public final class SearchSpecTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  // Inject deterministic test mapping; Android ICU itself is tested on device.
  UnaryOperator<String> fold=s->MatchText.norm(s).replace("醫","医").replace("電","电").replace("費","费").replace("腦","脑");
  SearchSpec x=new SearchSpec("電費 醫療",false,"camera",100,200,false,3);SearchSpec.Where w=x.where(fold);
  check(w.sql.contains(" AND instr")&&w.sql.contains("p.kind")&&w.sql.contains("p.date_ms>=?")&&w.sql.contains("p.date_ms<?"),"all");
  check(Arrays.equals(w.args,new String[]{"电费","医療","camera","100","200"}),"binding");
  x=new SearchSpec("電費＋醫療",true,"album:external|DCIM/Camera/",0,0,true,0);w=x.where(fold);
  check(w.sql.contains(" OR ")&&w.sql.contains("p.album_key=?"),"any");
  check(x.order().contains("p.date_ms ASC"),"old");
  check(SearchSpec.defaults("",false).where(fold).sql.equals("1=1"),"empty");
  check(SearchSpec.terms("電費 电费",fold).size()==1,"dedup");
  MatchText.Segment s=MatchText.full("電費 123 醫療",SearchSpec.terms("电费 医療",fold),fold);
  check(s.hits.size()==2&&s.text.substring(s.hits.get(0).start,s.hits.get(0).end).equals("電費"),"original highlight");
  s=MatchText.snippet("這裡前文很多很多很多，電腦和電費",SearchSpec.terms("电脑 电费",fold),fold);
  check(s.hits.size()==2&&s.text.contains("電腦"),"snippet");
  System.out.println("SearchSpec: 8 cases passed");
 }
}
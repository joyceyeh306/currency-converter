package tw.ajo.phototext;
import java.util.*;
import java.util.function.UnaryOperator;
final class SearchSpec {
 final String query,album;final boolean any,older;final long from,to;final int dateMode;
 SearchSpec(String q,boolean a,String al,long f,long t,boolean o,int dm){query=q==null?"":q;any=a;album=al==null?"":al;from=f;to=t;older=o;dateMode=dm;}
 static SearchSpec defaults(String q,boolean any){return new SearchSpec(q,any,"",0,0,false,0);}
 SearchSpec withQuery(String q,boolean a){return new SearchSpec(q,a,album,from,to,older,dateMode);}
 boolean filtered(){return !album.isEmpty()||from>0||to>0;}
 boolean changed(){return filtered()||older;}
 static ArrayList<String> terms(String q,UnaryOperator<String> fold){
  LinkedHashSet<String> out=new LinkedHashSet<>();
  if(q!=null)for(String s:q.split("[\\s\\p{Z}＋]+")){String n=fold.apply(s);if(!n.isEmpty())out.add(n);}
  return new ArrayList<>(out);
 }
 static final class Where {final String sql;final String[] args;Where(String s,ArrayList<String> a){sql=s;args=a.toArray(new String[0]);}}
 Where where(UnaryOperator<String> fold){
  ArrayList<String> a=new ArrayList<>();ArrayList<String> clauses=new ArrayList<>();ArrayList<String> terms=terms(query,fold);
  if(!terms.isEmpty()){ArrayList<String> ps=new ArrayList<>();for(String term:terms){ps.add("instr(r.folded,?)>0");a.add(term);}clauses.add("r.error='' AND ("+String.join(any?" OR ":" AND ",ps)+")");}
  if(album.equals("camera")||album.equals("screens")){clauses.add("p.kind=?");a.add(album);}
  else if(album.startsWith("album:")){clauses.add("p.album_key=?");a.add(album.substring(6));}
  if(from>0){clauses.add("p.date_ms>=?");a.add(String.valueOf(from));}
  if(to>0){clauses.add("p.date_ms<?");a.add(String.valueOf(to));}
  return new Where(clauses.isEmpty()?"1=1":String.join(" AND ",clauses),a);
 }
 String order(){return "CASE WHEN COALESCE(p.date_ms,0)>0 THEN 0 ELSE 1 END, p.date_ms "+(older?"ASC":"DESC")+", r.updated DESC, r.uri, r.mode";}
}

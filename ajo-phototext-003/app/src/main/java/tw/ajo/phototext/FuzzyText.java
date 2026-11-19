package tw.ajo.phototext;
import java.util.*;
import java.util.function.UnaryOperator;

final class FuzzyText {
 static final int NONE=0,NEAR=1,EXACT=2;
 static final class Hit{final int start,end,quality;Hit(int s,int e,int q){start=s;end=e;quality=q;}}
 static final class Score{
  final int exact,near,total;final boolean accepted;
  Score(int e,int n,int t,boolean any){exact=e;near=n;total=t;accepted=t==0||(any?e+n>0:e+n==t);}
  // An exact AND/OR match always precedes any row which needs a near match.
  boolean approximate(boolean any){return any?exact==0&&near>0:near>0;}
  int tier(boolean any){return approximate(any)?1:0;}
 }
 static boolean digit(char c){return c>='0'&&c<='9';}
 static boolean separator(char c){return "-‐‑‒–—−/／.．()（）·:：".indexOf(c)>=0;}
 static char number(char c){switch(c){case 'o':return '0';case 'i':case 'l':return '1';case 's':return '5';case 'b':return '8';case 'z':return '2';default:return c;}}
 static char similar(char c){switch(c){case '己':case '已':case '巳':return '己';case '未':case '末':return '未';case '土':case '士':return '土';case '日':case '曰':return '日';case '目':case '且':return '目';case 'o':case '0':return '0';case 'i':case 'l':case '1':return '1';default:return c;}}
 static boolean numeric(String s){int n=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(digit(c))n++;else if(!separator(c))return false;}return n>=4;}
 static String compact(String s){StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++)if(!separator(s.charAt(i)))b.append(s.charAt(i));return b.toString();}
 static ArrayList<String> terms(String q,UnaryOperator<String> fold,boolean fuzzy){
  if(fuzzy&&q!=null){String n=fold.apply(q);if(numeric(n))return new ArrayList<>(Collections.singletonList(n));}
  return SearchSpec.terms(q,fold);
 }
 static boolean eligible(String q){if(q.length()<4||q.length()>80)return false;boolean han=true,latin=true;for(int i=0;i<q.length();i++){char c=q.charAt(i);han&=Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN;latin&=c>='a'&&c<='z';}return han||latin;}
 static boolean sameScript(char x,char y){boolean a=Character.UnicodeScript.of(x)==Character.UnicodeScript.HAN,b=Character.UnicodeScript.of(y)==Character.UnicodeScript.HAN;return a&&b||x>='a'&&x<='z'&&y>='a'&&y<='z';}
 static boolean closeAt(String hay,int pos,String q,int len){
  if(pos<0||len<=0||pos+len>hay.length())return false;
  int a=0,b=0,edits=0;while(a<q.length()&&b<len){
   char x=q.charAt(a),y=hay.charAt(pos+b);if(x==y){a++;b++;continue;}
   if(++edits>1)return false;
   if(len==q.length()){if(!sameScript(x,y))return false;a++;b++;}
   else if(len>q.length()){if(!sameScript(x,y))return false;b++;}
   else{if(!sameScript(x,y))return false;a++;}
  }
  edits+=(q.length()-a)+(len-b);return edits<=1;
 }
 static Hit numericHit(String hay,String q){
  String needle=compact(q);if(needle.length()<4)return null;
  int n=needle.length();
  for(int i=0;i<hay.length();i++){
   if(number(hay.charAt(i))!=needle.charAt(0))continue;
   int j=i,k=0,actual=0,conf=0,sep=0;
   while(j<hay.length()&&k<n){char c=hay.charAt(j++);
    if(separator(c)){if(++sep>n+3)break;continue;}
    if(number(c)!=needle.charAt(k))break;
    if(digit(c))actual++;else conf++;
    k++;
   }
   if(k==n&&actual>=Math.max(3,n-1)&&conf<=1)return new Hit(i,j,NEAR);
  }return null;
 }
 static Hit best(String hay,String q){
  if(q.isEmpty()||hay.isEmpty())return null;
  int ex=hay.indexOf(q);if(ex>=0)return new Hit(ex,ex+q.length(),EXACT);
  if(numeric(q))return numericHit(hay,q);
  if(q.length()>=2&&q.length()<=80){
   for(int i=0;i+q.length()<=hay.length();i++){
    int changes=0,k=0;for(;k<q.length();k++){char a=q.charAt(k),b=hay.charAt(i+k);if(a==b)continue;if(similar(a)!=similar(b)||++changes>1)break;}
    if(k==q.length()&&changes==1)return new Hit(i,i+k,NEAR);
   }
  }
  if(!eligible(q))return null;
  // At edit distance one at least one of the two halves is unchanged.
  int half=q.length()/2;
  for(int side=0;side<2;side++){
   int offset=side==0?0:half;String anchor=side==0?q.substring(0,half):q.substring(half);
   for(int p=hay.indexOf(anchor);p>=0;p=hay.indexOf(anchor,p+1)){
    for(int shift=-1;shift<=1;shift++){int at=p-offset+shift;
     for(int len=q.length()-1;len<=q.length()+1;len++)if(closeAt(hay,at,q,len))return new Hit(at,at+len,NEAR);
    }
   }
  }return null;
 }
 static Score score(String hay,List<String> terms,boolean any){
  int e=0,n=0;for(String q:terms){Hit h=best(hay,q);if(h!=null){if(h.quality==EXACT)e++;else n++;}else if(!any)return new Score(e,n,terms.size(),false);}
  return new Score(e,n,terms.size(),any);
 }
 static MatchText.Segment full(String raw,List<String> terms,UnaryOperator<String> fold,boolean enabled){
  if(!enabled)return MatchText.full(raw,terms,fold);
  String s=raw==null?"":raw,hay=fold.apply(s);ArrayList<MatchText.Range> ranges=new ArrayList<>();
  for(String q:terms){
   ArrayList<MatchText.Range> exact=MatchText.find(s,q,fold);if(!exact.isEmpty()){ranges.addAll(exact);continue;}
   Hit hit=best(hay,q);if(hit==null)continue;
   String candidate=hay.substring(hit.start,hit.end);
   for(MatchText.Range x:MatchText.find(s,candidate,fold))ranges.add(new MatchText.Range(x.start,x.end,true));
  }
  ranges.sort(Comparator.comparingInt((MatchText.Range x)->x.start).thenComparingInt(x->x.end));
  ArrayList<MatchText.Range> merged=new ArrayList<>();for(MatchText.Range x:ranges){if(!merged.isEmpty()&&merged.get(merged.size()-1).end>x.start){MatchText.Range p=merged.remove(merged.size()-1);merged.add(new MatchText.Range(p.start,Math.max(p.end,x.end),p.approximate&&x.approximate));}else merged.add(x);}
  return new MatchText.Segment(s,merged);
 }
 static MatchText.Segment snippet(String raw,List<String> terms,UnaryOperator<String> fold,boolean enabled){
  if(!enabled)return MatchText.snippet(raw,terms,fold);
  String plain=(raw==null?"":raw).replaceAll("[\\s\\p{Z}]+"," ").trim();
  MatchText.Segment all=full(plain,terms,fold,true);
  int start=0,end=MatchText.after(plain,0,46);
  if(!all.hits.isEmpty()){MatchText.Range first=all.hits.get(0);start=MatchText.before(plain,first.start,6);end=MatchText.after(plain,first.end,32);}
  String prefix=start>0?"…":"",suffix=end<plain.length()?"…":"";ArrayList<MatchText.Range> out=new ArrayList<>();
  for(MatchText.Range x:all.hits){int a=Math.max(x.start,start),b=Math.min(x.end,end);if(a<b)out.add(new MatchText.Range(a-start+prefix.length(),b-start+prefix.length(),x.approximate));}
  return new MatchText.Segment(prefix+plain.substring(start,end)+suffix,out);
 }
}

package tw.ajo.phototext;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

final class MatchText {
 static final class Range {
  final int start,end;final boolean approximate;
  Range(int s,int e){this(s,e,false);}
  Range(int s,int e,boolean a){start=s;end=e;approximate=a;}
 }
 static final class Segment {
  final String text;
  final ArrayList<Range> hits;
  Segment(String t,ArrayList<Range> h){text=t;hits=h;}
 }
 // Same search normalization as IndexStore.norm; returned offsets remain in original UTF-16.
 static String norm(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+","");}
 static ArrayList<Range> find(String original,String query){return find(original,query,MatchText::norm);}
 static ArrayList<Range> find(String original,String query,java.util.function.UnaryOperator<String> fold){
  String needle=fold.apply(query), hay=fold.apply(original);
  ArrayList<Range> result=new ArrayList<>();
  if(needle.isEmpty()||hay.isEmpty()||!hay.contains(needle))return result;
  int[] start=new int[hay.length()],end=new int[hay.length()];
  BreakIterator iterator=BreakIterator.getCharacterInstance(Locale.ROOT);iterator.setText(original);
  int a=iterator.first(),at=0;
  for(int b=iterator.next();b!=BreakIterator.DONE;a=b,b=iterator.next()){
   int n=fold.apply(original.substring(a,b)).length();
   if(at+n>hay.length()){at=-1;break;}
   for(int k=0;k<n;k++){start[at]=a;end[at]=b;at++;}
  }
  // Rare context-sensitive normalization: reconstruct boundaries using normalized prefixes.
  if(at!=hay.length()){
   iterator.first();a=0;at=0;
   for(int b=iterator.next();b!=BreakIterator.DONE;a=b,b=iterator.next()){
    int n=Math.min(hay.length(),fold.apply(original.substring(0,b)).length());
    while(at<n){start[at]=a;end[at]=b;at++;}
   }
   if(at!=hay.length())return result;
  }
  for(int p=hay.indexOf(needle);p>=0;p=hay.indexOf(needle,p+1)){
   int s=start[p],e=end[p+needle.length()-1];if(e<=s)continue;
   if(!result.isEmpty()&&result.get(result.size()-1).end>s){
    Range prior=result.remove(result.size()-1);result.add(new Range(prior.start,Math.max(prior.end,e)));
   }else result.add(new Range(s,e));
  }return result;
 }
 static Segment full(String original,String query){String s=original==null?"":original;return new Segment(s,find(s,query));}
 static int before(String s,int offset,int n){return s.offsetByCodePoints(offset,-Math.min(n,s.codePointCount(0,offset)));}
 static int after(String s,int offset,int n){return s.offsetByCodePoints(offset,Math.min(n,s.codePointCount(offset,s.length())));}
 static Segment snippet(String original,String query){return snippet(original,java.util.Collections.singletonList(query),MatchText::norm);}
 static ArrayList<Range> all(String text,java.util.List<String> terms,java.util.function.UnaryOperator<String> fold){ArrayList<Range> hits=new ArrayList<>();for(String q:terms)hits.addAll(find(text,q,fold));hits.sort(java.util.Comparator.comparingInt((Range a)->a.start).thenComparingInt(a->a.end));ArrayList<Range> merged=new ArrayList<>();for(Range r:hits){if(!merged.isEmpty()&&merged.get(merged.size()-1).end>r.start){Range p=merged.remove(merged.size()-1);merged.add(new Range(p.start,Math.max(p.end,r.end)));}else merged.add(r);}return merged;}
 static Segment full(String original,java.util.List<String> terms,java.util.function.UnaryOperator<String> fold){String s=original==null?"":original;return new Segment(s,all(s,terms,fold));}
 static Segment snippet(String original,java.util.List<String> terms,java.util.function.UnaryOperator<String> fold){
  String plain=(original==null?"":original).replaceAll("[\\s\\p{Z}]+"," ").trim();
  ArrayList<Range> hits=all(plain,terms,fold);
  int start=0,end=after(plain,0,46);
  if(!hits.isEmpty()){
   Range first=hits.get(0);
   start=before(plain,first.start,6);end=after(plain,first.end,32);
  }
  String prefix=start>0?"…":"",suffix=end<plain.length()?"…":"";
  String snippet=prefix+plain.substring(start,end)+suffix;
  ArrayList<Range> shifted=new ArrayList<>();
  for(Range r:hits){int a=Math.max(start,r.start),b=Math.min(end,r.end);if(a<b)shifted.add(new Range(a-start+prefix.length(),b-start+prefix.length()));}
  return new Segment(snippet,shifted);
 }
}

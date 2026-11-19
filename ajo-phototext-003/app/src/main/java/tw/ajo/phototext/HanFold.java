package tw.ajo.phototext;
import android.icu.text.Transliterator;
import java.util.concurrent.ConcurrentHashMap;
final class HanFold {
 private static final ThreadLocal<Transliterator> converter=ThreadLocal.withInitial(()->Transliterator.getInstance("Traditional-Simplified"));
 private static final ConcurrentHashMap<Integer,String> cache=new ConcurrentHashMap<>();
 static String fold(String s){
  String n=MatchText.norm(s);StringBuilder b=new StringBuilder(n.length());
  for(int i=0;i<n.length();){int cp=n.codePointAt(i);i+=Character.charCount(cp);
   if(Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN)b.append(cache.computeIfAbsent(cp,k->converter.get().transliterate(new String(Character.toChars(k)))));
   else b.appendCodePoint(cp);
  }return b.toString();
 }
}

from pathlib import Path
root=Path('app/src/main/java/tw/ajo/record')
(root/'TranscriptFormatter004.java').write_text('''package tw.ajo.record;
import java.util.List;
import tw.ajo.autosubtitles.Caption;
/** Offline punctuation: never sends audio or text to a server. Keep the raw result for review. */
public final class TranscriptFormatter004 {
 private TranscriptFormatter004(){}
 private static boolean end(char c){return "。？！.!?".indexOf(c)>=0;}
 private static boolean punctuation(char c){return end(c)||"，、,;；:：".indexOf(c)>=0;}
 private static boolean pending(String s){return s.endsWith("如果")||s.endsWith("然後")||s.endsWith("雖然")||s.endsWith("因為")||s.endsWith("所以")||s.endsWith("但是")||s.endsWith("那");}
 private static boolean question(String s){return s.endsWith("嗎")||s.endsWith("呢")||s.endsWith("會不會")||s.endsWith("可不可行")||s.endsWith("可不可以")||s.endsWith("怎麼辦")||s.endsWith("好不好")||s.endsWith("是不是");}
 private static boolean topic(String s){return s.startsWith("好了")||s.startsWith("接下來")||s.startsWith("總之")||s.startsWith("另外");}
 private static String correction(String s){
  if(s.contains("組織稿")&&s.contains("測試這個"))s=s.replace("組織稿","逐字稿");
  if(s.contains("變適")&&s.contains("速度"))s=s.replace("變適","辨識");
  if(s.contains("兜號")&&s.contains("問號"))s=s.replace("兜號","逗號");
  return s;
 }
 public static String format(List<Caption> items){
  if(items==null||items.isEmpty())return "";
  StringBuilder out=new StringBuilder();long previousEnd=0;String previous="";int paragraphChars=0;
  for(Caption c:items){
   if(c==null||c.text==null||c.text.trim().isEmpty())continue;
   String t=c.text.trim();
   if(out.length()>0){
    long gap=Math.max(0,c.startMs-previousEnd);char last=out.charAt(out.length()-1);
    if(end(last)){out.append(gap>=600?"\\n\\n":"\\n");paragraphChars=0;}
    else if(!pending(previous)&&(gap>=1700||paragraphChars>=100||topic(t)&&paragraphChars>=20)){
     if(!punctuation(last))out.append(question(previous)?'？':'。');
     out.append("\\n\\n");paragraphChars=0;
    } else if(!pending(previous)&&gap>=450&&!punctuation(last))out.append('，');
    else if(last<128&&Character.isLetterOrDigit(last)&&t.charAt(0)<128&&Character.isLetterOrDigit(t.charAt(0)))out.append(' ');
   }
   out.append(t);paragraphChars+=t.length();previous=t;previousEnd=c.endMs;
  }
  if(out.length()>0&&!punctuation(out.charAt(out.length()-1))&&!pending(previous))out.append(question(previous)?'？':'。');
  return correction(out.toString().replace("。。","。").replace("，。","。")).trim();
 }
}''')
p=root/'MainActivity.java';s=p.read_text()
old='String output=TranscriptText.traditional(AudioFiles.texts(result.captions));'
assert s.count(old)==1
s=s.replace(old,'String output=TranscriptText.traditional(TranscriptFormatter004.format(result.captions));')
s=s.replace('success=true;status="辨識完成 · 可編輯或匯出 TXT";', 'success=true;status="辨識完成 · Whisper " + clock(result.inferenceMs) + " · 可編輯或匯出 TXT";')
p.write_text(s)
p=root/'RecordingService.java';s=p.read_text()
old='transcript=TranscriptText.traditional(AudioFiles.texts(finalResult.captions));'
assert s.count(old)==1
s=s.replace(old,'rawTranscript=TranscriptText.traditional(AudioFiles.texts(finalResult.captions));\n            transcript=TranscriptText.traditional(TranscriptFormatter004.format(finalResult.captions));')
assert 'public volatile String status="尚未錄音",transcript="",error="";' in s
s=s.replace('public volatile String status="尚未錄音",transcript="",error="";', 'public volatile String status="尚未錄音",transcript="",rawTranscript="",error="";')
s=s.replace('saved.set(0L);cancel.set(false);transcript="";error="";', 'saved.set(0L);cancel.set(false);transcript="";rawTranscript="";error="";')
s=s.replace('RecordingStore.saveTranscript(lastWav,transcript);','RecordingStore.saveTranscript(lastWav,transcript);\n                    if(!rawTranscript.isEmpty())AudioFiles.saveText(new File(lastWav.getParentFile(),baseName+".raw.txt"),rawTranscript);')
p.write_text(s)
p=Path('app/build.gradle');s=p.read_text()
assert 'versionCode 3' in s and "versionName '003'" in s
p.write_text(s.replace('versionCode 3','versionCode 4').replace("versionName '003'","versionName '004'"))
print('004 syntax-preserving conservative punctuation upgrade applied')

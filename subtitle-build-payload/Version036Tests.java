import tw.ajo.autosubtitles.Caption;
import tw.ajo.autosubtitles.SubtitleDraftStore;
import java.nio.file.*;
import java.io.File;
import java.util.*;
import java.util.Base64;
public final class Version036Tests{
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
  Caption a=new Caption(0,5000,"爸爸新年快樂"),b=new Caption(1000,2200,"媽媽新年快樂");
  a.setPosition(.4f,.64f);a.setSizeMultiplier(1.7f);a.setAngleOverride(12f);
  a.setFontOverride(2);a.setColorOverride(0xFF81DAED);
  check(b.sizeMultiplier==null&&b.angleOverride==null&&b.fontOverride==null&&b.colorOverride==null,"B unaffected by A");
  Caption copy=a.copy();check(copy.sizeMultiplier.equals(1.7f)&&copy.fontOverride==2&&copy.angleOverride.equals(12f)&&copy.colorOverride==0xFF81DAED,"Export copy must preserve local style");
  File tmp=Files.createTempDirectory("ajo036").toFile();String key="b".repeat(64);
  SubtitleDraftStore.save(tmp,key,Arrays.asList(a,b));
  List<Caption> restored=SubtitleDraftStore.load(tmp,key);
  check(restored.get(0).hasOwnPosition()&&restored.get(0).angleOverride.equals(12f)&&restored.get(0).fontOverride==2,"New draft restore");
  check(restored.get(1).angleOverride==null,"B draft independent");
  copy.resetLocalStyle();check(!copy.hasOwnPosition()&&copy.fontOverride==null&&copy.sizeMultiplier==null&&copy.angleOverride==null&&copy.colorOverride==null,"Single caption reset");
  check(a.hasOwnPosition(),"Reset does not affect original");
  String val=Base64.getEncoder().encodeToString("舊字幕".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  Files.writeString(new File(tmp,key+".captions").toPath(),"AJO_CAPTIONS_035_V2\n0\t2000\t\t"+val+"\t0.5\t0.75\n");
  check(SubtitleDraftStore.load(tmp,key).get(0).hasOwnPosition(),"035 draft compatible");
  Files.writeString(new File(tmp,key+".captions").toPath(),"AJO_CAPTIONS_028_V1\n0\t2000\t\t"+val+"\n");
  check(!SubtitleDraftStore.load(tmp,key).get(0).hasOwnPosition(),"028 draft compatible");
  Path path=Path.of("app/src/main/java/tw/ajo/autosubtitles/");
  String ui=Files.readString(path.resolve("MainActivity.java"));
  String scene=Files.readString(path.resolve("SubtitleScene.java"));
  String preview=Files.readString(path.resolve("SubtitlePreviewView.java"));
  String export=Files.readString(path.resolve("SubtitleExporter.java"));
  int play=ui.indexOf("Button play=button("),del=ui.indexOf("Button del=button("),
  split=ui.indexOf("Button split=button("),merge=ui.indexOf("Button merge=button("),
  time=ui.indexOf("editingStartLabel=addTimeStepper(editingBody"),
  appearance=ui.indexOf("Button toggleStyle=button("),nav=ui.indexOf("Button previous=button(");
  check(play>=0&&play<del&&del<split&&split<merge&&merge<time&&time<appearance&&appearance<nav,"Editor required operation order");
  check(ui.contains("appearance.setVisibility(View.GONE)")&&ui.contains("editingSizeSlider")&&ui.contains("editingAngleSlider")&&ui.contains("singleColor")&&ui.contains("singleFont"),"Four local controls");
  check(ui.contains("gestureCaption.setSizeMultiplier")&&!ui.contains("subtitleAngle=gestureStartAngle+delta"),"Pinch cannot rotate all captions");
  check(scene.contains("c.effectiveScale(scale)")&&scene.contains("c.effectiveColor(color)")&&scene.contains("c.effectiveAngle(angle)")&&scene.contains("c.fontOverride"),"Local appearance scene");
  check(preview.contains("subtitleTypeface,localFaces")&&export.contains("typeface,captionFaces"),"Preview and export parity");
  check(Files.readString(Path.of("app/build.gradle")).contains("versionName '036'"),"Version");
  System.out.println("Version036Tests passed");
 }
}

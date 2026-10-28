import java.io.*;
import java.util.*;
import tw.ajo.record.*;
import tw.ajo.autosubtitles.*;
public class TEST_007 {
 static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  List<SpeakerCaptionizer.Segment> ss=Arrays.asList(
   new SpeakerCaptionizer.Segment("speaker_0","請幫我做逐字稿，好嗎？",0,3000),
   new SpeakerCaptionizer.Segment("speaker_1","當然可以！",3100,5100));
  List<Caption> c=CloudCaption007.convert(ss,6000);
  check(c.size()==2 && c.get(0).text.endsWith("？"),"must retain punctuation");
  check(c.get(1).speaker.equals("speaker_1"),"retain speaker");
  String text=TimedTranscript005.text(CloudTimed007.fromCaptions(c));
  check(text.contains("逐字稿，好嗎？") && text.contains("當然可以！"),"no lost speech");
  File pcm=File.createTempFile("ajo007", ".pcm");
  File wav=File.createTempFile("ajo007", ".wav");
  try{
    byte[] data=new byte[32000];new Random(8).nextBytes(data);
    try(FileOutputStream f=new FileOutputStream(pcm)){f.write(data);}
    AudioFiles.makeWav(pcm,wav);
    File copy=CloudAudio007.toPcm(wav,wav.getParentFile());
    try{check(Arrays.equals(data,java.nio.file.Files.readAllBytes(copy.toPath())),"wav identical");}
    finally{copy.delete();}
  }finally{pcm.delete();wav.delete();}
  System.out.println("007 cloud caption punctuation, speaker, timestamp and WAV roundtrip PASS");
 }
}

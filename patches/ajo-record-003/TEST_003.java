import tw.ajo.record.*;
import java.io.*;
public class TEST_003 {
 static final int RATE=32000;
 static File make(int speechSeconds,int silenceSeconds,int moreSpeechSeconds) throws Exception {
  File f=File.createTempFile("clip003test",".pcm");
  try(OutputStream out=new BufferedOutputStream(new FileOutputStream(f))) {
   for(int sec=0;sec<speechSeconds+silenceSeconds+moreSpeechSeconds;sec++)
    for(int i=0;i<RATE/2;i++) {short v=(short)((sec<speechSeconds||sec>=speechSeconds+silenceSeconds)?7000*Math.sin(i*.1):0);out.write(v&255);out.write((v>>8)&255);}
  }return f;
 }
 static void chk(boolean v,String message){if(!v)throw new AssertionError(message);}
 public static void main(String[] args) throws Exception {
  File f=make(7,2,7);try {
   long end=SpeechChunker003.nextEnd(f,0,9L*RATE,false);
   chk(end>=7L*RATE && end<=9L*RATE,"pause split: "+end);
   chk(SpeechChunker003.nextEnd(f,0,4L*RATE,false)==-1,"early decision");
   chk(SpeechChunker003.nextEnd(f,0,16L*RATE,false)<=16L*RATE,"max cap");
   chk(SpeechChunker003.nextEnd(f,end,16L*RATE,true)==16L*RATE,"finish sample exact");
   chk(!SpeechChunker003.definitelySilent(f),"speech detected");
   File copy=AudioFiles.clip(f,end,16L*RATE-end,f.getParentFile());
   chk(copy.length()==16L*RATE-end,"clip byte length");copy.delete();
  }finally {f.delete();}
  File quiet=make(0,3,0);try{chk(SpeechChunker003.definitelySilent(quiet),"quiet skip");}finally{quiet.delete();}
  File longSpeech=make(18,0,0);try{
   long e=SpeechChunker003.nextEnd(longSpeech,0,16L*RATE,false);
   chk(e==16L*RATE,"continuous speech max cap");
  }finally{longSpeech.delete();}
  System.out.println("003: pause boundary / no short cut / max cap / exact sample coverage / silence / random access clip PASS");
 }
}

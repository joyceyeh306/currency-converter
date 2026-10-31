import java.io.*;
import tw.ajo.record.*;
public final class TEST_012 {
  private static void ok(boolean v,String s){if(!v)throw new AssertionError(s);}
  public static void main(String[] args)throws Exception {
    File f=File.createTempFile("speech012",".pcm");
    try {
      try(OutputStream out=new BufferedOutputStream(new FileOutputStream(f))){
        byte[] frame=new byte[32000];
        for(int i=0;i<frame.length;i+=2){int s=(short)(9000*Math.sin(i*0.024));frame[i]=(byte)s;frame[i+1]=(byte)(s>>8);}
        for(int k=0;k<50;k++)out.write(frame);
      }
      ok(SpeechHealth012.needsRecovery(f,"全精油"),"50 seconds of strong audio and 3 chars should trigger rescue");
      ok(!SpeechHealth012.needsRecovery(f,"這是一段比較完整的逐字稿，不能因為錄音時間長，就將正常內容任意覆蓋。"),"do not replace good transcript");
      File quiet=File.createTempFile("silence012",".pcm");
      try{
        try(OutputStream out=new FileOutputStream(quiet)){byte[] silence=new byte[32000];for(int k=0;k<50;k++)out.write(silence);}
        ok(!SpeechHealth012.needsRecovery(quiet,""),"silence must not cause fabricated text");
      }finally{quiet.delete();}
      System.out.println("Record 012 speech confidence guard PASS");
    }finally{f.delete();}
  }
}
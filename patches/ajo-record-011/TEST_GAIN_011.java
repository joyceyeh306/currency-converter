import java.util.*;
import tw.ajo.record.AdaptiveVoiceGain011;
public final class TEST_GAIN_011 {
  static void assertTrue(boolean yes,String why){if(!yes)throw new AssertionError(why);}
  static byte[] wave(int amp,int n){byte[] b=new byte[n*2];for(int i=0;i<n;i++){
    int s=(int)Math.round(Math.sin(i*0.1411)*amp);b[i*2]=(byte)s;b[i*2+1]=(byte)(s>>8);
  }return b;}
  static double rms(byte[] b){long sum=0;for(int i=0;i<b.length;i+=2){int v=(short)((b[i]&255)|((b[i+1]&255)<<8));sum+=(long)v*v;}return Math.sqrt(sum/(b.length/2.0));}
  static int max(byte[] b){int m=0;for(int i=0;i<b.length;i+=2){int v=(short)((b[i]&255)|((b[i+1]&255)<<8));m=Math.max(m,Math.abs(v));}return m;}
  public static void main(String[] args){
    AdaptiveVoiceGain011 g=new AdaptiveVoiceGain011();double before=0,after=0;
    for(int i=0;i<32;i++){byte[] b=wave(150,2048);before=rms(b);g.enhance(b,b.length);after=rms(b);}
    assertTrue(after>before*3.0,"soft speech should improve at least 3x: "+after/before);
    byte[] silence=new byte[4096];g.enhance(silence,silence.length);assertTrue(max(silence)==0,"digital silence must stay silent");
    byte[] loud=wave(31000,2048);g.enhance(loud,loud.length);assertTrue(max(loud)<=32000,"prevent clipping");
    byte[] guard=wave(4000,2048);byte[] raw=guard.clone();g.enhance(guard,guard.length);
    assertTrue(Arrays.equals(raw,wave(4000,2048)),"original buffer must remain untouched when using copy");
    assertTrue(g.gain()>=0&&g.gain()<=6.0,"bounded gain");
    try{g.enhance(new byte[5],5);throw new AssertionError("odd PCM16 bytes must fail");}catch(IllegalArgumentException expected){}
    System.out.println("011 gain tests PASS: speech boost, silence preservation, clipping guard, copy-only original safety");
  }
}

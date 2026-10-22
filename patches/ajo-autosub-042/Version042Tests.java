import java.util.*;
import tw.ajo.autosubtitles.*;

public final class Version042Tests {
    private static void ok(boolean x,String m){if(!x)throw new AssertionError(m);}
    public static void main(String[] args){
        Caption one=new Caption(1000,3000,"舅舅新年快樂");
        Caption two=new Caption(2400,3550,"新年快樂");
        List<Caption> raw=Arrays.asList(one,two);
        List<Caption> fixed=AndroidCaptionTiming.repair(raw,Collections.emptyList(),0,10000);
        ok(fixed.size()==2,"keep two captions");
        ok(fixed.get(0).endMs>fixed.get(1).startMs,"do not clip provider overlap");
        ok(fixed.get(0).endMs<=3000,"do not fabricate a longer tail");
        ok(one.endMs==3000&&two.startMs==2400,"source remains unchanged");
        List<Caption> disjoint=AndroidCaptionTiming.repair(Arrays.asList(
            new Caption(1000,2000,"爸爸"),new Caption(2500,3500,"媽媽")),Collections.emptyList(),0,10000);
        ok(disjoint.get(0).endMs<=2500,"no unsupported overlap added");
        List<Caption> clip=Arrays.asList(new Caption(0,1200,"上文"),
            new Caption(1400,3400,"舅舅新年快樂"),new Caption(3000,4300,"新年快樂"),
            new Caption(4300,6000,"下文"));
        List<Caption> rec=WhisperReviewCandidates.fromClip(clip,10000,11700,14100,22000);
        ok(rec.size()==2,"select two target phrases, preserve overlaps");
        ok(rec.get(0).startMs==11400&&rec.get(0).endMs==13400,"map clip time back to full video");
        ok(rec.get(1).startMs==13000&&rec.get(1).endMs==14300,"keep second phrase end past target");
        ok(WhisperReviewCandidates.resemblesExisting(rec.get(0),Arrays.asList(
            new Caption(11450,13300,"舅舅新年快樂"))),"overlapping same-text candidate duplicate");
        ok(!WhisperReviewCandidates.resemblesExisting(rec.get(0),Arrays.asList(
            new Caption(4000,5100,"舅舅新年快樂"))),"same words spoken at different time not duplicate");
        ok(rec.size()!=7,"candidate count not artificially fixed to seven");
        System.out.println("Version042Tests passed: provider overlap, original timing, non-overlap, second-pass candidates, duplicate guard");
    }
}

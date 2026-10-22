package tw.ajo.autosubtitles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Optional second-pass suggestions; never mutates an existing caption. */
public final class WhisperReviewCandidates {
    private WhisperReviewCandidates() {}
    public static List<Caption> fromClip(List<Caption> clipCaptions,long clipStart,
                            long targetStart,long targetEnd,long videoDuration) {
        ArrayList<Caption> result=new ArrayList<>();
        if(clipCaptions==null || videoDuration<=0 || targetEnd<=targetStart)return result;
        for(Caption c:clipCaptions){
            if(c==null || c.text==null || c.text.isBlank())continue;
            String t=c.text.trim();
            if(t.length()>120)continue;
            long a=Math.max(0,Math.min(videoDuration,clipStart+c.startMs));
            long b=Math.max(0,Math.min(videoDuration,clipStart+c.endMs));
            if(b-a<180)continue;
            long intersection=Math.min(b,targetEnd)-Math.max(a,targetStart);
            // A caption may stretch slightly beyond the original, overly-short target.
            if(intersection<Math.min(250,Math.max(120,(b-a)/4)))continue;
            result.add(new Caption(a,b,t));
            if(result.size()>=16)break;
        }
        result.sort(Comparator.comparingLong(x->x.startMs));
        return result;
    }
    public static boolean resemblesExisting(Caption proposal,List<Caption> existing){
        if(proposal==null||existing==null)return false;
        String text=normalize(proposal.text);
        if(text.isEmpty())return false;
        for(Caption c:existing){
            if(c==null||!text.equals(normalize(c.text)))continue;
            long overlap=Math.min(proposal.endMs,c.endMs)-Math.max(proposal.startMs,c.startMs);
            long shorter=Math.min(proposal.endMs-proposal.startMs,c.endMs-c.startMs);
            if(overlap>=Math.max(250,shorter/2))return true;
        }
        return false;
    }
    private static String normalize(String in){
        if(in==null)return "";
        StringBuilder b=new StringBuilder();
        for(int i=0;i<in.length();){
            int cp=in.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isLetterOrDigit(cp))b.appendCodePoint(Character.toLowerCase(cp));
        }
        return b.toString();
    }
}

package tw.ajo.autosubtitles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 040 review-only candidate detector. Energy activity is NOT proof of intelligible speech.
 * Existing caption text and timestamps are never changed by this policy.
 */
public final class SubtitleGapReview {
    private SubtitleGapReview() {}
    public static final long MIN_UNCOVERED_MS=650;
    public static final int MAX_CANDIDATES=12;

    public static List<SpeechTiming.Span> find(List<SpeechTiming.Span> activity,
                                               List<Caption> captions,long durationMs) {
        List<SpeechTiming.Span> uncovered=new ArrayList<>();
        if(activity==null || activity.isEmpty() || durationMs<=0) return uncovered;
        ArrayList<SpeechTiming.Span> protectedTimes=new ArrayList<>();
        if(captions!=null) for(Caption c:captions) {
            if(c==null)continue;
            long a=Math.max(0,c.startMs-160), b=Math.min(durationMs,c.endMs+160);
            if(b>a) protectedTimes.add(new SpeechTiming.Span(a,b));
        }
        protectedTimes.sort(Comparator.comparingLong(p->p.startMs));
        for(SpeechTiming.Span active:activity) {
            if(active==null)continue;
            long a=Math.max(0,active.startMs), b=Math.min(durationMs,active.endMs);
            if(b<=a)continue;
            long cursor=a;
            for(SpeechTiming.Span existing:protectedTimes) {
                if(existing.endMs<=cursor)continue;
                if(existing.startMs>=b)break;
                if(existing.startMs-cursor>=MIN_UNCOVERED_MS)
                    uncovered.add(new SpeechTiming.Span(cursor,existing.startMs));
                cursor=Math.max(cursor,Math.min(b,existing.endMs));
                if(cursor>=b)break;
            }
            if(b-cursor>=MIN_UNCOVERED_MS)uncovered.add(new SpeechTiming.Span(cursor,b));
        }
        // Only merge segments when the joining interval is also not protected.
        // Returning the shortest credible gaps prevents unnecessarily re-running long films.
        uncovered.sort(Comparator.comparingLong(p->p.startMs));
        ArrayList<SpeechTiming.Span> result=new ArrayList<>();
        for(SpeechTiming.Span span:uncovered) {
            if(result.size()==MAX_CANDIDATES)break;
            result.add(span);
        }
        return result;
    }

    /** Bounded original-timeline clip with enough acoustic context for Whisper. */
    public static SpeechTiming.Span clip(long startMs,long endMs,long videoMs) {
        if(videoMs<=0||endMs<=startMs)throw new IllegalArgumentException("Invalid review interval");
        long a=Math.max(0,Math.min(videoMs,startMs-1500));
        long b=Math.min(videoMs,Math.max(a+400,endMs+1500));
        if(b-a>18000) {
            long middle=Math.max(0,startMs+(endMs-startMs)/2);
            a=Math.max(0,middle-9000);
            b=Math.min(videoMs,a+18000);
        }
        if(b-a<300)throw new IllegalArgumentException("Review clip too short");
        return new SpeechTiming.Span(a,b);
    }

    /** Whisper returns clip-relative timestamps; never change the original caption times. */
    public static String previewText(List<Caption> transcript,long clipStartMs,long targetStartMs,long targetEndMs) {
        StringBuilder text=new StringBuilder();
        if(transcript==null)return "";
        for(Caption c:transcript) {
            if(c==null || c.text==null || c.text.trim().isEmpty())continue;
            long a=clipStartMs+c.startMs, b=clipStartMs+c.endMs;
            if(Math.min(b,targetEndMs)-Math.max(a,targetStartMs)<Math.min(300,Math.max(120,(b-a)*35/100)))continue;
            String piece=c.text.trim();
            if(piece.length()>120)continue;
            if(text.length()>0)text.append(' ');
            text.append(piece);
            if(text.length()>240)break;
        }
        return text.toString().trim();
    }
}

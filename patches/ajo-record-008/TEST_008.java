import tw.ajo.record.*;
public final class TEST_008 {
    private static void yes(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        SystemTranscript008 s=new SystemTranscript008();
        s.partial("我現在要測試這個");
        yes(s.text().contains("測試"),"partials visible");
        yes(s.stable().isEmpty(),"partial not committed");
        s.commit("我現在要測試這個逐字稿。");
        s.commit("我現在要測試這個逐字稿。");
        yes(s.stable().split("逐字稿",-1).length==2,"exact repeat suppressed");
        s.partial("下一段未確定");
        s.retainPartialOnClose();
        yes(s.stable().contains("下一段未確定"),"partial preserved at close");
        SystemTranscript008 o=new SystemTranscript008();
        o.commit("今天下午到公園散步");o.commit("到公園散步真開心");
        yes(o.stable().contains("真開心"),"overlap retained");
        yes(!TranscriptSafety008.runaway("今天我們吃飯然後去公園散步。"),"normal text accepted");
        yes(TranscriptSafety008.runaway("那你偷偷笑我幾點就走。".repeat(10)),"loop detected");
        System.out.println("008 test PASS: partial/final/no duplicate/overlap/long-loop guard");
    }
}

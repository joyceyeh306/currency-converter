from pathlib import Path
import re
import shutil

# Build on the verified v0.9.5 patch chain. The runner keeps the selected icon
# verification compatible with the embedded 144x144 transparent PNG.
base = Path('.github/scripts/v095_runner.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v095_runner.py', 'exec'), {'__name__': '__main__'})

java_path = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s = java_path.read_text(encoding='utf-8')


def replace_once(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'v0.9.6 patch failed: {label} source not found')
    s = s.replace(old, new, 1)


def sub1(pattern, replacement, label):
    global s
    s2, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'v0.9.6 patch failed for {label}: matched {n}')
    s = s2

# ---------------------------------------------------------------------------
# 1) Dark-mode palette, stable candidate ranking and richer interaction state.
# ---------------------------------------------------------------------------
if 'import android.content.res.Configuration;' not in s:
    s = s.replace('import android.content.SharedPreferences;\n', 'import android.content.SharedPreferences;\nimport android.content.res.Configuration;\n', 1)

state_anchor = '    private Mode temporaryReturnMode = Mode.CANGJIE;\n'
state_add = '''    private Mode temporaryReturnMode = Mode.CANGJIE;\n    private float emojiScrollY = 0f;\n    private float kaoScrollY = 0f;\n    private float panelLastY = 0f;\n    private boolean panelDragging = false;\n    private boolean spaceCursorMode = false;\n    private float spaceCursorLastX = 0f;\n    private float cursorCarry = 0f;\n    private final Map<String,String> continuationFull = new HashMap<>();\n'''
replace_once(state_anchor, state_add, 'interaction state')

learning_anchor = '    private boolean learningEnabled() { return settings.getBoolean("learning_enabled",true); }\n'
color_helpers = r'''    private boolean isDark(){
        String pref=settings.getString("theme_mode","system");
        if("dark".equals(pref)) return true;
        if("light".equals(pref)) return false;
        int night=getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night==Configuration.UI_MODE_NIGHT_YES;
    }
    private int bgColor(){ return isDark()?Color.rgb(30,30,32):Color.rgb(218,219,224); }
    private int keyColor(){ return isDark()?Color.rgb(62,62,65):Color.WHITE; }
    private int textColor(){ return isDark()?Color.rgb(244,244,246):Color.BLACK; }
    private int secondaryTextColor(){ return isDark()?Color.rgb(184,184,190):Color.DKGRAY; }
    private int candidateBgColor(){ return isDark()?Color.rgb(28,28,30):Color.rgb(218,219,224); }
    private int candidateExpandedColor(){ return isDark()?Color.rgb(43,43,46):Color.rgb(232,233,237); }
    private int dividerColor(){ return isDark()?Color.rgb(82,82,86):Color.rgb(198,199,204); }
    private int selectedCandidateColor(){ return isDark()?Color.rgb(76,76,80):Color.rgb(198,199,204); }
'''
replace_once(learning_anchor, learning_anchor + color_helpers, 'dark palette helpers')

s = s.replace('        setBackgroundColor(BG);', '        setBackgroundColor(bgColor());', 1)
s = s.replace('p.setStyle(Paint.Style.FILL); p.setColor(BG); c.drawRect(0,0,getWidth(),getHeight(),p);',
              'p.setStyle(Paint.Style.FILL); p.setColor(bgColor()); c.drawRect(0,0,getWidth(),getHeight(),p);', 1)
s = s.replace('t.setColor(Color.BLACK); t.setTextAlign(Paint.Align.CENTER); t.setTextSize(dp(size));',
              't.setColor(textColor()); t.setTextAlign(Paint.Align.CENTER); t.setTextSize(dp(size));', 1)
s = s.replace('RectF r=rect(x1,y1,x2,y2); p.setColor(Color.WHITE); c.drawRoundRect(r,dp(8),dp(8),p);',
              'RectF r=rect(x1,y1,x2,y2); p.setColor(keyColor()); c.drawRoundRect(r,dp(8),dp(8),p);', 1)

# Lower-left labels use the same visual base as ordinary key text before scaling.
s = s.replace('drawInputKey(c,18,518,145,647,left,22,"NUM","");',
              'drawInputKey(c,18,518,145,647,left,17.68f,"NUM","");', 1)
s = s.replace('drawInputKey(c,18,518,145,647,s,s.length()>3?16:20,"MAIN","");',
              'drawInputKey(c,18,518,145,647,s,17.68f,"MAIN","");', 1)
# Small mode mark follows dark mode too.
s = s.replace('t.setColor(Color.rgb(190,190,194)); t.setTextSize(dp(12*keyScale()));',
              't.setColor(isDark()?Color.rgb(145,145,150):Color.rgb(190,190,194)); t.setTextSize(dp(12*keyScale()));', 1)

# ---------------------------------------------------------------------------
# 2) Chinese-mode punctuation is consistently full-width.
# ---------------------------------------------------------------------------
s = s.replace('private final String[] cn2 = {"-","/",":",";","(",")","$","@","「","」"};',
              'private final String[] cn2 = {"－","／","：","；","（","）","＄","＠","「","」"};', 1)
s = s.replace('private final String[] syC2 = {"_","\\\\","|","~","《","》","¥","&","•"};',
              'private final String[] syC2 = {"＿","＼","｜","～","《","》","￥","＆","•"};', 1)

# Chinese symbol page gets full-width row-1 forms while English keeps ASCII.
old_symbol_loop = '''        for(int i=0;i<10;i++){\n            float x=18+i*116.4f;\n            drawInputKey(c,x,0,x+96,122,sy1[i],17.68f,"PUNCT",sy1[i]);\n        }\n'''
new_symbol_loop = '''        String[] row1=mode==Mode.ENGLISH?sy1:new String[]{"［","］","｛","｝","＃","％","＾","＊","＋","＝"};\n        for(int i=0;i<10;i++){\n            float x=18+i*116.4f;\n            drawInputKey(c,x,0,x+96,122,row1[i],17.68f,"PUNCT",row1[i]);\n        }\n'''
replace_once(old_symbol_loop, new_symbol_loop, 'Chinese symbol row')
s = s.replace('new String[]{"123","…","，","^_^","？","！","\'"};',
              'new String[]{"123","…","，","^_^","？","！","＇"};', 1)

punct_anchor = '    private void commitPunctuation(String s){ svc.commit(s); if(!lastCommitted.isEmpty()) learn(s); lastCommitted=""; page=Page.MAIN; candOff=0; syncComposition(); invalidate(); }\n'
punct_new = r'''    private String normalizeChinesePunctuation(String x){
        if(mode==Mode.ENGLISH||x==null) return x;
        switch(x){
            case ",": return "，"; case ".": return "。"; case "?": return "？"; case "!": return "！";
            case ":": return "："; case ";": return "；"; case "-": return "－"; case "/": return "／";
            case "(": return "（"; case ")": return "）"; case "[": return "［"; case "]": return "］";
            case "{": return "｛"; case "}": return "｝"; case "#": return "＃"; case "%": return "％";
            case "^": return "＾"; case "*": return "＊"; case "+": return "＋"; case "=": return "＝";
            case "_": return "＿"; case "\\": return "＼"; case "|": return "｜"; case "~": return "～";
            case "&": return "＆"; case "@": return "＠"; case "$": return "＄"; case "'": return "＇";
            default: return x;
        }
    }
    private void commitPunctuation(String s){ s=normalizeChinesePunctuation(s); svc.commit(s); if(!lastCommitted.isEmpty()) learn(s); lastCommitted=""; page=Page.MAIN; candOff=0; syncComposition(); invalidate(); }
'''
replace_once(punct_anchor, punct_new, 'full-width punctuation commit')

# ---------------------------------------------------------------------------
# 3) Candidate quality: hide supplementary-plane rare ideographs, reward useful
#    exact codes without letting rare exact codes beat normal Taiwan usage.
# ---------------------------------------------------------------------------
rank_pattern = r'''    private int builtinCommonRank\(String text\)\{.*?    private List<String> commonHome\(\)\{'''
rank_replacement = r'''    private int builtinCommonRank(String text){
        if(text==null||text.isEmpty()||text.codePointCount(0,text.length())!=1) return 0;
        int i=COMMON_CHARS.indexOf(text);
        return i<0?0:(COMMON_CHARS.length()-i);
    }
    private boolean hiddenRareCandidate(String text){
        if(text==null||text.isEmpty()||text.codePointCount(0,text.length())!=1) return false;
        int cp=text.codePointAt(0);
        // Extension-plane Han characters are extremely rare in ordinary Taiwan typing.
        // Keep them out of normal candidates rather than merely demoting them.
        return cp>0xFFFF || "𠃑".equals(text);
    }
    private int personalFrequency(String text){ return learningEnabled()?prefs.getInt("f_"+text,0):0; }
    private int learnedContextFrequency(String text){
        if(!learningEnabled()||lastCommitted==null||lastCommitted.isEmpty()) return 0;
        return prefs.getInt("b_"+lastCommitted+"_"+text,0);
    }
    private int builtInContextBonus(String text){
        if(lastCommitted==null||lastCommitted.isEmpty()) return 0;
        for(String phrase:phraseSuggestions(lastCommitted)){
            String c=continuationOnly(lastCommitted,phrase);
            if(text.equals(c)) return 1;
        }
        return 0;
    }
    private long commonScore(String text){
        long score=(long)personalFrequency(text)*6000L + (long)learnedContextFrequency(text)*18000L;
        score+=(long)builtInContextBonus(text)*120000L;
        score+=builtinCommonRank(text);
        return score;
    }
    private List<String> rankCommon(List<String> src){
        ArrayList<String> out=new ArrayList<>();
        for(String x:new LinkedHashSet<>(src)) if(!hiddenRareCandidate(x)) out.add(x);
        out.sort((a,b)->Long.compare(commonScore(b),commonScore(a)));
        return out;
    }
    private List<String> rankCangjie(List<String> exact,List<String> prefix){
        LinkedHashSet<String> all=new LinkedHashSet<>();
        if(exact!=null) all.addAll(exact);
        if(prefix!=null) all.addAll(prefix);
        ArrayList<String> out=new ArrayList<>();
        for(String x:all) if(!hiddenRareCandidate(x)) out.add(x);
        final LinkedHashSet<String> exactSet=new LinkedHashSet<>();
        if(exact!=null) for(String x:exact) if(!hiddenRareCandidate(x)) exactSet.add(x);
        out.sort((a,b)->{
            long sa=commonScore(a), sb=commonScore(b);
            // Useful exact codes get a strong but not absolute advantage. This makes
            // 一一 -> 二 and 竹手 -> 牛, while a hidden rare exact code never beats 好.
            if(exactSet.contains(a)) sa += builtinCommonRank(a)>0 ? 160000L : 25000L;
            if(exactSet.contains(b)) sb += builtinCommonRank(b)>0 ? 160000L : 25000L;
            return Long.compare(sb,sa);
        });
        return out;
    }
    private List<String> commonHome(){'''
sub1(rank_pattern, rank_replacement, 'candidate ranking helpers')

# Keep full learning target for prediction suffixes. Display remains continuation-only.
old_add_cont = '''    private void addContinuations(LinkedHashSet<String> out,String prev,List<String> src){\n        if(src==null) return;\n        for(String item:src){\n            String c=continuationOnly(prev,item);\n            if(c!=null&&!c.isEmpty()) out.add(c);\n        }\n    }\n'''
new_add_cont = '''    private void addContinuations(LinkedHashSet<String> out,String prev,List<String> src){\n        if(src==null) return;\n        for(String item:src){\n            String c=continuationOnly(prev,item);\n            if(c!=null&&!c.isEmpty()){ out.add(c); continuationFull.put(c,item); }\n        }\n    }\n'''
replace_once(old_add_cont, new_add_cont, 'prediction learning map')
s = s.replace('    private List<String> withLearnedNext(List<String> base){\n        LinkedHashSet<String> out=new LinkedHashSet<>();',
              '    private List<String> withLearnedNext(List<String> base){\n        continuationFull.clear();\n        LinkedHashSet<String> out=new LinkedHashSet<>();', 1)

choose_pattern = r'''    private void choose\(String s\)\{.*?\n    \}\n(?=    private void feedback\(\))'''
choose_new = r'''    private void choose(String s){
        if(s==null||s.isEmpty()) return;
        String learnText=continuationFull.containsKey(s)?continuationFull.get(s):s;
        svc.commit(s);
        learn(learnText);
        if(isPunctuation(s)) lastCommitted="";
        cjCode=""; zyCode=""; candOff=0; expanded=false; syncComposition(); invalidate();
    }
'''
sub1(choose_pattern, choose_new, 'prediction choose learning')

# Replace Cangjie and Zhuyin composition ranking with stable daily-use ranking.
cand_cj_pattern = r'''        if\(mode==Mode.CANGJIE\)\{.*?\n        \}\n        if\(mode==Mode.ZHUYIN\)\{'''
cand_cj_new = r'''        if(mode==Mode.CANGJIE){
            if(cjCode.isEmpty()) return withLearnedNext(commonHome());
            List<String> ex=cjExact.get(cjCode), pr=cj.get(cjCode);
            List<String> ranked=rankCangjie(ex,pr);
            if(ranked.isEmpty()) ranked=rankCommon(cangjieTypoCandidates(cjCode));
            return ranked;
        }
        if(mode==Mode.ZHUYIN){'''
sub1(cand_cj_pattern, cand_cj_new, 'Cangjie stable ranking')

zy_pattern = r'''        if\(mode==Mode.ZHUYIN\)\{\n            if\(zyCode.isEmpty\(\)\) return withLearnedNext\(commonHome\(\)\);.*?\n        \}\n        return Arrays.asList'''
zy_new = r'''        if(mode==Mode.ZHUYIN){
            if(zyCode.isEmpty()) return withLearnedNext(commonHome());
            LinkedHashSet<String> merged=new LinkedHashSet<>();
            List<String> ex=zy.get(zyCode); if(ex!=null) merged.addAll(ex);
            for(Map.Entry<String,List<String>> e:zy.entrySet()) if(!e.getKey().equals(zyCode)&&e.getKey().startsWith(zyCode)) merged.addAll(e.getValue());
            return rankCommon(new ArrayList<>(merged));
        }
        return Arrays.asList'''
sub1(zy_pattern, zy_new, 'Zhuyin stable ranking')

# ---------------------------------------------------------------------------
# 4) Candidate strip: first/main candidate gets a subtle selected background.
#    Also use the night palette throughout the strip.
# ---------------------------------------------------------------------------
new_draw_candidates = r'''    private void drawCandidates(Canvas c){
        int h=candH(),rows=expanded?3:1;
        p.setColor(expanded?candidateExpandedColor():candidateBgColor());
        c.drawRect(0,0,getWidth(),h*rows,p);

        List<String> items=candidates();
        float arrowW=dp(46), usable=getWidth()-arrowW;
        int pageSize=7*rows;
        if(candOff>=items.size()) candOff=0;
        int n=Math.max(0,Math.min(pageSize,items.size()-candOff));
        boolean composing=(mode==Mode.CANGJIE&&!cjCode.isEmpty())||(mode==Mode.ZHUYIN&&!zyCode.isEmpty());

        if(composing){
            float cw=usable/7f;
            for(int i=0;i<n;i++){
                int row=i/7,col=i%7;
                RectF r=new RectF(col*cw,row*h,(col+1)*cw,(row+1)*h);
                if(i==0&&candOff==0){ p.setColor(selectedCandidateColor()); c.drawRoundRect(new RectF(r.left+dp(3),r.top+dp(3),r.right-dp(3),r.bottom-dp(3)),dp(9),dp(9),p); }
                String item=items.get(candOff+i);
                t.setTextSize(dp(22*candScale())); t.setTextAlign(Paint.Align.CENTER); t.setColor(textColor());
                android.graphics.Rect b=new android.graphics.Rect(); t.getTextBounds(item,0,item.length(),b);
                c.drawText(item,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t);
                hits.add(new Hit(r,"CAND",item));
            }
        }else{
            for(int row=0;row<rows;row++){
                int rowStart=row*7,rowCount=Math.min(7,n-rowStart); if(rowCount<=0) break;
                float totalWeight=0f; for(int j=0;j<rowCount;j++) totalWeight+=candidateWeight(items.get(candOff+rowStart+j));
                float left=0f;
                for(int j=0;j<rowCount;j++){
                    String item=items.get(candOff+rowStart+j);
                    float w=(j==rowCount-1)?(usable-left):(usable*candidateWeight(item)/totalWeight);
                    RectF r=new RectF(left,row*h,left+w,(row+1)*h);
                    int cps=item.codePointCount(0,item.length()); float sz=cps<=1?22f:(cps==2?19f:(cps==3?16.5f:14.5f));
                    t.setTextSize(dp(sz*candScale())); float maxText=Math.max(dp(18),r.width()-dp(18)); float measured=t.measureText(item);
                    if(measured>maxText&&measured>0f) t.setTextSize(t.getTextSize()*(maxText/measured));
                    t.setTextAlign(Paint.Align.CENTER); t.setColor(textColor());
                    android.graphics.Rect b=new android.graphics.Rect(); t.getTextBounds(item,0,item.length(),b);
                    c.drawText(item,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t); hits.add(new Hit(r,"CAND",item));
                    if(j<rowCount-1){ String next=items.get(candOff+rowStart+j+1); boolean phrase=cps>1||next.codePointCount(0,next.length())>1;
                        if(phrase){ p.setColor(dividerColor()); c.drawRect(r.right-dp(.5f),row*h+dp(9),r.right+dp(.5f),(row+1)*h-dp(9),p); } }
                    left+=w;
                }
            }
        }
        RectF ar=new RectF(getWidth()-arrowW,0,getWidth(),h*rows); drawTextCentered(c,expanded?"⌃":"⌄",ar,21); hits.add(new Hit(ar,"EXPAND",""));
    }
'''
sub1(r'    private void drawCandidates\(Canvas c\)\{.*?\n    \}\n(?=\n    private void drawFooter)', new_draw_candidates, 'candidate selected cell and dark palette')

# ---------------------------------------------------------------------------
# 5) Touch tolerance: candidate/key boundary favours the first root row; the
#    一/。 boundary strongly favours 一 for right-hand blind typing.
# ---------------------------------------------------------------------------
find_pattern = r'''    private Hit findHit\(float x,float y\)\{.*?\n    \}\n    private boolean sameHit'''
find_new = r'''    private Hit findHit(float x,float y){
        // Near the candidate/key seam, favour the first physical key row. This
        // prevents a slightly high blind tap from accidentally choosing a candidate.
        if(!expanded&&page!=Page.EMOJI&&page!=Page.KAOMOJI&&y>=candH()-dp(8)&&y<=candH()+dp(18)){
            for(int i=hits.size()-1;i>=0;i--){
                Hit h=hits.get(i); if("CAND".equals(h.action)||"EXPAND".equals(h.action)) continue;
                RectF rr=new RectF(h.r); rr.inset(-dp(7),-dp(14));
                if(rr.contains(x,y)) return h;
            }
        }
        for(int i=hits.size()-1;i>=0;i--){
            Hit h=hits.get(i);
            if(h.r.contains(x,y)){
                // The left portion of the visible period key is treated as 一.
                if(page==Page.MAIN&&mode==Mode.CANGJIE&&"PUNCT".equals(h.action)&&"。".equals(h.value)
                        &&x<h.r.left+h.r.width()*0.42f){
                    for(int j=hits.size()-1;j>=0;j--){ Hit root=hits.get(j); if("CJ".equals(root.action)&&"m".equals(root.value)) return root; }
                }
                return h;
            }
        }
        Hit best=null; float bestD=Float.MAX_VALUE;
        for(int i=hits.size()-1;i>=0;i--){
            Hit h=hits.get(i); if(!canExpand(h)) continue;
            float px=sidePunct(h)?dp(2):dp(12),py=sidePunct(h)?dp(4):dp(10);
            if(page==Page.MAIN&&mode==Mode.CANGJIE&&"CJ".equals(h.action)&&"m".equals(h.value)) px=dp(18);
            RectF rr=new RectF(h.r); rr.inset(-px,-py); if(!rr.contains(x,y)) continue;
            float dx=Math.max(Math.max(h.r.left-x,0),x-h.r.right),dy=Math.max(Math.max(h.r.top-y,0),y-h.r.bottom),d=dx*dx+dy*dy;
            if(d<bestD){bestD=d;best=h;}
        }
        return best;
    }
    private boolean sameHit'''
sub1(find_pattern, find_new, 'touch tolerance')

# ---------------------------------------------------------------------------
# 6) Emoji and kaomoji become continuously vertically scrollable instead of
#    page-by-page. Category and return controls remain fixed.
# ---------------------------------------------------------------------------
scroll_helpers_anchor = '    private void scrollKaomoji(boolean forward){ int rows=Math.max(1,(int)Math.floor(((getHeight()-dp(70)-dp(48))-dp(8))/dp(46))); int pageSize=rows*3; if(forward) kaoOff=Math.min(Math.max(0,kaos.length-pageSize),kaoOff+pageSize); else kaoOff=Math.max(0,kaoOff-pageSize); invalidate(); }\n'
scroll_helpers_new = scroll_helpers_anchor + r'''
    private float emojiMaxScroll(){
        float catH=dp(40),top=catH+dp(32),safeBottom=getHeight()-dp(70),listBottom=safeBottom-dp(48),cellH=dp(48);
        int rows=(emojiItems().length+6)/7; return Math.max(0f,rows*cellH-(listBottom-top));
    }
    private float kaoMaxScroll(){
        float top=dp(8),safeBottom=getHeight()-dp(70),listBottom=safeBottom-dp(48),rowH=dp(46);
        int rows=(kaos.length+2)/3; return Math.max(0f,rows*rowH-(listBottom-top));
    }
    private void clampPanelScroll(){
        emojiScrollY=Math.max(0f,Math.min(emojiScrollY,emojiMaxScroll()));
        kaoScrollY=Math.max(0f,Math.min(kaoScrollY,kaoMaxScroll()));
    }
'''
replace_once(scroll_helpers_anchor, scroll_helpers_new, 'continuous scroll helpers')

new_draw_emoji = r'''    private void drawEmoji(Canvas c){
        p.setColor(bgColor()); c.drawRect(0,0,getWidth(),getHeight(),p);
        String[] cats={"最近","表情","人物","動物","食物","旅行","物品","活動","符號","旗幟"}; float catH=dp(40),catW=getWidth()/10f;
        for(int i=0;i<10;i++){ RectF r=new RectF(i*catW,0,(i+1)*catW,catH); if(i==emojiCategory){ p.setColor(selectedCandidateColor()); c.drawRoundRect(r,dp(8),dp(8),p); } drawTextCentered(c,cats[i],r,13*keyScale()); hits.add(new Hit(r,"EMOJI_CAT",String.valueOf(i))); }
        t.setTextAlign(Paint.Align.LEFT); t.setTextSize(dp(14)); t.setColor(secondaryTextColor()); c.drawText("上下滑動瀏覽",dp(14),catH+dp(22),t);
        float top=catH+dp(32),safeBottom=getHeight()-dp(70),backH=dp(48),listBottom=safeBottom-backH,cellH=dp(48); int cols=7; String[] list=emojiItems();
        emojiScrollY=Math.max(0f,Math.min(emojiScrollY,emojiMaxScroll()));
        int firstRow=(int)Math.floor(emojiScrollY/cellH); float offset=-(emojiScrollY-firstRow*cellH); float cellW=getWidth()/(float)cols;
        c.save(); c.clipRect(0,top,getWidth(),listBottom);
        int visualRow=0;
        for(float y=top+offset;y<listBottom+cellH;y+=cellH,visualRow++){
            int dataRow=firstRow+visualRow;
            for(int col=0;col<cols;col++){
                int idx=dataRow*cols+col; if(idx>=list.length) break;
                RectF r=new RectF(col*cellW,y,(col+1)*cellW,y+cellH); drawTextCentered(c,list[idx],r,25*keyScale());
                RectF hr=new RectF(r); if(hr.bottom>top&&hr.top<listBottom){ hr.top=Math.max(hr.top,top); hr.bottom=Math.min(hr.bottom,listBottom); hits.add(new Hit(hr,"EMOJI_CHAR",list[idx])); }
            }
        }
        c.restore();
        RectF back=new RectF(0,listBottom,dp(110),safeBottom); hits.add(new Hit(back,"MAIN","")); drawTextCentered(c,"⌨",back,22*keyScale());
    }
'''
sub1(r'    private void drawEmoji\(Canvas c\)\{.*?\n    \}\n(?=    private void drawKaomoji)', new_draw_emoji, 'continuous emoji drawing')

new_draw_kao = r'''    private void drawKaomoji(Canvas c){
        p.setColor(bgColor()); c.drawRect(0,0,getWidth(),getHeight(),p);
        float top=dp(8),rowH=dp(46),safeBottom=getHeight()-dp(70),backH=dp(48),listBottom=safeBottom-backH; int cols=3; float cellW=getWidth()/(float)cols;
        kaoScrollY=Math.max(0f,Math.min(kaoScrollY,kaoMaxScroll())); int firstRow=(int)Math.floor(kaoScrollY/rowH); float offset=-(kaoScrollY-firstRow*rowH);
        c.save(); c.clipRect(0,top,getWidth(),listBottom);
        int visualRow=0;
        for(float y=top+offset;y<listBottom+rowH;y+=rowH,visualRow++){
            int dataRow=firstRow+visualRow;
            for(int col=0;col<cols;col++){
                int idx=dataRow*cols+col; if(idx>=kaos.length) break;
                RectF r=new RectF(col*cellW,y,(col+1)*cellW,y+rowH); p.setColor(dividerColor()); c.drawRect(r.left,r.bottom-1,r.right,r.bottom,p); drawTextCentered(c,kaos[idx],r,15*keyScale());
                RectF hr=new RectF(r); if(hr.bottom>top&&hr.top<listBottom){ hr.top=Math.max(hr.top,top); hr.bottom=Math.min(hr.bottom,listBottom); hits.add(new Hit(hr,"KAO_CHAR",kaos[idx])); }
            }
        }
        c.restore();
        RectF back=new RectF(0,listBottom,dp(110),safeBottom); hits.add(new Hit(back,"MAIN","")); drawTextCentered(c,"⌨",back,22*keyScale());
    }
'''
sub1(r'    private void drawKaomoji\(Canvas c\)\{.*?\n    \}\n(?=\n    private List<String> recentEmoji)', new_draw_kao, 'continuous kaomoji drawing')

# Category/page entry resets pixel scroll rather than page offsets only.
s = s.replace('case "EMOJI": page=Page.EMOJI; emojiCategory=1; emojiOff=0; syncComposition(); invalidate(); break;',
              'case "EMOJI": page=Page.EMOJI; emojiCategory=1; emojiOff=0; emojiScrollY=0f; syncComposition(); invalidate(); break;', 1)
s = s.replace('case "KAO": page=Page.KAOMOJI; kaoOff=0; syncComposition(); invalidate(); break;',
              'case "KAO": page=Page.KAOMOJI; kaoOff=0; kaoScrollY=0f; syncComposition(); invalidate(); break;', 1)
s = s.replace('case "EMOJI_CAT": emojiCategory=Integer.parseInt(h.value); emojiOff=0; invalidate(); break;',
              'case "EMOJI_CAT": emojiCategory=Integer.parseInt(h.value); emojiOff=0; emojiScrollY=0f; invalidate(); break;', 1)

# ---------------------------------------------------------------------------
# 7) Touch handling adds continuous panel drag and spacebar trackpad cursor mode.
# ---------------------------------------------------------------------------
on_touch_new = r'''    @Override public boolean onTouchEvent(MotionEvent e){
        float x=e.getX(),y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            downAt=SystemClock.uptimeMillis(); downX=x; downY=y; panelLastY=y; panelDragging=false; downHit=findHit(x,y); backspaceRepeating=false; longPressDirect=false; spaceCursorMode=false; cursorCarry=0f;
            if(downHit!=null&&"BACK".equals(downHit.action)) handler.postDelayed(repeatBackspace,420);
            if(downHit!=null&&page==Page.MAIN&&("CJ".equals(downHit.action)||"ZY".equals(downHit.action))) handler.postDelayed(directSymbolLongPress,430);
            if(downHit!=null&&"SPACE".equals(downHit.action)&&cjCode.isEmpty()&&zyCode.isEmpty()) handler.postDelayed(spaceCursorLongPress,380);
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_CANCEL){ handler.removeCallbacks(repeatBackspace); handler.removeCallbacks(directSymbolLongPress); handler.removeCallbacks(spaceCursorLongPress); downHit=null; panelDragging=false; spaceCursorMode=false; return true; }
        if(e.getAction()==MotionEvent.ACTION_MOVE){
            if(page==Page.EMOJI||page==Page.KAOMOJI){
                if(downY>dp(55)&&downY<getHeight()-dp(112)){
                    float total=Math.abs(y-downY); if(total>dp(4)) panelDragging=true;
                    if(panelDragging){ float delta=panelLastY-y; if(page==Page.EMOJI) emojiScrollY+=delta; else kaoScrollY+=delta; clampPanelScroll(); panelLastY=y; invalidate(); return true; }
                }
            }
            if(spaceCursorMode){
                float delta=x-spaceCursorLastX; cursorCarry+=delta; float unit=dp(18);
                int steps=(int)(cursorCarry/unit);
                if(steps!=0){ svc.moveCursor(steps); cursorCarry-=steps*unit; spaceCursorLastX=x; }
                return true;
            }
            if(Math.hypot(x-downX,y-downY)>dp(22)){ handler.removeCallbacks(directSymbolLongPress); handler.removeCallbacks(spaceCursorLongPress); }
            return true;
        }
        if(e.getAction()!=MotionEvent.ACTION_UP) return true;
        handler.removeCallbacks(repeatBackspace); handler.removeCallbacks(directSymbolLongPress); handler.removeCallbacks(spaceCursorLongPress);
        float dx=x-downX,dy=y-downY,adx=Math.abs(dx),ady=Math.abs(dy);
        if(panelDragging){ panelDragging=false; downHit=null; return true; }
        if(spaceCursorMode){ spaceCursorMode=false; downHit=null; return true; }
        if(page!=Page.EMOJI&&page!=Page.KAOMOJI&&downY<candH()*(expanded?3:1)&&adx>dp(34)&&adx>ady*1.25f){ feedback(); pageCandidates(dx<0); downHit=null; return true; }
        if(downHit!=null&&"SPACE".equals(downHit.action)&&cjCode.isEmpty()&&zyCode.isEmpty()){
            float vth=Math.max(dp(36),downHit.r.height()*0.22f);
            if(dy>0&&ady>vth&&ady>adx*1.35f){ if(temporaryEnglish){ feedback(); exitTemporaryEnglish(); downHit=null; return true; } if(mode!=Mode.ENGLISH){ feedback(); enterTemporaryEnglish(); downHit=null; return true; } }
            float th=Math.max(dp(42),downHit.r.width()*0.16f);
            if(adx>th&&adx>ady*1.5f){ feedback(); temporaryEnglish=false; if(dx<0) cycleMode(); else cycleModeBackward(); downHit=null; return true; }
        }
        if(longPressDirect){ downHit=null; return true; }
        Hit up=findHit(x,y); float move=(float)Math.hypot(dx,dy);
        if(downHit!=null){ if("BACK".equals(downHit.action)&&backspaceRepeating){} else if(move<=dp(28)||sameHit(downHit,up)){ feedback(); act(downHit); } }
        downHit=null; return true;
    }
'''
sub1(r'    @Override public boolean onTouchEvent\(MotionEvent e\)\{.*?\n    \}\n(?=\n    private final Runnable directSymbolLongPress)', on_touch_new, 'continuous scrolling and cursor gesture')

runnable_anchor = '    private final Runnable directSymbolLongPress=new Runnable(){ @Override public void run(){ if(downHit==null||!("CJ".equals(downHit.action)||"ZY".equals(downHit.action))) return; longPressDirect=true; feedback(); cjCode=""; zyCode=""; candOff=0; expanded=false; svc.commit(downHit.label); lastCommitted=""; syncComposition(); invalidate(); } };\n'
runnable_new = runnable_anchor + '''    private final Runnable spaceCursorLongPress=new Runnable(){ @Override public void run(){ if(downHit==null||!"SPACE".equals(downHit.action)||!cjCode.isEmpty()||!zyCode.isEmpty()) return; spaceCursorMode=true; spaceCursorLastX=downX; cursorCarry=0f; feedback(); } };\n'''
replace_once(runnable_anchor, runnable_new, 'space cursor long press')

# ---------------------------------------------------------------------------
# 8) Version metadata and settings text.
# ---------------------------------------------------------------------------
java_path.write_text(s, encoding='utf-8')

gradle = Path('imeapp/app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 16', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '0.9.6'", g, count=1)
gradle.write_text(g, encoding='utf-8')

# ---------------------------------------------------------------------------
# 9) Grapheme-aware backspace + cursor movement service.
# ---------------------------------------------------------------------------
service_path = Path('imeapp/app/src/main/java/tw/ajo/ime/AjoImeService.java')
service = service_path.read_text(encoding='utf-8')
backspace_pattern = r'''    public void backspace\(\) \{.*?\n    \}\n(?=\n    public void enter\(\))'''
backspace_new = r'''    private boolean isCombiningOrVariation(int cp){
        int type=Character.getType(cp);
        return cp==0xFE0E||cp==0xFE0F||cp==0x20E3||
                type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK||
                (cp>=0x1F3FB&&cp<=0x1F3FF);
    }

    private int lastGraphemeUnits(CharSequence before){
        if(before==null||before.length()==0) return 0;
        String x=before.toString(); int end=x.length(),start=end;
        int cp=Character.codePointBefore(x,start); start-=Character.charCount(cp);
        while(start>0&&isCombiningOrVariation(cp)){ cp=Character.codePointBefore(x,start); start-=Character.charCount(cp); }
        // Flags are pairs of regional indicators.
        if(cp>=0x1F1E6&&cp<=0x1F1FF&&start>0){ int prev=Character.codePointBefore(x,start); if(prev>=0x1F1E6&&prev<=0x1F1FF) start-=Character.charCount(prev); }
        // Consume complete ZWJ emoji chains, including modifiers/variation selectors.
        while(start>0){
            int prev=Character.codePointBefore(x,start);
            if(prev!=0x200D) break;
            start-=Character.charCount(prev);
            if(start<=0) break;
            int base=Character.codePointBefore(x,start); start-=Character.charCount(base);
            while(start>0){ int mark=Character.codePointBefore(x,start); if(!isCombiningOrVariation(mark)) break; start-=Character.charCount(mark); }
        }
        return end-start;
    }

    public void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(64, 0);
        if (before != null && before.length() > 0) {
            int units=lastGraphemeUnits(before);
            if(units>0) ic.deleteSurroundingText(units,0);
        } else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        }
    }

    public void moveCursor(int steps){
        InputConnection ic=getCurrentInputConnection(); if(ic==null||steps==0) return;
        int key=steps>0?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT;
        for(int i=0;i<Math.min(12,Math.abs(steps));i++){
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));
        }
    }
'''
service2, n = re.subn(backspace_pattern, backspace_new, service, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f'v0.9.6 patch failed for grapheme backspace: matched {n}')
service_path.write_text(service2, encoding='utf-8')

# ---------------------------------------------------------------------------
# 10) Settings: theme selector and local JSON backup/restore of learned data.
# ---------------------------------------------------------------------------
main = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m = main.read_text(encoding='utf-8')
for old, new in [
    ('import android.content.Intent;\n', 'import android.content.Intent;\nimport android.net.Uri;\n'),
    ('import android.widget.Button;\n', 'import android.widget.ArrayAdapter;\nimport android.widget.Button;\n'),
    ('import android.widget.Switch;\n', 'import android.widget.Switch;\nimport android.widget.Spinner;\n'),
    ('import android.widget.Toast;\n', 'import android.widget.Toast;\n\nimport org.json.JSONObject;\n\nimport java.io.BufferedReader;\nimport java.io.InputStreamReader;\nimport java.io.OutputStream;\nimport java.nio.charset.StandardCharsets;\nimport java.util.Map;\n')
]:
    if old in m and new not in m: m=m.replace(old,new,1)

m = m.replace('public class MainActivity extends Activity {\n    private SharedPreferences settings;\n    private SharedPreferences learning;\n',
'''public class MainActivity extends Activity {\n    private SharedPreferences settings;\n    private SharedPreferences learning;\n    private static final int REQ_EXPORT_LEARNING=701;\n    private static final int REQ_IMPORT_LEARNING=702;\n''',1)

m = re.sub(r'version\.setText\("[^"]*"\);', 'version.setText("v0.9.6｜日常輸入體驗修正版");', m, count=1)
m = re.sub(r'intro\.setText\("[^"]*"\);',
'''intro.setText("這版集中修正實際使用幾天後發現的問題：候選列與第一排字根增加防誤觸、右側「一／。」改成偏向「一」的盲打容錯；倉頡候選改為台灣日常字優先，常用完整碼會得到合理加權，極罕見擴充漢字不再干擾一般候選。Emoji／顏文字改成連續上下滑動，加入夜間配色、空白鍵長按拖動游標、全形中文標點，以及個人學習資料匯出／匯入。模式順序維持倉頡 → English → 注音 → 倉頡。");''', m, count=1)

seek_anchor = '        addSeek(root, "候選字大小", "調整上方候選列的字體", "candidate_text_size", 85, 125, 100, "%");\n'
theme_block = seek_anchor + '''        addThemeSpinner(root);\n'''
if seek_anchor not in m:
    raise SystemExit('v0.9.6 patch failed: theme settings insertion point not found')
m = m.replace(seek_anchor, theme_block, 1)

clear_anchor = '''        Button clear = button("清除個人學習紀錄");\n        clear.setOnClickListener(v -> {\n            learning.edit().clear().apply();\n            Toast.makeText(this, "個人常用字／詞紀錄已清除", Toast.LENGTH_SHORT).show();\n        });\n        root.addView(clear);\n'''
backup_block = clear_anchor + '''\n        Button exportLearning = button("匯出個人字庫／學習資料");\n        exportLearning.setOnClickListener(v -> {\n            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE,"阿喬輸入法_個人字庫.json"); startActivityForResult(i,REQ_EXPORT_LEARNING);\n        });\n        root.addView(exportLearning);\n\n        Button importLearning = button("匯入個人字庫／學習資料");\n        importLearning.setOnClickListener(v -> {\n            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); startActivityForResult(i,REQ_IMPORT_LEARNING);\n        });\n        root.addView(importLearning);\n'''
if clear_anchor not in m:
    raise SystemExit('v0.9.6 patch failed: backup buttons insertion point not found')
m=m.replace(clear_anchor,backup_block,1)

note_text = 'note.setText("倉頡版本：第三代。\\n模式順序：倉頡 → English → 注音 → 倉頡；空白鍵左右滑切換，向右為反方向。正在組字時不切換。\\n空白鍵向下滑：從倉頡或注音暫時切到 English；在暫時 English 再向下滑一次回原中文模式。\\n長按空白鍵後左右拖：移動文字游標。\\n長按倉頡字根或注音符號：直接輸出鍵面文字，不進入組字。\\n候選列與第一排字根交界會偏向字根，降低盲打時誤選候選。\\n倉頡「一／。」交界偏向「一」，畫面位置不變。\\n中文模式標點統一使用全形。\\nEmoji／顏文字頁可像一般手機一樣連續上下拖動瀏覽。\\n候選排序綜合個人習慣、上下文、台灣常用度與正常完整碼；極罕見擴充漢字不進一般候選。\\n個人字庫可匯出／匯入 JSON，資料仍只在使用者主動存取的本機檔案中。");'
m2,n=re.subn(r'note\.setText\(".*?"\);',lambda _:note_text,m,count=1,flags=re.S)
if n!=1: raise SystemExit(f'v0.9.6 patch failed: settings note matched {n}')
m=m2

# Helper methods inserted before addSeek().
helper_anchor = '    private void addSeek(LinearLayout root, String title, String sub, String key, int min, int max, int def, String suffix) {\n'
helpers = r'''    private void addThemeSpinner(LinearLayout root){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0,dp(10),0,dp(10));
        TextView label=new TextView(this); label.setText("外觀"); label.setTextSize(17); label.setTextColor(Color.BLACK); box.addView(label);
        TextView hint=new TextView(this); hint.setText("鍵盤配色：跟隨系統／淺色／深色"); hint.setTextSize(13); hint.setTextColor(Color.GRAY); box.addView(hint);
        Spinner sp=new Spinner(this); String[] labels={"跟隨系統","淺色","深色"}; String[] values={"system","light","dark"};
        ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels); sp.setAdapter(ad);
        String cur=settings.getString("theme_mode","system"); int pos="light".equals(cur)?1:("dark".equals(cur)?2:0); sp.setSelection(pos,false);
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int position,long id){ settings.edit().putString("theme_mode",values[position]).apply(); } public void onNothingSelected(android.widget.AdapterView<?> p){} });
        box.addView(sp); root.addView(box);
    }

    private JSONObject learningJson() throws Exception{
        JSONObject root=new JSONObject(); root.put("format","ajo-ime-learning-v1"); JSONObject data=new JSONObject();
        for(Map.Entry<String,?> e:learning.getAll().entrySet()) data.put(e.getKey(),e.getValue()); root.put("data",data); return root;
    }
    private void writeLearning(Uri uri) throws Exception{
        OutputStream out=getContentResolver().openOutputStream(uri,"w"); if(out==null) throw new Exception("無法開啟檔案");
        byte[] bytes=learningJson().toString(2).getBytes(StandardCharsets.UTF_8); out.write(bytes); out.close();
    }
    private void readLearning(Uri uri) throws Exception{
        BufferedReader br=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line); br.close();
        JSONObject root=new JSONObject(sb.toString()); if(!"ajo-ime-learning-v1".equals(root.optString("format"))) throw new Exception("不是阿喬輸入法字庫檔"); JSONObject data=root.getJSONObject("data");
        SharedPreferences.Editor ed=learning.edit().clear(); java.util.Iterator<String> it=data.keys(); while(it.hasNext()){ String k=it.next(); Object v=data.get(k); if(v instanceof Integer) ed.putInt(k,(Integer)v); else if(v instanceof Long) ed.putLong(k,(Long)v); else if(v instanceof Boolean) ed.putBoolean(k,(Boolean)v); else if(v instanceof Number) ed.putInt(k,((Number)v).intValue()); else ed.putString(k,String.valueOf(v)); } ed.apply();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null||data.getData()==null) return;
        try{ if(requestCode==REQ_EXPORT_LEARNING){ writeLearning(data.getData()); Toast.makeText(this,"個人字庫已匯出",Toast.LENGTH_SHORT).show(); } else if(requestCode==REQ_IMPORT_LEARNING){ readLearning(data.getData()); Toast.makeText(this,"個人字庫已匯入",Toast.LENGTH_SHORT).show(); } }
        catch(Exception e){ Toast.makeText(this,"字庫處理失敗："+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }

'''
if helper_anchor not in m:
    raise SystemExit('v0.9.6 patch failed: MainActivity helper insertion point not found')
m=m.replace(helper_anchor,helpers+helper_anchor,1)
main.write_text(m,encoding='utf-8')

# ---------------------------------------------------------------------------
# 11) Proper launcher/adaptive-icon resources using the selected green 倉 art.
# ---------------------------------------------------------------------------
res=Path('imeapp/app/src/main/res')
icon_path=res/'drawable'/'ic_launcher_ajo.png'
if not icon_path.exists(): raise SystemExit('v0.9.6 patch failed: selected icon missing after v0.9.5 runner')
(res/'mipmap').mkdir(parents=True,exist_ok=True)
shutil.copyfile(icon_path,res/'mipmap'/'ic_launcher.png')
shutil.copyfile(icon_path,res/'mipmap'/'ic_launcher_round.png')
(res/'mipmap-anydpi-v26').mkdir(parents=True,exist_ok=True)
adaptive='''<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/ic_launcher_transparent" />\n    <foreground android:drawable="@drawable/ic_launcher_ajo" />\n</adaptive-icon>\n'''
(res/'mipmap-anydpi-v26'/'ic_launcher.xml').write_text(adaptive,encoding='utf-8')
(res/'mipmap-anydpi-v26'/'ic_launcher_round.xml').write_text(adaptive,encoding='utf-8')
(res/'values').mkdir(parents=True,exist_ok=True)
(res/'values'/'ic_launcher_colors.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources><color name="ic_launcher_transparent">#00000000</color></resources>\n',encoding='utf-8')
manifest=Path('imeapp/app/src/main/AndroidManifest.xml')
ms=manifest.read_text(encoding='utf-8')
ms=ms.replace('android:icon="@drawable/ic_launcher_ajo"','android:icon="@mipmap/ic_launcher"')
ms=ms.replace('android:roundIcon="@drawable/ic_launcher_ajo"','android:roundIcon="@mipmap/ic_launcher_round"')
manifest.write_text(ms,encoding='utf-8')

# Sanity checks.
assert 'versionCode 16' in g and "versionName '0.9.6'" in g
assert 'rankCangjie' in s and 'hiddenRareCandidate' in s
assert 'emojiScrollY' in s and 'spaceCursorLongPress' in s
assert 'candidateBgColor()' in s and 'normalizeChinesePunctuation' in s
assert 'lastGraphemeUnits' in service2 and 'moveCursor' in service2
assert 'addThemeSpinner(root)' in m and 'REQ_EXPORT_LEARNING' in m
assert '@mipmap/ic_launcher' in ms
print('v0.9.6 daily-use Cangjie correction applied')

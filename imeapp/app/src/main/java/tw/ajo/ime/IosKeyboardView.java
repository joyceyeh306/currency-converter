package tw.ajo.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IosKeyboardView extends View {
    enum Mode { CANGJIE, ZHUYIN, ENGLISH }
    enum Page { MAIN, NUM, SYM, EMOJI, KAOMOJI }

    private static final float IMG_W = 1170f;
    private static final float IMG_H = 842f;
    private static final int BG = Color.rgb(218, 219, 224);

    private final AjoImeService service;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, List<String>> cangjiePrefix = new HashMap<>();
    private final Map<String, List<String>> zhuyinMap = new HashMap<>();
    private final SharedPreferences prefs;

    private Mode mode = Mode.CANGJIE;
    private Page page = Page.MAIN;
    private String cangjieCode = "";
    private String zhuyinBuffer = "";
    private boolean shift = false;
    private boolean capsLock = false;
    private long lastShiftTap = 0;
    private boolean expandedCandidates = false;
    private boolean modeMenu = false;
    private float downX, downY;
    private long downAt;
    private int kaomojiOffset = 0;
    private EditorInfo editorInfo;

    private final String[] cjRow1 = {"手","田","水","口","廿","卜","山","戈","人","心"};
    private final char[] cjCode1 = {'q','w','e','r','t','y','u','i','o','p'};
    private final String[] cjRow2 = {"日","尸","木","火","土","竹","十","大","中"};
    private final char[] cjCode2 = {'a','s','d','f','g','h','j','k','l'};
    private final String[] cjRow3 = {"重","難","金","女","月","弓","一"};
    private final char[] cjCode3 = {'z','x','c','v','b','n','m'};

    private final String[] zyRow1 = {"ㄅ","ㄉ","ˇ","ˋ","ㄓ","ˊ","˙","ㄚ","ㄞ","ㄢ","ㄦ"};
    private final String[] zyRow2 = {"ㄆ","ㄊ","ㄍ","ㄐ","ㄔ","ㄗ","ㄧ","ㄛ","ㄟ","ㄣ"};
    private final String[] zyRow3 = {"ㄇ","ㄋ","ㄎ","ㄑ","ㄕ","ㄘ","ㄨ","ㄜ","ㄠ","ㄤ"};
    private final String[] zyRow4 = {"ㄈ","ㄌ","ㄏ","ㄒ","ㄖ","ㄙ","ㄩ","ㄝ","ㄡ","ㄥ"};

    private final String[] enRow1 = {"q","w","e","r","t","y","u","i","o","p"};
    private final String[] enRow2 = {"a","s","d","f","g","h","j","k","l"};
    private final String[] enRow3 = {"z","x","c","v","b","n","m"};

    private final String[] numChineseRow2 = {"-","/",":",";","(",")","$","@","「","」"};
    private final String[] numChineseRow3 = {"#+=","。","，","、","？","！","．"};
    private final String[] numEnglishRow2 = {"-","/",":",";","(",")","$","&","@","\""};
    private final String[] numEnglishRow3 = {"#+=",".",",","?","!","'"};
    private final String[] symRow1 = {"[","]","{","}","#","%","^","*","+","="};
    private final String[] symChineseRow2 = {"_","\\","|","~","《","》","¥","&","•"};
    private final String[] symEnglishRow2 = {"_","\\","|","~","<",">","€","£","¥","•"};
    private final String[] symChineseRow3 = {"123","…","，","^_^","？","！","'"};
    private final String[] symEnglishRow3 = {"123",".",",","?","!","'"};

    private final String[] emoji = {
            "😅","🥰","👍","🥲","🍰","😳","🤣",
            "😜","😭","😓","😵‍💫","😥","⬇️","🙄",
            "😆","💕","😁","💝","🙏","🚑","😀",
            "😂","😱","❤️","🎂","🥺","😄","🌹",
            "😊","😉","😍","🤔","😴","🤗","🎉",
            "🔥","✨","💯","👏","🙌","💪","👌"
    };

    private final String[] kaomoji = {
            "^_^","^^","^_^","(^_^)","(^ ^)","(^_-^)",
            "(^_^)","^o^","(o^^o)","(^_^)a","(^_^)v",":)",
            ":(",":-)","=)","=(",";-) ",":-|",":-(",":-D",
            ":D",":-P",":P","凸^_^凸","(´▽｀)","(*^^*)",
            "(*^_^*)","(^_^*)","*^_^*","V(^_^)V","Y(^_^)Y",
            "d(^_^o)","o(^_^)o","p(^_^)q","(^_^)","(#^.^#)",
            "(*^o^*)","(^.^)","(^O^)","(^o^)","(^｡^)",
            "(^○^)",")^o^(","*^O^*","=^.^=","(^▽^)",
            "o(^▽^)o","(^▽^)","(^◇^)","(^3^)","(^3^)-☆",
            "(*^3^)","(^ω^)","(>^ω^<)","^ω^","┌(^ω^)┐",
            "↖(^ω^)↗","(^ω^)","(^人^)","^*^","〜^_^", "(∩_∩)",
            "O(∩_∩)O","O(∩_∩)O~","o(∩_∩)o","(´▽｀)","(´▽｀)",
            "(￣▽￣)","(*￣︶￣*)","(*￣▽￣*)","(￣▽￣)","(*☺-☺*)",
            "(T_T)","(╥﹏╥)","(>_<)","(；ω；)","(｡•́︿•̀｡)",
            "(ง •̀_•́)ง","ᕦ(ò_óˇ)ᕤ","ヽ(•‿•)ノ","¯\\_(ツ)_/¯","(¬_¬)"
    };

    public IosKeyboardView(Context context, AjoImeService service) {
        super(context);
        this.service = service;
        this.prefs = context.getSharedPreferences("ime_learning", Context.MODE_PRIVATE);
        setBackgroundColor(BG);
        setFocusable(true);
        loadCangjie();
        initZhuyin();
    }

    public void onEditorChanged(EditorInfo info) {
        editorInfo = info;
        invalidate();
    }

    private void loadCangjie() {
        try {
            InputStream in = getContext().getAssets().open("cangjie_prefix.tsv");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                int p = line.indexOf('\t');
                if (p <= 0 || p >= line.length()-1) continue;
                String key = line.substring(0,p);
                String[] vals = line.substring(p+1).trim().split(" +");
                cangjiePrefix.put(key, Arrays.asList(vals));
            }
            br.close();
        } catch (Exception ignored) {
            cangjiePrefix.put("hqi", Arrays.asList("我"));
            cangjiePrefix.put("o", Arrays.asList("人","你","他"));
            cangjiePrefix.put("a", Arrays.asList("日","是","時"));
        }
    }

    private void initZhuyin() {
        putZ("ㄨㄛˇ", "我");
        putZ("ㄋㄧˇ", "你妳擬");
        putZ("ㄊㄚ", "他她它");
        putZ("ㄕˋ", "是事市式視世士");
        putZ("ㄉㄜ˙", "的得地");
        putZ("ㄧㄡˇ", "有友");
        putZ("ㄅㄨˋ", "不部步布");
        putZ("ㄗㄞˋ", "在再載");
        putZ("ㄐㄧㄣ", "今金斤津");
        putZ("ㄊㄧㄢ", "天添田");
        putZ("ㄧㄠˋ", "要藥耀");
        putZ("ㄑㄩˋ", "去趣");
        putZ("ㄔ", "吃癡");
        putZ("ㄈㄢˋ", "飯範犯");
        putZ("ㄏㄠˇ", "好郝");
        putZ("ㄇㄚ˙", "嗎嘛媽");
    }

    private void putZ(String key, String chars) {
        ArrayList<String> list = new ArrayList<>();
        chars.codePoints().forEach(cp -> list.add(new String(Character.toChars(cp))));
        zhuyinMap.put(key, list);
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int cand = candidateHeight();
        int body = Math.round(w * IMG_H / IMG_W);
        setMeasuredDimension(w, cand + body);
    }

    private int candidateHeight() { return dp(46); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (page == Page.EMOJI) { drawEmoji(c); return; }
        if (page == Page.KAOMOJI) { drawKaomoji(c); return; }

        int ch = candidateHeight();
        drawCandidateBar(c, ch);
        drawBody(c, ch);
        if (expandedCandidates) drawCandidateBar(c, ch);
        if (modeMenu) drawModeMenu(c);
    }

    private void drawBody(Canvas c, int ch) {
        paint.setColor(BG); c.drawRect(0,ch,getWidth(),getHeight(),paint);
        if (page == Page.MAIN) {
            if (mode == Mode.CANGJIE) drawCangjie(c,ch);
            else if (mode == Mode.ZHUYIN) drawZhuyin(c,ch);
            else drawEnglish(c,ch);
        } else if (page == Page.NUM) drawNumbers(c,ch);
        else if (page == Page.SYM) drawSymbols(c,ch);
        drawFooter(c,ch);
    }

    private float sx(float x){ return x/IMG_W*getWidth(); }
    private float sy(float y,int ch){ return ch + y/IMG_H*(getHeight()-ch); }

    private void key(Canvas c,int ch,float x1,float y1,float x2,float y2,String label,float textDp){
        RectF r=new RectF(sx(x1),sy(y1,ch),sx(x2),sy(y2,ch));
        paint.setColor(Color.WHITE); c.drawRoundRect(r,dp(8),dp(8),paint);
        textPaint.setColor(Color.BLACK); textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setTextSize(dp(textDp));
        Paint.FontMetrics fm=textPaint.getFontMetrics(); float base=r.centerY()-(fm.ascent+fm.descent)/2;
        c.drawText(label,r.centerX(),base,textPaint);
    }

    private void drawCangjie(Canvas c,int ch){
        for(int i=0;i<10;i++){float x=18+i*116.4f;key(c,ch,x,0,x+96,122,cjRow1[i],26);}
        for(int i=0;i<9;i++){float x=76+i*114.8f;key(c,ch,x,155,x+96,282,cjRow2[i],26);}
        for(int i=0;i<7;i++){float x=116+i*116.5f;key(c,ch,x,318,x+96,444,cjRow3[i],25);}
        key(c,ch,1017,318,1150,444,"⌫",24);
        drawBottomRow(c,ch,"123","倉",false);
    }

    private void drawZhuyin(Canvas c,int ch){
        for(int i=0;i<11;i++){float x=18+i*103.3f;key(c,ch,x,0,x+88,108,zyRow1[i],25);}
        for(int i=0;i<10;i++){float x=55+i*105.5f;key(c,ch,x,122,x+88,236,zyRow2[i],25);}
        for(int i=0;i<10;i++){float x=82+i*101.5f;key(c,ch,x,252,x+88,365,zyRow3[i],25);}
        for(int i=0;i<10;i++){float x=18+i*103.4f;key(c,ch,x,382,x+88,494,zyRow4[i],24);}
        key(c,ch,1060,382,1150,494,"⌫",22);
        drawBottomRow(c,ch,"123","注",true);
    }

    private void drawEnglish(Canvas c,int ch){
        for(int i=0;i<10;i++){float x=18+i*116.4f;key(c,ch,x,0,x+96,122,enRow1[i],27);}
        for(int i=0;i<9;i++){float x=76+i*114.8f;key(c,ch,x,155,x+96,282,enRow2[i],27);}
        key(c,ch,18,318,145,444,"⇧",25);
        for(int i=0;i<7;i++){float x=172+i*116.2f;key(c,ch,x,318,x+96,444,enRow3[i],27);}
        key(c,ch,1017,318,1150,444,"⌫",24);
        drawBottomRow(c,ch,"123","A",false);
    }

    private void drawBottomRow(Canvas c,int ch,String left,String spaceMark,boolean zh){
        key(c,ch,18,478,145,607,left, left.length()>3?15:22);
        key(c,ch,160,478,287,607,"☺",23);
        key(c,ch,303,478,864,607,"",22);
        key(c,ch,880,478,1150,607,"↩",25);
        textPaint.setColor(Color.rgb(190,190,194));textPaint.setTextSize(dp(12));textPaint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(spaceMark,sx(842),sy(586,ch),textPaint);
    }

    private void drawNumbers(Canvas c,int ch){
        for(int i=0;i<10;i++){float x=18+i*116.4f;key(c,ch,x,0,x+96,122,String.valueOf((i+1)%10),25);}
        String[] r2=mode==Mode.ENGLISH?numEnglishRow2:numChineseRow2;
        for(int i=0;i<10;i++){float x=18+i*116.4f;key(c,ch,x,155,x+96,282,r2[i],22);}
        if(mode==Mode.ENGLISH){
            String[] labs={"#+=",".",",","?","!","'"};float[] xs={18,138,257,376,495,614};
            for(int i=0;i<labs.length;i++)key(c,ch,xs[i],318,xs[i]+100,444,labs[i],20);
        }else{
            String[] labs={"#+=","。","，","、","？","！","．"};float[] xs={18,154,285,416,547,678,809};
            for(int i=0;i<labs.length;i++)key(c,ch,xs[i],318,xs[i]+112,444,labs[i],20);
        }
        key(c,ch,1017,318,1150,444,"⌫",24);
        String label=mode==Mode.CANGJIE?"倉頡":mode==Mode.ZHUYIN?"注音":"ABC";
        String mark=mode==Mode.CANGJIE?"倉":mode==Mode.ZHUYIN?"注":"A";
        drawBottomRow(c,ch,label,mark,false);
    }

    private void drawSymbols(Canvas c,int ch){
        for(int i=0;i<10;i++){float x=18+i*116.4f;key(c,ch,x,0,x+96,122,symRow1[i],21);}
        String[] r2=mode==Mode.ENGLISH?symEnglishRow2:symChineseRow2;
        float cell=IMG_W/r2.length;
        for(int i=0;i<r2.length;i++){float x=i*cell+10;key(c,ch,x,155,x+cell-20,282,r2[i],20);}
        if(mode==Mode.ENGLISH){
            String[] labs={"123",".",",","?","!","'"};float[] xs={18,138,257,376,495,614};
            for(int i=0;i<labs.length;i++)key(c,ch,xs[i],318,xs[i]+100,444,labs[i],20);
        }else{
            String[] labs={"123","…","，","^_^","？","！","'"};float[] xs={18,138,257,376,495,614,733};
            for(int i=0;i<labs.length;i++)key(c,ch,xs[i],318,xs[i]+100,444,labs[i],18);
        }
        key(c,ch,1017,318,1150,444,"⌫",24);
        String label=mode==Mode.CANGJIE?"倉頡":mode==Mode.ZHUYIN?"注音":"ABC";
        String mark=mode==Mode.CANGJIE?"倉":mode==Mode.ZHUYIN?"注":"A";
        drawBottomRow(c,ch,label,mark,false);
    }

    private void drawFooter(Canvas c,int ch){
        textPaint.setColor(Color.BLACK);textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setTextSize(dp(31));
        c.drawText("◎",sx(92),sy(740,ch),textPaint);
        paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2.5f));
        float mx=sx(1042), my=sy(706,ch);c.drawRoundRect(new RectF(mx-dp(6),my-dp(17),mx+dp(6),my+dp(9)),dp(6),dp(6),paint);
        c.drawArc(new RectF(mx-dp(13),my-dp(2),mx+dp(13),my+dp(22)),0,180,false,paint);
        c.drawLine(mx,my+dp(20),mx,my+dp(31),paint);c.drawLine(mx-dp(9),my+dp(31),mx+dp(9),my+dp(31),paint);paint.setStyle(Paint.Style.FILL);
    }

    private void drawCandidateBar(Canvas c, int h) {
        paint.setColor(BG); c.drawRect(0,0,getWidth(),h,paint);
        List<String> candidates = currentCandidates();
        int visible = expandedCandidates ? Math.min(21, candidates.size()) : Math.min(7, candidates.size());
        if (expandedCandidates) {
            paint.setColor(Color.rgb(232,233,237));
            c.drawRect(0,0,getWidth(),h*3,paint);
        }
        float arrowW = dp(48);
        float usable = getWidth()-arrowW;
        int cols = 7;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(dp(22));
        for (int i=0; i<visible; i++) {
            int row = i/cols, col=i%cols;
            float cw = usable/cols;
            float cx = col*cw+cw/2;
            float cy = row*h + h*0.67f;
            c.drawText(candidates.get(i), cx, cy, textPaint);
        }
        paint.setColor(Color.rgb(190,191,196));
        float ax = getWidth()-arrowW/2;
        float ay = h/2;
        paint.setStrokeWidth(dp(2.4f)); paint.setStyle(Paint.Style.STROKE);
        float d=dp(7);
        if (!expandedCandidates) {
            c.drawLine(ax-d,ay-d/2,ax,ay+d/2,paint); c.drawLine(ax,ay+d/2,ax+d,ay-d/2,paint);
        } else {
            c.drawLine(ax-d,ay+d/2,ax,ay-d/2,paint); c.drawLine(ax,ay-d/2,ax+d,ay+d/2,paint);
        }
        paint.setStyle(Paint.Style.FILL);

        if (expandedCandidates) {
            paint.setColor(Color.rgb(205,206,211));
            c.drawLine(0,h,getWidth(),h,paint); c.drawLine(0,h*2,getWidth(),h*2,paint);
        }
    }

    private List<String> currentCandidates() {
        if (mode == Mode.CANGJIE) {
            if (!cangjieCode.isEmpty()) {
                List<String> l = cangjiePrefix.get(cangjieCode);
                if (l == null) return Collections.singletonList(codeToRoots(cangjieCode));
                return boosted(l);
            }
            return Arrays.asList("的","嗎","為","成","過","變","法","我","你","是","有","在","不","人");
        }
        if (mode == Mode.ZHUYIN) {
            if (!zhuyinBuffer.isEmpty()) {
                LinkedHashSet<String> out = new LinkedHashSet<>();
                List<String> exact = zhuyinMap.get(zhuyinBuffer);
                if (exact != null) out.addAll(exact);
                for (Map.Entry<String,List<String>> e: zhuyinMap.entrySet()) {
                    if (e.getKey().startsWith(zhuyinBuffer)) out.addAll(e.getValue());
                }
                if (out.isEmpty()) out.add(zhuyinBuffer);
                return boosted(new ArrayList<>(out));
            }
            return Arrays.asList("的","我","是","了","不","在","有","你","這","人","要","好","就","也");
        }
        return Arrays.asList("i","the","i’m","and","to","you","a","is","of","it","that","for","in","on");
    }

    private List<String> boosted(List<String> input) {
        ArrayList<String> l = new ArrayList<>(input);
        l.sort(Comparator.comparingInt((String s) -> -prefs.getInt("f_"+s,0)));
        return l;
    }

    private void learn(String s) {
        if (s == null || s.isEmpty()) return;
        int n = prefs.getInt("f_"+s,0);
        prefs.edit().putInt("f_"+s, Math.min(100000,n+1)).apply();
    }

    private String codeToRoots(String code) {
        String letters="abcdefghijklmnopqrstuvwxyz";
        String roots="日月金木水火土竹戈十大中一弓人心手口尸廿山女田難卜符";
        StringBuilder sb=new StringBuilder();
        for(char ch:code.toCharArray()) { int i=letters.indexOf(ch); if(i>=0) sb.append(roots.charAt(i)); }
        return sb.toString();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX(), y=e.getY();
        if (e.getAction()==MotionEvent.ACTION_DOWN) { downX=x; downY=y; downAt=SystemClock.uptimeMillis(); return true; }
        if (e.getAction()!=MotionEvent.ACTION_UP) return true;
        long held=SystemClock.uptimeMillis()-downAt;

        if (page == Page.KAOMOJI && Math.abs(y-downY)>dp(35)) {
            int dir = y < downY ? 1 : -1;
            kaomojiOffset = Math.max(0, Math.min(Math.max(0,kaomoji.length-18), kaomojiOffset + dir*9));
            invalidate(); return true;
        }

        if (modeMenu) { handleModeMenu(x,y); return true; }
        if (page == Page.EMOJI) { handleEmoji(x,y,held); return true; }
        if (page == Page.KAOMOJI) { handleKaomoji(x,y,held); return true; }

        int ch=candidateHeight();
        int candidateRows = expandedCandidates ? 3 : 1;
        if (y < ch*candidateRows) { handleCandidate(x,y); return true; }

        float ix = x/getWidth()*IMG_W;
        float iy = (y-ch)/(getHeight()-ch)*IMG_H;
        if (isGlobe(ix,iy)) {
            if (held >= 450) { modeMenu=true; invalidate(); }
            else cycleMode();
            return true;
        }
        if (isMic(ix,iy)) { service.voiceComingSoon(); return true; }

        if (page==Page.MAIN) handleMain(ix,iy,held);
        else if (page==Page.NUM) handleNum(ix,iy);
        else if (page==Page.SYM) handleSym(ix,iy);
        return true;
    }

    private void handleCandidate(float x,float y) {
        int h=candidateHeight();
        float arrowW=dp(48);
        if (x>getWidth()-arrowW) { expandedCandidates=!expandedCandidates; invalidate(); return; }
        List<String> c=currentCandidates();
        int row=(int)(y/h), col=(int)(x/((getWidth()-arrowW)/7f));
        int idx=row*7+col;
        if (idx>=0 && idx<c.size()) chooseCandidate(c.get(idx));
    }

    private void chooseCandidate(String s) {
        if (mode==Mode.CANGJIE && !cangjieCode.isEmpty() && cangjiePrefix.get(cangjieCode)==null && s.equals(codeToRoots(cangjieCode))) return;
        service.commit(s);
        learn(s);
        cangjieCode=""; zhuyinBuffer=""; expandedCandidates=false;
        invalidate();
    }

    private boolean isGlobe(float x,float y){ return y>620 && x<230; }
    private boolean isMic(float x,float y){ return y>620 && x>930; }

    private void handleMain(float x,float y,long held) {
        if (mode==Mode.CANGJIE) handleCangjieMain(x,y);
        else if (mode==Mode.ZHUYIN) handleZhuyinMain(x,y);
        else handleEnglishMain(x,y,held);
    }

    private void handleCangjieMain(float x,float y) {
        if (y<130) { int i=(int)(x/(IMG_W/10f)); if(i>=0&&i<10) addCj(cjCode1[i]); return; }
        if (y<290) {
            float start=52, width=1066; if(x>=start&&x<start+width){ int i=(int)((x-start)/(width/9f)); if(i>=0&&i<9)addCj(cjCode2[i]); } return;
        }
        if (y<465) {
            if(x>985){ doBackspace(); return; }
            float start=100, width=820; if(x>=start&&x<start+width){ int i=(int)((x-start)/(width/7f)); if(i>=0&&i<7)addCj(cjCode3[i]); } return;
        }
        handleBottom(x,y,"倉頡");
    }

    private void addCj(char c){ if(cangjieCode.length()<5)cangjieCode+=c; invalidate(); }

    private void handleZhuyinMain(float x,float y) {
        if(y<116){ int i=(int)(x/(IMG_W/11f)); if(i>=0&&i<11)addZ(zyRow1[i]); return; }
        if(y<242){ float s=50,w=1070; if(x>=s&&x<s+w){int i=(int)((x-s)/(w/10f));if(i>=0&&i<10)addZ(zyRow2[i]);}return;}
        if(y<374){ float s=80,w=1020; if(x>=s&&x<s+w){int i=(int)((x-s)/(w/10f));if(i>=0&&i<10)addZ(zyRow3[i]);}return;}
        if(y<505){
            if(x>1030){doBackspace();return;}
            float s=18,w=1015;if(x>=s&&x<s+w){int i=(int)((x-s)/(w/10f));if(i>=0&&i<10)addZ(zyRow4[i]);}return;
        }
        handleBottom(x,y,"注音");
    }

    private void addZ(String z){ if(zhuyinBuffer.length()<8)zhuyinBuffer+=z; invalidate(); }

    private void handleEnglishMain(float x,float y,long held) {
        if(y<130){int i=(int)(x/(IMG_W/10f));if(i>=0&&i<10)englishLetter(enRow1[i]);return;}
        if(y<290){float s=55,w=1060;if(x>=s&&x<s+w){int i=(int)((x-s)/(w/9f));if(i>=0&&i<9)englishLetter(enRow2[i]);}return;}
        if(y<465){
            if(x<155){handleShift();return;}
            if(x>990){service.backspace();return;}
            float s=165,w=805;if(x>=s&&x<s+w){int i=(int)((x-s)/(w/7f));if(i>=0&&i<7)englishLetter(enRow3[i]);}return;
        }
        handleBottom(x,y,"ABC");
    }

    private void englishLetter(String l){
        String out=(shift||capsLock)?l.toUpperCase(Locale.ROOT):l;
        service.commit(out); if(shift&&!capsLock)shift=false; invalidate();
    }
    private void handleShift(){ long now=SystemClock.uptimeMillis(); if(now-lastShiftTap<350){capsLock=!capsLock;shift=capsLock;}else shift=!shift; lastShiftTap=now;invalidate(); }

    private void handleBottom(float x,float y,String label) {
        if(y<625){
            if(x<150){page=Page.NUM;invalidate();return;}
            if(x<300){page=Page.EMOJI;invalidate();return;}
            if(x<875){
                if(mode==Mode.CANGJIE&&!cangjieCode.isEmpty()){List<String> c=currentCandidates();if(!c.isEmpty())chooseCandidate(c.get(0));else service.commit(" ");}
                else if(mode==Mode.ZHUYIN&&!zhuyinBuffer.isEmpty()){List<String> c=currentCandidates();if(!c.isEmpty())chooseCandidate(c.get(0));else service.commit(" ");}
                else service.commit(" ");
                return;
            }
            service.enter();return;
        }
    }

    private void handleNum(float x,float y) {
        if(y<135){int i=(int)(x/(IMG_W/10f));if(i>=0&&i<10)service.commit(String.valueOf((i+1)%10));return;}
        if(y<300){String[] row=mode==Mode.ENGLISH?numEnglishRow2:numChineseRow2;int i=(int)(x/(IMG_W/10f));if(i>=0&&i<row.length)commitPunct(row[i]);return;}
        if(y<465){
            if(x>1000){service.backspace();return;}
            String[] row=mode==Mode.ENGLISH?numEnglishRow3:numChineseRow3;
            float cell=IMG_W/(row.length+1f); int i=(int)(x/cell);
            if(i==0){page=Page.SYM;invalidate();return;}
            int idx=i; if(idx>=1&&idx<row.length){commitPunct(row[idx]);return;}
            if(idx>=row.length && x<1000 && row.length>1){commitPunct(row[row.length-1]);}
            return;
        }
        if(y<625){
            if(x<150){page=Page.MAIN;invalidate();return;}
            if(x<300){page=Page.EMOJI;invalidate();return;}
            if(x<875){service.commit(" ");return;}
            service.enter();
        }
    }

    private void handleSym(float x,float y) {
        if(y<135){int i=(int)(x/(IMG_W/10f));if(i>=0&&i<symRow1.length)commitPunct(symRow1[i]);return;}
        if(y<300){String[] r=mode==Mode.ENGLISH?symEnglishRow2:symChineseRow2;int i=(int)(x/(IMG_W/r.length));if(i>=0&&i<r.length)commitPunct(r[i]);return;}
        if(y<465){
            if(x>1000){service.backspace();return;}
            if(mode==Mode.ENGLISH){
                float cell=IMG_W/7f;int i=(int)(x/cell);
                if(i==0){page=Page.NUM;invalidate();return;}
                if(i>0&&i<symEnglishRow3.length)commitPunct(symEnglishRow3[i]);
            } else {
                float cell=IMG_W/8f;int i=(int)(x/cell);
                if(i==0){page=Page.NUM;invalidate();return;}
                if(i==3){page=Page.KAOMOJI;invalidate();return;}
                if(i>0&&i<symChineseRow3.length)commitPunct(symChineseRow3[i]);
            }
            return;
        }
        if(y<625){
            if(x<150){page=Page.MAIN;invalidate();return;}
            if(x<300){page=Page.EMOJI;invalidate();return;}
            if(x<875){service.commit(" ");return;}
            service.enter();
        }
    }

    private void commitPunct(String s){ if("#+=".equals(s)||"123".equals(s)||"^_^".equals(s))return; service.commit(s); page=Page.MAIN; invalidate(); }

    private void doBackspace(){
        if(mode==Mode.CANGJIE&&!cangjieCode.isEmpty()){cangjieCode=cangjieCode.substring(0,cangjieCode.length()-1);invalidate();return;}
        if(mode==Mode.ZHUYIN&&!zhuyinBuffer.isEmpty()){int cp=zhuyinBuffer.codePointBefore(zhuyinBuffer.length());zhuyinBuffer=zhuyinBuffer.substring(0,zhuyinBuffer.length()-Character.charCount(cp));invalidate();return;}
        service.backspace();
    }

    private void cycleMode(){
        if(mode==Mode.CANGJIE)mode=Mode.ZHUYIN; else if(mode==Mode.ZHUYIN)mode=Mode.ENGLISH; else mode=Mode.CANGJIE;
        page=Page.MAIN;cangjieCode="";zhuyinBuffer="";expandedCandidates=false;invalidate();
    }

    private void drawModeMenu(Canvas c){
        float w=getWidth();float h=getHeight();float top=h-dp(132);float pad=dp(12);float gap=dp(8);float bw=(w-pad*2-gap*2)/3f;
        paint.setColor(Color.argb(225,245,245,247));c.drawRoundRect(new RectF(pad,top,w-pad,h-dp(52)),dp(15),dp(15),paint);
        String[] labels={"倉頡","注音","English"};textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setTextSize(dp(18));textPaint.setColor(Color.BLACK);
        for(int i=0;i<3;i++){float l=pad+i*(bw+gap);paint.setColor(Color.WHITE);c.drawRoundRect(new RectF(l,top+dp(10),l+bw,top+dp(66)),dp(12),dp(12),paint);c.drawText(labels[i],l+bw/2,top+dp(46),textPaint);}
    }

    private void handleModeMenu(float x,float y){
        float w=getWidth(),h=getHeight(),top=h-dp(132),pad=dp(12),gap=dp(8),bw=(w-pad*2-gap*2)/3f;
        if(y>=top+dp(5)&&y<=top+dp(75)){int i=(int)((x-pad)/(bw+gap));if(i>=0&&i<3){mode=Mode.values()[i];page=Page.MAIN;cangjieCode="";zhuyinBuffer="";}}
        modeMenu=false;invalidate();
    }

    private void drawEmoji(Canvas c){
        c.drawColor(BG);
        float w=getWidth(),h=getHeight();
        paint.setColor(Color.rgb(238,239,242));c.drawRoundRect(new RectF(dp(16),dp(14),w-dp(16),dp(58)),dp(22),dp(22),paint);
        textPaint.setTextAlign(Paint.Align.LEFT);textPaint.setTextSize(dp(16));textPaint.setColor(Color.GRAY);c.drawText("⌕  搜尋表情符號",dp(28),dp(43),textPaint);
        int cols=7;float gridTop=dp(72),gridBottom=h-dp(118);float cellW=w/cols;float cellH=(gridBottom-gridTop)/4f;textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setTextSize(dp(28));textPaint.setTextColor(Color.BLACK);
        for(int i=0;i<Math.min(28,emoji.length);i++){int r=i/cols,cl=i%cols;c.drawText(emoji[i],cl*cellW+cellW/2,gridTop+r*cellH+cellH*0.67f,textPaint);}
        textPaint.setTextAlign(Paint.Align.LEFT);textPaint.setTextSize(dp(15));textPaint.setColor(Color.DKGRAY);c.drawText(modeLabel(),dp(16),h-dp(82),textPaint);
        textPaint.setTextSize(dp(19));c.drawText("◷   ☺   ♡   ♫   ✈   ⚑",dp(82),h-dp(82),textPaint);
        drawFooterIcons(c,h);
    }

    private void handleEmoji(float x,float y,long held){
        float w=getWidth(),h=getHeight();
        if(y<h-dp(118)&&y>dp(72)){
            int cols=7;float cellW=w/cols;float cellH=(h-dp(118)-dp(72))/4f;int col=(int)(x/cellW),row=(int)((y-dp(72))/cellH);int idx=row*cols+col;if(idx>=0&&idx<emoji.length){service.commit(emoji[idx]);return;}
        }
        if(y>h-dp(115)&&y<h-dp(55)&&x<dp(72)){page=Page.MAIN;invalidate();return;}
        if(y>h-dp(58)&&x<dp(160)){if(held>=450){modeMenu=true;}else cycleMode();return;}
        if(y>h-dp(58)&&x>w-dp(150)){service.voiceComingSoon();return;}
        if(modeMenu)handleModeMenu(x,y);
    }

    private void drawKaomoji(Canvas c){
        c.drawColor(BG);float w=getWidth(),h=getHeight();
        textPaint.setTextAlign(Paint.Align.RIGHT);textPaint.setTextSize(dp(27));textPaint.setColor(Color.BLACK);c.drawText("⌃",w-dp(20),dp(34),textPaint);
        int cols=3,rows=6;float top=dp(14),bottom=h-dp(72),cellW=w/cols,cellH=(bottom-top)/rows;textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setTextSize(dp(19));
        for(int i=0;i<rows*cols;i++){int idx=kaomojiOffset+i;if(idx>=kaomoji.length)break;int r=i/cols,cl=i%cols;float yy=top+r*cellH+cellH*0.62f;c.drawText(kaomoji[idx],cl*cellW+cellW/2,yy,textPaint);paint.setColor(Color.rgb(188,189,194));c.drawRect(0,top+(r+1)*cellH,w,top+(r+1)*cellH+1,paint);}
        drawFooterIcons(c,h);
    }

    private void handleKaomoji(float x,float y,long held){
        float w=getWidth(),h=getHeight();
        if(y<dp(55)&&x>w-dp(80)){page=Page.SYM;invalidate();return;}
        if(y<h-dp(72)){int cols=3,rows=6;float top=dp(14),bottom=h-dp(72),cellW=w/cols,cellH=(bottom-top)/rows;int col=(int)(x/cellW),row=(int)((y-top)/cellH);int idx=kaomojiOffset+row*cols+col;if(idx>=0&&idx<kaomoji.length){service.commit(kaomoji[idx]);return;}}
        if(y>h-dp(60)&&x<dp(160)){if(held>=450)modeMenu=true;else cycleMode();return;}
        if(y>h-dp(60)&&x>w-dp(150))service.voiceComingSoon();
    }

    private void drawFooterIcons(Canvas c,float h){
        textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setTextSize(dp(31));textPaint.setColor(Color.BLACK);c.drawText("◎",dp(58),h-dp(18),textPaint);textPaint.setTextSize(dp(28));c.drawText("♩",getWidth()-dp(58),h-dp(18),textPaint);
    }

    private String modeLabel(){return mode==Mode.CANGJIE?"倉頡":mode==Mode.ZHUYIN?"注音":"ABC";}
}

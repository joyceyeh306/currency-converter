package tw.ajo.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import java.io.BufferedReader;
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

public class PreciseKeyboardView extends View {
    enum Mode { CANGJIE, ENGLISH, ZHUYIN }
    enum Page { MAIN, NUM, SYM, EMOJI, KAOMOJI }

    private static final float IW = 1170f;
    private static final float IH = 842f;
    private static final int BG = Color.rgb(218, 219, 224);
    private static final String RECENT_SEP = "\u001F";

    private final AjoImeService svc;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences prefs;
    private final SharedPreferences settings;
    private final Map<String, List<String>> cj = new HashMap<>();
    private final Map<String, List<String>> cjExact = new HashMap<>();
    private final Map<String, List<String>> zy = new HashMap<>();
    private final ArrayList<Hit> hits = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Mode mode = Mode.CANGJIE;
    private Page page = Page.MAIN;
    private String cjCode = "";
    private String zyCode = "";
    private String lastCommitted = "";
    private boolean shift = false;
    private boolean caps = false;
    private boolean expanded = false;
    private int candOff = 0;
    private int emojiCategory = 1;
    private int emojiOff = 0;
    private int kaoOff = 0;
    private Hit downHit = null;
    private float downX, downY;
    private long downAt;
    private boolean backspaceRepeating = false;
    private boolean longPressDirect = false;

    private static class Hit {
        final RectF r;
        final String action;
        final String value;
        final String label;
        Hit(RectF r, String action, String value) { this(r, action, value, value); }
        Hit(RectF r, String action, String value, String label) {
            this.r = r; this.action = action; this.value = value; this.label = label;
        }
    }

    private final String[] c1 = {"手","田","水","口","廿","卜","山","戈","人","心"};
    private final String[] c2 = {"日","尸","木","火","土","竹","十","大","中"};
    private final String[] c3 = {"重","難","金","女","月","弓","一"};
    private final char[] k1 = {'q','w','e','r','t','y','u','i','o','p'};
    private final char[] k2 = {'a','s','d','f','g','h','j','k','l'};
    private final char[] k3 = {'z','x','c','v','b','n','m'};

    private final String[] z1 = {"ㄅ","ㄉ","ˇ","ˋ","ㄓ","ˊ","˙","ㄚ","ㄞ","ㄢ","ㄦ"};
    private final String[] z2 = {"ㄆ","ㄊ","ㄍ","ㄐ","ㄔ","ㄗ","ㄧ","ㄛ","ㄟ","ㄣ"};
    private final String[] z3 = {"ㄇ","ㄋ","ㄎ","ㄑ","ㄕ","ㄘ","ㄨ","ㄜ","ㄠ","ㄤ"};
    private final String[] z4 = {"ㄈ","ㄌ","ㄏ","ㄒ","ㄖ","ㄙ","ㄩ","ㄝ","ㄡ","ㄥ"};

    private final String[] q1 = {"q","w","e","r","t","y","u","i","o","p"};
    private final String[] q2 = {"a","s","d","f","g","h","j","k","l"};
    private final String[] q3 = {"z","x","c","v","b","n","m"};
    private final String[] cn2 = {"-","/",":",";","(",")","$","@","「","」"};
    private final String[] en2 = {"-","/",":",";","(",")","$","&","@","\""};
    private final String[] sy1 = {"[","]","{","}","#","%","^","*","+","="};
    private final String[] syC2 = {"_","\\","|","~","《","》","¥","&","•"};
    private final String[] syE2 = {"_","\\","|","~","<",">","€","£","¥","•"};

    private final String[] emojiFaces = {
            "😀","😃","😄","😁","😆","😅","😂","🤣","🥲","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚",
            "😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🥸","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️",
            "😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓",
            "🤗","🤔","🫣","🤭","🫢","🫡","🤫","🫠","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱",
            "😴","🤤","😪","😵","😵‍💫","🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕","🤑","🤠","😈","👿","💀","☠️","👻","👽","🤖"
    };
    private final String[] emojiPeople = {
            "👍","👎","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","👈","👉","👆","👇","☝️","🫵","👏","🙌","🫶","👐","🤲","🤝","🙏",
            "✍️","💅","🤳","💪","🦾","🦵","🦶","👂","👃","🧠","🫀","🫁","🦷","👀","👁️","👅","👄","🫦","👶","🧒","👦","👧","🧑","👨","👩",
            "🧓","👴","👵","🙍","🙎","🙅","🙆","💁","🙋","🧏","🙇","🤦","🤷","👮","👷","💂","🕵️","👩‍⚕️","👩‍🏫","👩‍💻","👩‍🍳","👩‍🎨","👩‍🚀","🧙","🧚","🧛","🧜","🧝","🧞","🧟"
    };
    private final String[] emojiAnimals = {
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐒","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦇",
            "🐺","🐗","🐴","🦄","🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪰","🪲","🪳","🦟","🦗","🕷️","🦂","🐢","🐍","🦎","🦖","🦕","🐙","🦑","🦐","🦞","🦀","🐠","🐟","🐡","🦈","🐬","🐳","🐋","🦭","🐊","🐆","🐅","🐃","🐂","🐄","🦬","🐘","🦣","🦏","🦛","🐪","🐫","🦒","🦘","🦥","🦦","🦨","🦡","🐾","🌵","🌲","🌳","🌴","🌱","🌿","☘️","🍀","🌹","🌸","🌺","🌻","🌼","🌷"
    };
    private final String[] emojiFood = {
            "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶️","🫑","🌽","🥕","🫒","🧄","🧅","🥔","🍠",
            "🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🌭","🍔","🍟","🍕","🫓","🥪","🥙","🧆","🌮","🌯","🫔","🥗","🥘","🫕","🥫","🍝","🍜","🍲","🍛","🍣","🍱","🥟","🦪","🍤","🍙","🍚","🍘","🍥","🥠","🥮","🍢","🍡","🍧","🍨","🍦","🥧","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🌰","🥜","☕","🧋","🥤","🧃","🥛","🍵"
    };
    private final String[] emojiTravel = {
            "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🛵","🏍️","🚲","🛴","🚨","🚔","🚍","🚘","🚖","✈️","🛫","🛬","🛩️","💺","🚁","🚟","🚠","🚡","🛰️","🚀","🛸","🚉","🚆","🚄","🚅","🚈","🚝","🚞","🚋","🚃","🚂","🚢","⛴️","🛥️","🚤","⛵","🛶","⚓","🗺️","🗿","🗽","🗼","🏰","🏯","🏟️","🎡","🎢","🎠","⛲","⛺","🏖️","🏝️","🏜️","🌋","⛰️","🏔️","🗻","🌅","🌄","🌠","🎇","🎆","🌃","🌆","🌇","🌉"
    };
    private final String[] emojiObjects = {
            "⌚","📱","📲","💻","⌨️","🖥️","🖨️","🖱️","🖲️","🕹️","🗜️","💽","💾","💿","📀","📷","📸","📹","🎥","📽️","🎞️","📞","☎️","📟","📺","📻","🎙️","🎚️","🎛️","🧭","⏱️","⏲️","⏰","🕰️","⌛","⏳","📡","🔋","🪫","🔌","💡","🔦","🕯️","🧯","🛢️","💸","💵","💴","💶","💷","🪙","💰","💳","💎","⚖️","🪜","🧰","🪛","🔧","🔨","⚒️","🛠️","⛏️","🪚","🔩","⚙️","🧱","⛓️","🧲","🔫","💣","🧨","🪓","🔪","🗡️","🛡️","🚬","⚰️","🪦","🏺","🔮","📿","🧿","💈","⚗️","🔭","🔬","🕳️","🩹","🩺","💊","🩻","🚪","🪑","🛏️","🛋️","🚿","🛁","🪞","🪟","🛒","🎁","🎈","🎀","🎊","🎉","🪩"
    };
    private final String[] emojiSymbols = {
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❤️‍🔥","❤️‍🩹","❣️","💕","💞","💓","💗","💖","💘","💝","💟","☮️","✝️","☪️","🕉️","☸️","✡️","🔯","🕎","☯️","☦️","🛐","⛎","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","🆔","⚛️","☢️","☣️","📴","📳","🈶","🈚","🈸","🈺","🈷️","✴️","🆚","💮","🉐","㊙️","㊗️","🈴","🈵","🈹","🈲","🅰️","🅱️","🆎","🆑","🅾️","🆘","❌","⭕","🛑","⛔","📛","🚫","💯","💢","♨️","🚷","🚯","🚳","🚱","🔞","📵","❗","❕","❓","❔","‼️","⁉️","🔅","🔆","〽️","⚠️","🚸","🔱","⚜️","🔰","♻️","✅","🈯","💹","❇️","✳️","❎","🌐","💠","Ⓜ️","🌀","💤","🏧","🚾","♿","🅿️","🛗","🚹","🚺","🚼","🚻","🚮","🎦","📶","🈁","🔣","ℹ️","🔤","🔡","🔠","🆖","🆗","🆙","🆒","🆕","🆓","0️⃣","1️⃣","2️⃣","3️⃣","4️⃣","5️⃣","6️⃣","7️⃣","8️⃣","9️⃣","🔟","▶️","⏸️","⏯️","⏹️","⏺️","⏭️","⏮️","⏩","⏪","🔀","🔁","🔂","◀️","🔼","🔽","⏫","⏬","➡️","⬅️","⬆️","⬇️","↗️","↘️","↙️","↖️","↕️","↔️","🔄","↪️","↩️","⤴️","⤵️","#️⃣","*️⃣","ℹ️","🔔","🔕","📣","📢","💬","💭","🗯️","♠️","♣️","♥️","♦️"
    };

    private final String[] kaos = {
            "^_^","^^","(^_^)","(^ ^)","(^_-^)","^o^","(o^^o)","(^_^)a","(^_^)v",":)",":(",":-)","=)","=(",";-)",":-|",":-(",":-D",":D",":-P",":P","XD","XDD","QQ",
            "(´▽｀)","(*^^*)","(*^_^*)","(^_^*)","*^_^*","V(^_^)V","Y(^_^)Y","d(^_^o)","o(^_^)o","p(^_^)q","(#^.^#)","(*^o^*)","(^.^)","(^O^)","(^o^)","(^｡^)","(^○^)",")^o^(","*^O^*","=^.^=","(^▽^)","o(^▽^)o","(^◇^)","(^3^)","(^3^)-☆","(*^3^)","(^ω^)","(>^ω^<)","^ω^","┌(^ω^)┐","↖(^ω^)↗","(^人^)","^*^","〜^_^","(∩_∩)","O(∩_∩)O","o(∩_∩)o","(￣▽￣)","(*￣︶￣*)","(*￣▽￣*)","(*☺-☺*)",
            "(๑•̀ㅂ•́)و✧","٩(ˊᗜˋ*)و","ヽ(✿ﾟ▽ﾟ)ノ","ヾ(≧▽≦*)o","(≧▽≦)","(๑´ڡ`๑)","(๑•̀ᄇ•́)و ✧","(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧","(づ｡◕‿‿◕｡)づ","(｡･ω･｡)ﾉ♡","( ˘ ³˘)♥","(´ε｀ )♡","(♥ω♥*)","( ˘ ³˘)❤","(っ˘з(˘⌣˘ )",
            "(T_T)","(╥﹏╥)","(>_<)","(；ω；)","(｡•́︿•̀｡)","(´；ω；｀)","ಥ_ಥ","(இ﹏இ`｡)","(っ˘̩╭╮˘̩)っ","(ノД`)・゜・。","｡ﾟ(ﾟ´Д｀ﾟ)ﾟ｡","(ToT)","(ಥ﹏ಥ)","(｡•́︵•̀｡)",
            "(ง •̀_•́)ง","ᕦ(ò_óˇ)ᕤ","(ง'̀-'́)ง","(ง°ل͜°)ง","٩(๑`^´๑)۶","(╬ಠ益ಠ)","(＃`Д´)","ヽ(`Д´)ﾉ","(ノಠ益ಠ)ノ彡┻━┻","(╯°□°）╯︵ ┻━┻","┬─┬ ノ( ゜-゜ノ)","┬─┬ ﾉ(° -°ﾉ)",
            "ヽ(•‿•)ノ","¯\\_(ツ)_/¯","(¬_¬)","ಠ_ಠ","ಠ‿ಠ","(¬‿¬)","(눈_눈)","(￣ー￣)","(；一_一)","(・_・;)","(・・;)","(´・ω・`)","(´･_･`)","(°ー°〃)","(⊙_⊙;)","Σ(ﾟДﾟ)","(ﾟдﾟ)！","(°ロ°) !","(⊙o⊙)","(O_O)","(＠_＠;)","(๑•́ ₃ •̀๑)","(￣^￣)ゞ","(｀･ω･´)ゞ","(￣▽￣)ゞ","(๑˃̵ᴗ˂̵)و","(｡•̀ᴗ-)✧","(๑•̀ㅁ•́๑)✧","ʕ•ᴥ•ʔ","ʕっ•ᴥ•ʔっ","ฅ^•ﻌ•^ฅ","(=^･ω･^=)","U・ᴥ・U","(•ө•)♡"
    };

    public PreciseKeyboardView(Context c, AjoImeService service) {
        super(c);
        svc = service;
        prefs = c.getSharedPreferences("ime_learning", Context.MODE_PRIVATE);
        settings = c.getSharedPreferences("ime_settings", Context.MODE_PRIVATE);
        setBackgroundColor(BG);
        loadCangjie();
        loadCangjieExact();
        loadZhuyin();
    }

    public void onEditorChanged(EditorInfo e) {
        cjCode=""; zyCode=""; expanded=false; candOff=0; syncComposition(); invalidate();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int candH() { return dp(46); }
    private float heightScale() { return settings.getInt("keyboard_height",90)/100f; }
    private float keyScale() { return Math.max(70,Math.min(100,settings.getInt("key_text_size",100)))/100f; }
    private float candScale() { return settings.getInt("candidate_text_size",100)/100f; }
    private boolean learningEnabled() { return settings.getBoolean("learning_enabled",true); }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int w=MeasureSpec.getSize(widthSpec);
        int body=Math.round(w*IH/IW*heightScale());
        setMeasuredDimension(w,candH()+body);
    }
    private float sx(float x){ return x/IW*getWidth(); }
    private float sy(float y){ return candH()+y/IH*(getHeight()-candH()); }
    private RectF rect(float x1,float y1,float x2,float y2){ return new RectF(sx(x1),sy(y1),sx(x2),sy(y2)); }

    private void loadMap(String asset, Map<String,List<String>> target) {
        try(BufferedReader b=new BufferedReader(new InputStreamReader(getContext().getAssets().open(asset),StandardCharsets.UTF_8))){
            String line; while((line=b.readLine())!=null){ int i=line.indexOf('\t'); if(i<=0) continue; String v=line.substring(i+1).trim(); if(!v.isEmpty()) target.put(line.substring(0,i),Arrays.asList(v.split(" +"))); }
        }catch(Exception ignored){}
    }
    private void loadCangjie(){ loadMap("cangjie_prefix.tsv",cj); if(cj.isEmpty()) cj.put("o",Arrays.asList("人","你","他")); }
    private void loadCangjieExact(){ loadMap("cangjie_exact.tsv",cjExact); if(cjExact.isEmpty()) cjExact.put("mwyl",Arrays.asList("面")); }
    private void loadZhuyin(){ loadMap("zhuyin.tsv",zy); if(zy.isEmpty()) zy.put("ㄋㄧˇ",Arrays.asList("你","妳","擬")); }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c); hits.clear(); p.setStyle(Paint.Style.FILL); p.setColor(BG); c.drawRect(0,0,getWidth(),getHeight(),p);
        if(page==Page.EMOJI){ drawEmoji(c); syncComposition(); return; }
        if(page==Page.KAOMOJI){ drawKaomoji(c); syncComposition(); return; }
        if(page==Page.MAIN){ if(mode==Mode.CANGJIE) drawCangjie(c); else if(mode==Mode.ENGLISH) drawEnglish(c); else drawZhuyin(c); }
        else if(page==Page.NUM) drawNumbers(c); else drawSymbols(c);
        drawFooter(c); drawCandidates(c); syncComposition();
    }

    private void drawTextCentered(Canvas c,String label,RectF r,float size){
        if(label==null||label.isEmpty()) return;
        t.setColor(Color.BLACK); t.setTextAlign(Paint.Align.CENTER); t.setTextSize(dp(size));
        android.graphics.Rect b=new android.graphics.Rect(); t.getTextBounds(label,0,label.length(),b);
        c.drawText(label,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t);
    }
    private void drawKey(Canvas c,float x1,float y1,float x2,float y2,String label,float size,String action,String value,boolean scalable){
        RectF r=rect(x1,y1,x2,y2); p.setColor(Color.WHITE); c.drawRoundRect(r,dp(8),dp(8),p);
        drawTextCentered(c,label,r,size*(scalable?keyScale():1f));
        if(action!=null) hits.add(new Hit(new RectF(r),action,value,label));
    }
    private void drawInputKey(Canvas c,float x1,float y1,float x2,float y2,String label,float size,String action,String value){ drawKey(c,x1,y1,x2,y2,label,size,action,value,true); }
    private void drawFixedKey(Canvas c,float x1,float y1,float x2,float y2,String label,float size,String action,String value){ drawKey(c,x1,y1,x2,y2,label,size,action,value,false); }

    private void drawCangjie(Canvas c){
        for(int i=0;i<10;i++){ float x=18+i*116.4f; drawInputKey(c,x,0,x+96,122,c1[i],17.68f,"CJ",String.valueOf(k1[i])); }
        for(int i=0;i<9;i++){ float x=76+i*114.8f; drawInputKey(c,x,155,x+96,282,c2[i],17.68f,"CJ",String.valueOf(k2[i])); }
        // Restore the original third-row root coordinates. Punctuation uses only the old empty gaps.
        drawInputKey(c,34,318,96,444,"，",17.68f,"PUNCT","，");
        for(int i=0;i<7;i++){ float x=116+i*116.5f; drawInputKey(c,x,318,x+96,444,c3[i],17.68f,"CJ",String.valueOf(k3[i])); }
        drawInputKey(c,932,318,994,444,"。",17.68f,"PUNCT","。");
        drawFixedKey(c,1017,318,1150,444,"⌫",24,"BACK","");
        drawBottom(c,"123","倉");
    }
    private void drawEnglish(Canvas c){
        for(int i=0;i<10;i++){ float x=18+i*116.4f; drawInputKey(c,x,0,x+96,122,q1[i],17.68f,"LETTER",q1[i]); }
        for(int i=0;i<9;i++){ float x=76+i*114.8f; drawInputKey(c,x,155,x+96,282,q2[i],17.68f,"LETTER",q2[i]); }
        drawFixedKey(c,18,318,145,444,caps?"⇧•":"⇧",25,"SHIFT","");
        for(int i=0;i<7;i++){ float x=172+i*116.2f; drawInputKey(c,x,318,x+96,444,q3[i],17.68f,"LETTER",q3[i]); }
        drawFixedKey(c,1017,318,1150,444,"⌫",24,"BACK",""); drawBottom(c,"123","A");
    }
    private void drawZhuyin(Canvas c){
        for(int i=0;i<11;i++){ float x=18+i*103.3f; drawInputKey(c,x,0,x+88,108,z1[i],17.68f,"ZY",z1[i]); }
        for(int i=0;i<10;i++){ float x=55+i*105.5f; drawInputKey(c,x,122,x+88,236,z2[i],17.68f,"ZY",z2[i]); }
        for(int i=0;i<10;i++){ float x=82+i*101.5f; drawInputKey(c,x,252,x+88,365,z3[i],17.68f,"ZY",z3[i]); }
        for(int i=0;i<10;i++){ float x=18+i*103.4f; drawInputKey(c,x,382,x+88,494,z4[i],17.68f,"ZY",z4[i]); }
        drawFixedKey(c,1060,382,1150,494,"⌫",22,"BACK",""); drawBottom(c,"123","注");
    }
    private void drawBottom(Canvas c,String left,String mark){
        drawFixedKey(c,18,518,145,647,left,22,"NUM",""); drawFixedKey(c,160,518,287,647,"☺",23,"EMOJI",""); drawFixedKey(c,303,518,864,647,"",22,"SPACE",""); drawFixedKey(c,880,518,1150,647,"↩",25,"ENTER","");
        t.setColor(Color.rgb(190,190,194)); t.setTextSize(dp(12)); t.setTextAlign(Paint.Align.RIGHT); c.drawText(mark,sx(842),sy(626),t);
    }
    private void drawNumbers(Canvas c){
        for(int i=0;i<10;i++){ float x=18+i*116.4f; String s=String.valueOf((i+1)%10); drawInputKey(c,x,0,x+96,122,s,25,"NUMBER",s); }
        String[] row2=mode==Mode.ENGLISH?en2:cn2;
        for(int i=0;i<10;i++){ float x=18+i*116.4f; drawInputKey(c,x,155,x+96,282,row2[i],22,"PUNCT",row2[i]); }
        String[] row3=mode==Mode.ENGLISH?new String[]{"🔣",".",",","?","!","'"}:new String[]{"🔣","。","，","、","？","！","．"};
        float step=mode==Mode.ENGLISH?158f:136f, width=mode==Mode.ENGLISH?138f:116f;
        for(int i=0;i<row3.length;i++){ float x=18+i*step; drawInputKey(c,x,318,x+width,444,row3[i],21,i==0?"SYM":"PUNCT",row3[i]); }
        drawFixedKey(c,1017,318,1150,444,"⌫",24,"BACK",""); drawAltBottom(c);
    }
    private void drawSymbols(Canvas c){
        for(int i=0;i<10;i++){ float x=18+i*116.4f; drawInputKey(c,x,0,x+96,122,sy1[i],22,"PUNCT",sy1[i]); }
        String[] row2=mode==Mode.ENGLISH?syE2:syC2; float cell=IW/row2.length;
        for(int i=0;i<row2.length;i++){ float x=i*cell+10; drawInputKey(c,x,155,x+cell-20,282,row2[i],21,"PUNCT",row2[i]); }
        String[] row3=mode==Mode.ENGLISH?new String[]{"123",".",",","?","!","'"}:new String[]{"123","…","，","^_^","？","！","'"};
        float step=mode==Mode.ENGLISH?158f:136f,width=mode==Mode.ENGLISH?138f:116f;
        for(int i=0;i<row3.length;i++){ float x=18+i*step; String a=i==0?"NUM":((mode!=Mode.ENGLISH&&i==3)?"KAO":"PUNCT"); boolean scale=i!=0; drawKey(c,x,318,x+width,444,row3[i],20,a,row3[i],scale); }
        drawFixedKey(c,1017,318,1150,444,"⌫",24,"BACK",""); drawAltBottom(c);
    }
    private void drawAltBottom(Canvas c){
        String s=mode==Mode.ENGLISH?"ABC":(mode==Mode.CANGJIE?"倉頡":"注音"); drawFixedKey(c,18,518,145,647,s,s.length()>3?16:20,"MAIN",""); drawFixedKey(c,160,518,287,647,"☺",23,"EMOJI",""); drawFixedKey(c,303,518,864,647,"",22,"SPACE",""); drawFixedKey(c,880,518,1150,647,"↩",25,"ENTER","");
    }

    private void drawCandidates(Canvas c){
        int h=candH(),rows=expanded?3:1; p.setColor(expanded?Color.rgb(232,233,237):BG); c.drawRect(0,0,getWidth(),h*rows,p);
        List<String> items=candidates(); float arrowW=dp(46),cw=(getWidth()-arrowW)/7f; int pageSize=7*rows; if(candOff>=items.size()) candOff=0; int n=Math.max(0,Math.min(pageSize,items.size()-candOff));
        for(int i=0;i<n;i++){ int row=i/7,col=i%7; RectF r=new RectF(col*cw,row*h,(col+1)*cw,(row+1)*h); String item=items.get(candOff+i); float sz=item.length()<=2?22:(item.length()<=4?17:14); t.setTextSize(dp(sz*candScale())); t.setTextAlign(Paint.Align.CENTER); t.setColor(Color.BLACK); android.graphics.Rect b=new android.graphics.Rect(); t.getTextBounds(item,0,item.length(),b); c.drawText(item,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t); hits.add(new Hit(r,"CAND",item)); }
        RectF ar=new RectF(getWidth()-arrowW,0,getWidth(),h*rows); drawTextCentered(c,expanded?"⌃":"⌄",ar,21); hits.add(new Hit(ar,"EXPAND",""));
    }

    private void drawFooter(Canvas c){
        RectF mic=rect(855,682,1005,838); hits.add(new Hit(mic,"MIC","")); drawTextCentered(c,"🎙️",mic,22);
    }

    private String[] emojiItems(){
        if(emojiCategory==0){ List<String> r=recentEmoji(); return r.toArray(new String[0]); }
        if(emojiCategory==2) return emojiPeople; if(emojiCategory==3) return emojiAnimals; if(emojiCategory==4) return emojiFood; if(emojiCategory==5) return emojiTravel; if(emojiCategory==6) return emojiObjects; if(emojiCategory==7) return emojiSymbols; return emojiFaces;
    }
    private void drawEmoji(Canvas c){
        p.setColor(BG); c.drawRect(0,0,getWidth(),getHeight(),p);
        String[] cats={"最近","表情","人物","動物","食物","旅行","物品","符號"}; float catH=dp(40),catW=getWidth()/8f;
        for(int i=0;i<8;i++){ RectF r=new RectF(i*catW,0,(i+1)*catW,catH); if(i==emojiCategory){ p.setColor(Color.rgb(197,198,204)); c.drawRoundRect(r,dp(8),dp(8),p); } drawTextCentered(c,cats[i],r,13); hits.add(new Hit(r,"EMOJI_CAT",String.valueOf(i))); }
        t.setTextAlign(Paint.Align.LEFT); t.setTextSize(dp(14)); t.setColor(Color.DKGRAY); c.drawText("分類搜尋｜上下滑查看更多",dp(14),catH+dp(22),t);
        float top=catH+dp(32),safeBottom=getHeight()-dp(70),backH=dp(48),listBottom=safeBottom-backH,cellH=dp(48); int cols=7,rows=Math.max(1,(int)Math.floor((listBottom-top)/cellH)); String[] list=emojiItems(); int pageSize=rows*cols; if(emojiOff>=list.length) emojiOff=0; int n=Math.max(0,Math.min(pageSize,list.length-emojiOff)); float cellW=getWidth()/(float)cols;
        for(int i=0;i<n;i++){ int row=i/cols,col=i%cols; RectF r=new RectF(col*cellW,top+row*cellH,(col+1)*cellW,top+(row+1)*cellH); drawTextCentered(c,list[emojiOff+i],r,25); hits.add(new Hit(r,"EMOJI_CHAR",list[emojiOff+i])); }
        RectF back=new RectF(0,listBottom,dp(110),safeBottom); hits.add(new Hit(back,"MAIN","")); drawTextCentered(c,"⌨",back,22);
    }
    private void drawKaomoji(Canvas c){
        p.setColor(BG); c.drawRect(0,0,getWidth(),getHeight(),p); float top=dp(8),rowH=dp(46),safeBottom=getHeight()-dp(70),backH=dp(48),listBottom=safeBottom-backH; int cols=3,rows=Math.max(1,(int)Math.floor((listBottom-top)/rowH)); int pageSize=rows*cols; if(kaoOff>=kaos.length) kaoOff=0; int n=Math.max(0,Math.min(pageSize,kaos.length-kaoOff)); float cellW=getWidth()/(float)cols;
        for(int i=0;i<n;i++){ int row=i/cols,col=i%cols; RectF r=new RectF(col*cellW,top+row*rowH,(col+1)*cellW,top+(row+1)*rowH); p.setColor(Color.rgb(205,206,211)); c.drawRect(r.left,r.bottom-1,r.right,r.bottom,p); drawTextCentered(c,kaos[kaoOff+i],r,15); hits.add(new Hit(r,"KAO_CHAR",kaos[kaoOff+i])); }
        RectF back=new RectF(0,listBottom,dp(110),safeBottom); hits.add(new Hit(back,"MAIN","")); drawTextCentered(c,"⌨",back,22);
    }

    private List<String> recentEmoji(){ String raw=prefs.getString("recent_emoji",""); if(raw.isEmpty()) return new ArrayList<>(); return new ArrayList<>(Arrays.asList(raw.split(RECENT_SEP,-1))); }
    private void saveRecentEmoji(String e){ List<String> r=recentEmoji(); r.remove(e); r.add(0,e); if(r.size()>42) r=new ArrayList<>(r.subList(0,42)); prefs.edit().putString("recent_emoji",String.join(RECENT_SEP,r)).apply(); }

    private List<String> candidates(){
        if(mode==Mode.CANGJIE){
            if(cjCode.isEmpty()) return withLearnedNext(Arrays.asList("的","嗎","為","成","過","變","法","我","妳","你","是","有","在","不","人"));
            LinkedHashSet<String> out=new LinkedHashSet<>(); List<String> ex=cjExact.get(cjCode); if(ex!=null) out.addAll(boost(ex)); List<String> pr=cj.get(cjCode); if(pr!=null) out.addAll(boost(pr)); if(out.size()<7) out.addAll(cangjieTypoCandidates(cjCode)); return new ArrayList<>(out);
        }
        if(mode==Mode.ZHUYIN){
            if(zyCode.isEmpty()) return withLearnedNext(Arrays.asList("的","我","是","了","不","在","有","妳","你","這","人","要","好","就","也"));
            LinkedHashSet<String> out=new LinkedHashSet<>(); List<String> ex=zy.get(zyCode); if(ex!=null) out.addAll(boost(ex)); ArrayList<String> pr=new ArrayList<>(); for(Map.Entry<String,List<String>> e:zy.entrySet()) if(!e.getKey().equals(zyCode)&&e.getKey().startsWith(zyCode)) pr.addAll(e.getValue()); out.addAll(boost(pr)); return new ArrayList<>(out);
        }
        return Arrays.asList("i","the","i’m","and","to","you","a","is","of","it","that","for","in","on");
    }
    private List<String> withLearnedNext(List<String> base){ LinkedHashSet<String> out=new LinkedHashSet<>(); if(learningEnabled()&&!lastCommitted.isEmpty()) out.addAll(learnedNext(lastCommitted)); out.addAll(phraseSuggestions(lastCommitted)); out.addAll(sentencePunctuation(lastCommitted)); out.addAll(base); return new ArrayList<>(out); }
    private List<String> phraseSuggestions(String s){
        if(s==null||s.isEmpty()) return Collections.emptyList(); String tail=s.substring(s.length()-1);
        switch(tail){
            case "我": return Arrays.asList("我覺得","我們","我的","我要","我想","我知道","我可以","我沒有");
            case "妳": return Arrays.asList("妳要","妳看","妳可以","妳知道","妳覺得","妳的","妳有");
            case "你": return Arrays.asList("你要","你看","你可以","你知道","你好","你的","你有");
            case "不": return Arrays.asList("不是","不要","不會","不用","不知道","不可能","不錯","不需要");
            case "好": return Arrays.asList("好像","好的","好吧","好啊","好啦","好嗎","好看","好用");
            case "沒": return Arrays.asList("沒有","沒關係","沒辦法","沒事","沒問題","沒想到");
            case "怎": return Arrays.asList("怎麼","怎樣","怎麼辦","怎麼會");
            case "為": return Arrays.asList("為什麼","為了","為何");
            case "可": return Arrays.asList("可以","可能","可是","可不可以");
            case "真": return Arrays.asList("真的","真的嗎","真的是","真好");
            case "這": return Arrays.asList("這個","這樣","這裡","這次","這麼","這版");
            case "那": return Arrays.asList("那個","那麼","那就","那邊","那時候");
            case "會": return Arrays.asList("會不會","會有","會變","會比較","會更");
            case "要": return Arrays.asList("要不要","要怎麼","要用","要做","要改");
            case "很": return Arrays.asList("很好","很像","很多","很難","很方便","很清楚");
            case "太": return Arrays.asList("太好了","太大","太小","太多","太少");
            case "如": return Arrays.asList("如果","如何");
            case "所": return Arrays.asList("所以","所有");
            case "因": return Arrays.asList("因為","因此");
            case "還": return Arrays.asList("還是","還有","還沒","還要","還可以");
            case "現": return Arrays.asList("現在","現有","目前");
            case "已": return Arrays.asList("已經","已有");
            case "之": return Arrays.asList("之後","之前","之間");
            case "輸": return Arrays.asList("輸入法","輸入","輸出");
            case "按": return Arrays.asList("按鍵","按下","按到");
            case "字": return Arrays.asList("字根","字體","字詞","字典");
            case "常": return Arrays.asList("常用字","常用詞","常常","常見");
            case "候": return Arrays.asList("候選字","候選詞","候選列");
            case "更": return Arrays.asList("更新","更改","更加","更好");
            case "可": default: return Collections.emptyList();
        }
    }
    private List<String> sentencePunctuation(String s){ if(s==null||s.isEmpty()) return Collections.emptyList(); String tail=s.substring(s.length()-1); if("嗎呢嘛".contains(tail)) return Arrays.asList("？","。","！"); if("吧啦喔啊".contains(tail)) return Arrays.asList("。","！","？"); if("了呀".contains(tail)) return Arrays.asList("。","，","！"); return Collections.emptyList(); }

    private String neighborLetters(char ch){
        switch(ch){
            case 'q':return "wa"; case 'w':return "qeas"; case 'e':return "wrsd"; case 'r':return "etdf"; case 't':return "ryfg"; case 'y':return "tugh"; case 'u':return "yihj"; case 'i':return "uojk"; case 'o':return "ipkl"; case 'p':return "ol";
            case 'a':return "qwsz"; case 's':return "awedxz"; case 'd':return "serfcx"; case 'f':return "drtgcv"; case 'g':return "ftyhbv"; case 'h':return "gyujbn"; case 'j':return "huikmn"; case 'k':return "jiolm"; case 'l':return "kop";
            case 'z':return "asx"; case 'x':return "zsdc"; case 'c':return "xdfv"; case 'v':return "cfgb"; case 'b':return "vghn"; case 'n':return "bhjm"; case 'm':return "njk"; default:return "";
        }
    }
    private List<String> cangjieTypoCandidates(String code){ LinkedHashSet<String> out=new LinkedHashSet<>(); char[] a=code.toCharArray(); for(int i=0;i<a.length;i++){ char orig=a[i]; String near=neighborLetters(orig); for(int j=0;j<near.length();j++){ a[i]=near.charAt(j); List<String> x=cjExact.get(new String(a)); if(x!=null) for(String s:boost(x)){ out.add(s); if(out.size()>=14){ a[i]=orig; return new ArrayList<>(out); } } } a[i]=orig; } return new ArrayList<>(out); }

    private List<String> learnedNext(String prev){ String prefix="b_"+prev+"_"; ArrayList<String> words=new ArrayList<>(); for(String k:prefs.getAll().keySet()) if(k.startsWith(prefix)) words.add(k.substring(prefix.length())); words.sort(Comparator.comparingInt((String s)->-prefs.getInt(prefix+s,0))); if(words.size()>12) return new ArrayList<>(words.subList(0,12)); return words; }
    private List<String> boost(List<String> src){ ArrayList<String> out=new ArrayList<>(src); if(learningEnabled()) out.sort(Comparator.comparingInt((String s)->-prefs.getInt("f_"+s,0))); return out; }
    private void learn(String s){ if(s==null||s.isEmpty()) return; if(learningEnabled()){ SharedPreferences.Editor e=prefs.edit(); e.putInt("f_"+s,Math.min(100000,prefs.getInt("f_"+s,0)+1)); if(!lastCommitted.isEmpty()){ String k="b_"+lastCommitted+"_"+s; e.putInt(k,Math.min(100000,prefs.getInt(k,0)+1)); } e.apply(); } lastCommitted=s; }

    private String composition(){ if(page!=Page.MAIN) return ""; if(mode==Mode.CANGJIE&&!cjCode.isEmpty()) return roots(cjCode); if(mode==Mode.ZHUYIN&&!zyCode.isEmpty()) return zyCode; return ""; }
    private void syncComposition(){ svc.updateComposition(composition()); }
    private String roots(String code){ String letters="abcdefghijklmnopqrstuvwxyz",roots="日月金木水火土竹戈十大中一弓人心手口尸廿山女田難卜符"; StringBuilder b=new StringBuilder(); for(char ch:code.toCharArray()){ int i=letters.indexOf(ch); if(i>=0)b.append(roots.charAt(i)); } return b.toString(); }
    private boolean isPunctuation(String s){ return s!=null&&!s.isEmpty()&&"，。？！、…；：,.?!".contains(s); }
    private void choose(String s){ if(s==null||s.isEmpty()) return; svc.commit(s); learn(s); if(isPunctuation(s)) lastCommitted=""; cjCode=""; zyCode=""; candOff=0; expanded=false; syncComposition(); invalidate(); }

    private void feedback(){ if(settings.getBoolean("key_sound",true)){ AudioManager am=(AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE); if(am!=null) am.playSoundEffect(AudioManager.FX_KEY_CLICK,0.35f); } if(settings.getBoolean("key_vibration",false)) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); }

    private boolean canExpand(Hit h){ return Arrays.asList("CJ","ZY","LETTER","SHIFT","BACK","NUMBER","PUNCT","NUM","SYM","MAIN","EMOJI","SPACE","ENTER").contains(h.action); }
    private boolean sidePunct(Hit h){ return page==Page.MAIN&&mode==Mode.CANGJIE&&"PUNCT".equals(h.action)&&("，".equals(h.value)||"。".equals(h.value)); }
    private Hit findHit(float x,float y){
        for(int i=hits.size()-1;i>=0;i--) if(hits.get(i).r.contains(x,y)) return hits.get(i);
        Hit best=null; float bestD=Float.MAX_VALUE;
        for(int i=hits.size()-1;i>=0;i--){ Hit h=hits.get(i); if(!canExpand(h)) continue; float px=sidePunct(h)?dp(3):dp(12),py=sidePunct(h)?dp(5):dp(10); RectF rr=new RectF(h.r); rr.inset(-px,-py); if(!rr.contains(x,y)) continue; float dx=Math.max(Math.max(h.r.left-x,0),x-h.r.right),dy=Math.max(Math.max(h.r.top-y,0),y-h.r.bottom),d=dx*dx+dy*dy; if(d<bestD){bestD=d;best=h;} }
        return best;
    }
    private boolean sameHit(Hit a,Hit b){ return a!=null&&b!=null&&a.action.equals(b.action)&&((a.value==null&&b.value==null)||(a.value!=null&&a.value.equals(b.value))); }

    private void pageCandidates(boolean forward){ List<String> items=candidates(); int n=7*(expanded?3:1); if(forward){ if(candOff+n<items.size()) candOff+=n; }else candOff=Math.max(0,candOff-n); invalidate(); }
    private void scrollEmoji(boolean forward){ String[] list=emojiItems(); int rows=Math.max(1,(int)Math.floor(((getHeight()-dp(70)-dp(48))-(dp(40)+dp(32)))/dp(48))); int pageSize=rows*7; if(forward) emojiOff=Math.min(Math.max(0,list.length-pageSize),emojiOff+pageSize); else emojiOff=Math.max(0,emojiOff-pageSize); invalidate(); }
    private void scrollKaomoji(boolean forward){ int rows=Math.max(1,(int)Math.floor(((getHeight()-dp(70)-dp(48))-dp(8))/dp(46))); int pageSize=rows*3; if(forward) kaoOff=Math.min(Math.max(0,kaos.length-pageSize),kaoOff+pageSize); else kaoOff=Math.max(0,kaoOff-pageSize); invalidate(); }

    @Override public boolean onTouchEvent(MotionEvent e){
        float x=e.getX(),y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){ downAt=SystemClock.uptimeMillis(); downX=x; downY=y; downHit=findHit(x,y); backspaceRepeating=false; longPressDirect=false; if(downHit!=null&&"BACK".equals(downHit.action)) handler.postDelayed(repeatBackspace,420); if(downHit!=null&&page==Page.MAIN&&("CJ".equals(downHit.action)||"ZY".equals(downHit.action))) handler.postDelayed(directSymbolLongPress,430); return true; }
        if(e.getAction()==MotionEvent.ACTION_CANCEL){ handler.removeCallbacks(repeatBackspace); handler.removeCallbacks(directSymbolLongPress); downHit=null; return true; }
        if(e.getAction()==MotionEvent.ACTION_MOVE){ if(Math.hypot(x-downX,y-downY)>dp(22)) handler.removeCallbacks(directSymbolLongPress); return true; }
        if(e.getAction()!=MotionEvent.ACTION_UP) return true;
        handler.removeCallbacks(repeatBackspace); handler.removeCallbacks(directSymbolLongPress);
        float dx=x-downX,dy=y-downY,adx=Math.abs(dx),ady=Math.abs(dy);
        if(page==Page.EMOJI&&ady>dp(32)&&ady>adx*1.2f){ feedback(); scrollEmoji(dy<0); downHit=null; return true; }
        if(page==Page.KAOMOJI&&ady>dp(32)&&ady>adx*1.2f){ feedback(); scrollKaomoji(dy<0); downHit=null; return true; }
        if(page!=Page.EMOJI&&page!=Page.KAOMOJI&&downY<candH()*(expanded?3:1)&&adx>dp(34)&&adx>ady*1.25f){ feedback(); pageCandidates(dx<0); downHit=null; return true; }
        if(downHit!=null&&"SPACE".equals(downHit.action)&&cjCode.isEmpty()&&zyCode.isEmpty()){ float th=Math.max(dp(42),downHit.r.width()*0.16f); if(adx>th&&adx>ady*1.5f){ feedback(); if(dx<0) cycleMode(); else cycleModeBackward(); downHit=null; return true; } }
        if(longPressDirect){ downHit=null; return true; }
        Hit up=findHit(x,y); float move=(float)Math.hypot(dx,dy);
        if(downHit!=null){ if("BACK".equals(downHit.action)&&backspaceRepeating){} else if(move<=dp(28)||sameHit(downHit,up)){ feedback(); act(downHit); } }
        downHit=null; return true;
    }

    private final Runnable directSymbolLongPress=new Runnable(){ @Override public void run(){ if(downHit==null||!("CJ".equals(downHit.action)||"ZY".equals(downHit.action))) return; longPressDirect=true; feedback(); cjCode=""; zyCode=""; candOff=0; expanded=false; svc.commit(downHit.label); lastCommitted=""; syncComposition(); invalidate(); } };
    private final Runnable repeatBackspace=new Runnable(){ @Override public void run(){ if(downHit==null||!"BACK".equals(downHit.action)) return; backspaceRepeating=true; doBackspace(); handler.postDelayed(this,70); } };

    private void act(Hit h){
        switch(h.action){
            case "CJ": if(cjCode.length()<5){cjCode+=h.value;candOff=0;syncComposition();invalidate();} break;
            case "ZY": if(zyCode.length()<8){zyCode+=h.value;candOff=0;syncComposition();invalidate();} break;
            case "LETTER": commitLetter(h.value); break;
            case "SHIFT": shift(); break;
            case "BACK": doBackspace(); break;
            case "NUMBER": svc.commit(h.value); lastCommitted=""; break;
            case "PUNCT": commitPunctuation(h.value); break;
            case "NUM": page=Page.NUM; syncComposition(); invalidate(); break;
            case "SYM": page=Page.SYM; syncComposition(); invalidate(); break;
            case "MAIN": page=Page.MAIN; syncComposition(); invalidate(); break;
            case "EMOJI": page=Page.EMOJI; emojiCategory=1; emojiOff=0; syncComposition(); invalidate(); break;
            case "KAO": page=Page.KAOMOJI; kaoOff=0; syncComposition(); invalidate(); break;
            case "EMOJI_CAT": emojiCategory=Integer.parseInt(h.value); emojiOff=0; invalidate(); break;
            case "EMOJI_CHAR": svc.commit(h.value); saveRecentEmoji(h.value); invalidate(); break;
            case "KAO_CHAR": svc.commit(h.value); invalidate(); break;
            case "SPACE": doSpace(); break;
            case "ENTER": svc.enter(); lastCommitted=""; break;
            case "EXPAND": expanded=!expanded; candOff=0; invalidate(); break;
            case "CAND": choose(h.value); break;
            case "MIC": svc.voiceComingSoon(); break;
        }
    }
    private void commitPunctuation(String s){ svc.commit(s); if(!lastCommitted.isEmpty()) learn(s); lastCommitted=""; page=Page.MAIN; candOff=0; syncComposition(); invalidate(); }
    private void commitLetter(String s){ String out=(shift||caps)?s.toUpperCase(Locale.ROOT):s; svc.commit(out); lastCommitted=""; if(shift&&!caps) shift=false; invalidate(); }
    private void shift(){ if(shift&&!caps){caps=true;shift=true;} else if(caps){caps=false;shift=false;} else shift=true; invalidate(); }
    private void doBackspace(){ if(mode==Mode.CANGJIE&&!cjCode.isEmpty()){cjCode=cjCode.substring(0,cjCode.length()-1);candOff=0;syncComposition();invalidate();return;} if(mode==Mode.ZHUYIN&&!zyCode.isEmpty()){int cp=zyCode.codePointBefore(zyCode.length());zyCode=zyCode.substring(0,zyCode.length()-Character.charCount(cp));candOff=0;syncComposition();invalidate();return;} svc.backspace(); }
    private void doSpace(){ if(mode==Mode.CANGJIE&&!cjCode.isEmpty()){List<String>a=candidates();if(!a.isEmpty())choose(a.get(0));return;} if(mode==Mode.ZHUYIN&&!zyCode.isEmpty()){List<String>a=candidates();if(!a.isEmpty())choose(a.get(0));return;} svc.commit(" "); lastCommitted=""; }
    private void cycleMode(){ if(mode==Mode.CANGJIE)mode=Mode.ENGLISH;else if(mode==Mode.ENGLISH)mode=Mode.ZHUYIN;else mode=Mode.CANGJIE; page=Page.MAIN;cjCode="";zyCode="";expanded=false;candOff=0;syncComposition();invalidate(); }
    private void cycleModeBackward(){ if(mode==Mode.CANGJIE)mode=Mode.ZHUYIN;else if(mode==Mode.ZHUYIN)mode=Mode.ENGLISH;else mode=Mode.CANGJIE; page=Page.MAIN;cjCode="";zyCode="";expanded=false;candOff=0;syncComposition();invalidate(); }

    public void refreshSettings(){ requestLayout(); invalidate(); post(() -> svc.repositionCompositionPopup()); }
}

from pathlib import Path
import json
import re

JAVA = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
MAIN = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
GRADLE = Path('imeapp/app/build.gradle')

s = JAVA.read_text(encoding='utf-8')


def sub1(pattern, replacement, text=None, flags=0, label='fragment'):
    global s
    target = s if text is None else text
    out, n = re.subn(pattern, replacement, target, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f'v0.9 patch failed for {label}: matched {n}')
    if text is None:
        s = out
    return out


# Restore the exact pre-punctuation Cangjie third-row root positions. The comma and
# period occupy only the previously unused spaces, so muscle memory is untouched.
new_cangjie = r'''    private void drawCangjie(Canvas c) {
        for (int i=0;i<10;i++) { float x=18+i*116.4f; drawMainKey(c,x,0,x+96,122,c1[i],17.68f,"CJ",String.valueOf(k1[i])); }
        for (int i=0;i<9;i++) { float x=76+i*114.8f; drawMainKey(c,x,155,x+96,282,c2[i],17.68f,"CJ",String.valueOf(k2[i])); }

        // Original third-row geometry: roots stay exactly where they were before punctuation was added.
        drawMainKey(c,38,318,96,444,"，",17.68f,"PUNCT","，");
        for (int i=0;i<7;i++) {
            float x=116+i*116.5f;
            drawMainKey(c,x,318,x+96,444,c3[i],17.68f,"CJ",String.valueOf(k3[i]));
        }
        drawMainKey(c,935,318,993,444,"。",17.68f,"PUNCT","。");
        drawKey(c,1017,318,1150,444,"⌫",24,"BACK","");
        drawBottom(c,"123","倉");
    }

'''
sub1(r'    private void drawCangjie\(Canvas c\) \{.*?\n    \}\n\n(?=    private void drawEnglish)', new_cangjie, flags=re.S, label='Cangjie geometry')

# Numbers and symbols use the same 70-100% text-size control as the three main keyboards.
new_numbers = r'''    private void drawNumbers(Canvas c) {
        for (int i=0;i<10;i++) { float x=18+i*116.4f; drawMainKey(c,x,0,x+96,122,String.valueOf((i+1)%10),17.68f,"NUMBER",String.valueOf((i+1)%10)); }
        String[] row2 = mode==Mode.ENGLISH ? en2 : cn2;
        for (int i=0;i<10;i++) { float x=18+i*116.4f; drawMainKey(c,x,155,x+96,282,row2[i],17.68f,"PUNCT",row2[i]); }

        String[] row3 = mode==Mode.ENGLISH ?
                new String[]{"🔣",".",",","?","!","'"} :
                new String[]{"🔣","。","，","、","？","！","．"};
        float step = mode==Mode.ENGLISH ? 158f : 136f;
        float width = mode==Mode.ENGLISH ? 138f : 116f;
        for (int i=0;i<row3.length;i++) {
            float x=18+i*step;
            drawMainKey(c,x,318,x+width,444,row3[i],17.68f,i==0?"SYM":"PUNCT",row3[i]);
        }
        drawKey(c,1017,318,1150,444,"⌫",24,"BACK","");
        drawAltBottom(c);
    }

'''
sub1(r'    private void drawNumbers\(Canvas c\) \{.*?\n    \}\n\n(?=    private void drawSymbols)', new_numbers, flags=re.S, label='number page text sizing')

new_symbols = r'''    private void drawSymbols(Canvas c) {
        for (int i=0;i<10;i++) { float x=18+i*116.4f; drawMainKey(c,x,0,x+96,122,sy1[i],17.68f,"PUNCT",sy1[i]); }
        String[] row2 = mode==Mode.ENGLISH ? syE2 : syC2;
        float cell = IW / row2.length;
        for (int i=0;i<row2.length;i++) {
            float x=i*cell+10;
            drawMainKey(c,x,155,x+cell-20,282,row2[i],17.68f,"PUNCT",row2[i]);
        }
        String[] row3 = mode==Mode.ENGLISH ?
                new String[]{"123",".",",","?","!","'"} :
                new String[]{"123","…","，","^_^","？","！","'"};
        float step = mode==Mode.ENGLISH ? 158f : 136f;
        float width = mode==Mode.ENGLISH ? 138f : 116f;
        for (int i=0;i<row3.length;i++) {
            float x=18+i*step;
            String action = i==0 ? "NUM" : ((mode!=Mode.ENGLISH && i==3) ? "KAO" : "PUNCT");
            float size = i==0 ? 19.0f : 17.68f;
            drawMainKey(c,x,318,x+width,444,row3[i],size,action,row3[i]);
        }
        drawKey(c,1017,318,1150,444,"⌫",24,"BACK","");
        drawAltBottom(c);
    }

'''
sub1(r'    private void drawSymbols\(Canvas c\) \{.*?\n    \}\n\n(?=    private void drawAltBottom)', new_symbols, flags=re.S, label='symbol page text sizing')

# Candidate phrases: learned continuations come first, then a Taiwan-Traditional curated
# continuation table, then punctuation and ordinary single-character defaults.
new_next = r'''    private List<String> withLearnedNext(List<String> base) {
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(learningEnabled()&&!lastCommitted.isEmpty()) out.addAll(learnedNext(lastCommitted));
        out.addAll(commonContinuations(lastCommitted));
        out.addAll(sentencePunctuation(lastCommitted));
        out.addAll(base);
        return new ArrayList<>(out);
    }

    private List<String> commonContinuations(String prev) {
        if(prev==null||prev.isEmpty()) return Collections.emptyList();
        String k=prev.substring(prev.length()-1);
        switch(k) {
            case "我": return Arrays.asList("覺得","知道","想要","可以","不會","還是");
            case "你": case "妳": return Arrays.asList("覺得","知道","可以","要不要","好嗎","也是");
            case "他": case "她": return Arrays.asList("覺得","知道","可以","也是","沒有");
            case "沒": return Arrays.asList("關係","辦法","問題","有","事","想到");
            case "有": return Arrays.asList("沒有","辦法","問題","時候","可能","一點");
            case "為": return Arrays.asList("什麼","何","了","什麼會");
            case "非": return Arrays.asList("常","洲","得","常好","常重要");
            case "可": return Arrays.asList("以","能","是","不可以");
            case "好": return Arrays.asList("像","的","啊","吧","嗎","多了");
            case "真": return Arrays.asList("的","的是","的嗎","好","棒");
            case "怎": return Arrays.asList("麼","樣","麼辦","麼會");
            case "因": return Arrays.asList("為","此");
            case "所": return Arrays.asList("以","有","謂");
            case "如": return Arrays.asList("果","何","果說");
            case "這": return Arrays.asList("樣","個","是","次","裡","邊");
            case "那": return Arrays.asList("個","麼","樣","就","裡","邊");
            case "不": return Arrays.asList("知道","用","會","是","要","錯");
            case "還": return Arrays.asList("是","有","可以","好","沒","要");
            case "已": return Arrays.asList("經","經有","經是");
            case "現": return Arrays.asList("在","在就","在是");
            case "需": return Arrays.asList("要","求");
            case "想": return Arrays.asList("要","知道","問","說","看看");
            case "知": return Arrays.asList("道","道了","不知道");
            case "覺": return Arrays.asList("得","得很","得是");
            case "請": return Arrays.asList("問","幫我","告訴我","再");
            case "幫": return Arrays.asList("我","忙","我看");
            case "對": return Arrays.asList("不起","啊","的","吧","嗎");
            case "謝": return Arrays.asList("謝","謝你","謝妳");
            case "再": return Arrays.asList("見","來","試","看看","一次");
            case "早": return Arrays.asList("安","上","點");
            case "晚": return Arrays.asList("安","上","點");
            case "很": return Arrays.asList("好","棒","像","多","重要","清楚");
            case "太": return Arrays.asList("好了","棒了","多","少","大","小");
            case "一": return Arrays.asList("樣","下","個","點","直","起");
            case "就": return Arrays.asList("是","可以","會","好","這樣");
            case "也": return Arrays.asList("是","可以","有","沒有","不錯");
            case "要": return Arrays.asList("不要","怎麼","用","改","做","去");
            case "能": return Arrays.asList("不能","夠","不能用","做到");
            case "應": return Arrays.asList("該","用","該要");
            case "會": return Arrays.asList("不會","有","是","比較","變成");
            case "看": return Arrays.asList("起來","看","得到","不到","一下");
            case "感": return Arrays.asList("覺","覺很","謝");
            case "輸": return Arrays.asList("入法","入","出");
            case "手": return Arrays.asList("機","機上","指");
            case "字": return Arrays.asList("根","體","詞","串","幕");
            case "常": return Arrays.asList("用","見","常","用字","用詞");
            case "更": return Arrays.asList("新","好","多","改","換");
            case "修": return Arrays.asList("正","改","正版");
            case "版": return Arrays.asList("本","面","型");
            case "可": return Arrays.asList("以","能","是");
            default: return Collections.emptyList();
        }
    }

'''
sub1(r'    private List<String> withLearnedNext\(List<String> base\) \{.*?\n    \}\n\n(?=    private List<String> sentencePunctuation)', new_next, flags=re.S, label='phrase continuations')

# Cangjie mistype rescue: only when the entered code has zero normal candidates, try
# replacing the LAST root with a left/right neighbor. Never rewrite the composition.
needle = '''        if(pref!=null) for(String item:boost(pref)) out.add(item);\n        return new ArrayList<>(out);'''
replacement = '''        if(pref!=null) for(String item:boost(pref)) out.add(item);\n        if(out.isEmpty()) out.addAll(cangjieMistypeCandidates(cjCode));\n        return new ArrayList<>(out);'''
if needle not in s:
    raise SystemExit('v0.9 patch failed: Cangjie candidate return not found')
s = s.replace(needle, replacement, 1)

mistype_helper = r'''    private List<String> cangjieMistypeCandidates(String code) {
        if(code==null||code.isEmpty()) return Collections.emptyList();
        char last=code.charAt(code.length()-1);
        String row = "qwertyuiop".indexOf(last)>=0 ? "qwertyuiop" :
                     ("asdfghjkl".indexOf(last)>=0 ? "asdfghjkl" : "zxcvbnm");
        int pos=row.indexOf(last);
        if(pos<0) return Collections.emptyList();
        LinkedHashSet<String> out=new LinkedHashSet<>();
        int[] near={pos-1,pos+1};
        for(int np:near) {
            if(np<0||np>=row.length()) continue;
            String alt=code.substring(0,code.length()-1)+row.charAt(np);
            List<String> exact=cjExact.get(alt);
            if(exact!=null) for(String item:boost(exact)) { out.add(item); if(out.size()>=12) break; }
            if(out.size()>=12) break;
            List<String> pref=cj.get(alt);
            if(pref!=null) for(String item:boost(pref)) { out.add(item); if(out.size()>=12) break; }
            if(out.size()>=12) break;
        }
        return new ArrayList<>(out);
    }

'''
anchor = '    private List<String> withLearnedNext(List<String> base) {'
if anchor not in s:
    raise SystemExit('v0.9 patch failed: withLearnedNext anchor missing')
s = s.replace(anchor, mistype_helper + anchor, 1)

# Let phrase candidates fit inside the seven-column strip without losing ordinary single-char size.
needle = '''            String item=items.get(candOff+i);\n            c.drawText(item,r.centerX(),row*h+h*.67f,t);'''
replacement = '''            String item=items.get(candOff+i);\n            int cps=item.codePointCount(0,item.length());\n            float itemSize=cps>=4?14.5f:(cps==3?16.5f:(cps==2?19f:22f));\n            t.setTextSize(dp(itemSize*candScale()));\n            c.drawText(item,r.centerX(),row*h+h*.67f,t);'''
if needle not in s:
    raise SystemExit('v0.9 patch failed: candidate draw loop not found')
s = s.replace(needle, replacement, 1)

# Much larger Emoji and kaomoji collections. Keep the user's existing favorites first.
emojis = [
    "😅","🥰","👍","🥲","🍰","😳","🤣","😜","😭","😓","😵‍💫","😥","⬇️","🙄",
    "😆","💕","😁","💝","🙏","🚑","😀","😂","😱","❤️","🎂","🥺","😄","🌹",
    "😊","😉","😍","🤔","😴","🤗","🎉","🔥","✨","💯","👏","🙌","💪","👌",
    "😃","😋","😎","🤩","😘","😗","☺️","🙂","🤭","🫣","🫢","🤫","🫡","🫠",
    "😐","😑","😶","🫥","😏","😒","😬","😮‍💨","🤥","😌","😔","😪","🤤","😵",
    "🤯","🤠","🥳","🥸","😇","🤓","🧐","😕","🫤","😟","🙁","☹️","😮","😯",
    "😲","😧","😨","😰","😢","😖","😣","😞","😩","🥱","😤","😡","😠","🤬",
    "😈","👿","💀","☠️","💩","🤡","👻","👽","🤖","😺","😸","😹","😻","😼",
    "😽","🙀","😿","😾","🙈","🙉","🙊","💋","💌","💘","💖","💗","💓","💞",
    "💔","❤️‍🔥","❤️‍🩹","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💟","❣️",
    "👋","🤚","🖐️","✋","🖖","🫱","🫲","🫶","🤝","✌️","🤞","🫰","🤟","🤘",
    "🤙","👈","👉","👆","👇","☝️","✍️","🤳","💅","👀","👁️","👂","🧠","🫀",
    "👶","🧒","👧","🧑","👩","👨","👵","👴","🙍","🙎","🙅","🙆","💁","🙋",
    "🧏","🙇","🤦","🤷","💆","💇","🚶","🏃","💃","🕺","🧘","🛌","🗣️","👤",
    "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸",
    "🐵","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🐺","🐗","🐴","🦄","🐝","🦋",
    "🐌","🐞","🐜","🪲","🐢","🐍","🦎","🐙","🦑","🦐","🦀","🐠","🐟","🐬",
    "🌸","🌼","🌻","🌺","🌷","🪻","🌱","🌿","☘️","🍀","🌵","🌴","🌲","🍁",
    "🍎","🍊","🍋","🍉","🍇","🍓","🫐","🍒","🥝","🍅","🥑","🥦","🥕","🌽",
    "🍞","🥐","🥖","🥨","🧀","🥚","🍳","🥞","🧇","🍔","🍟","🍕","🌭","🥪",
    "🍜","🍝","🍣","🍱","🍙","🍚","🍛","🍲","🥟","🍤","🍦","🍩","🍪","🍫",
    "☕","🍵","🧋","🥤","🧃","🍺","🍻","🥂","🍷","🧊","🥢","🍴","🥄","🔪",
    "⚽","🏀","🏈","⚾","🎾","🏐","🏓","🏸","🥊","🏊","🚴","🏆","🥇","🎯",
    "🎮","🎲","🧩","🎨","🎬","🎤","🎧","🎵","🎶","🎹","🥁","📷","📸","📱",
    "💻","⌚","📺","💡","🔦","🔋","🔌","💰","💳","🎁","🎈","🛒","📦","✉️",
    "📩","📌","📍","📎","✏️","📝","📅","📆","🔍","🔒","🔑","🗑️","✅","☑️",
    "❌","⭕","❗","❓","‼️","⁉️","⚠️","🚫","🔴","🟠","🟡","🟢","🔵","🟣",
    "⬆️","➡️","⬅️","↗️","↘️","↙️","↖️","↔️","↕️","🔁","🔄","▶️","⏸️","⏩",
    "🚗","🚕","🚌","🚆","🚇","✈️","🚀","🚲","🛵","🚦","🗺️","🏠","🏥","🏫",
    "🌍","🌏","🌎","☀️","🌤️","☁️","🌧️","⛈️","🌈","❄️","☃️","🌙","⭐","🌟"
]

kaos = [
    "^_^","^^","(^_^)","(^ ^)","(^_-^)","^o^","(o^^o)","(^_^)a","(^_^)v",":)",":(",":-)",
    "=)","=(",";-)",":-|",":-(",":-D",":D",":-P",":P","凸^_^凸","(´▽｀)","(*^^*)","(*^_^*)",
    "(^_^*)","*^_^*","V(^_^)V","Y(^_^)Y","d(^_^o)","o(^_^)o","p(^_^)q","(#^.^#)","(*^o^*)",
    "(^.^)","(^O^)","(^o^)","(^｡^)","(^○^)",")^o^(","*^O^*","=^.^=","(^▽^)","o(^▽^)o",
    "(^◇^)","(^3^)","(^3^)-☆","(*^3^)","(^ω^)","(>^ω^<)","^ω^","┌(^ω^)┐","↖(^ω^)↗",
    "(^人^)","^*^","〜^_^","(∩_∩)","O(∩_∩)O","o(∩_∩)o","(￣▽￣)","(*￣︶￣*)","(*￣▽￣*)",
    "(*☺-☺*)","(T_T)","(╥﹏╥)","(>_<)","(；ω；)","(｡•́︿•̀｡)","(ง •̀_•́)ง","ᕦ(ò_óˇ)ᕤ",
    "ヽ(•‿•)ノ","¯\\_(ツ)_/¯","(¬_¬)",
    "XD","XDD","XDDD","QQ","Q_Q","QAQ","TAT","T_T","Orz","orz","OTZ","囧","= =","=_=","-_-","=_=|||",
    "(・∀・)","(・ω・)","(・_・;)","(´・ω・`)","(´･_･`)","(´▽`ʃ♡ƪ)","(´∀｀)♡","(´ε｀ )♡","(｡♥‿♥｡)",
    "(♡˙︶˙♡)","(๑´ڡ`๑)","(๑•̀ㅂ•́)و✧","(๑•̀ᄇ•́)و ✧","٩(ˊᗜˋ*)و","٩(๑❛ᴗ❛๑)۶","٩(｡•́‿•̀｡)۶",
    "ヾ(≧▽≦*)o","ヽ(✿ﾟ▽ﾟ)ノ","ヾ(•ω•`)o","(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧","(ﾉ´ヮ`)ﾉ*: ･ﾟ","(✧∀✧)/","(๑˃̵ᴗ˂̵)و",
    "(≧▽≦)","(≧ω≦)","(≧∇≦)/","(≧∀≦)ゞ","(๑>◡<๑)","(๑˘︶˘๑)","(￣▽￣)ノ","ヾ(´︶`*)ﾉ♬",
    "(づ｡◕‿‿◕｡)づ","(つ≧▽≦)つ","(っ˘ω˘ς )","⊂(・▽・⊂)","(つ✧ω✧)つ","(っ´▽`)っ",
    "(´；ω；｀)","(இ﹏இ`｡)","(ಥ﹏ಥ)","(ಥ_ಥ)","(╥_╥)","(｡•́︿•̀｡)","(つ﹏⊂)","(っ˘̩╭╮˘̩)っ",
    "(╯︵╰,)","(ノ_<。)","(;´༎ຶД༎ຶ`)","｡ﾟ(ﾟ´Д｀ﾟ)ﾟ｡","(ó﹏ò｡)","(ᗒᗣᗕ)՞",
    "(¬‿¬)","(¬_¬ )","(눈_눈)","(￢_￢)","(ಠ_ಠ)","ಠ_ಠ","ಠ‿ಠ","(；一_一)","(￣ー￣)","(￣^￣)",
    "(╬ಠ益ಠ)","(ノಠ益ಠ)ノ","(ง'̀-'́)ง","(ง •̀_•́)ง","(╯°□°）╯︵ ┻━┻","┬─┬ ノ( ゜-゜ノ)","┬─┬ ﾉ(° -°ﾉ)",
    "(╯°Д°）╯︵ /(.□ . \\)","ヽ(`Д´)ﾉ","(＃`Д´)","(｀Д´*)","(ﾒﾟ皿ﾟ)ﾌﾝｶﾞｰ","(#｀ε´#)",
    "Σ(ﾟДﾟ)","Σ(°△°|||)︴","Σ(っ °Д °;)っ","(⊙_⊙)","(⊙o⊙)","(°ロ°) !","(O_O;)","(ﾟДﾟ;)",
    "(・・ ) ?","(・_・ヾ","(・・?)","(°ー°〃)","(´･ω･`)?","(・・;)","(・・;)ゞ",
    "(￣o￣) . z Z","(＿ ＿*) Z z z","(－_－) zzZ","(∪｡∪)｡｡｡zzZ","(￣ρ￣)..zzZZ",
    "m(_ _)m","<(_ _)>","(シ_ _)シ","(人´∀｀)．☆．。．:*･ﾟ","(人 •͈ᴗ•͈)","(人´ω｀*)♡","(ㅅ´ ˘ `)♡",
    "(*￣3￣)╭","( ˘ ³˘)♥","(づ￣ ³￣)づ","(っ˘з(˘⌣˘ )","( ˘⌣˘)♡(˘⌣˘ )","♡(ŐωŐ人)",
    "♪(´▽｀)","(〜￣▽￣)〜","〜(￣▽￣〜)","ヘ(￣ω￣ヘ)","(ノ￣ω￣)ノ","└(￣-￣└))","((┘￣ω￣)┘",
    "ᕕ( ᐛ )ᕗ","ᕙ(⇀‸↼‶)ᕗ","ᕦ(ò_óˇ)ᕤ","ᕦ(ò_óˇ)ᕤ","୧(๑•̀ᗝ•́)૭","୧(๑•̀⌄•́๑)૭✧",
    "ʕ•ᴥ•ʔ","ʕっ•ᴥ•ʔっ","ʕノ)ᴥ(ヾʔ","ฅ^•ﻌ•^ฅ","(=^･ω･^=)","/ᐠ｡ꞈ｡ᐟ\\","U・ᴥ・U","▼・ᴥ・▼",
    "(☞ﾟヮﾟ)☞","☜(ﾟヮﾟ☜)","☞￣ᴥ￣☞","←_←","→_→","↑_↑","↓_↓","(☞ ಠ_ಠ)☞",
    "¯\\_(ツ)_/¯","┐(´ー｀)┌","┐(￣ヘ￣)┌","╮(￣▽￣)╭","╮(╯▽╰)╭","乁( •_• )ㄏ","乁( . ര ʖ̯ ര . )ㄏ"
]


def java_array(name, values):
    lines=[f'    private final String[] {name} = {{']
    for i in range(0,len(values),8):
        chunk=values[i:i+8]
        suffix=',' if i+8 < len(values) else ''
        lines.append('            '+','.join(json.dumps(v,ensure_ascii=False) for v in chunk)+suffix)
    lines.append('    };')
    return '\n'.join(lines)

s, n = re.subn(r'    private final String\[\] emojis = \{.*?\n    \};', java_array('emojis', emojis), s, count=1, flags=re.S)
if n != 1: raise SystemExit('v0.9 patch failed: emoji array')
s, n = re.subn(r'    private final String\[\] kaos = \{.*?\n    \};', java_array('kaos', kaos), s, count=1, flags=re.S)
if n != 1: raise SystemExit('v0.9 patch failed: kaomoji array')

# Emoji paging/vertical swipe with ColorOS safe-area preserved. Remove the fake search label.
s = s.replace('    private int kaoOff = 0;\n    private int candOff = 0;', '    private int kaoOff = 0;\n    private int emojiOff = 0;\n    private int candOff = 0;', 1)

new_emoji_draw = r'''    private int emojiVisibleRows() {
        float top=dp(38), rowH=dp(44);
        float safeBottom=getHeight()-dp(70);
        float backH=dp(48);
        return Math.max(1,(int)Math.floor((safeBottom-backH-top)/rowH));
    }

    private int emojiPageSize() { return emojiVisibleRows()*7; }

    private void scrollEmoji(boolean forward) {
        int pageSize=emojiPageSize();
        int rowsTotal=(emojis.length+6)/7;
        int maxStart=Math.max(0,Math.max(0,rowsTotal-emojiVisibleRows())*7);
        if(forward) emojiOff=Math.min(maxStart,emojiOff+pageSize);
        else emojiOff=Math.max(0,emojiOff-pageSize);
        invalidate();
    }

    private void drawEmoji(Canvas c) {
        p.setColor(BG);
        c.drawRect(0,0,getWidth(),getHeight(),p);
        t.setColor(Color.DKGRAY);
        t.setTextSize(dp(15));
        t.setTextAlign(Paint.Align.LEFT);
        c.drawText("表情符號",dp(18),dp(27),t);
        int cols=7;
        float top=dp(38), cellW=getWidth()/(float)cols, cellH=dp(44);
        float safeBottom=getHeight()-dp(70);
        float backH=dp(48);
        float listBottom=safeBottom-backH;
        int rows=emojiVisibleRows();
        int count=Math.max(0,Math.min(rows*cols,emojis.length-emojiOff));
        for(int i=0;i<count;i++) {
            int row=i/cols,col=i%cols;
            RectF r=new RectF(col*cellW,top+row*cellH,(col+1)*cellW,top+(row+1)*cellH);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(dp(24));
            t.setColor(Color.BLACK);
            String item=emojis[emojiOff+i];
            c.drawText(item,r.centerX(),r.centerY()+dp(8),t);
            hits.add(new Hit(r,"EMOJI_CHAR",item));
        }
        RectF back=new RectF(0,listBottom,dp(110),safeBottom);
        hits.add(new Hit(back,"MAIN",""));
        t.setTextAlign(Paint.Align.CENTER); t.setTextSize(dp(22)); t.setColor(Color.BLACK);
        android.graphics.Rect b=new android.graphics.Rect(); String label="⌨"; t.getTextBounds(label,0,label.length(),b);
        c.drawText(label,back.centerX(),back.centerY()-(b.top+b.bottom)/2f,t);
    }

'''
sub1(r'    private void drawEmoji\(Canvas c\) \{.*?\n    \}\n\n(?=    private int kaomojiVisibleRows)', new_emoji_draw, flags=re.S, label='emoji paging')

# Add Emoji vertical swipe beside the existing kaomoji swipe behavior.
needle = '''        if(page==Page.KAOMOJI&&ady>dp(34)&&ady>adx*1.25f) {'''
replacement = '''        if(page==Page.EMOJI&&ady>dp(30)&&ady>adx*1.20f) {\n            feedback();\n            scrollEmoji(dy<0);\n            downHit=null;\n            return true;\n        }\n\n        if(page==Page.KAOMOJI&&ady>dp(34)&&ady>adx*1.25f) {'''
if needle not in s: raise SystemExit('v0.9 patch failed: kaomoji swipe anchor')
s=s.replace(needle,replacement,1)

needle='            case "EMOJI": page=Page.EMOJI; syncComposition(); invalidate(); break;'
replacement='            case "EMOJI": page=Page.EMOJI; emojiOff=0; syncComposition(); invalidate(); break;'
if needle not in s: raise SystemExit('v0.9 patch failed: emoji action')
s=s.replace(needle,replacement,1)

JAVA.write_text(s,encoding='utf-8')

# Update the visible app version/help text for this build only.
m=MAIN.read_text(encoding='utf-8')
m, n = re.subn(r'version\.setText\("[^"]*"\);', 'version.setText("v0.9｜詞彙與版面修正版");', m, count=1)
if n != 1: raise SystemExit('v0.9 patch failed: MainActivity version')
m, n = re.subn(r'intro\.setText\("[^"]*"\);', 'intro.setText("保留原本倉頡按鍵位置，逗號與句點改用較窄輔助鍵；倉頡／English／注音／數字／符號共用 70%～100% 文字大小。新增常用詞接續候選、保守的相鄰字根誤觸提示，並大幅擴充 Emoji 與顏文字，可上下滑動瀏覽。");', m, count=1)
if n != 1: raise SystemExit('v0.9 patch failed: MainActivity intro')
MAIN.write_text(m,encoding='utf-8')

# Build metadata. Stable signing remains unchanged in build.gradle signingConfigs.
g=GRADLE.read_text(encoding='utf-8')
g, n = re.subn(r'versionCode\s+\d+', 'versionCode 10', g, count=1)
if n != 1: raise SystemExit('v0.9 patch failed: versionCode')
g, n = re.subn(r"versionName\s+'[^']+'", "versionName '0.9'", g, count=1)
if n != 1: raise SystemExit('v0.9 patch failed: versionName')
GRADLE.write_text(g,encoding='utf-8')

# Build-time assertions for the user-visible promises.
checks = [
    'float x=116+i*116.5f;',
    'drawMainKey(c,935,318,993,444,"。"',
    'drawMainKey(c,x,0,x+96,122,String.valueOf((i+1)%10),17.68f',
    'commonContinuations',
    'cangjieMistypeCandidates',
    'scrollEmoji',
    '"XD"',
    '"QQ"',
    '(╯°□°）╯︵ ┻━┻'
]
final=JAVA.read_text(encoding='utf-8')
for check in checks:
    if check not in final:
        raise SystemExit('v0.9 assertion failed: '+check)
print('v0.9 patch complete; emoji count=',len(emojis),'kaomoji count=',len(kaos))

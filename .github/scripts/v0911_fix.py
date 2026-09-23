from pathlib import Path
import re

base = Path('.github/scripts/v0910_fix.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v0910_fix.py', 'exec'), {'__name__': '__main__'})

java_path=Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s=java_path.read_text(encoding='utf-8')

def replace_once(old,new,label):
    global s
    if old not in s:
        raise SystemExit(f'v0.9.11 patch failed: {label} source not found')
    s=s.replace(old,new,1)

def sub1(pattern,replacement,label):
    global s
    s2,n=re.subn(pattern,replacement,s,count=1,flags=re.S)
    if n!=1:
        raise SystemExit(f'v0.9.11 patch failed: {label} matched {n}')
    s=s2

replace_once(
'''        // With 3+ roots the user has expressed much more intent. Keep ordinary
        // BMP Han exact matches (e.g. HAP -> 皂, 皀) ahead of prefix completions.
        if(code!=null&&code.length()>=3&&text.codePointCount(0,text.length())==1){
''',
'''        // From two roots onward, an ordinary BMP Han exact match is a real
        // complete Cangjie code and stays ahead of prefix completions.
        if(code!=null&&code.length()>=2&&text.codePointCount(0,text.length())==1){
''',
'Cangjie two-root exact candidates'
)

replace_once(
'''    private void drawCangjie(Canvas c){
        for(int i=0;i<10;i++){ float x=18+i*116.4f; drawInputKey(c,x,0,x+96,122,c1[i],17.68f,"CJ",String.valueOf(k1[i])); }
        for(int i=0;i<9;i++){ float x=76+i*114.8f; drawInputKey(c,x,155,x+96,282,c2[i],17.68f,"CJ",String.valueOf(k2[i])); }
        // Restore the original third-row root coordinates. Punctuation uses only the old empty gaps.
        drawInputKey(c,34,318,96,444,"，",17.68f,"PUNCT","，");
        for(int i=0;i<7;i++){ float x=116+i*116.5f; drawInputKey(c,x,318,x+96,444,c3[i],17.68f,"CJ",String.valueOf(k3[i])); }
        drawInputKey(c,932,318,994,444,"。",17.68f,"PUNCT","。");
        drawFixedKey(c,1017,318,1150,444,"⌫",24,"BACK","");
        drawBottom(c,"123","倉");
    }
''',
'''    private void drawCangjie(Canvas c){
        for(int i=0;i<10;i++){ float x=18+i*116.4f; drawInputKey(c,x,0,x+96,122,c1[i],17.68f,"CJ",String.valueOf(k1[i])); }
        for(int i=0;i<9;i++){ float x=76+i*114.8f; drawInputKey(c,x,155,x+96,282,c2[i],17.68f,"CJ",String.valueOf(k2[i])); }
        drawInputKey(c,34,318,96,444,"，",17.68f,"PUNCT","，");
        for(int i=0;i<7;i++){ float x=116+i*116.5f; drawInputKey(c,x,318,x+96,444,c3[i],17.68f,"CJ",String.valueOf(k3[i])); }
        drawFixedKey(c,932,318,1150,444,"⌫",24,"BACK","");
        drawBottom(c,"123","倉");
    }
''',
'Cangjie period relocation'
)

sub1(
    r'''    private void drawBottom\(Canvas c,String left,String mark\)\{.*?\n    \}''',
    r'''    private void drawBottom(Canvas c,String left,String mark){
        drawFixedKey(c,18,518,145,647,left,22,"NUM","");
        drawFixedKey(c,160,518,287,647,"☺",23,"EMOJI","");
        if(mode==Mode.ENGLISH){
            drawFixedKey(c,303,518,864,647,"",22,"SPACE","");
            drawFixedKey(c,880,518,1150,647,"↩",25,"ENTER","");
            t.setColor(secondaryTextColor()); t.setTextSize(dp(12)); t.setTextAlign(Paint.Align.RIGHT); c.drawText(mark,sx(842),sy(626),t);
        }else{
            drawFixedKey(c,303,518,760,647,"",22,"SPACE","");
            drawInputKey(c,776,518,864,647,"。",17.68f,"PUNCT","。");
            drawFixedKey(c,880,518,1150,647,"↩",25,"ENTER","");
            t.setColor(secondaryTextColor()); t.setTextSize(dp(12)); t.setTextAlign(Paint.Align.RIGHT); c.drawText(mark,sx(738),sy(626),t);
        }
    }''',
    'Chinese bottom-row period'
)
replace_once(
'''                // Borrow only a slim strip from the wide backspace key for 。.
                // A tap actually inside the visible 。 key always remains 。.
                if(page==Page.MAIN&&mode==Mode.CANGJIE&&"BACK".equals(h.action)&&x<h.r.left+dp(10)){
                    for(int j=hits.size()-1;j>=0;j--){
                        Hit period=hits.get(j);
                        if("PUNCT".equals(period.action)&&"。".equals(period.value)) return period;
                    }
                }
                return h;
''',
'''                return h;
''',
'remove old period/backspace remap'
)

replace_once(
'''    private boolean sidePunct(Hit h){ return page==Page.MAIN&&mode==Mode.CANGJIE&&"PUNCT".equals(h.action)&&("，".equals(h.value)||"。".equals(h.value)); }
''',
'''    private boolean sidePunct(Hit h){ return page==Page.MAIN&&mode!=Mode.ENGLISH&&"PUNCT".equals(h.action)&&("，".equals(h.value)||"。".equals(h.value)); }
''',
'compact Chinese punctuation hit area'
)

replace_once(
'''            float px=sidePunct(h)?dp(2):dp(12),py=sidePunct(h)?dp(4):dp(10);
            if(page==Page.MAIN&&mode==Mode.CANGJIE&&"CJ".equals(h.action)&&"m".equals(h.value)) px=dp(12);
            if(page==Page.MAIN&&mode==Mode.CANGJIE&&"PUNCT".equals(h.action)&&"。".equals(h.value)) px=dp(10);
''',
'''            float px=sidePunct(h)?dp(2):dp(12),py=sidePunct(h)?dp(4):dp(10);
            if(page==Page.MAIN&&mode==Mode.CANGJIE&&"CJ".equals(h.action)&&"m".equals(h.value)) px=dp(12);
''',
'remove old period hit expansion'
)

replace_once(
'''    private final Map<String, List<String>> zy = new HashMap<>();
''',
'''    private final Map<String, List<String>> zy = new HashMap<>();
    private final Map<String, List<String>> zyInitials = new HashMap<>();
''',
'Zhuyin initials map field'
)

replace_once(
'''        loadCangjieExact();
        loadZhuyin();
''',
'''        loadCangjieExact();
        loadZhuyin();
        loadZhuyinInitials();
''',
'load Zhuyin initials'
)

replace_once(
'''    private void loadZhuyin(){ loadMap("zhuyin.tsv",zy); if(zy.isEmpty()) zy.put("ㄋㄧˇ",Arrays.asList("你","妳","擬")); }
''',
'''    private void loadZhuyin(){ loadMap("zhuyin.tsv",zy); if(zy.isEmpty()) zy.put("ㄋㄧˇ",Arrays.asList("你","妳","擬")); }
    private void loadZhuyinInitials(){ loadMap("zhuyin_initials.tsv",zyInitials); }
''',
'Zhuyin initials loader'
)

replace_once(
'''        if(mode==Mode.ZHUYIN){
            if(zyCode.isEmpty()) return withLearnedNext(commonHome());
            LinkedHashSet<String> merged=new LinkedHashSet<>();
            List<String> ex=zy.get(zyCode); if(ex!=null) merged.addAll(ex);
            for(Map.Entry<String,List<String>> e:zy.entrySet()) if(!e.getKey().equals(zyCode)&&e.getKey().startsWith(zyCode)) merged.addAll(e.getValue());
            return rankCommon(new ArrayList<>(merged));
        }
''',
'''        if(mode==Mode.ZHUYIN){
            if(zyCode.isEmpty()) return withLearnedNext(commonHome());

            LinkedHashSet<String> ordinary=new LinkedHashSet<>();
            List<String> ex=zy.get(zyCode); if(ex!=null) ordinary.addAll(ex);
            for(Map.Entry<String,List<String>> e:zy.entrySet()){
                if(!e.getKey().equals(zyCode)&&e.getKey().startsWith(zyCode)) ordinary.addAll(e.getValue());
            }

            LinkedHashSet<String> out=new LinkedHashSet<>();
            out.addAll(rankCommon(new ArrayList<>(ordinary)));

            if(zyCode.codePointCount(0,zyCode.length())>=2){
                List<String> phrases=zyInitials.get(zyCode);
                if(phrases!=null) out.addAll(phrases);
            }
            return new ArrayList<>(out);
        }
''',
'Zhuyin first-symbol phrase candidates'
)

java_path.write_text(s,encoding='utf-8')

main_path=Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m=main_path.read_text(encoding='utf-8')
m=re.sub(r'version\.setText\("[^"]*"\);',
         'version.setText("v0.9.11｜中文鍵位與候選修正版");',m,count=1)
m=re.sub(r'intro\.setText\("[^"]*"\);',
         'intro.setText("保留 v0.9.10 的語音輸入修正。本版把中文句號移到空白鍵與換行鍵之間，倉頡第三排不再讓句號干擾「一」；修正正常二碼倉頡完整碼候選，例如「干＝一十、甘＝廿一」會優先於前綴補字；注音加入常用詞首符號縮寫候選，例如只輸入「ㄨㄇ」即可出現「我們」。");',
         m,count=1)
main_path.write_text(m,encoding='utf-8')

gradle=Path('imeapp/app/build.gradle')
g=gradle.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 21',g,count=1)
g=re.sub(r"versionName\s+'[^']+'","versionName '0.9.11'",g,count=1)
gradle.write_text(g,encoding='utf-8')

assert "versionCode 21" in g and "versionName '0.9.11'" in g
assert 'drawInputKey(c,776,518,864,647,"。"' in s
assert 'drawInputKey(c,932,318,994,444,"。"' not in s
assert 'drawFixedKey(c,932,318,1150,444,"⌫"' in s
assert 'code.length()>=2' in s
assert 'loadZhuyinInitials();' in s
assert 'zyInitials.get(zyCode)' in s
assert 'v0.9.11｜中文鍵位與候選修正版' in m
print('v0.9.11 Chinese period layout, Cangjie exacts and Zhuyin initials applied')

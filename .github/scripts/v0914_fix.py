from pathlib import Path
import re

base=Path('.github/scripts/v0913_fix.py').read_text(encoding='utf-8')
exec(compile(base,'.github/scripts/v0913_fix.py','exec'),{'__name__':'__main__'})

java_path=Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s=java_path.read_text(encoding='utf-8')

def replace_once(old,new,label):
    global s
    if old not in s:
        raise SystemExit(f'v0.9.14 patch failed: {label} source not found')
    s=s.replace(old,new,1)

# A corpus-derived Taiwan Traditional Chinese common-character rank is generated
# by the build workflow from libchewing-data. This prevents two-root exact-code
# fixes from becoming one-off hard-coded exceptions (e.g. 干/甘/占).
replace_once(
'''    private final Map<String, List<String>> zy = new HashMap<>();
    private final Map<String, List<String>> zyInitials = new HashMap<>();
''',
'''    private final Map<String, List<String>> zy = new HashMap<>();
    private final Map<String, List<String>> zyInitials = new HashMap<>();
    private final Map<String, Integer> commonFreq = new HashMap<>();
''',
'common-frequency map field'
)

replace_once(
'''        loadZhuyin();
        loadZhuyinInitials();
''',
'''        loadZhuyin();
        loadZhuyinInitials();
        loadCommonFrequency();
''',
'load common-frequency asset'
)

anchor='''    private void loadZhuyinInitials(){ loadMap("zhuyin_initials.tsv",zyInitials); }
'''
if anchor not in s:
    raise SystemExit('v0.9.14 patch failed: zhuyin initials loader anchor missing')
s=s.replace(anchor,anchor+r'''
    private void loadCommonFrequency(){
        try(BufferedReader b=new BufferedReader(new InputStreamReader(getContext().getAssets().open("common_chars.tsv"),StandardCharsets.UTF_8))){
            String line;
            while((line=b.readLine())!=null){
                int i=line.indexOf('\t');
                if(i<=0) continue;
                String ch=line.substring(0,i);
                try{
                    int score=Integer.parseInt(line.substring(i+1).trim());
                    if(score>0) commonFreq.put(ch,score);
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}
    }
''',1)

old_rank=r'''    private int builtinCommonRank(String text){
        if(text==null||text.isEmpty()||text.codePointCount(0,text.length())!=1) return 0;
        int i=COMMON_CHARS.indexOf(text);
        return i<0?0:(COMMON_CHARS.length()-i);
    }
'''
new_rank=r'''    private int builtinCommonRank(String text){
        if(text==null||text.isEmpty()||text.codePointCount(0,text.length())!=1) return 0;
        Integer corpus=commonFreq.get(text);
        if(corpus!=null&&corpus>0) return 100000+corpus;
        int i=COMMON_CHARS.indexOf(text);
        return i<0?0:(COMMON_CHARS.length()-i);
    }
'''
replace_once(old_rank,new_rank,'corpus common rank')

# The v0.9.12 emergency additions are no longer necessary for correctness, but
# leaving them in COMMON_CHARS is harmless. The real rule now applies to all
# common two-root exact characters through builtinCommonRank().
java_path.write_text(s,encoding='utf-8')

main_path=Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m=main_path.read_text(encoding='utf-8')
m=re.sub(r'version\.setText\("[^"]*"\);',
         'version.setText("v0.9.14｜二碼完整字排序修正版");',m,count=1)
m=re.sub(r'intro\.setText\("[^"]*"\);',
         'intro.setText("二碼倉頡完整字改用台灣繁體中文常用字頻率表判斷：常用完整字會排在前綴候選之前，例如卜口→占、一十→干、廿一→甘；罕見完整碼仍保留在後方，不會因為是二碼完整碼就擠進第一排。保留 v0.9.13 的長停頓語音與候選連續上下滑動。");',
         m,count=1)
main_path.write_text(m,encoding='utf-8')

gradle=Path('imeapp/app/build.gradle')
g=gradle.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 24',g,count=1)
g=re.sub(r"versionName\s+'[^']+'","versionName '0.9.14'",g,count=1)
gradle.write_text(g,encoding='utf-8')

assert 'commonFreq' in s and 'loadCommonFrequency' in s
assert '100000+corpus' in s
assert "versionCode 24" in g and "versionName '0.9.14'" in g
assert 'v0.9.14｜二碼完整字排序修正版' in m
print('v0.9.14 corpus-based two-root exact ranking applied')

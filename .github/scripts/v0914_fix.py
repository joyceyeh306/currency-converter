from pathlib import Path
import re

base=Path('.github/scripts/v0913_fix.py').read_text(encoding='utf-8')
exec(compile(base,'.github/scripts/v0913_fix.py','exec'),{'__name__':'__main__'})

java_path=Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s=java_path.read_text(encoding='utf-8')

if 'import java.util.HashSet;' not in s:
    s=s.replace('import java.util.HashMap;\n','import java.util.HashMap;\nimport java.util.HashSet;\n',1)
if 'import java.util.Set;' not in s:
    s=s.replace('import java.util.List;\n','import java.util.List;\nimport java.util.Set;\n',1)

def replace_once(old,new,label):
    global s
    if old not in s:
        raise SystemExit(f'v0.9.14 patch failed: {label} source not found')
    s=s.replace(old,new,1)

# Use Taiwan MOE's 4,808-character common set for two-root exact promotion.
# This makes the rule general (not a one-off list for 干/甘/占) while keeping
# obscure exact-code characters behind useful prefix candidates.
replace_once(
'''    private final Map<String, List<String>> zy = new HashMap<>();
    private final Map<String, List<String>> zyInitials = new HashMap<>();
''',
'''    private final Map<String, List<String>> zy = new HashMap<>();
    private final Map<String, List<String>> zyInitials = new HashMap<>();
    private final Set<String> moeCommon = new HashSet<>();
''',
'MOE common-character set field'
)

replace_once(
'''        loadZhuyin();
        loadZhuyinInitials();
''',
'''        loadZhuyin();
        loadZhuyinInitials();
        loadMoeCommon();
''',
'load MOE common-character asset'
)

anchor='''    private void loadZhuyinInitials(){ loadMap("zhuyin_initials.tsv",zyInitials); }
'''
if anchor not in s:
    raise SystemExit('v0.9.14 patch failed: zhuyin initials loader anchor missing')
s=s.replace(anchor,anchor+r'''
    private void loadMoeCommon(){
        try(BufferedReader b=new BufferedReader(new InputStreamReader(getContext().getAssets().open("moe_common_chars.txt"),StandardCharsets.UTF_8))){
            String line;
            while((line=b.readLine())!=null){
                String ch=line.trim();
                if(!ch.isEmpty()&&ch.codePointCount(0,ch.length())==1) moeCommon.add(ch);
            }
        }catch(Exception ignored){}
    }
''',1)

old_rule=r'''        // For 3+ roots, ordinary exact Han matches stay ahead. Two-root
        // exacts are promoted only when they are common/learned/root-exact.
        if(code!=null&&code.length()>=3&&text.codePointCount(0,text.length())==1){
            int cp=text.codePointAt(0);
            return cp>=0x4E00&&cp<=0x9FFF;
        }
'''
new_rule=r'''        // For exactly two roots, promote ordinary Taiwan MOE common
        // characters as real complete codes (e.g. YR->占, MJ->干, TM->甘).
        // Rare two-root exacts remain deferred behind useful prefix matches.
        if(code!=null&&code.length()==2&&text.codePointCount(0,text.length())==1&&moeCommon.contains(text)){
            return true;
        }
        // With 3+ roots the user has expressed much more intent, so ordinary
        // BMP Han exact matches remain ahead of prefix completions.
        if(code!=null&&code.length()>=3&&text.codePointCount(0,text.length())==1){
            int cp=text.codePointAt(0);
            return cp>=0x4E00&&cp<=0x9FFF;
        }
'''
replace_once(old_rule,new_rule,'general two-root MOE common rule')

java_path.write_text(s,encoding='utf-8')

main_path=Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m=main_path.read_text(encoding='utf-8')
m=re.sub(r'version\.setText\("[^"]*"\);',
         'version.setText("v0.9.14｜二碼完整字排序修正版");',m,count=1)
m=re.sub(r'intro\.setText\("[^"]*"\);',
         'intro.setText("二碼倉頡完整字改用台灣教育部 4,808 個常用國字判斷：常用完整字會排在前綴候選之前，例如卜口→占、一十→干、廿一→甘；罕見完整碼仍保留在後方，不會因為是二碼完整碼就擠進第一排。保留 v0.9.13 的長停頓語音與候選連續上下滑動。");',
         m,count=1)
main_path.write_text(m,encoding='utf-8')

gradle=Path('imeapp/app/build.gradle')
g=gradle.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 24',g,count=1)
g=re.sub(r"versionName\s+'[^']+'","versionName '0.9.14'",g,count=1)
gradle.write_text(g,encoding='utf-8')

assert 'moeCommon' in s and 'loadMoeCommon' in s
assert 'code.length()==2' in s and 'moeCommon.contains(text)' in s
assert "versionCode 24" in g and "versionName '0.9.14'" in g
assert 'v0.9.14｜二碼完整字排序修正版' in m
print('v0.9.14 Taiwan MOE common-set two-root exact ranking applied')

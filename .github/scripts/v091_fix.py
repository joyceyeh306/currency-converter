from pathlib import Path
import re

p = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s = p.read_text(encoding='utf-8')
s = s.replace('            case "可": default: return Collections.emptyList();', '            default: return Collections.emptyList();')

marker = '            case "為": return Arrays.asList("為什麼","為了","為何");\n'
if marker in s and 'case "非"' not in s:
    s = s.replace(marker, marker + '            case "非": return Arrays.asList("非常","非法","非洲","非得","非必要");\n', 1)

old = '    private void choose(String s){ if(s==null||s.isEmpty()) return; svc.commit(s); learn(s); if(isPunctuation(s)) lastCommitted=""; cjCode=""; zyCode=""; candOff=0; expanded=false; syncComposition(); invalidate(); }'
new = '''    private void choose(String s){
        if(s==null||s.isEmpty()) return;
        String commit=s;
        if(!lastCommitted.isEmpty() && !isPunctuation(s)) {
            if(s.length()>lastCommitted.length() && s.startsWith(lastCommitted)) {
                commit=s.substring(lastCommitted.length());
            } else {
                String tail=lastCommitted.substring(lastCommitted.length()-1);
                if(s.length()>tail.length() && s.startsWith(tail)) commit=s.substring(tail.length());
            }
        }
        if(!commit.isEmpty()) svc.commit(commit);
        learn(s);
        if(isPunctuation(s)) lastCommitted="";
        cjCode=""; zyCode=""; candOff=0; expanded=false; syncComposition(); invalidate();
    }'''
if old not in s:
    raise SystemExit('choose() source not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

g = Path('imeapp/app/build.gradle')
gs = g.read_text(encoding='utf-8')
gs = re.sub(r'versionCode\s+\d+', 'versionCode 11', gs, count=1)
gs = re.sub(r"versionName\s+'[^']+'", "versionName '0.9.1'", gs, count=1)
g.write_text(gs, encoding='utf-8')

m = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
ms = m.read_text(encoding='utf-8')
ms = re.sub(r'version\.setText\("[^"]*"\);', 'version.setText("v0.9.1｜詞句候選修正版");', ms, count=1)
m.write_text(ms, encoding='utf-8')

assert 'float x=116+i*116.5f' in s
assert 'phraseSuggestions' in s
assert 'cangjieTypoCandidates' in s
assert 'recentEmoji' in s
assert 'emojiCategory' in s
print('v0.9.1 phrase candidate correction applied')

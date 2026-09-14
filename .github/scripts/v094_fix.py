from pathlib import Path
import re

# Build on the verified v0.9.3 runtime patch chain.
base = Path('.github/scripts/v093_fix.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v093_fix.py', 'exec'), {'__name__': '__main__'})

java_path = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s = java_path.read_text(encoding='utf-8')


def sub1(pattern, replacement, label):
    global s
    s2, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'v0.9.4 patch failed for {label}: matched {n}')
    s = s2


# Temporary English state for a quick spacebar vertical gesture.
old_state = '    private boolean longPressDirect = false;\n'
new_state = '''    private boolean longPressDirect = false;\n    private boolean temporaryEnglish = false;\n    private Mode temporaryReturnMode = Mode.CANGJIE;\n'''
if old_state not in s:
    raise SystemExit('v0.9.4 patch failed: state insertion point not found')
s = s.replace(old_state, new_state, 1)

# From the second Cangjie root onward, personal frequency ranks across the combined
# exact-code + prefix candidate pool. Exact-code priority remains only as a tie-breaker,
# so a frequently selected character such as 好 (VND) can outrank unused rare VN exact chars.
old_cj = '''        if(mode==Mode.CANGJIE){\n            if(cjCode.isEmpty()) return withLearnedNext(Arrays.asList("的","嗎","為","成","過","變","法","我","妳","你","是","有","在","不","人"));\n            LinkedHashSet<String> out=new LinkedHashSet<>(); List<String> ex=cjExact.get(cjCode); if(ex!=null) out.addAll(boost(ex)); List<String> pr=cj.get(cjCode); if(pr!=null) out.addAll(boost(pr)); if(out.isEmpty()) out.addAll(cangjieTypoCandidates(cjCode)); return new ArrayList<>(out);\n        }\n'''
new_cj = '''        if(mode==Mode.CANGJIE){\n            if(cjCode.isEmpty()) return withLearnedNext(Arrays.asList("的","嗎","為","成","過","變","法","我","妳","你","是","有","在","不","人"));\n            List<String> ex=cjExact.get(cjCode),pr=cj.get(cjCode);\n            LinkedHashSet<String> merged=new LinkedHashSet<>();\n            if(cjCode.length()<2){\n                if(ex!=null) merged.addAll(boost(ex));\n                if(pr!=null) merged.addAll(boost(pr));\n            }else{\n                if(ex!=null) merged.addAll(ex);\n                if(pr!=null) merged.addAll(pr);\n                if(learningEnabled()){\n                    ArrayList<String> ranked=new ArrayList<>(merged);\n                    ranked.sort(Comparator.comparingInt((String x)->-prefs.getInt("f_"+x,0)));\n                    merged.clear(); merged.addAll(ranked);\n                }\n            }\n            if(merged.isEmpty()) merged.addAll(cangjieTypoCandidates(cjCode));\n            return new ArrayList<>(merged);\n        }\n'''
if old_cj not in s:
    raise SystemExit('v0.9.4 patch failed: Cangjie candidate block not found')
s = s.replace(old_cj, new_cj, 1)

# Spacebar vertical gesture: up = temporary English from either Chinese mode;
# down = return to the Chinese mode that was active before the temporary switch.
old_space_gesture = '        if(downHit!=null&&"SPACE".equals(downHit.action)&&cjCode.isEmpty()&&zyCode.isEmpty()){ float th=Math.max(dp(42),downHit.r.width()*0.16f); if(adx>th&&adx>ady*1.5f){ feedback(); if(dx<0) cycleMode(); else cycleModeBackward(); downHit=null; return true; } }\n'
new_space_gesture = '''        if(downHit!=null&&"SPACE".equals(downHit.action)&&cjCode.isEmpty()&&zyCode.isEmpty()){\n            float vth=Math.max(dp(36),downHit.r.height()*0.22f);\n            if(ady>vth&&ady>adx*1.35f){\n                if(dy<0&&mode!=Mode.ENGLISH){ feedback(); enterTemporaryEnglish(); downHit=null; return true; }\n                if(dy>0&&temporaryEnglish){ feedback(); exitTemporaryEnglish(); downHit=null; return true; }\n            }\n            float th=Math.max(dp(42),downHit.r.width()*0.16f);\n            if(adx>th&&adx>ady*1.5f){ feedback(); temporaryEnglish=false; if(dx<0) cycleMode(); else cycleModeBackward(); downHit=null; return true; }\n        }\n'''
if old_space_gesture not in s:
    raise SystemExit('v0.9.4 patch failed: space gesture block not found')
s = s.replace(old_space_gesture, new_space_gesture, 1)

old_cycle = '''    private void cycleMode(){ if(mode==Mode.CANGJIE)mode=Mode.ENGLISH;else if(mode==Mode.ENGLISH)mode=Mode.ZHUYIN;else mode=Mode.CANGJIE; page=Page.MAIN;cjCode="";zyCode="";expanded=false;candOff=0;syncComposition();invalidate(); }\n    private void cycleModeBackward(){ if(mode==Mode.CANGJIE)mode=Mode.ZHUYIN;else if(mode==Mode.ZHUYIN)mode=Mode.ENGLISH;else mode=Mode.CANGJIE; page=Page.MAIN;cjCode="";zyCode="";expanded=false;candOff=0;syncComposition();invalidate(); }\n'''
new_cycle = '''    private void enterTemporaryEnglish(){ temporaryReturnMode=mode; temporaryEnglish=true; mode=Mode.ENGLISH; page=Page.MAIN; shift=false; caps=false; cjCode=""; zyCode=""; expanded=false; candOff=0; syncComposition(); invalidate(); }\n    private void exitTemporaryEnglish(){ if(!temporaryEnglish) return; mode=temporaryReturnMode; temporaryEnglish=false; page=Page.MAIN; shift=false; caps=false; cjCode=""; zyCode=""; expanded=false; candOff=0; syncComposition(); invalidate(); }\n    private void cycleMode(){ temporaryEnglish=false; if(mode==Mode.CANGJIE)mode=Mode.ENGLISH;else if(mode==Mode.ENGLISH)mode=Mode.ZHUYIN;else mode=Mode.CANGJIE; page=Page.MAIN;cjCode="";zyCode="";expanded=false;candOff=0;syncComposition();invalidate(); }\n    private void cycleModeBackward(){ temporaryEnglish=false; if(mode==Mode.CANGJIE)mode=Mode.ZHUYIN;else if(mode==Mode.ZHUYIN)mode=Mode.ENGLISH;else mode=Mode.CANGJIE; page=Page.MAIN;cjCode="";zyCode="";expanded=false;candOff=0;syncComposition();invalidate(); }\n'''
if old_cycle not in s:
    raise SystemExit('v0.9.4 patch failed: cycle mode block not found')
s = s.replace(old_cycle, new_cycle, 1)

java_path.write_text(s, encoding='utf-8')

# Unicode-safe backspace: delete a complete supplementary-plane code point instead of
# deleting only half of a surrogate pair and briefly leaving the replacement glyph �.
service_path = Path('imeapp/app/src/main/java/tw/ajo/ime/AjoImeService.java')
service = service_path.read_text(encoding='utf-8')
old_backspace = '''    public void backspace() {\n        InputConnection ic = getCurrentInputConnection();\n        if (ic == null) return;\n        CharSequence before = ic.getTextBeforeCursor(1, 0);\n        if (before != null && before.length() > 0) ic.deleteSurroundingText(1, 0);\n        else {\n            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));\n            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));\n        }\n    }\n'''
new_backspace = '''    public void backspace() {\n        InputConnection ic = getCurrentInputConnection();\n        if (ic == null) return;\n        CharSequence before = ic.getTextBeforeCursor(2, 0);\n        if (before != null && before.length() > 0) {\n            int len = before.length();\n            int units = 1;\n            char last = before.charAt(len - 1);\n            if (Character.isLowSurrogate(last) && len >= 2 && Character.isHighSurrogate(before.charAt(len - 2))) units = 2;\n            ic.deleteSurroundingText(units, 0);\n        } else {\n            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));\n            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));\n        }\n    }\n'''
if old_backspace not in service:
    raise SystemExit('v0.9.4 patch failed: backspace source not found')
service = service.replace(old_backspace, new_backspace, 1)
service_path.write_text(service, encoding='utf-8')

# v0.9.4 metadata and settings-page gesture guide.
gradle = Path('imeapp/app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 14', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '0.9.4'", g, count=1)
gradle.write_text(g, encoding='utf-8')

main = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m = main.read_text(encoding='utf-8')
m = re.sub(r'version\.setText\("[^"]*"\);', 'version.setText("v0.9.4｜常用字與手勢修正版");', m, count=1)
m = re.sub(
    r'intro\.setText\("[^"]*"\);',
    'intro.setText("倉頡輸入到第 2 個字根起，個人常用字會跨完整碼與前綴候選一起排序，避免罕用完整碼長期壓住真正常打的字；退格改為 Unicode 安全刪除，修正罕見字刪除時短暫出現亂碼。新增空白鍵上滑暫時切到 English、下滑回原中文模式，並在設定頁集中列出操作手勢。語音輸入仍保留第二階段，不在本版啟用。");',
    m,
    count=1,
)
m = m.replace(
    'addSeek(root, "按鍵文字大小", "倉頡、English、注音、數字與符號頁同步調整；按鍵外框大小不變", "key_text_size", 70, 100, 100, "%");',
    'addSeek(root, "按鍵文字大小", "所有鍵帽內文字、數字、符號、Emoji 與功能圖示同步調整；按鍵外框大小不變", "key_text_size", 70, 100, 100, "%");',
    1,
)
old_note_start = '        TextView note = new TextView(this);\n'
if old_note_start not in m:
    raise SystemExit('v0.9.4 patch failed: note insertion point not found')
m = m.replace(old_note_start, '        section(root, "操作手勢說明");\n        TextView note = new TextView(this);\n', 1)
m = re.sub(
    r'note\.setText\("[^"]*"\);',
    'note.setText("倉頡版本：第三代。\\n空白鍵向左滑：倉頡 → English → 注音 → 倉頡；向右滑為反方向。正在組字時不切換。\\n空白鍵向上滑：從倉頡或注音暫時切到 English；暫時 English 時向下滑：回到原本的中文模式。\\n長按倉頡字根或注音符號：直接輸出鍵面文字，不進入組字。\\n候選列左右滑：翻頁；右側箭頭可展開更多候選。\\nEmoji／顏文字頁上下滑：瀏覽更多。\\n倉頡輸入第 2 個字根起，個人常用字會跨完整碼與前綴候選一起排序。\\n倉頡碼若疑似按錯相鄰字根，只在沒有正常候選時提供修正候選，不會自動改碼。\\n個人學習與最近使用資料只保存在這支手機內。");',
    m,
    count=1,
)
main.write_text(m, encoding='utf-8')

assert 'temporaryEnglish = false' in s
assert 'enterTemporaryEnglish()' in s and 'exitTemporaryEnglish()' in s
assert 'cjCode.length()<2' in s
assert 'prefs.getInt("f_"+x,0)' in s
assert 'Character.isLowSurrogate' in service
assert 'section(root, "操作手勢說明")' in m
assert "versionCode 14" in g and "versionName '0.9.4'" in g
print('v0.9.4 common-character ranking, Unicode backspace and temporary-English gesture applied')

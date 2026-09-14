from pathlib import Path
import re

p = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
s = p.read_text(encoding='utf-8')

note_java = 'note.setText("倉頡版本：第三代。\\n空白鍵向左滑：倉頡 → English → 注音 → 倉頡；向右滑為反方向。正在組字時不切換。\\n空白鍵向上滑：從倉頡或注音暫時切到 English；暫時 English 時向下滑：回到原本的中文模式。\\n長按倉頡字根或注音符號：直接輸出鍵面文字，不進入組字。\\n候選列左右滑：翻頁；右側箭頭可展開更多候選。\\nEmoji／顏文字頁上下滑：瀏覽更多。\\n倉頡輸入第 2 個字根起，個人常用字會跨完整碼與前綴候選一起排序。\\n倉頡碼若疑似按錯相鄰字根，只在沒有正常候選時提供修正候選，不會自動改碼。\\n個人學習與最近使用資料只保存在這支手機內。");'

s2, n = re.subn(r'note\.setText\(".*?"\);', lambda m: note_java, s, count=1, flags=re.S)
if n != 1:
    raise SystemExit(f'v0.9.4 note repair failed: matched {n}')

p.write_text(s2, encoding='utf-8')
assert '\\n空白鍵向左滑' in s2
assert 'section(root, "操作手勢說明")' in s2
print('v0.9.4 settings gesture note escaping repaired')

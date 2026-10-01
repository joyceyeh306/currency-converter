from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

old="function receiptTimeParts(text){const m=String(text||'').match(/(?:^|[^0-9])([01]?[0-9]|2[0-3]):([0-5][0-9])(?::[0-5][0-9])?(?:[^0-9]|$)/);return m?String(m[1]).padStart(2,'0')+':'+m[2]:''}"
new="function receiptTimeParts(text){const raw=String(text||''),m=raw.match(/(?:^|[^0-9])([01]?[0-9]|2[0-3]):([0-5][0-9])(?::[0-5][0-9])?(?:[^0-9]|$)/);if(!m)return'';let h=Number(m[1]);const pm=/(오후|\\bpm\\b)/i.test(raw),am=/(오전|\\bam\\b)/i.test(raw);if(pm&&h<12)h+=12;if(am&&h===12)h=0;return String(h).padStart(2,'0')+':'+m[2]}"
if old not in s: raise SystemExit('v1.0.23 failed: receiptTimeParts')
s=s.replace(old,new,1)

start=s.index("function guessReceiptDateTime(lines,fallbackDate=''){")
end=s.index("function receiptStoredDateTime",start)
new_guess="""function guessReceiptDateTime(lines,fallbackDate=''){const preferred=/(date *of *sale|date|time|일시|날짜|판매일|판매시간|거래일시|거래시간|승인시간|결제시간)/i;let date=fallbackDate?String(fallbackDate).replace(/-/g,'/'):'',time='';for(const line of lines){if(!preferred.test(line))continue;if(!date)date=receiptDateParts(line);if(!time)time=receiptTimeParts(line);if(date&&time)break}if(!time||!date){for(const line of lines){if(!date)date=receiptDateParts(line);if(!time)time=receiptTimeParts(line);if(date&&time)break}}return date&&time?date+' '+time:(date?date:'')}
"""
s=s[:start]+new_guess+s[end:]

old="showNewReceiptSplit({uri:obj.uri||'',category,store,amount:cands[0]?.amount??'',dateTime,batchIndex,batchTotal})"
new="showNewReceiptSplit({uri:obj.uri||'',category,store,amount:'',dateTime,batchIndex,batchTotal})"
if old not in s: raise SystemExit('v1.0.23 failed: OCR amount blank')
s=s.replace(old,new,1)

old_note="辨識結果只先幫妳帶入；請直接對照左邊收據修改。照片會隨這筆消費一起保存。"
new_note="店名與時間辨識結果只先幫妳帶入；金額不自動帶入，請直接對照左邊收據輸入。照片會隨這筆消費一起保存。"
if old_note not in s: raise SystemExit('v1.0.23 failed: helper text')
s=s.replace(old_note,new_note,1)

p.write_text(s)

p=root/'app/build.gradle'
g=p.read_text()
g=re.sub(r"versionCode\s+\d+","versionCode 123",g,count=1)
g=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.23'",g,count=1)
p.write_text(g)

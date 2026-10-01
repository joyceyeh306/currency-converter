from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

# Receipt time parser.
mark="function guessReceiptCategory(text){"
i=s.index(mark)
s=s[:i]+r'''function guessReceiptTime(lines){const re=/(?:^|[^\d])([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?(?:[^\d]|$)/,preferred=/(date\s*of\s*sale|date|time|일시|날짜|판매일|판매시간|거래일시|거래시간|승인시간|결제시간)/i;for(const line of lines){if(!preferred.test(line))continue;const m=line.match(re);if(m)return String(m[1]).padStart(2,'0')+':'+m[2]}for(const line of lines){const m=line.match(re);if(m)return String(m[1]).padStart(2,'0')+':'+m[2]}return''}
'''+s[i:]

# Pinch zoom helpers.
mark="function receiptSplitShell("
i=s.index(mark)
s=s[:i]+r'''let receiptPreviewZoom=1,receiptPinchStart=null,receiptLastTap=0;
function setReceiptPreviewZoom(z){const area=$('#receiptPreviewScroll'),img=$('#receiptPreviewImg');if(!area||!img)return;z=Math.max(1,Math.min(4,z));const old=receiptPreviewZoom||1,ratio=z/old,cx=area.scrollLeft+area.clientWidth/2,cy=area.scrollTop+area.clientHeight/2;receiptPreviewZoom=z;img.style.width=(z*100)+'%';const b=$('#receiptZoomBtn');if(b)b.textContent=z===1?'放大':Math.round(z*100)+'%';requestAnimationFrame(()=>{area.scrollLeft=Math.max(0,cx*ratio-area.clientWidth/2);area.scrollTop=Math.max(0,cy*ratio-area.clientHeight/2)})}
function receiptZoomToggle(){setReceiptPreviewZoom(receiptPreviewZoom>1.05?1:2)}
function initReceiptPreviewZoom(){const area=$('#receiptPreviewScroll'),img=$('#receiptPreviewImg');if(!area||!img)return;receiptPreviewZoom=1;receiptPinchStart=null;const dist=t=>Math.hypot(t[0].clientX-t[1].clientX,t[0].clientY-t[1].clientY);area.addEventListener('touchstart',e=>{if(e.touches.length===2){receiptPinchStart={distance:dist(e.touches),zoom:receiptPreviewZoom};e.preventDefault();return}if(e.touches.length===1){const now=Date.now();if(now-receiptLastTap<280){e.preventDefault();receiptZoomToggle();receiptLastTap=0}else receiptLastTap=now}},{passive:false});area.addEventListener('touchmove',e=>{if(e.touches.length===2&&receiptPinchStart){e.preventDefault();setReceiptPreviewZoom(receiptPinchStart.zoom*dist(e.touches)/receiptPinchStart.distance)}},{passive:false});area.addEventListener('touchend',()=>{receiptPinchStart=null},{passive:true})}
'''+s[i:]

# Change the existing preview button/image to in-app zoom.
s=s.replace('<button onclick="openActiveReceiptOriginal()">放大</button>','<button id="receiptZoomBtn" onclick="receiptZoomToggle()">放大</button>',1)
s=s.replace('<div class="receipt-preview-scroll">${preview?`<img class="receipt-preview-img"','<div id="receiptPreviewScroll" class="receipt-preview-scroll">${preview?`<img id="receiptPreviewImg" class="receipt-preview-img"',1)
s=s.replace('`);initReceiptDivider()}','`);initReceiptDivider();initReceiptPreviewZoom()}',1)

# New receipt screen: add time field.
s=s.replace("function showNewReceiptSplit({uri,category,store,amount,batchIndex=1,batchTotal=1})","function showNewReceiptSplit({uri,category,store,amount,time='',batchIndex=1,batchTotal=1})",1)
needle='</select></div><div class="field"><label>店名／備註</label><textarea class="cost-form-note" id="rCostNote"'
repl='</select></div><div class="field"><label>時間</label><input id="rCostTime" type="time" value="${esc(time||\'\')}"></div><div class="field"><label>店名／備註</label><textarea class="cost-form-note" id="rCostNote"'
if needle not in s: raise SystemExit('v120 new time field marker')
s=s.replace(needle,repl,1)

# OCR time extraction and pass-through.
needle="category=guessReceiptCategory(lines.join('\\\\n')),batchTotal="
repl="category=guessReceiptCategory(lines.join('\\n')),time=guessReceiptTime(lines),batchTotal="
if needle not in s: raise SystemExit('v120 OCR time marker')
s=s.replace(needle,repl,1)
s=s.replace("amount:cands[0]?.amount??'',batchIndex,batchTotal","amount:cands[0]?.amount??'',time,batchIndex,batchTotal",1)
s=s.replace("amount:'',batchIndex,batchTotal","amount:'',time:'',batchIndex,batchTotal",1)

# Save time with receipt.
needle="category:$('#rCostCategory').value,note:$('#rCostNote').value.trim()"
repl="category:$('#rCostCategory').value,time:$('#rCostTime')?.value||'',note:$('#rCostNote').value.trim()"
if needle not in s: raise SystemExit('v120 receipt save time marker')
s=s.replace(needle,repl,1)

# Saved receipt edit: add time field.
needle='</select></div><div class="field"><label>店名／備註</label><textarea class="cost-form-note" id="rsNote"'
repl='</select></div><div class="field"><label>時間</label><input id="rsTime" type="time" value="${esc(f.x.time||\'\')}"></div><div class="field"><label>店名／備註</label><textarea class="cost-form-note" id="rsNote"'
if needle not in s: raise SystemExit('v120 saved edit time marker')
s=s.replace(needle,repl,1)

# Saved receipt read-only: show time after category.
needle='</div></div><div class="receipt-read-card"><div class="receipt-read-label">店名／備註</div>'
repl='</div></div><div class="receipt-read-card"><div class="receipt-read-label">時間</div><div class="receipt-read-value">${esc(f.x.time||\'—\')}</div></div><div class="receipt-read-card"><div class="receipt-read-label">店名／備註</div>'
if needle not in s: raise SystemExit('v120 saved read time marker')
s=s.replace(needle,repl,1)

needle="Object.assign(f.x,{category:$('#rsCategory').value,note:$('#rsNote').value.trim(),amount})"
repl="Object.assign(f.x,{category:$('#rsCategory').value,time:$('#rsTime')?.value||'',note:$('#rsNote').value.trim(),amount})"
if needle not in s: raise SystemExit('v120 saved edit persist marker')
s=s.replace(needle,repl,1)

# Show time in daily expense list.
needle="<b>${esc(x.category||'其他')}</b>"
repl="<b>${x.time?esc(x.time)+'　':''}${esc(x.category||'其他')}</b>"
if needle not in s: raise SystemExit('v120 list time marker')
s=s.replace(needle,repl,1)

p.write_text(s)

p=root/'app/build.gradle'
g=p.read_text()
g=re.sub(r"versionCode\s+\d+","versionCode 120",g,count=1)
g=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.20'",g,count=1)
p.write_text(g)

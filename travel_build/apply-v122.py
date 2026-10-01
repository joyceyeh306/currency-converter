from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

s=s.replace(
  ".receipt-preview-scroll{flex:1;min-height:0;overflow:auto;padding:8px 6px 18px;text-align:center;overscroll-behavior:contain}",
  ".receipt-preview-scroll{flex:1;min-height:0;overflow:auto;padding:8px 6px 18px;text-align:center;overscroll-behavior:contain;touch-action:none}",
  1
)

start=s.index("function guessReceiptTime(lines){")
end=s.index("function guessReceiptCategory",start)
dt_helpers=r'''function receiptDateParts(text){const m=String(text||'').match(/(?:^|[^0-9])((?:20)?[0-9]{2})[/.\-]([0-9]{1,2})[/.\-]([0-9]{1,2})(?:[^0-9]|$)/);if(!m)return'';let y=m[1];if(y.length===2)y='20'+y;const mo=Number(m[2]),d=Number(m[3]);if(mo<1||mo>12||d<1||d>31)return'';return y+'/'+String(mo).padStart(2,'0')+'/'+String(d).padStart(2,'0')}
function receiptTimeParts(text){const m=String(text||'').match(/(?:^|[^0-9])([01]?[0-9]|2[0-3]):([0-5][0-9])(?::[0-5][0-9])?(?:[^0-9]|$)/);return m?String(m[1]).padStart(2,'0')+':'+m[2]:''}
function normalizeReceiptDateTime(value,fallbackDate=''){let v=String(value||'').trim();if(!v)return'';const d=receiptDateParts(v)||(fallbackDate?String(fallbackDate).replace(/-/g,'/'):'');const t=receiptTimeParts(v);if(!d||!t)return'';return d+' '+t}
function receiptTimeFromDateTime(value){return receiptTimeParts(value)}
function guessReceiptDateTime(lines,fallbackDate=''){const preferred=/(date *of *sale|date|time|일시|날짜|판매일|판매시간|거래일시|거래시간|승인시간|결제시간)/i;let date='',time='';for(const line of lines){if(!preferred.test(line))continue;if(!date)date=receiptDateParts(line);if(!time)time=receiptTimeParts(line);if(date&&time)break}if(!date||!time){for(const line of lines){if(!date)date=receiptDateParts(line);if(!time)time=receiptTimeParts(line);if(date&&time)break}}if(!date&&fallbackDate)date=String(fallbackDate).replace(/-/g,'/');return date&&time?date+' '+time:(date?date:'')}
function receiptStoredDateTime(x,d){if(x?.dateTime)return x.dateTime;if(x?.time&&d?.date)return String(d.date).replace(/-/g,'/')+' '+x.time;return x?.time||''}
'''
s=s[:start]+dt_helpers+s[end:]

start=s.index("let receiptPreviewZoom=1,receiptPinchStart=null,receiptLastTap=0;")
end=s.index("function receiptSplitShell",start)
zoom_helpers=r'''let receiptPreviewZoom=1,receiptPinchStart=null,receiptLastTap=0,receiptPanStart=null;
function setReceiptPreviewZoom(z){const area=$('#receiptPreviewScroll'),img=$('#receiptPreviewImg');if(!area||!img)return;z=Math.max(1,Math.min(4,z));const old=receiptPreviewZoom||1,ratio=z/old,cx=area.scrollLeft+area.clientWidth/2,cy=area.scrollTop+area.clientHeight/2;receiptPreviewZoom=z;img.style.width=(z*100)+'%';const b=$('#receiptZoomBtn');if(b)b.textContent=z===1?'放大':Math.round(z*100)+'%';requestAnimationFrame(()=>{area.scrollLeft=Math.max(0,cx*ratio-area.clientWidth/2);area.scrollTop=Math.max(0,cy*ratio-area.clientHeight/2)})}
function receiptZoomToggle(){setReceiptPreviewZoom(receiptPreviewZoom>1.05?1:2)}
function initReceiptPreviewZoom(){const area=$('#receiptPreviewScroll'),img=$('#receiptPreviewImg');if(!area||!img)return;receiptPreviewZoom=1;receiptPinchStart=null;receiptPanStart=null;const dist=t=>Math.hypot(t[0].clientX-t[1].clientX,t[0].clientY-t[1].clientY);area.addEventListener('touchstart',e=>{if(e.touches.length===2){receiptPanStart=null;receiptPinchStart={distance:dist(e.touches),zoom:receiptPreviewZoom};e.preventDefault();return}if(e.touches.length===1){const t=e.touches[0];receiptPanStart={x:t.clientX,y:t.clientY,left:area.scrollLeft,top:area.scrollTop,moved:false};receiptPinchStart=null}},{passive:false});area.addEventListener('touchmove',e=>{if(e.touches.length===2&&receiptPinchStart){e.preventDefault();setReceiptPreviewZoom(receiptPinchStart.zoom*dist(e.touches)/receiptPinchStart.distance);return}if(e.touches.length===1&&receiptPanStart){const t=e.touches[0],dx=t.clientX-receiptPanStart.x,dy=t.clientY-receiptPanStart.y;if(Math.abs(dx)>3||Math.abs(dy)>3)receiptPanStart.moved=true;e.preventDefault();area.scrollLeft=receiptPanStart.left-dx;area.scrollTop=receiptPanStart.top-dy}},{passive:false});area.addEventListener('touchend',e=>{if(e.touches.length<2)receiptPinchStart=null;if(e.touches.length===0&&receiptPanStart){if(!receiptPanStart.moved){const now=Date.now();if(now-receiptLastTap<280){receiptZoomToggle();receiptLastTap=0}else receiptLastTap=now}receiptPanStart=null}},{passive:false});area.addEventListener('touchcancel',()=>{receiptPinchStart=null;receiptPanStart=null},{passive:true})}
'''
s=s[:start]+zoom_helpers+s[end:]

s=s.replace(
  "function showNewReceiptSplit({uri,category,store,amount,time='',batchIndex=1,batchTotal=1})",
  "function showNewReceiptSplit({uri,category,store,amount,dateTime='',batchIndex=1,batchTotal=1})",
  1
)
s=s.replace(
  '<div class="field"><label>時間</label><input id="rCostTime" type="time" value="${esc(time||\'\')}"></div>',
  '<div class="field"><label>日期時間（24小時制）</label><input id="rCostDateTime" type="text" inputmode="numeric" value="${esc(dateTime||\'\')}" placeholder="2026/09/30 20:59"></div>',
  1
)
s=s.replace(
  '<div class="field"><label>時間</label><input id="rsTime" type="time" value="${esc(f.x.time||\'\')}"></div>',
  '<div class="field"><label>日期時間（24小時制）</label><input id="rsDateTime" type="text" inputmode="numeric" value="${esc(receiptStoredDateTime(f.x,d))}" placeholder="2026/09/30 20:59"></div>',
  1
)
s=s.replace(
  '<div class="receipt-read-card"><div class="receipt-read-label">時間</div><div class="receipt-read-value">${esc(f.x.time||\'—\')}</div></div>',
  '<div class="receipt-read-card"><div class="receipt-read-label">日期時間（24小時制）</div><div class="receipt-read-value">${esc(receiptStoredDateTime(f.x,d)||\'—\')}</div></div>',
  1
)

old="function saveReceiptSplitEdit(id){const f=findCostById(id);if(!f.x)return;const amount=$('#rsAmount')?.value;if(amount==='')return appAlert('請填金額');Object.assign(f.x,{category:$('#rsCategory').value,time:$('#rsTime')?.value||'',note:$('#rsNote').value.trim(),amount});saveData();renderDay();openReceiptCostView(id,false)}"
new="function saveReceiptSplitEdit(id){const f=findCostById(id);if(!f.x)return;const amount=$('#rsAmount')?.value;if(amount==='')return appAlert('請填金額');const raw=$('#rsDateTime')?.value.trim()||'',dateTime=raw?normalizeReceiptDateTime(raw,f.d?.date||''):'';if(raw&&!dateTime)return appAlert('日期時間請用 24 小時制，例如 2026/09/30 20:59');Object.assign(f.x,{category:$('#rsCategory').value,dateTime,time:receiptTimeFromDateTime(dateTime),note:$('#rsNote').value.trim(),amount});saveData();renderDay();openReceiptCostView(id,false)}"
if old not in s: raise SystemExit('v1.0.22 failed: saved edit')
s=s.replace(old,new,1)

old="category=guessReceiptCategory(lines.join('\\n')),time=guessReceiptTime(lines),batchTotal="
new="category=guessReceiptCategory(lines.join('\\n')),dateTime=guessReceiptDateTime(lines,d?.date||''),batchTotal="
if old not in s: raise SystemExit('v1.0.22 failed: OCR parser marker')
s=s.replace(old,new,1)
s=s.replace("amount:cands[0]?.amount??'',time,batchIndex,batchTotal","amount:cands[0]?.amount??'',dateTime,batchIndex,batchTotal",1)
s=s.replace("amount:'',time:'',batchIndex,batchTotal","amount:'',dateTime:(dayById(tripById(route.tripId),route.dayId)?.date||'').replace(/-/g,'/'),batchIndex,batchTotal",1)

old="function saveReceiptCost(){const d=dayById(tripById(route.tripId),route.dayId);if(!d)return;const amount=$('#rCostAmount')?.value;if(amount==='')return appAlert('請填金額');const id=uid();let receiptUri='';try{if(window.Android&&Android.keepReceiptPhoto)receiptUri=Android.keepReceiptPhoto()||''}catch(e){}d.costs=d.costs||[];d.costs.push({id,category:$('#rCostCategory').value,time:$('#rCostTime')?.value||'',note:$('#rCostNote').value.trim(),amount,receiptUri});saveData();receiptBatchSaved++;pendingReceiptResult=null;closeReceiptSplit();renderDay();if(!receiptUri)appAlert('消費已建立，但收據照片保存失敗');if(receiptUriQueue.length){advanceReceiptQueue()}else appAlert('已建立消費並保存收據')}"
new="function saveReceiptCost(){const d=dayById(tripById(route.tripId),route.dayId);if(!d)return;const amount=$('#rCostAmount')?.value;if(amount==='')return appAlert('請填金額');const raw=$('#rCostDateTime')?.value.trim()||'',dateTime=raw?normalizeReceiptDateTime(raw,d.date||''):'';if(raw&&!dateTime)return appAlert('日期時間請用 24 小時制，例如 2026/09/30 20:59');const id=uid();let receiptUri='';try{if(window.Android&&Android.keepReceiptPhoto)receiptUri=Android.keepReceiptPhoto()||''}catch(e){}d.costs=d.costs||[];d.costs.push({id,category:$('#rCostCategory').value,dateTime,time:receiptTimeFromDateTime(dateTime),note:$('#rCostNote').value.trim(),amount,receiptUri});saveData();receiptBatchSaved++;pendingReceiptResult=null;closeReceiptSplit();renderDay();if(!receiptUri)appAlert('消費已建立，但收據照片保存失敗');if(receiptUriQueue.length){advanceReceiptQueue()}else appAlert('已建立消費並保存收據')}"
if old not in s: raise SystemExit('v1.0.22 failed: new receipt save')
s=s.replace(old,new,1)

old="tm=x.time?`${esc(x.time)}　`:'';"
new="tm=x.dateTime?`${esc(x.dateTime)}　`:(x.time?`${esc(x.time)}　`:'');"
if old in s: s=s.replace(old,new,1)

p.write_text(s)

p=root/'app/build.gradle'
g=p.read_text()
g=re.sub(r"versionCode\\s+\\d+","versionCode 122",g,count=1)
g=re.sub(r"versionName\\s+['\\\"][^'\\\"]+['\\\"]","versionName '1.0.22'",g,count=1)
p.write_text(g)

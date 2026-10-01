from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

marker=".receipt-photo-link{display:inline-flex;align-items:center;gap:4px}\n"
extra=r'''.receipt-split{position:fixed;inset:0;z-index:900;background:#fff;display:grid;grid-template-columns:var(--receipt-left,45%) 8px minmax(0,1fr);width:100%;height:100%;overflow:hidden}
.receipt-split-left,.receipt-split-right{min-width:0;height:100%;display:flex;flex-direction:column;background:#fff}
.receipt-split-left{background:#f1f0eb}
.receipt-split-head{flex:0 0 auto;min-height:50px;background:var(--yellow);padding:8px 10px;display:flex;align-items:center;justify-content:space-between;gap:8px;border-bottom:1px solid #d9ba00}
.receipt-split-title{font-size:.9rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.receipt-split-head button{border:0;border-radius:10px;background:rgba(255,255,255,.72);min-height:34px;padding:6px 9px;font-size:.76rem}
.receipt-preview-scroll{flex:1;min-height:0;overflow:auto;padding:8px 6px 18px;text-align:center;overscroll-behavior:contain}
.receipt-preview-img{display:block;width:100%;height:auto;max-width:none;margin:0 auto;background:#fff;box-shadow:0 1px 6px rgba(0,0,0,.12);user-select:none;-webkit-user-drag:none}
.receipt-preview-empty{padding:24px 8px;color:#777;font-size:.76rem;line-height:1.5}
.receipt-split-divider{height:100%;background:#ddd8ca;cursor:col-resize;touch-action:none;position:relative}
.receipt-split-divider:after{content:"";position:absolute;left:2px;top:45%;width:4px;height:42px;border-radius:4px;background:#aaa38e}
.receipt-split-body{flex:1;min-height:0;overflow:auto;padding:12px 10px 18px}
.receipt-split-foot{flex:0 0 auto;padding:9px 9px 10px;border-top:1px solid #e8e5da;display:flex;gap:6px;justify-content:flex-end;background:#fff}
.receipt-split-foot button{min-height:42px;padding:7px 10px}
.receipt-manual-note{font-size:.7rem;line-height:1.45;color:#777;background:#f7f5ee;border-radius:9px;padding:7px 8px;margin-bottom:10px}
.receipt-read-card{border-bottom:1px solid #ece8dc;padding:10px 2px}
.receipt-read-card:first-child{padding-top:1px}.receipt-read-label{font-size:.7rem;color:#777;margin-bottom:3px}.receipt-read-value{font-size:.95rem;line-height:1.4;overflow-wrap:anywhere}
.receipt-split .field label{font-size:.72rem}.receipt-split .field input,.receipt-split .field select,.receipt-split .field textarea{font-size:.88rem;padding:9px}.receipt-split .cost-form-note{min-height:92px}
'''
if extra not in s:
    if marker not in s: raise SystemExit('v1.0.19a failed: style marker')
    s=s.replace(marker,marker+extra,1)

start=s.index("function editCost(id){")
end=s.index("function saveCostEdit(id)",start)
s=s[:start]+r'''function editCost(id){const f=findCostById(id);if(!f.x)return;if(f.x.receiptUri){openReceiptCostView(id);return}dlg('編輯花費',costForm(f.x),`<button class="dangerbtn" onclick="deleteCostAsk('${id}')">刪除</button><button class="secondary" onclick="closeDlg()">取消</button><button class="primary" onclick="saveCostEdit('${id}')">儲存</button>`)}
'''+s[end:]

ocr=s.index("function receiptOcrResult(json){")
helpers=r'''let activeReceiptSplit=null;
function receiptPreviewData(uri){try{return window.Android&&Android.getReceiptPreviewData?Android.getReceiptPreviewData(uri)||'':''}catch(e){return''}}
function openActiveReceiptOriginal(){if(!activeReceiptSplit?.uri)return;try{if(window.Android&&Android.openReceiptPhoto)Android.openReceiptPhoto(activeReceiptSplit.uri)}catch(e){appAlert('收據照片無法開啟')}}
function closeReceiptSplit(){const el=$('#receiptSplit');if(el)el.remove();activeReceiptSplit=null}
function initReceiptDivider(){const box=$('#receiptSplit'),bar=$('#receiptSplitDivider');if(!box||!bar)return;let dragging=false;const move=e=>{if(!dragging)return;const r=box.getBoundingClientRect(),x=(e.clientX-r.left)/r.width*100,p=Math.max(34,Math.min(64,x));box.style.setProperty('--receipt-left',p+'%')};bar.onpointerdown=e=>{dragging=true;bar.setPointerCapture?.(e.pointerId);move(e)};bar.onpointermove=move;bar.onpointerup=bar.onpointercancel=()=>dragging=false}
function receiptSplitShell({title,uri,body,foot,progress='',mode='view'}){const preview=receiptPreviewData(uri);closeReceiptSplit();activeReceiptSplit={...(activeReceiptSplit||{}),mode,uri};document.body.insertAdjacentHTML('beforeend',`<div id="receiptSplit" class="receipt-split"><section class="receipt-split-left"><div class="receipt-split-head"><div class="receipt-split-title">${esc(progress||'收據')}</div><button onclick="openActiveReceiptOriginal()">放大</button></div><div class="receipt-preview-scroll">${preview?`<img class="receipt-preview-img" src="${preview}" draggable="false">`:`<div class="receipt-preview-empty">收據預覽無法載入。<br>可按「放大」嘗試開啟原圖。</div>`}</div></section><div id="receiptSplitDivider" class="receipt-split-divider"></div><section class="receipt-split-right"><div class="receipt-split-head"><div class="receipt-split-title">${esc(title)}</div><button onclick="${mode==='new'?'cancelReceiptConfirm()':'closeReceiptSplit()'}">×</button></div><div class="receipt-split-body">${body}</div><div class="receipt-split-foot">${foot}</div></section></div>`);initReceiptDivider()}
function showNewReceiptSplit({uri,category,store,amount,batchIndex=1,batchTotal=1}){activeReceiptSplit={mode:'new',uri};const d=dayById(tripById(route.tripId),route.dayId),t=tripById(route.tripId),cur=d?.currency||t?.currencies?.[0]||'TWD';const body=`<div class="receipt-manual-note">辨識結果只先幫妳帶入；請直接對照左邊收據修改。照片會隨這筆消費一起保存。</div><div class="formgrid"><div class="field"><label>分類</label><select id="rCostCategory">${costOptions.map(o=>`<option ${o===category?'selected':''}>${o}</option>`).join('')}</select></div><div class="field"><label>店名／備註</label><textarea class="cost-form-note" id="rCostNote" placeholder="可直接看左邊收據輸入">${esc(store||'')}</textarea></div><div class="field"><label>金額（${esc(cur)}）</label><input id="rCostAmount" type="number" inputmode="decimal" step="any" value="${amount??''}" placeholder="0"></div></div>`;const foot=batchTotal>1?`<button class="secondary" onclick="skipReceipt()">略過</button><button class="secondary" onclick="cancelReceiptConfirm()">取消全部</button><button class="primary" onclick="saveReceiptCost()">儲存</button>`:`<button class="secondary" onclick="cancelReceiptConfirm()">取消</button><button class="primary" onclick="saveReceiptCost()">儲存</button>`;receiptSplitShell({title:'收據記帳',uri,body,foot,progress:batchTotal>1?`第 ${batchIndex} / ${batchTotal} 張`:'收據',mode:'new'})}
function openReceiptCostView(id,editing=false){const f=findCostById(id);if(!f.x?.receiptUri)return;const d=f.d,t=f.t||tripById(route.tripId),cur=d?.currency||t?.currencies?.[0]||'TWD';let body,foot;if(editing){body=`<div class="formgrid"><div class="field"><label>分類</label><select id="rsCategory">${costOptions.map(o=>`<option ${o===f.x.category?'selected':''}>${o}</option>`).join('')}</select></div><div class="field"><label>店名／備註</label><textarea class="cost-form-note" id="rsNote">${esc(f.x.note||'')}</textarea></div><div class="field"><label>金額（${esc(cur)}）</label><input id="rsAmount" type="number" inputmode="decimal" step="any" value="${esc(f.x.amount??'')}"></div></div>`;foot=`<button class="dangerbtn" onclick="deleteReceiptCostAsk('${id}')">刪除</button><button class="secondary" onclick="openReceiptCostView('${id}',false)">取消修改</button><button class="primary" onclick="saveReceiptSplitEdit('${id}')">儲存</button>`}else{body=`<div class="receipt-read-card"><div class="receipt-read-label">分類</div><div class="receipt-read-value">${esc(f.x.category||'')}</div></div><div class="receipt-read-card"><div class="receipt-read-label">店名／備註</div><div class="receipt-read-value">${esc(f.x.note||'—')}</div></div><div class="receipt-read-card"><div class="receipt-read-label">金額</div><div class="receipt-read-value">${num(Number(f.x.amount||0))} ${esc(cur)}</div></div>`;foot=`<button class="dangerbtn" onclick="deleteReceiptCostAsk('${id}')">刪除</button><button class="secondary" onclick="closeReceiptSplit()">關閉</button><button class="primary" onclick="openReceiptCostView('${id}',true)">修改</button>`}activeReceiptSplit={mode:editing?'edit':'view',uri:f.x.receiptUri,id};receiptSplitShell({title:editing?'修改消費':'消費明細',uri:f.x.receiptUri,body,foot,progress:'收據',mode:editing?'edit':'view'})}
function saveReceiptSplitEdit(id){const f=findCostById(id);if(!f.x)return;const amount=$('#rsAmount')?.value;if(amount==='')return appAlert('請填金額');Object.assign(f.x,{category:$('#rsCategory').value,note:$('#rsNote').value.trim(),amount});saveData();renderDay();openReceiptCostView(id,false)}
function deleteReceiptCostAsk(id){const f=findCostById(id);if(!f.x)return;dlg('刪除花費',`<p>確定刪除這筆「${esc(f.x.category)}${f.x.note?'・'+esc(f.x.note):''}」？</p><p class="subtle">連同 App 內保存的收據照片一起刪除。</p>`,`<button class="secondary" onclick="closeDlg()">取消</button><button class="dangerbtn" onclick="deleteReceiptCostConfirm('${id}')">確定刪除</button>`)}
function deleteReceiptCostConfirm(id){const f=findCostById(id);if(!f.d||!f.x)return;try{if(f.x.receiptUri&&window.Android&&Android.deleteReceiptPhoto)Android.deleteReceiptPhoto(f.x.receiptUri)}catch(e){}f.d.costs=(f.d.costs||[]).filter(x=>x.id!==id);saveData();closeDlg();closeReceiptSplit();renderDay()}
'''
s=s[:ocr]+helpers+s[ocr:]

start=s.index("function receiptOcrResult(json){")
end=s.index("function chooseReceiptAmount",start)
s=s[:start]+r'''function receiptOcrResult(json){let obj;try{obj=typeof json==='string'?JSON.parse(json):json}catch(e){return receiptOcrError('收據辨識結果無法讀取')}const lines=normalizeReceiptLines(obj.text||''),d=dayById(tripById(route.tripId),route.dayId),t=tripById(route.tripId),cur=d?.currency||t?.currencies?.[0]||'TWD',store=guessReceiptStore(lines),cands=receiptAmountCandidates(lines,cur),category=guessReceiptCategory(lines.join('\\n')),batchTotal=receiptUriQueue.length||1,batchIndex=receiptUriQueue.length?receiptBatchIndex+1:1;pendingReceiptResult={uri:obj.uri||'',source:obj.source||'',lines,cands};showNewReceiptSplit({uri:obj.uri||'',category,store,amount:cands[0]?.amount??'',batchIndex,batchTotal})}
'''+s[end:]

start=s.index("function receiptOcrError(message){")
end=s.index("function retryReceiptOcr",start)
s=s[:start]+r'''function receiptOcrError(message,uri=''){const batchTotal=receiptUriQueue.length||1,batchIndex=receiptUriQueue.length?receiptBatchIndex+1:1,currentUri=uri||receiptUriQueue[receiptBatchIndex]||'';if(currentUri){pendingReceiptResult={uri:currentUri,source:'',lines:[],cands:[]};showNewReceiptSplit({uri:currentUri,category:'其他',store:'',amount:'',batchIndex,batchTotal});appAlert('辨識不到也沒關係，可直接對照收據輸入');return}dlg('收據辨識暫時無法使用',`<div class="receipt-status">${esc(message||'辨識失敗')}</div>`,`<button class="secondary" onclick="cancelReceiptConfirm()">取消</button><button class="primary" onclick="retryReceiptOcr()">再試一次</button>`)}
'''+s[end:]

start=s.index("function cancelReceiptConfirm(){")
end=s.index("function saveReceiptCost",start)
s=s[:start]+r'''function cancelReceiptConfirm(){try{if(window.Android&&Android.discardReceiptPhoto)Android.discardReceiptPhoto()}catch(e){}pendingReceiptResult=null;receiptUriQueue=[];receiptBatchIndex=0;receiptBatchSaved=0;receiptBatchSkipped=0;closeReceiptSplit();closeDlg()}
'''+s[end:]

start=s.index("function saveReceiptCost(){")
end=s.index("function skipReceipt",start)
s=s[:start]+r'''function saveReceiptCost(){const d=dayById(tripById(route.tripId),route.dayId);if(!d)return;const amount=$('#rCostAmount')?.value;if(amount==='')return appAlert('請填金額');const id=uid();let receiptUri='';try{if(window.Android&&Android.keepReceiptPhoto)receiptUri=Android.keepReceiptPhoto()||''}catch(e){}d.costs=d.costs||[];d.costs.push({id,category:$('#rCostCategory').value,note:$('#rCostNote').value.trim(),amount,receiptUri});saveData();receiptBatchSaved++;pendingReceiptResult=null;closeReceiptSplit();renderDay();if(!receiptUri)appAlert('消費已建立，但收據照片保存失敗');if(receiptUriQueue.length){advanceReceiptQueue()}else appAlert('已建立消費並保存收據')}
'''+s[end:]

start=s.index("function skipReceipt(){")
end=s.index("function receiptBatchFinished",start)
s=s[:start]+r'''function skipReceipt(){try{if(window.Android&&Android.discardReceiptPhoto)Android.discardReceiptPhoto()}catch(e){}receiptBatchSkipped++;pendingReceiptResult=null;closeReceiptSplit();advanceReceiptQueue()}
'''+s[end:]

start=s.index("function openSavedReceipt(id){")
end=s.index("\n\nfunction num",start)
s=s[:start]+r'''function openSavedReceipt(id){openReceiptCostView(id,false)}
'''+s[end:]

old="function confirmDeleteCost(id){const f=findCostById(id);if(!f.d)return;f.d.costs=(f.d.costs||[]).filter(x=>x.id!==id);saveData();closeDlg();renderDay()}"
new="function confirmDeleteCost(id){const f=findCostById(id);if(!f.d)return;try{if(f.x?.receiptUri&&window.Android&&Android.deleteReceiptPhoto)Android.deleteReceiptPhoto(f.x.receiptUri)}catch(e){}f.d.costs=(f.d.costs||[]).filter(x=>x.id!==id);saveData();closeDlg();renderDay()}"
if old in s: s=s.replace(old,new,1)

p.write_text(s)

p=root/'app/build.gradle'
g=p.read_text()
g=re.sub(r"versionCode\s+\d+","versionCode 119",g,count=1)
g=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.19'",g,count=1)
p.write_text(g)

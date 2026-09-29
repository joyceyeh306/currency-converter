from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit('v1.0.9 patch failed: '+label)
    s=s.replace(old,new,1)

style_marker=".detail-foot-actions{display:flex;gap:8px;justify-content:flex-end;flex-wrap:wrap}\n"
extra=r""".time-picker-button{width:100%;border:1px solid #ddd7c4;border-radius:12px;background:#fff;padding:9px 12px;display:flex;align-items:center;justify-content:space-between;gap:10px;text-align:left;min-height:48px}.time-picker-button .time-value{font-size:1.1rem;color:#111}.time-picker-button small{font-size:.72rem;color:var(--muted);font-weight:400}
.wheel-overlay{position:fixed;inset:0;background:rgba(0,0,0,.42);z-index:500;display:flex;align-items:flex-end;justify-content:center}.wheel-sheet{width:min(620px,100%);background:#fff;border-radius:22px 22px 0 0;padding:14px 16px calc(16px + env(safe-area-inset-bottom));box-shadow:0 -12px 35px rgba(0,0,0,.22)}.wheel-title{text-align:center;font-size:1rem;margin-bottom:8px}.wheel-grid{position:relative;display:grid;grid-template-columns:1fr 34px 1fr;align-items:center;max-width:320px;margin:0 auto}.wheel-col{height:220px;overflow-y:auto;scroll-snap-type:y mandatory;padding:88px 0;scrollbar-width:none;overscroll-behavior:contain}.wheel-col::-webkit-scrollbar{display:none}.wheel-option{height:44px;scroll-snap-align:center;display:flex;align-items:center;justify-content:center;font-size:1.18rem;color:#9a9a9a}.wheel-option.selected{font-size:1.36rem;color:#111}.wheel-colon{text-align:center;font-size:1.45rem;z-index:2}.wheel-center{position:absolute;left:0;right:0;top:88px;height:44px;border-top:1px solid #e4d78c;border-bottom:1px solid #e4d78c;background:rgba(255,214,0,.08);pointer-events:none}.wheel-actions{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin-top:10px}.wheel-actions button{width:100%}
.cost-card.read-mode{padding:10px 11px 12px}.cost-summary-top{display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:8px}.cost-summary-top .tiny{padding:7px 11px}.cost-detail-title{font-size:.74rem;color:var(--muted);margin:11px 2px 3px;padding-top:9px;border-top:1px solid #eee7ca}.cost-detail-list{display:grid}.cost-detail-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px;align-items:center;width:100%;border:0;border-top:1px solid #f0ecdf;background:transparent;padding:8px 2px;text-align:left;color:#222}.cost-detail-row:first-child{border-top:0}.cost-detail-text{min-width:0;font-size:.78rem;line-height:1.35}.cost-detail-text b{font-weight:500;margin-right:7px}.cost-detail-text .note{color:#666;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;display:inline;max-width:100%}.cost-detail-value{text-align:right;font-size:.78rem;white-space:nowrap}.cost-detail-value small{display:block;color:var(--muted);font-size:.64rem;margin-top:2px}.cost-empty{padding:12px 2px;color:var(--muted);font-size:.82rem}.cost-form-note{width:100%;min-height:72px;border:1px solid #ddd7c4;border-radius:12px;padding:11px}
"""
if style_marker not in s:
    raise SystemExit('v1.0.9 patch failed: style marker')
s=s.replace(style_marker,style_marker+extra,1)

old_time="""<div class="field"><label>時間（24 小時制）</label><input id="iTime" class="time-input" type="text" inputmode="numeric" maxlength="5" autocomplete="off" value="${esc(it.time||'')}" placeholder="例如輸入 0830" oninput="formatTimeTyping(this)"></div>"""
new_time="""<div class="field"><label>時間（24 小時制）</label><input id="iTime" type="hidden" value="${esc(it.time||'')}"><button type="button" class="time-picker-button" onclick="openTimeWheel()"><span class="time-value" id="iTimeText">${esc(it.time||'未設定')}</span><small>點選時間</small></button></div>"""
rep(old_time,new_time,'item time field')

needle="""function normalizeTime(raw){raw=String(raw||'').trim();if(!raw)return'';let h,m;if(raw.includes(':')){const p=raw.split(':');if(p.length!==2)return null;h=Number(p[0]);m=Number(p[1])}else{const d=raw.replace(/\\D/g,'');if(d.length===3){h=Number(d.slice(0,1));m=Number(d.slice(1))}else if(d.length===4){h=Number(d.slice(0,2));m=Number(d.slice(2))}else return null}if(!Number.isInteger(h)||!Number.isInteger(m)||h<0||h>23||m<0||m>59)return null;return String(h).padStart(2,'0')+':'+String(m).padStart(2,'0')}
"""
if needle not in s:
    raise SystemExit('v1.0.9 patch failed: normalize time')
wheel=r"""let wheelHour=12,wheelMinute=0,wheelTimer=null;
function wheelOptions(max,selected,kind){let out='';for(let i=0;i<max;i++){const v=String(i).padStart(2,'0');out+=`<div class="wheel-option ${i===selected?'selected':''}" data-v="${i}" onclick="wheelTap('${kind}',${i},this)">${v}</div>`}return out}
function refreshWheelSelected(col){const idx=Math.max(0,Math.min(col.children.length-1,Math.round(col.scrollTop/44)));[...col.children].forEach((x,i)=>x.classList.toggle('selected',i===idx));if(col.dataset.kind==='h')wheelHour=idx;else wheelMinute=idx}
function wheelScroll(col){clearTimeout(wheelTimer);wheelTimer=setTimeout(()=>{const idx=Math.max(0,Math.min(col.children.length-1,Math.round(col.scrollTop/44)));col.scrollTo({top:idx*44,behavior:'smooth'});refreshWheelSelected(col)},70)}
function wheelTap(kind,v,el){const col=el.parentElement;col.scrollTo({top:v*44,behavior:'smooth'});if(kind==='h')wheelHour=v;else wheelMinute=v;setTimeout(()=>refreshWheelSelected(col),180)}
function openTimeWheel(){const raw=$('#iTime')?.value||'';const m=/^(\d{2}):(\d{2})$/.exec(raw);if(m){wheelHour=Number(m[1]);wheelMinute=Number(m[2])}else{const n=new Date();wheelHour=n.getHours();wheelMinute=Math.round(n.getMinutes()/5)*5;if(wheelMinute>=60){wheelMinute=0;wheelHour=(wheelHour+1)%24}}const ov=document.createElement('div');ov.className='wheel-overlay';ov.id='timeWheelOverlay';ov.innerHTML=`<div class="wheel-sheet"><div class="wheel-title">選擇時間</div><div class="wheel-grid"><div class="wheel-center"></div><div class="wheel-col" data-kind="h" onscroll="wheelScroll(this)">${wheelOptions(24,wheelHour,'h')}</div><div class="wheel-colon">：</div><div class="wheel-col" data-kind="m" onscroll="wheelScroll(this)">${wheelOptions(60,wheelMinute,'m')}</div></div><div class="wheel-actions"><button class="secondary" onclick="clearTimeWheel()">清除時間</button><button class="secondary" onclick="closeTimeWheel()">取消</button><buttn class="primary" onclick="confirmTimeWheel()">完成</button></div></div>`;document.body.appendChild(ov);requestAnimationFrame(()=>{$$('.wheel-col').filter(x=>x.closest('#timeWheelOverlay')).forEach(col=>{const v=col.dataset.kind==='h'?wheelHour:wheelMinute;col.scrollTop=v*44;refreshWheelSelected(col)})})}
function closeTimeWheel(){document.getElementById('timeWheelOverlay')?.remove()}
function clearTimeWheel(){const el=$('#iTime'),txt=$('#iTimeText');if(el)el.value='';if(txt)txt.textContent='未設定匯率';closeTimeWheel()}
function confirmTimeWheel()){const v=String(wheelHour).padStart(2,'0')+':'+String(wheelMinute).padStart(2,'0'),el=$('#iTime'),txt=$('#iTimeText');if(el)el.value=v;if(txt)txt.textContent=v;closeTimeWheel()}
"""
s=s.replace(needle,needle+wheel,1)

start=s.index("function costSection(t,d){")
end=s.index("function num(x){",start)
if start<0 or end<0:
    raise SystemExit('v1.0.9 patch failed: cost block')

new_cost=r"""function costSection(t,d){d.costs=d.costs||[];const cur=d.currency||t.currencies[0]||'TWD';const sums={};let grand=0;for(const x of d.costs){const a=Number(x.amount)||0,g=costGroup[x.category]||'其他';sums[g]=(sums[g]||0)+a;grand+=a}const order=['餐飲','購物','交通','門票／活動','住宿','其他'];const details=d.costs.map(x=>costDetailHtml(x,cur,t)).join('');return `<section class="meals"><div class="section-title"><span>今天花多少錢呢?</span><span class="day-currency">當日貨幣：${esc(cur)}</span></div><div class="card cost-card read-mode"><div class="cost-summary-top"><span class="subtle">${d.costs.length?`共 ${d.costs.length} 筆`:'今天還沒有花費紀錄'}</span><button class="tiny yellow" onclick="newCost()">＋ 新增</button></div>${d.costs.length?`<div class="totals">${order.filter(g=>sums[g]).map(g=>`<div class="total"><span>${g}</span>${moneyPair(t,cur,sums[g])}</div>`).join('')}<div class="total grand"><span>今日總額</span>${moneyPair(t,cur,grand)}</div></div><div class="cost-detail-title">今日明細・點一下可修改</div><div class="cost-detail-list">${details}</div>`:`<div class="cost-empty">按右上角「＋ 新增」記下第一筆花費。</div>`}</div></section>`}
function costDetailHtml(x,cur,t){const a=Number(x.amount)||0,r=twdRateFor(t,cur),tw=cur==='TWD'?'':r?`約 NT$${num(a*r)}`:'未設定匯率';return `<button class="cost-detail-row" onclick="editCost('${x.id}')"><span class="cost-detail-text"><b>${esc(x.category||'其他')}</b><span class="note">${esc(x.note||'')}</span></span><span class="cost-detail-value">${x.amount!==''?num(a):'—'} ${esc(cur)}${tw?`<small>${tw}</small>`:''}</span></button>`}
function costForm(x={}){return `<div class="formgrid"><div class="field"><label>項目</label><select id="cCategory">${costOptions.map(o=>`<option ${o===(x.category||'早餐')?'selected':''}>${o}</option>`).join('')}</select></div><div class="field"><label>備註（選填）</label><textarea class="cost-form-note" id="cNote" placeholder="例如：Egg Drop、7-11">${esc(x.note||'')}</textarea></div><div class="field"><label>金額</label><input id="cAmount" type="number" inputmode="decimal" step="any" value="${esc(x.amount??'')}" placeholder="0"></div></div>`}
function newCost(){dlg('新增花費',costForm({category:'早餐'}),`<button class="secondary" onclick="closeDlg()">取消</button><button class="primary" onclick="saveNewCost()">新增</button>`);setTimeout(()=>$('#cAmount')?.focus(),80)}
function saveNewCost(){const d=dayById(tripById(route.tripId),route.dayId),amount=$('#cAmount').value;if(amount==='')return appAlert('請填金額');d.costs=d.costs||[];d.costs.push({id:uid(),category:$('#cCategory').value,note:$('#cNote').value.trim(),amount});saveData();closeDlg();renderDay()}
function findCostById(id){const d=dayById(tripById(route.tripId),route.dayId);return {d,x:(d?.costs||[]).find(c=>c.id===id)}}
function editCost(id){const f=findCostById(id);if(!f.x)return;dlg('編輯花費',costForm(f.x),`<button class="dangerbtn" onclick="deleteCostAsk('${id}')">刪除</button><button class="secondary" onclick="closeDlg()">取消</button><button class="primary" onclick="saveCostEdit('${id}')">儲存</button>`)}
function saveCostEdit(id){const f=findCostById(id),amount=$('#cAmount').value;if(!f.x)return;if(amount==='')return appAlert('請填金額');Object.assign(f.x,{category:$('#cCategory').value,note:$('#cNote').value.trim(),amount});saveData();closeDlg();renderDay()}
function deleteCostAsk(id){const f=findCostById(id);if(!f.x)return;dlg('刪除花費',`<p>確定刪除這歆「${esc(f.x.category)}${f.x.note?'・'+esc(f.x.note):''}」？</p>`,`<button class="secondary" onclick="editCost('${id}')">取消</button><button class="dangerbtn" onclick="confirmDeleteCost('${id}')">確定刪除</button>`)}
function confirmDeleteCost(id){const f=findCostById(id);if(!f.d)return;f.d.costs=(f.d.costs||[]).filter(x=>x.id!==id);saveData();closeDlg();renderDay()}
"""
s=s[:start]+new_cost+s[end:]

p.write_text(s)

g=root/'app/build.gradle'
b=g.read_text()
b=re.sub(r"versionCode\s+\d+","versionCode 109",b,count=1)
b=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.9'",b,count=1)
g.write_text(b)

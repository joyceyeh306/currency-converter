from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

def sub_one(pattern, repl, text, label):
    out,n=re.subn(pattern, repl, text, count=1, flags=re.M)
    if n!=1:
        raise SystemExit(f'v1.0.5 patch failed: {label}')
    return out

if 't.passes=t.passes||[];' not in s:
    s=s.replace('    t.stays=t.stays||[];\n', '    t.stays=t.stays||[];\n    t.passes=t.passes||[];\n', 1)

css_marker='.stay-status{display:inline-block;background:var(--yellow);border-radius:999px;padding:3px 8px;font-size:.73rem;margin-right:6px}'
if '.pass-card{' not in s:
    pass_css=css_marker+'''.pass-card{background:#fffdf4;border:1px solid #e8d98c;border-left:5px solid #e4bd00;border-radius:14px;padding:9px 10px;margin-bottom:7px}.pass-main{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:7px;align-items:start}.pass-title{font-size:1rem;line-height:1.35}.pass-meta{font-size:.8rem;color:var(--muted);line-height:1.45;margin-top:3px}.pass-note{font-size:.82rem;color:#555;line-height:1.45;margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.pass-status{display:inline-block;background:var(--yellow2);border-radius:999px;padding:3px 8px;font-size:.72rem;margin-right:6px}.pass-overview-note{font-size:.84rem;color:#555;line-height:1.5;margin-top:5px;white-space:pre-wrap}'''
    if css_marker not in s:
        raise SystemExit('v1.0.5 patch failed: CSS marker')
    s=s.replace(css_marker,pass_css,1)

stay_helper="function stayDayStatus(st,date){if(st.start===date&&st.end===date)return'今日住宿';if(st.start===date)return'今日入住';if(st.end===date)return'今日退房';return'住宿中'}"
if 'function passesForDay' not in s:
    helpers=stay_helper+"""
function passesForDay(t,date){return (t.passes||[]).filter(p=>p.start<=date&&date<=p.end).sort((a,b)=>a.start.localeCompare(b.start))}
function passDayStatus(p,date){if(p.start===date&&p.end===date)return'今日有效';if(p.start===date)return'今日啟用';if(p.end===date)return'最後一天';return'使用中'}
function passDayProgress(p,date){const one=86400000,s=new Date(p.start+'T00:00:00'),e=new Date(p.end+'T00:00:00'),d=new Date(date+'T00:00:00');const total=Math.floor((e-s)/one)+1,day=Math.floor((d-s)/one)+1;return {day,total}}
"""
    if stay_helper not in s:
        raise SystemExit('v1.0.5 patch failed: stay helper')
    s=s.replace(stay_helper,helpers,1)

renderTrip=r'''function renderTrip(){const t=tripById(route.tripId);if(!t){route.tripId=null;return render()}const days=[...t.days].sort((a,b)=>a.date.localeCompare(b.date));const stays=[...(t.stays||[])].sort((a,b)=>a.start.localeCompare(b.start));const passes=[...(t.passes||[])].sort((a,b)=>a.start.localeCompare(b.start));$('#screen').innerHTML=topbar(t.name,true)+`<main class="content"><div class="section-title"><span>這次去哪？</span><span class="section-actions"><button class="tiny" onclick="editTrip('${t.id}')">旅程設定</button><button class="tiny yellow" onclick="newDay('${t.id}')">＋ 日期</button></span></div><div class="trip-mini-meta"><span>${fmtDate(t.start)} ～ ${fmtDate(t.end)}</span><span>・</span><span>${t.currencies.join('、')||'未設定貨幣'}</span></div>${days.length?`<section class="card" style="padding:3px 12px">${days.map(d=>`<div class="day-row" onclick="openDay('${d.id}')"><div class="dateblock">${fmtDate(d.date)}<small>週${weekday(d.date)}</small></div><div class="city">${esc(d.city||'未填城市')}<small>${d.items.length?esc([...d.items].sort((a,b)=>(a.time||'99:99').localeCompare(b.time||'99:99'))[0].title):'尚未安排行程'}</small></div><button class="day-menu" title="編輯這一天" onclick="event.stopPropagation();editDay('${d.id}')">⋯</button></div>`).join('')}</section>`:`<div class="empty">還沒有日期，請先新增。</div>`}<div class="section-title"><span>住宿</span><button class="tiny yellow" onclick="newStay('${t.id}')">＋ 新增住宿</button></div>${stays.length?stays.map(st=>`<div class="stay-card" onclick="editStay('${st.id}')"><div class="stay-title">🏨 ${esc(st.name)}</div><div class="stay-meta">${fmtDate(st.start)} 入住 → ${fmtDate(st.end)} 退房${st.breakfast&&st.breakfast!=='未設定'?`　早餐：${esc(st.breakfast)}`:''}</div>${st.note?`<div class="stay-overview-note">${esc(st.note)}</div>`:''}</div>`).join(''):`<div class="empty">尚未新增住宿</div>`}<div class="section-title"><span>票券／通行證</span><button class="tiny yellow" onclick="newPass('${t.id}')">＋ 新增票券</button></div>${passes.length?passes.map(p=>`<div class="pass-card" onclick="editPass('${p.id}')"><div class="pass-title">🎫 ${esc(p.name)}</div><div class="pass-meta">${fmtDate(p.start)} ～ ${fmtDate(p.end)}${passDayProgress(p,p.start).total>1?`　${passDayProgress(p,p.start).total} 日`:''}</div>${p.note?`<div class="pass-overview-note">${esc(p.note)}</div>`:''}</div>`).join(''):`<div class="empty">尚未新增票券／通行證</div>`}</main>`}'''
s=sub_one(r'^function renderTrip\(\)\{.*$',renderTrip,s,'renderTrip')

renderDay=r'''async function renderDay(){const t=tripById(route.tripId),d=dayById(t,route.dayId);if(!d){route.dayId=null;return render()}const items=[...d.items].sort((a,b)=>(a.time||'99:99').localeCompare(b.time||'99:99'));const counts={};for(const it of items)counts[it.id]=await countAttachments(it.id);const stays=staysForDay(t,d.date),stayCounts={};for(const st of stays)stayCounts[st.id]=await countAttachments(st.id);const passes=passesForDay(t,d.date),passCounts={};for(const p of passes)passCounts[p.id]=await countAttachments(p.id);const stayHtml=stays.length?`<div class="section-title">今日住宿</div>${stays.map(st=>{const mapUrl=preferredMapUrl(st);return `<div class="stay-card"><div class="stay-main"><div onclick="editStay('${st.id}')"><div class="stay-title"><span class="stay-status">${stayDayStatus(st,d.date)}</span>🏨 ${esc(st.name)}</div><div class="stay-meta">${fmtDate(st.start)} 入住 → ${fmtDate(st.end)} 退房${st.breakfast&&st.breakfast!=='未設定'?`　早餐：${esc(st.breakfast)}`:''}</div></div><div class="quick">${stayCounts[st.id]?`<button class="qbtn has" onclick="event.stopPropagation();openAttachments('${st.id}')">📄${stayCounts[st.id]>1?stayCounts[st.id]:''}</button>`:`<button class="qbtn" onclick="event.stopPropagation();openAttachments('${st.id}')">＋📄</button>`}${mapUrl?`<button class="qbtn has" title="${mapButtonTitle(st)}" onclick="event.stopPropagation();openStayMap('${st.id}')">📍</button>`:''}</div></div></div>`}).join('')}`:'';const passHtml=passes.length?`<div class="section-title">今日票券</div>${passes.map(p=>{const pg=passDayProgress(p,d.date);return `<div class="pass-card"><div class="pass-main"><div onclick="editPass('${p.id}')"><div class="pass-title"><span class="pass-status">${passDayStatus(p,d.date)}</span>🎫 ${esc(p.name)}</div><div class="pass-meta">${fmtDate(p.start)} ～ ${fmtDate(p.end)}${pg.total>1?`　第 ${pg.day}/${pg.total} 天`:''}</div>${p.note?`<div class="pass-note">${esc(p.note)}</div>`:''}</div><div class="quick">${passCounts[p.id]?`<button class="qbtn has" onclick="event.stopPropagation();openAttachments('${p.id}')">📄${passCounts[p.id]>1?passCounts[p.id]:''}</button>`:''}</div></div></div>`}).join('')}`:'';$('#screen').innerHTML=topbar(`${fmtDate(d.date)}｜${d.city}`,true)+`<main class="content">${stayHtml}${passHtml}<div class="section-title">今日行程</div><section class="itinerary">${items.length?items.map(it=>{const mapUrl=preferredMapUrl(it);return `<div class="item"><div class="time">${esc(it.time||'—')}</div><div class="item-main" onclick="viewItem('${it.id}')"><div class="item-title"><span class="type-mark">${typeIcon(it.type)}</span> ${esc(it.title)}</div>${it.note?`<div class="item-note" title="${esc(it.note)}">${esc(it.note)}</div>`:''}</div><div class="quick">${counts[it.id]?`<button class="qbtn has" onclick="event.stopPropagation();openAttachments('${it.id}')">📄${counts[it.id]>1?counts[it.id]:''}</button>`:''}${mapUrl?`<button class="qbtn has" title="${mapButtonTitle(it)}" onclick="event.stopPropagation();openItemMap('${it.id}')">📍</button>`:''}<button class="qbtn" title="編輯行程" onclick="event.stopPropagation();editItem('${it.id}')">⋯</button></div></div>`}).join(''):`<div class="empty" style="margin:12px">今天還沒有行程</div>`}</section><button class="primary addline" onclick="newItem('${d.id}')">＋ 新增行程</button>${costSection(t,d)}</main>`}'''
s=sub_one(r'^async function renderDay\(\)\{.*$',renderDay,s,'renderDay')

stay_delete="async function confirmDeleteStay(id){const found=findStay(id);if(!found)return;if($('#deleteStayFiles')?.checked)await deleteAttachmentsByItem(id);found.t.stays=(found.t.stays||[]).filter(x=>x.id!==id);saveData();closeDlg();render()}"
if 'function passForm' not in s:
    pass_crud=stay_delete+r'''
function passForm(p={},includeFiles=false){return `<div class="formgrid"><div class="field"><label>票券／通行證名稱</label><input id="pName" value="${esc(p.name||'')}" placeholder="例如：Swiss Travel Pass 8日"></div><div class="fx-row"><div class="field"><label>啟用日期</label><input id="pStart" type="date" value="${esc(p.start||'')}"></div><div class="field"><label>到期日期</label><input id="pEnd" type="date" value="${esc(p.end||'')}"></div></div><div class="field"><label>簡單備註</label><textarea id="pNote" style="width:100%;min-height:72px;border:1px solid #ddd7c4;border-radius:12px;padding:11px" placeholder="例如：二等艙、兩人票券、護照姓名等">${esc(p.note||'')}</textarea></div>${includeFiles?`<div class="field"><label>票券 PDF／圖片（可多選）</label><input id="pFiles" type="file" accept="application/pdf,image/*" multiple></div>`:''}</div>`}
function newPass(tid){dlg('新增票券／通行證',passForm({},true),`<button class="secondary" onclick="closeDlg()">取消</button><button class="primary" onclick="saveNewPass('${tid}')">新增</button>`)}
async function saveNewPass(tid){const t=tripById(tid),name=$('#pName').value.trim(),start=$('#pStart').value,end=$('#pEnd').value;if(!name||!start||!end)return alert('請填名稱、啟用與到期日期');if(end<start)return alert('到期日期不能早於啟用日期');const id=uid();t.passes=t.passes||[];t.passes.push({id,name,start,end,note:$('#pNote').value});saveData();for(const f of [...($('#pFiles')?.files||[])])await putAttachment({id:uid(),itemId:id,name:f.name,type:f.type||'application/octet-stream',blob:f,created:Date.now()});closeDlg();render()}
function findPass(id){for(const t of data.trips){const p=(t.passes||[]).find(x=>x.id===id);if(p)return {t,p}}return null}
function editPass(id){const found=findPass(id);if(!found)return;dlg('編輯票券／通行證',passForm(found.p,true),`<button class="dangerbtn" onclick="deletePass('${id}')">刪除票券</button><button class="primary" onclick="savePassEdit('${id}')">儲存</button>`)}
async function savePassEdit(id){const found=findPass(id);if(!found)return;const name=$('#pName').value.trim(),start=$('#pStart').value,end=$('#pEnd').value;if(!name||!start||!end)return alert('請填名稱、啟用與到期日期');if(end<start)return alert('到期日期不能早於啟用日期');Object.assign(found.p,{name,start,end,note:$('#pNote').value});for(const f of [...($('#pFiles')?.files||[])])await putAttachment({id:uid(),itemId:id,name:f.name,type:f.type||'application/octet-stream',blob:f,created:Date.now()});saveData();closeDlg();render()}
function deletePass(id){const found=findPass(id);if(!found)return;dlg('刪除票券／通行證',`<p>確定刪除「${esc(found.p.name)}」？</p><label class="checkline" style="margin-top:12px"><input id="deletePassFiles" type="checkbox" checked> 同時刪除離線 PDF／圖片</label>`,`<button class="secondary" onclick="editPass('${id}')">取消</button><button class="dangerbtn" onclick="confirmDeletePass('${id}')">確定刪除</button>`)}
async function confirmDeletePass(id){const found=findPass(id);if(!found)return;if($('#deletePassFiles')?.checked)await deleteAttachmentsByItem(id);found.t.passes=(found.t.passes||[]).filter(x=>x.id!==id);saveData();closeDlg();render()}
'''
    if stay_delete not in s:
        raise SystemExit('v1.0.5 patch failed: stay delete marker')
    s=s.replace(stay_delete,pass_crud,1)

show_new=r'''async function showTripAttachments(tid){const t=tripById(tid),rows=[];for(const st of t.stays||[]){const as=await getAttachments(st.id);if(as.length)rows.push({label:`住宿｜${st.name}`,as})}for(const p of t.passes||[]){const as=await getAttachments(p.id);if(as.length)rows.push({label:`票券｜${p.name}`,as})}for(const d of t.days){for(const it of d.items){const as=await getAttachments(it.id);if(as.length)rows.push({label:`${fmtDate(d.date)}｜${it.title}`,as})}}dlg('這趟旅程的附件',rows.length?rows.map(r=>`<div style="margin-bottom:13px"><strong>${esc(r.label)}</strong>${r.as.map(a=>attachHtml(a)).join('')}</div>`).join(''):'<div class="empty">這趟旅程還沒有附件</div>')}'''
s=sub_one(r'^async function showTripAttachments\(tid\)\{.*$',show_new,s,'showTripAttachments')

s=sub_one(r'^async function tripAttachmentIds\(t\)\{.*$',"async function tripAttachmentIds(t){const ids=new Set([...(t.stays||[]).map(x=>x.id),...(t.passes||[]).map(x=>x.id)]);for(const d of t.days||[])for(const it of d.items||[])ids.add(it.id);return ids}",s,'tripAttachmentIds')
if 'function tripAttachmentIdsForExport' not in s:
    anchor="async function tripAttachmentIds(t){const ids=new Set([...(t.stays||[]).map(x=>x.id),...(t.passes||[]).map(x=>x.id)]);for(const d of t.days||[])for(const it of d.items||[])ids.add(it.id);return ids}"
    s=s.replace(anchor,anchor+"\nasync function tripAttachmentIdsForExport(t){return tripAttachmentIds(t)}",1)

export_new=r'''function exportTrip(){const t=currentMemoTrip();if(!t)return;const stays=(t.stays||[]).map(st=>`<div class="row">🏨 ${esc(st.name)}｜${st.start} → ${st.end}${st.breakfast&&st.breakfast!=='未設定'?`｜早餐：${esc(st.breakfast)}`:''}${st.note?`<br><small>${esc(st.note)}</small>`:''}</div>`).join('');const passes=(t.passes||[]).map(p=>`<div class="row">🎫 ${esc(p.name)}｜${p.start} → ${p.end}${p.note?`<br><small>${esc(p.note)}</small>`:''}</div>`).join('');const html=`<!doctype html><meta charset="utf-8"><title>${esc(t.name)}</title><style>body{font-family:sans-serif;max-width:800px;margin:30px auto;line-height:1.7}h1{border-bottom:5px solid #ffd600}.day{margin:22px 0}.row{padding:6px 0;border-bottom:1px solid #ddd}</style><h1>${esc(t.name)}</h1><p>${t.start} ～ ${t.end}</p><h2>住宿</h2>${stays||'<p>無</p>'}<h2>票券／通行證</h2>${passes||'<p>無</p>'}${[...t.days].sort((a,b)=>a.date.localeCompare(b.date)).map(d=>`<section class="day"><h2>${d.date}｜${esc(d.city)}</h2>${[...d.items].sort((a,b)=>(a.time||'99').localeCompare(b.time||'99')).map(i=>`<div class="row">${esc(i.time||'')}　${esc(i.title)}${i.note?`<br><small>${esc(i.note)}</small>`:''}</div>`).join('')}${(d.costs||[]).length?`<h3>餐食與花費</h3>${d.costs.map(c=>`<div class="row">${esc(c.category)}｜${esc(c.note||'')}｜${esc(c.amount||'')} ${esc(d.currency||'')}</div>`).join('')}`:''}</section>`).join('')}<h2>旅行備忘錄</h2><pre style="white-space:pre-wrap">${esc(t.memo||'')}</pre>`;downloadBlob(new Blob([html],{type:'text/html'}),`${t.name}.html`)}'''
s=sub_one(r'^function exportTrip\(\)\{.*$',export_new,s,'exportTrip')

bp=root/'app/build.gradle'
bs=bp.read_text()
if "versionName '1.0.4'" not in bs:
    raise SystemExit('v1.0.5 patch failed: expected v1.0.4')
bs=bs.replace("versionCode 5\n        versionName '1.0.4'", "versionCode 6\n        versionName '1.0.5'")
bp.write_text(bs)

p.write_text(s)

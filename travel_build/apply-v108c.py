from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit('v1.0.8c patch failed: '+label)
    s=s.replace(old,new,1)

rep("dlg('票券・附件',","dlg('附件',",'attachment title')

rep("async function tripAttachmentIds(t){const ids=new Set([...(t.stays||[]).map(x=>x.id),...(t.passes||[]).map(x=>x.id)]);for(const d of t.days||[])for(const it of d.items||[])ids.add(it.id);return ids}","async function tripAttachmentIds(t){const ids=new Set([...(t.stays||[]).map(x=>x.id),...(t.passes||[]).map(x=>x.id),...(t.alternatives||[]).map(x=>x.id)]);for(const d of t.days||[])for(const it of d.items||[])ids.add(it.id);return ids}",'trip attachment ids')
rep("function validTargetIds(t){const ids=new Set([...(t.stays||[]).map(x=>x.id),...(t.passes||[]).map(x=>x.id)]);for(const d of t.days||[])for(const i of d.items||[])ids.add(i.id);return ids}","function validTargetIds(t){const ids=new Set([...(t.stays||[]).map(x=>x.id),...(t.passes||[]).map(x=>x.id),...(t.alternatives||[]).map(x=>x.id)]);for(const d of t.days||[])for(const i of d.items||[])ids.add(i.id);return ids}",'valid target ids')

old_show="""async function showTripAttachments(tid,dayId=''){const t=tripById(tid),rows=[],d=dayId?dayById(t,dayId):null;if(d){for(const st of staysForDay(t,d.date)){const as=await getAttachments(st.id);if(as.length)rows.push({label:`住宿｜${st.name}`,as})}for(const p of passesForDay(t,d.date)){const as=await getAttachments(p.id);if(as.length)rows.push({label:`交通／票券｜${p.name}`,as})}for(const it of d.items||[]){const as=await getAttachments(it.id);if(as.length)rows.push({label:`${fmtDate(d.date)}｜${it.title}`,as})}}else{for(const st of t.stays||[]){const as=await getAttachments(st.id);if(as.length)rows.push({label:`住宿｜${st.name}`,as})}for(const p of t.passes||[]){const as=await getAttachments(p.id);if(as.length)rows.push({label:`交通／票券｜${p.name}`,as})}for(const day of t.days){for(const it of day.items){const as=await getAttachments(it.id);if(as.length)rows.push({label:`${fmtDate(day.date)}｜${it.title}`,as})}}}dlg(dayId?'今日附件':'這趟旅程的附件',rows.length?rows.map(r=>`<div style=\"margin-bottom:13px\"><strong>${esc(r.label)}</strong>${r.as.map(a=>attachHtml(a)).join('')}</div>`).join(''):'<div class=\"empty\">目前沒有附件</div>')}"""
new_show="""async function showTripAttachments(tid,dayId=''){const t=tripById(tid),rows=[],d=dayId?dayById(t,dayId):null;if(d){for(const st of staysForDay(t,d.date)){const as=await getAttachments(st.id);if(as.length)rows.push({label:`住宿｜${st.name}`,as})}for(const p of passesForDay(t,d.date)){const as=await getAttachments(p.id);if(as.length)rows.push({label:`交通／票券｜${p.name}`,as})}for(const it of d.items||[]){const as=await getAttachments(it.id);if(as.length)rows.push({label:`${fmtDate(d.date)}｜${it.title}`,as})}}else{for(const st of t.stays||[]){const as=await getAttachments(st.id);if(as.length)rows.push({label:`住宿｜${st.name}`,as})}for(const p of t.passes||[]){const as=await getAttachments(p.id);if(as.length)rows.push({label:`交通／票券｜${p.name}`,as})}for(const a of t.alternatives||[]){const as=await getAttachments(a.id);if(as.length)rows.push({label:`備選｜${a.title}`,as})}for(const day of t.days){for(const it of day.items){const as=await getAttachments(it.id);if(as.length)rows.push({label:`${fmtDate(day.date)}｜${it.title}`,as})}}}dlg(dayId?'今日附件':'這趟旅程的附件',rows.length?rows.map(r=>`<div style=\"margin-bottom:13px\"><strong>${esc(r.label)}</strong>${r.as.map(a=>attachHtml(a)).join('')}</div>`).join(''):'<div class=\"empty\">目前沒有附件</div>')}"""
rep(old_show,new_show,'show trip attachments')

old_merge="""function mergeTripUpdate(oldTrip,incoming){const t=migrateData({font:data.font,trips:[structuredClone(incoming)]}).trips[0];t.memo=t.memo||oldTrip.memo||'';t.reminders=structuredClone(oldTrip.reminders||[]);for(const d of t.days||[]){const old=(oldTrip.days||[]).find(x=>x.id===d.id)||(oldTrip.days||[]).find(x=>x.date===d.date);if(old){if((old.costs||[]).length)d.costs=structuredClone(old.costs);if(old.memo)d.memo=old.memo}}const valid=validTargetIds(t);t.reminders=t.reminders.filter(r=>r.targetType==='trip'||valid.has(r.targetId));return t}"""
new_merge="""function mergeTripUpdate(oldTrip,incoming){const t=migrateData({font:data.font,trips:[structuredClone(incoming)]}).trips[0];t.memo=t.memo||oldTrip.memo||'';t.reminders=structuredClone(oldTrip.reminders||[]);const seen=new Set((t.alternatives||[]).map(a=>a.id));t.alternatives=[...(t.alternatives||[]),...(oldTrip.alternatives||[]).filter(a=>!seen.has(a.id)).map(a=>structuredClone(a))];for(const d of t.days||[]){const old=(oldTrip.days||[]).find(x=>x.id===d.id)||(oldTrip.days||[]).find(x=>x.date===d.date);if(old){if((old.costs||[]).length)d.costs=structuredClone(old.costs);if(old.memo)d.memo=old.memo}}const valid=validTargetIds(t);t.reminders=t.reminders.filter(r=>r.targetType==='trip'||valid.has(r.targetId));return t}"""
rep(old_merge,new_merge,'merge alternatives')

rep("for(const p of t.passes||[]){const o=p.id;p.id=uid();map.set(o,p.id)}for(const d of t.days||[]){","for(const p of t.passes||[]){const o=p.id;p.id=uid();map.set(o,p.id)}for(const a of t.alternatives||[]){const o=a.id;a.id=uid();map.set(o,a.id)}for(const d of t.days||[]){",'remap alternatives')
rep("for(const p of t.passes||[])await deleteAttachmentsByItem(p.id);data.trips=","for(const p of t.passes||[])await deleteAttachmentsByItem(p.id);for(const a of t.alternatives||[])await deleteAttachmentsByItem(a.id);data.trips=",'delete alt attachments')
rep('data=obj.data;saveData();','data=migrateData(obj.data);saveData();','restore migrate')

# readable HTML export: inject alternate section without replacing the whole function
needle="const passes=(t.passes||[]).map(p=>`<div class=\"row\">🎫 ${esc(p.name)}｜${p.start} → ${p.end}${p.note?`<br><small>${esc(p.note)}</small>`:''}</div>`).join('');const html="
replacement="const passes=(t.passes||[]).map(p=>`<div class=\"row\">🎫 ${esc(p.name)}｜${p.start} → ${p.end}${p.note?`<br><small>${esc(p.note)}</small>`:''}</div>`).join('');const alternatives=(t.alternatives||[]).map(a=>`<div class=\"row\">${esc(a.type||'備選')}｜${esc(a.title)}${a.area?`｜${esc(a.area)}`:''}${a.note?`<br><small>${esc(a.note)}</small>`:''}</div>`).join('');const html="
rep(needle,replacement,'export alternatives var')
rep("<h2>交通／票券</h2>${passes||'<p>無</p>'}${[...t.days]","<h2>交通／票券</h2>${passes||'<p>無</p>'}<h2>備選地點</h2>${alternatives||'<p>無</p>'}${[...t.days]",'export alt section')

p.write_text(s)

# version
g=root/'app/build.gradle'
b=g.read_text()
b=re.sub(r"versionCode\s+\d+","versionCode 108",b,count=1)
b=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.8'",b,count=1)
g.write_text(b)

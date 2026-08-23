from pathlib import Path
import re
root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

# App/web title.
s=s.replace('<title>阿喬的旅行筆記本</title>','<title>自助旅行筆記</title>')

# Compact list stays compact, but reserve shrinking center column and one-line notes.
s=re.sub(r'(\.item\{[^}]*grid-template-columns:)([^;]+)(;[^}]*\})', lambda m:m.group(1)+'58px minmax(0,1fr) auto'+m.group(3), s, count=1)
if '.item-main{' not in s:
    s=s.replace('.item-title{font-weight:400;line-height:1.25}', '.item-main{min-width:0;cursor:pointer}.item-title{font-weight:400;line-height:1.25}')
s=s.replace('.item-note{font-size:.83rem;color:var(--muted);margin-top:4px;line-height:1.45}', '.item-note{font-size:.83rem;color:var(--muted);margin-top:4px;line-height:1.45;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}')
if '.stay-overview-note{' not in s:
    s=s.replace('.savehint{text-align:right;color:var(--muted);font-size:.78rem;margin-top:6px}', '.savehint{text-align:right;color:var(--muted);font-size:.78rem;margin-top:6px}.stay-overview-note{font-size:.84rem;color:#555;line-height:1.5;margin-top:5px;white-space:pre-wrap}.detail-title{font-size:1.18rem;line-height:1.4;margin:0 0 4px}.detail-meta{font-size:.86rem;color:var(--muted);margin-bottom:14px}.detail-block{border-top:1px solid #eee9dc;padding:11px 0}.detail-block:first-of-type{border-top:0}.detail-label{font-size:.75rem;color:var(--muted);margin-bottom:5px}.detail-text{white-space:pre-wrap;line-height:1.65}.detail-actions{display:flex;gap:8px;flex-wrap:wrap}.detail-actions button{flex:1;min-width:120px}')

helpers="""function isNaverMapUrl(u=''){u=String(u||'').trim();return /^(https?:\\/\\/)?((m\\.)?map\\.naver\\.com|naver\\.me)(\\/|$)/i.test(u)}
function preferredMapUrl(obj){const other=String(obj?.url||'').trim();if(isNaverMapUrl(other))return other;return String(obj?.maps||'').trim()}
function mapButtonTitle(obj){return isNaverMapUrl(obj?.url||'')?'開啟 NAVER 地圖':'開啟地圖'}
function openExternalUrl(u){u=String(u||'').trim();if(!u)return;window.open(u,'_blank','noopener')}
function openItemMap(iid){const d=dayById(tripById(route.tripId),route.dayId),it=d?.items.find(x=>x.id===iid);if(it)openExternalUrl(preferredMapUrl(it))}
function openStayMap(id){const found=findStay(id);if(found)openExternalUrl(preferredMapUrl(found.st))}
"""
if 'function isNaverMapUrl' not in s:
    anchor="function fmtDate(s)"
    s=s.replace(anchor, helpers+anchor)

renderTrips="""function renderTrips(){if(route.dayId)return renderDay();if(route.tripId)return renderTrip();const groups={current:[],upcoming:[],past:[]};data.trips.forEach(t=>groups[tripStatus(t)].push(t));groups.upcoming.sort((a,b)=>a.start.localeCompare(b.start));groups.past.sort((a,b)=>b.start.localeCompare(a.start));const block=(title,arr)=>`<div class=\"section-title\">${title}</div>${arr.length?arr.map(t=>tripCard(t)).join(''):`<div class=\"empty\">目前沒有${title}</div>`}`;$('#screen').innerHTML=topbar('自助旅行筆記',false)+`<main class=\"content\">${block('旅行中',groups.current)}${block('即將出發',groups.upcoming)}${block('過往旅程',groups.past)}</main><button class=\"fab\" onclick=\"newTrip()\">＋</button>`}"""
renderTrip="""function renderTrip(){const t=tripById(route.tripId);if(!t){route.tripId=null;return render()}const days=[...t.days].sort((a,b)=>a.date.localeCompare(b.date));const stays=[...(t.stays||[])].sort((a,b)=>a.start.localeCompare(b.start));$('#screen').innerHTML=topbar(t.name,true)+`<main class=\"content\"><div class=\"section-title\"><span>這次去哪？</span><span class=\"section-actions\"><button class=\"tiny\" onclick=\"editTrip('${t.id}')\">旅程設定</button><button class=\"tiny yellow\" onclick=\"newDay('${t.id}')\">＋ 日期</button></span></div><div class=\"trip-mini-meta\"><span>${fmtDate(t.start)} ～ ${fmtDate(t.end)}</span><span>・</span><span>${t.currencies.join('、')||'未設定貨幣'}</span></div>${days.length?`<section class=\"card\" style=\"padding:3px 12px\">${days.map(d=>`<div class=\"day-row\" onclick=\"openDay('${d.id}')\"><div class=\"dateblock\">${fmtDate(d.date)}<small>週${weekday(d.date)}</small></div><div class=\"city\">${esc(d.city||'未填城市')}<small>${d.items.length?esc([...d.items].sort((a,b)=>(a.time||'99:99').localeCompare(b.time||'99:99'))[0].title):'尚未安排行程'}</small></div><button class=\"day-menu\" title=\"編輯這一天\" onclick=\"event.stopPropagation();editDay('${d.id}')\">⋯</button></div>`).join('')}</section>`:`<div class=\"empty\">還沒有日期，請先新增。</div>`}<div class=\"section-title\"><span>住宿</span><button class=\"tiny yellow\" onclick=\"newStay('${t.id}')\">＋ 新增住宿</button></div>${stays.length?stays.map(st=>`<div class=\"stay-card\" onclick=\"editStay('${st.id}')\"><div class=\"stay-title\">🏨 ${esc(st.name)}</div><div class=\"stay-meta\">${fmtDate(st.start)} 入住 → ${fmtDate(st.end)} 退房${st.breakfast&&st.breakfast!=='未設定'?`　早餐：${esc(st.breakfast)}`:''}</div>${st.note?`<div class=\"stay-overview-note\">${esc(st.note)}</div>`:''}</div>`).join(''):`<div class=\"empty\">尚未新增住宿</div>`}</main>`}"""
renderDay="""async function renderDay(){const t=tripById(route.tripId),d=dayById(t,route.dayId);if(!d){route.dayId=null;return render()}const items=[...d.items].sort((a,b)=>(a.time||'99:99').localeCompare(b.time||'99:99'));const counts={};for(const it of items)counts[it.id]=await countAttachments(it.id);const stays=staysForDay(t,d.date),stayCounts={};for(const st of stays)stayCounts[st.id]=await countAttachments(st.id);const stayHtml=stays.length?`<div class=\"section-title\">今日住宿</div>${stays.map(st=>{const mapUrl=preferredMapUrl(st);return `<div class=\"stay-card\"><div class=\"stay-main\"><div onclick=\"editStay('${st.id}')\"><div class=\"stay-title\"><span class=\"stay-status\">${stayDayStatus(st,d.date)}</span>🏨 ${esc(st.name)}</div><div class=\"stay-meta\">${fmtDate(st.start)} 入住 → ${fmtDate(st.end)} 退房${st.breakfast&&st.breakfast!=='未設定'?`　早餐：${esc(st.breakfast)}`:''}</div></div><div class=\"quick\">${stayCounts[st.id]?`<button class=\"qbtn has\" onclick=\"event.stopPropagation();openAttachments('${st.id}')\">📄${stayCounts[st.id]>1?stayCounts[st.id]:''}</button>`:`<button class=\"qbtn\" onclick=\"event.stopPropagation();openAttachments('${st.id}')\">＋📄</button>`}${mapUrl?`<button class=\"qbtn has\" title=\"${mapButtonTitle(st)}\" onclick=\"event.stopPropagation();openStayMap('${st.id}')\">📍</button>`:''}</div></div></div>`}).join('')}`:'';$('#screen').innerHTML=topbar(`${fmtDate(d.date)}｜${d.city}`,true)+`<main class=\"content\">${stayHtml}<div class=\"section-title\">今日行程</div><section class=\"itinerary\">${items.length?items.map(it=>{const mapUrl=preferredMapUrl(it);return `<div class=\"item\"><div class=\"time\">${esc(it.time||'—')}</div><div class=\"item-main\" onclick=\"viewItem('${it.id}')\"><div class=\"item-title\"><span class=\"type-mark\">${typeIcon(it.type)}</span> ${esc(it.title)}</div>${it.note?`<div class=\"item-note\" title=\"${esc(it.note)}\">${esc(it.note)}</div>`:''}</div><div class=\"quick\">${counts[it.id]?`<button class=\"qbtn has\" onclick=\"event.stopPropagation();openAttachments('${it.id}')\">📄${counts[it.id]>1?counts[it.id]:''}</button>`:''}${mapUrl?`<button class=\"qbtn has\" title=\"${mapButtonTitle(it)}\" onclick=\"event.stopPropagation();openItemMap('${it.id}')\">📍</button>`:''}<button class=\"qbtn\" title=\"編輯行程\" onclick=\"event.stopPropagation();editItem('${it.id}')\">⋯</button></div></div>`}).join(''):`<div class=\"empty\" style=\"margin:12px\">今天還沒有行程</div>`}</section><button class=\"primary addline\" onclick=\"newItem('${d.id}')\">＋ 新增行程</button>${costSection(t,d)}</main>`}"""
viewItem="""async function viewItem(iid){const d=dayById(tripById(route.tripId),route.dayId),it=d?.items.find(x=>x.id===iid);if(!it)return;const count=await countAttachments(iid);const hasNaver=isNaverMapUrl(it.url||'');let actions='';if(hasNaver)actions+=`<button class=\"primary\" onclick=\"openExternalUrl('${esc(it.url)}')\">NAVER 地圖</button>`;if(it.maps)actions+=`<button class=\"secondary\" onclick=\"openExternalUrl('${esc(it.maps)}')\">Google Maps</button>`;if(it.url&&!hasNaver)actions+=`<button class=\"secondary\" onclick=\"openExternalUrl('${esc(it.url)}')\">其他連結</button>`;if(count)actions+=`<button class=\"secondary\" onclick=\"openAttachments('${iid}')\">附件 ${count}</button>`;const body=`<div class=\"detail-title\"><span class=\"type-mark\">${typeIcon(it.type)}</span> ${esc(it.title)}</div><div class=\"detail-meta\">${it.time?esc(it.time)+'　':''}${esc(it.type||'')}</div>${it.note?`<div class=\"detail-block\"><div class=\"detail-label\">備註</div><div class=\"detail-text\">${esc(it.note)}</div></div>`:''}${actions?`<div class=\"detail-block\"><div class=\"detail-label\">連結與附件</div><div class=\"detail-actions\">${actions}</div></div>`:''}`;dlg('行程詳細內容',body,`<button class=\"secondary\" onclick=\"closeDlg()\">關閉</button>`)}"""

for pattern,repl in [
    (r'^function renderTrips\(\)\{.*$',renderTrips),
    (r'^function renderTrip\(\)\{.*$',renderTrip),
    (r'^async function renderDay\(\)\{.*$',renderDay),
]:
    s,n=re.subn(pattern,lambda m,repl=repl:repl,s,count=1,flags=re.M)
    if n!=1: raise SystemExit(f'Could not patch {pattern}')

if 'async function viewItem(' not in s:
    s=s.replace('function itemForm(', viewItem+'\nfunction itemForm(',1)

p.write_text(s)

# Android app label and version.
p=root/'app/src/main/res/values/strings.xml'
s=p.read_text().replace('阿喬的旅行筆記本','自助旅行筆記')
p.write_text(s)
p=root/'app/build.gradle'
s=p.read_text().replace("versionCode 4\n        versionName '1.0.3'", "versionCode 5\n        versionName '1.0.4'")
p.write_text(s)

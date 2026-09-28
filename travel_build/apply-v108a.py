from pathlib import Path

p=Path('/tmp/ajo/ajo_build_min/app/src/main/assets/index.html')
s=p.read_text()

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit('v1.0.8a patch failed: '+label)
    s=s.replace(old,new,1)

rep('grid-template-columns:repeat(4,1fr)','grid-template-columns:repeat(5,1fr)','nav columns')
rep('.cost-add{width:100%;margin-top:7px;padding:9px 10px}', '''.cost-add{width:100%;padding:9px 10px}.cost-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:7px}.cost-done{width:100%;padding:9px 10px}\n.top-settings{border:0;background:rgba(255,255,255,.66);width:38px;height:38px;min-width:38px;border-radius:11px;color:#111;padding:9px}.top-settings .ui-icon{width:20px;height:20px}\n.app-alert-toast{position:fixed;left:50%;top:max(64px,calc(env(safe-area-inset-top) + 54px));transform:translate(-50%,-10px);z-index:220;background:#fffbea;border:1px solid #e4c400;border-radius:13px;padding:10px 14px;box-shadow:0 6px 22px rgba(0,0,0,.18);max-width:min(88vw,480px);font-size:.9rem;line-height:1.4;opacity:0;pointer-events:none;transition:.18s}.app-alert-toast.show{opacity:1;transform:translate(-50%,0)}\n.trip-context{font-size:.82rem;color:var(--muted);margin:0 2px 8px}\n.big-actions{display:grid;grid-template-columns:.8fr 1.7fr;gap:10px;margin-top:10px}.big-actions button{width:100%}''','ui extra styles')

rep('''  trips:'<path d="M5 7h14v11H5z"/><path d="M9 7V5h6v2"/>',\n  big:''','''  trips:'<path d="M5 7h14v11H5z"/><path d="M9 7V5h6v2"/>',\n  alt:'<path d="M12 21s6-5.1 6-11a6 6 0 1 0-12 0c0 5.9 6 11 6 11z"/><circle cx="12" cy="10" r="2"/>',\n  big:''','alt icon')

old_context="""function contextTools(tid,dayId=''){return `<div class=\"context-tools\"><button class=\"context-btn\" onclick=\"openReminders('${tid}','${dayId}')\">${iconSvg('bell')}<span>提醒</span></button><button class=\"context-btn\" onclick=\"${dayId?`openDayMemo('${tid}','${dayId}')`:`openTripMemo('${tid}')`}\">${iconSvg('note')}<span>備忘</span></button><button class=\"context-btn\" onclick=\"showTripAttachments('${tid}','${dayId}')\">${iconSvg('clip')}<span>附件</span></button></div>`}"""
new_context="""function contextTools(tid,dayId=''){const memo=dayId?`<button class=\"context-btn\" onclick=\"openDayMemo('${tid}','${dayId}')\">${iconSvg('note')}<span>備忘</span></button>`:'';return `<div class=\"context-tools\"><button class=\"context-btn\" onclick=\"openReminders('${tid}','${dayId}')\">${iconSvg('bell')}<span>提醒</span></button>${memo}<button class=\"context-btn\" onclick=\"showTripAttachments('${tid}','${dayId}')\">${iconSvg('clip')}<span>附件</span></button></div>`}"""
rep(old_context,new_context,'context tools')

old_top="""function topbar(title,back){return `<header class=\"topbar\">${back?`<button class=\"back\" onclick=\"goBack()\">‹</button>`:''}<h1>${esc(title)}</h1>${route.tab==='trips'&&route.dayId?`<button class=\"iconbtn\" title=\"編輯這一天\" onclick=\"editDay('${route.dayId}')\">⋯</button>`:''}</header>`}"""
new_top="""function topbar(title,back){const settings=route.tab==='trips'&&!route.tripId&&!route.dayId?`<button class=\"top-settings\" title=\"設定\" onclick=\"openSettings()\">${iconSvg('settings')}</button>`:'';return `<header class=\"topbar\">${back?`<button class=\"back\" onclick=\"goBack()\">‹</button>`:''}<h1>${esc(title)}</h1>${settings}${route.tab==='trips'&&route.dayId?`<button class=\"iconbtn\" title=\"編輯這一天\" onclick=\"editDay('${route.dayId}')\">⋯</button>`:''}</header>`}"""
rep(old_top,new_top,'topbar settings')

old_nav="""function renderNav(){const tabs=[['trips','旅程','trips'],['big','大字卡','big'],['fx','匯率','fx'],['settings','設定','settings']];$('#bottomNav').innerHTML=tabs.map(([id,label,ic])=>`<button class=\"navbtn ${route.tab===id?'active':''}\" onclick=\"switchTab('${id}')\">${iconSvg(ic)}${label}</button>`).join('')}\nfunction switchTab(tab){route.tab=tab;if(tab!=='trips'){route.dayId=null}render()}\nfunction goBack(){if(route.tab==='trips'&&route.dayId){route.dayId=null}else if(route.tab==='trips'&&route.tripId){route.tripId=null}render()}\nfunction render(){document.documentElement.style.setProperty('--fs',data.font==='large'?1.12:data.font==='max'?1.24:1);renderNav();if(route.tab==='trips')renderTrips();if(route.tab==='big')renderBig();if(route.tab==='fx')renderFx();if(route.tab==='settings')renderSettings()}"""
new_nav="""function renderNav(){const tabs=[['trips','旅程','trips'],['alt','備選','alt'],['memo','備忘','note'],['big','字卡','big'],['fx','匯率','fx']];$('#bottomNav').innerHTML=tabs.map(([id,label,ic])=>`<button class=\"navbtn ${route.tab===id?'active':''}\" onclick=\"switchTab('${id}')\">${iconSvg(ic)}${label}</button>`).join('')}\nfunction switchTab(tab){route.tab=tab;render()}\nfunction openSettings(){route.tab='settings';render()}\nfunction goBack(){if(route.tab==='settings'){route.tab='trips'}else if(route.tab==='trips'&&route.dayId){route.dayId=null}else if(route.tab==='trips'&&route.tripId){route.tripId=null}render()}\nfunction render(){document.documentElement.style.setProperty('--fs',data.font==='large'?1.12:data.font==='max'?1.24:1);renderNav();if(route.tab==='trips')renderTrips();if(route.tab==='alt')renderAlt();if(route.tab==='memo')renderMemo();if(route.tab==='big')renderBig();if(route.tab==='fx')renderFx();if(route.tab==='settings')renderSettings()}"""
rep(old_nav,new_nav,'bottom nav')

rep('function currentMemoTrip(){return tripById(data.currentTripId)||data.trips[0]}',"function currentMemoTrip(){return tripById(route.tripId)||tripById(data.currentTripId)||data.trips[0]}",'current context trip')

ms=s.index('function renderMemo(){')
me=s.index('function renderSettings(){',ms)
if ms<0 or me<0: raise SystemExit('v1.0.8a patch failed: memo block')
s=s[:ms]+'''function renderMemo(){const t=currentMemoTrip();if(!t){$('#screen').innerHTML=topbar('備忘',false)+`<main class="content"><div class="empty">請先新增一趟旅程</div></main>`;return}$('#screen').innerHTML=topbar('備忘',false)+`<main class="content"><div class="trip-context">${esc(t.name)}</div><textarea class="note-area" id="memoArea" placeholder="自由記下這趟旅行的事情…">${esc(t.memo||'')}</textarea><div class="savehint">自動儲存</div></main>`;$('#memoArea').addEventListener('input',e=>{t.memo=e.target.value;saveData()})}\n'''+s[me:]

rep("$('#screen').innerHTML=topbar('設定',false)+","$('#screen').innerHTML=topbar('設定',true)+",'settings back')

dlg_marker='''function dlg(title,body,foot=''){const d=$('#dialog');d.innerHTML=`<div class="dlg-head"><span>${title}</span><button class="dlg-close" onclick="closeDlg()">×</button></div><div class="dlg-body">${body}</div>${foot?`<div class="dlg-foot">${foot}</div>`:''}`;d.showModal();return d}\n'''
if dlg_marker not in s: raise SystemExit('v1.0.8a patch failed: dlg')
s=s.replace(dlg_marker,dlg_marker+'''function appAlert(message){let el=document.getElementById('appAlertToast');if(!el){el=document.createElement('div');el.id='appAlertToast';el.className='app-alert-toast';document.body.appendChild(el)}el.textContent=message;el.classList.add('show');clearTimeout(window._appAlertTimer);window._appAlertTimer=setTimeout(()=>el.classList.remove('show'),2600)}\n''',1)
s=s.replace('alert(','appAlert(')

rep('''<button class="secondary cost-add" onclick="addCost()">＋ 新增一筆</button><div class="totals" id="costTotals">''','''<div class="cost-actions"><button class="secondary cost-add" onclick="addCost()">＋ 新增一筆</button><button class="primary cost-done" onclick="finishCosts()">完成</button></div><div class="totals" id="costTotals">''','cost donee')
rep("function addCost(){const d=dayById(tripById(route.tripId),route.dayId);d.costs=d.costs||[];d.costs.push({id:uid(),category:'早餐',note:'',amount:''});saveData();renderDay()}","function addCost(){const d=dayById(tripById(route.tripId),route.dayId);d.costs=d.costs||[];d.costs.push({id:uid(),category:'早餐',note:'',amount:''});saveData();renderDay()}\nfunction finishCosts(){document.activeElement?.blur?.();saveData();appAlert('花費已儲存')}",'finish costs')

bs=s.index('function renderBig(){')
be=s.index('function clearBig()',bs)
if bs<0 or be<0: raise SystemExit('v1.0.8a patch failed: big card')
s=s[:bs]+'''function renderBig(){$('#screen').innerHTML=topbar('字卡',false)+`<main class="content bigcard-entry"><div class="subtle" style="margin-bottom:10px">把翻譯或其他 App 的文字貼進來；長按輸入框即可選擇「貼上」。</div><textarea id="bigInput" placeholder="在這裡輸入或貼上文字…">${esc(sessionStorage.getItem('bigtext')||'')}</textarea><div class="big-actions"><button class="secondary" onclick="clearBig()">清除</button><button class="primary" onclick="showFullText()">橫式全螢幕顯示</button></div></main>`;$('#bigInput').addEventListener('input',e=>sessionStorage.setItem('bigtext',e.target.value))}\n'''+s[be:]

rep("if(route.tab!=='trips'){route={tab:'trips',tripId:null,dayId:null};render();return true}","if(route.tab!=='trips'){route.tab='trips';render();return true}",'android back')

p.write_text(s)

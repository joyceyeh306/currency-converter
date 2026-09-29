from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit('v1.0.10 patch failed: '+label)
    s=s.replace(old,new,1)

old_css = """.wheel-overlay{position:fixed;inset:0;background:rgba(0,0,0,.42);z-index:500;display:flex;align-items:flex-end;justify-content:center}.wheel-sheet{width:min(620px,100%);background:#fff;border-radius:22px 22px 0 0;padding:14px 16px calc(16px + env(safe-area-inset-bottom));box-shadow:0 -12px 35px rgba(0,0,0,.22)}.wheel-title{text-align:center;font-size:1rem;margin-bottom:8px}.wheel-grid{position:relative;display:grid;grid-template-columns:1fr 34px 1fr;align-items:center;max-width:320px;margin:0 auto}.wheel-col{height:220px;overflow-y:auto;scroll-snap-type:y mandatory;padding:88px 0;scrollbar-width:none;overscroll-behavior:contain}.wheel-col::-webkit-scrollbar{display:none}.wheel-option{height:44px;scroll-snap-align:center;display:flex;align-items:center;justify-content:center;font-size:1.18rem;color:#9a9a9a}.wheel-option.selected{font-size:1.36rem;color:#111}.wheel-colon{text-align:center;font-size:1.45rem;z-index:2}.wheel-center{position:absolute;left:0;right:0;top:88px;height:44px;border-top:1px solid #e4d78c;border-bottom:1px solid #e4d78c;background:rgba(255,214,0,.08);pointer-events:none}.wheel-actions{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin-top:10px}.wheel-actions button{width:100%}
"""
new_css = """.inline-time-wheel{display:none;background:#fffdf4;border:1px solid #eadb83;border-radius:14px;padding:10px 10px 9px;margin-top:-2px}.inline-time-wheel.show{display:block}.wheel-title{text-align:center;font-size:.86rem;color:#555;margin-bottom:5px}.wheel-grid{position:relative;display:grid;grid-template-columns:1fr 30px 1fr;align-items:center;max-width:300px;margin:0 auto}.wheel-col{height:176px;overflow-y:auto;scroll-snap-type:y mandatory;padding:66px 0;scrollbar-width:none;overscroll-behavior:contain}.wheel-col::-webkit-scrollbar{display:none}.wheel-option{height:44px;scroll-snap-align:center;display:flex;align-items:center;justify-content:center;font-size:1.05rem;color:#aaa}.wheel-option.selected{font-size:1.3rem;color:#111}.wheel-colon{text-align:center;font-size:1.35rem;z-index:2}.wheel-center{position:absolute;left:0;right:0;top:66px;height:44px;border-top:1px solid #e4d78c;border-bottom:1px solid #e4d78c;background:rgba(255,214,0,.08);pointer-events:none}.wheel-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:8px}.wheel-actions button{width:100%;padding:8px 9px}.wheel-clear{grid-column:1 / -1;background:transparent!important;color:#777!important;text-decoration:underline;padding:4px!important}
"""
rep(old_css,new_css,'inline wheel styles')

old_form = """<div class="field"><label>時間（24 小時制）</label><input id="iTime" type="hidden" value="${esc(it.time||'')}"><button type="button" class="time-picker-button" onclick="openTimeWheel()"><span class="time-value" id="iTimeText">${esc(it.time||'未設定')}</span><small>點選時間</small></button></div><div class="field"><label>種類</label><select id="iType">"""
new_form = """<div class="field"><label>時間（24 小時制）</label><input id="iTime" type="hidden" value="${esc(it.time||'')}"><button type="button" class="time-picker-button" onclick="toggleInlineTimeWheel()"><span class="time-value" id="iTimeText">${esc(it.time||'未設定')}</span><small>點選時間</small></button></div><div class="field"><label>種類</label><select id="iType">"""
rep(old_form,new_form,'time button')

needle = """</select></div></div><div class="field"><label>行程名稱</label>"""
insert = """</select></div></div><div id="inlineTimeWheel" class="inline-time-wheel"></div><div class="field"><label>行程名稱</label>"""
rep(needle,insert,'inline picker container')

start=s.index("let wheelHour=12,wheelMinute=0,wheelTimer=null;")
end=s.index("async function viewItem(",start)
if start<0 or end<0:
    raise SystemExit('v1.0.10 patch failed: wheel function block')

wheel = r"""let wheelHour=12,wheelMinute=0,wheelTimer=null;
function wheelOptions(max,selected,kind){let out='';for(let i=0;i<max;i++){const v=String(i).padStart(2,'0');out+=`<div class="wheel-option ${i===selected?'selected':''}" data-v="${i}" onclick="wheelTap('${kind}',${i},this)">${v}</div>`}return out}
function refreshWheelSelected(col){const idx=Math.max(0,Math.min(col.children.length-1,Math.round(col.scrollTop/44)));[...col.children].forEach((x,i)=>x.classList.toggle('selected',i===idx));if(col.dataset.kind==='h')wheelHour=idx;else wheelMinute=idx}
function wheelScroll(col){clearTimeout(wheelTimer);wheelTimer=setTimeout(()=>{const idx=Math.max(0,Math.min(col.children.length-1,Math.round(col.scrollTop/44)));col.scrollTo({top:idx*44,behavior:'smooth'});refreshWheelSelected(col)},70)}
function wheelTap(kind,v,el){const col=el.parentElement;col.scrollTo({top:v*44,behavior:'smooth'});if(kind==='h')wheelHour=v;else wheelMinute=v;setTimeout(()=>refreshWheelSelected(col),180)}
function initWheelFromCurrent(){const raw=$('#iTime')?.value||'';const m=/^(\d{2}):(\d{2})$/.exec(raw);if(m){wheelHour=Number(m[1]);wheelMinute=Number(m[2])}else{const n=new Date();wheelHour=n.getHours();wheelMinute=Math.round(n.getMinutes()/5)*5;if(wheelMinute>=60){wheelMinute=0;wheelHour=(wheelHour+1)%24}}}
function renderInlineTimeWheel(){const box=$('#inlineTimeWheel');if(!box)return;box.innerHTML=`<div class="wheel-title">上下滑動選擇時間</div><div class="wheel-grid"><div class="wheel-center"></div><div class="wheel-col" data-kind="h" onscroll="wheelScroll(this)">${wheelOptions(24,wheelHour,'h')}</div><div class="wheel-colon">：</div><div class="wheel-col" data-kind="m" onscroll="wheelScroll(this)">${wheelOptions(60,wheelMinute,'m')}</div></div><div class="wheel-actions"><button type="button" class="secondary" onclick="cancelInlineTimeWheel()">取消</button><button type="button" class="primary" onclick="confirmInlineTimeWheel()">完成</button><button type="button" class="wheel-clear" onclick="clearInlineTimeWheel()">清除時間</button></div>`;box.classList.add('show');requestAnimationFrame(()=>{box.querySelectorAll('.wheel-col').forEach(col=>{const v=col.dataset.kind==='h'?wheelHour:wheelMinute;col.scrollTop=v*44;refreshWheelSelected(col)});box.scrollIntoView({block:'nearest',behavior:'smooth'})})}
function toggleInlineTimeWheel(){const box=$('#inlineTimeWheel');if(!box)return;if(box.classList.contains('show')){box.classList.remove('show');box.innerHTML='';return}initWheelFromCurrent();renderInlineTimeWheel()}
function cancelInlineTimeWheel(){const box=$('#inlineTimeWheel');if(box){box.classList.remove('show');box.innerHTML=''}}
function clearInlineTimeWheel(){const el=$('#iTime'),txt=$('#iTimeText');if(el)el.value='';if(txt)txt.textContent='未設定';cancelInlineTimeWheel()}
function confirmInlineTimeWheel(){const v=String(wheelHour).padStart(2,'0')+':'+String(wheelMinute).padStart(2,'0'),el=$('#iTime'),txt=$('#iTimeText');if(el)el.value=v;if(txt)txt.textContent=v;cancelInlineTimeWheel()}
"""
s=s[:start]+wheel+s[end:]

old_close="function closeDlg(){const d=$('#dialog');if(d.open)d.close()}"
if old_close in s:
    s=s.replace(old_close,"function closeDlg(){document.getElementById('timeWheelOverlay')?.remove();const d=$('#dialog');if(d.open)d.close()}",1)

if '<buttn' in s:
    raise SystemExit('invalid buttn tag still present')

p.write_text(s)

g=root/'app/build.gradle'
b=g.read_text()
b=re.sub(r"versionCode\s+\d+","versionCode 110",b,count=1)
b=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.10'",b,count=1)
g.write_text(b)

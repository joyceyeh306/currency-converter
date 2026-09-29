from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit('v1.0.12 patch failed: '+label)
    s=s.replace(old,new,1)

# Only the daily yellow date bar stays visible while scrolling.
rep(
".topbar{position:relative;z-index:20;background:var(--yellow);min-height:48px;padding:6px 12px;border-bottom:1px solid #e3be00;display:flex;align-items:center;gap:7px}",
".topbar{position:relative;z-index:20;background:var(--yellow);min-height:48px;padding:6px 12px;border-bottom:1px solid #e3be00;display:flex;align-items:center;gap:7px}.topbar.day-sticky{position:sticky;top:0;z-index:35}",
"daily sticky style"
)

old_top="""function topbar(title,back){const settings=route.tab==='trips'&&!route.tripId&&!route.dayId?`<button class="top-settings" title="設定" onclick="openSettings()">${iconSvg('settings')}</button>`:'';return `<header class="topbar">${back?`<button class="back" onclick="goBack()">‹</button>`:''}<h1>${esc(title)}</h1>${settings}${route.tab==='trips'&&route.dayId?`<button class="iconbtn" title="編輯這一天" onclick="editDay('${route.dayId}')">⋯</button>`:''}</header>`}"""
new_top="""function topbar(title,back){const settings=route.tab==='trips'&&!route.tripId&&!route.dayId?`<button class="top-settings" title="設定" onclick="openSettings()">${iconSvg('settings')}</button>`:'';const daySticky=route.tab==='trips'&&route.dayId?' day-sticky':'';return `<header class="topbar${daySticky}">${back?`<button class="back" onclick="goBack()">‹</button>`:''}<h1>${esc(title)}</h1>${settings}${route.tab==='trips'&&route.dayId?`<button class="iconbtn" title="編輯這一天" onclick="editDay('${route.dayId}')">⋯</button>`:''}</header>`}"""
rep(old_top,new_top,"daily sticky topbar")

# Keep two-line notes, but reclaim wasted space from the amount column.
rep(
".cost-detail-row{display:grid;grid-template-columns:minmax(0,1fr) 142px;gap:10px;align-items:start;width:100%;max-width:100%;min-width:0;border:0;border-top:1px solid #f0ecdf;background:transparent;padding:9px 2px;text-align:left;color:#222;overflow:hidden}",
".cost-detail-row{display:grid;grid-template-columns:minmax(0,1fr) 116px;gap:8px;align-items:start;width:100%;max-width:100%;min-width:0;border:0;border-top:1px solid #f0ecdf;background:transparent;padding:9px 2px;text-align:left;color:#222;overflow:hidden}",
"expense detail columns"
)
rep(
".cost-detail-value{width:142px;min-width:142px;text-align:right;font-size:.78rem;line-height:1.25;white-space:nowrap;overflow:hidden}",
".cost-detail-value{width:116px;min-width:116px;text-align:right;font-size:.78rem;line-height:1.25;white-space:nowrap;overflow:hidden}",
"expense value width"
)
rep(
"@media(max-width:390px){.cost-detail-row{grid-template-columns:minmax(0,1fr) 126px}.cost-detail-value{width:126px;min-width:126px}",
"@media(max-width:390px){.cost-detail-row{grid-template-columns:minmax(0,1fr) 106px;gap:7px}.cost-detail-value{width:106px;min-width:106px}",
"mobile expense width"
)

p.write_text(s)

g=root/'app/build.gradle'
b=g.read_text()
b=re.sub(r"versionCode\s+\d+","versionCode 112",b,count=1)
b=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.12'",b,count=1)
g.write_text(b)

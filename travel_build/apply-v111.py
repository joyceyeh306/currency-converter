from pathlib import Path
import re

root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit('v1.0.11 patch failed: '+label)
    s=s.replace(old,new,1)

# Stop the entire app from becoming horizontally scrollable.
rep(
"html,body{margin:0;background:var(--bg);color:var(--ink);font-family:-apple-system,BlinkMacSystemFont,\"Noto Sans TC\",\"PingFang TC\",\"Microsoft JhengHei\",sans-serif;font-size:calc(16px * var(--fs))}",
"html,body{margin:0;background:var(--bg);color:var(--ink);font-family:-apple-system,BlinkMacSystemFont,\"Noto Sans TC\",\"PingFang TC\",\"Microsoft JhengHei\",sans-serif;font-size:calc(16px * var(--fs));width:100%;max-width:100%;overflow-x:hidden}",
"root horizontal overflow"
)

rep(
"#app{max-width:620px;margin:auto;min-height:100vh;background:#fff;box-shadow:0 0 24px rgba(0,0,0,.06);padding-bottom:64px}",
"#app{width:100%;max-width:620px;margin:auto;min-height:100vh;background:#fff;box-shadow:0 0 24px rgba(0,0,0,.06);padding-bottom:64px;overflow-x:hidden}#screen,.content{width:100%;max-width:100%;min-width:0;overflow-x:hidden}",
"app horizontal overflow"
)

# Day content should not slide under a sticky yellow header.
rep(
".topbar{position:sticky;top:0;z-index:20;background:var(--yellow);min-height:48px;padding:6px 12px;border-bottom:1px solid #e3be00;display:flex;align-items:center;gap:7px}",
".topbar{position:relative;z-index:20;background:var(--yellow);min-height:48px;padding:6px 12px;border-bottom:1px solid #e3be00;display:flex;align-items:center;gap:7px}",
"non sticky topbar"
)

# Keep cards and itinerary within the viewport.
rep(
".itinerary{background:#fff;border:1px solid var(--line);border-radius:17px;overflow:hidden;box-shadow:var(--shadow)}",
".itinerary{background:#fff;border:1px solid var(--line);border-radius:17px;overflow:hidden;box-shadow:var(--shadow);width:100%;max-width:100%;min-width:0}",
"itinerary width"
)

# Rebuild expense detail layout: fixed right amount column, note under category, max 2 lines.
old = ".cost-detail-list{display:grid}.cost-detail-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px;align-items:center;width:100%;border:0;border-top:1px solid #f0ecdf;background:transparent;padding:8px 2px;text-align:left;color:#222}.cost-detail-row:first-child{border-top:0}.cost-detail-text{min-width:0;font-size:.78rem;line-height:1.35}.cost-detail-text b{font-weight:500;margin-right:7px}.cost-detail-text .note{color:#666;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;display:inline;max-width:100%}.cost-detail-value{text-align:right;font-size:.78rem;white-space:nowrap}.cost-detail-value small{display:block;color:var(--muted);font-size:.64rem;margin-top:2px}"
new = ".cost-detail-list{display:grid;min-width:0;max-width:100%}.cost-detail-row{display:grid;grid-template-columns:minmax(0,1fr) 142px;gap:10px;align-items:start;width:100%;max-width:100%;min-width:0;border:0;border-top:1px solid #f0ecdf;background:transparent;padding:9px 2px;text-align:left;color:#222;overflow:hidden}.cost-detail-row:first-child{border-top:0}.cost-detail-text{min-width:0;max-width:100%;font-size:.78rem;line-height:1.35;overflow:hidden}.cost-detail-text b{display:block;font-weight:500;margin:0 0 2px}.cost-detail-text .note{display:-webkit-box;color:#666;white-space:normal;overflow:hidden;text-overflow:ellipsis;overflow-wrap:anywhere;word-break:break-word;-webkit-box-orient:vertical;-webkit-line-clamp:2;line-clamp:2;max-width:100%}.cost-detail-value{width:142px;min-width:142px;text-align:right;font-size:.78rem;line-height:1.25;white-space:nowrap;overflow:hidden}.cost-detail-value small{display:block;color:var(--muted);font-size:.64rem;margin-top:2px;white-space:nowrap}"
rep(old,new,"expense detail layout")

# On very small screens, reserve a slightly narrower amount column.
marker="@media(max-width:390px){.content{padding:7px 10px 9px}"
if marker not in s:
    raise SystemExit('v1.0.11 patch failed: mobile media marker')
s=s.replace(marker,"@media(max-width:390px){.cost-detail-row{grid-template-columns:minmax(0,1fr) 126px}.cost-detail-value{width:126px;min-width:126px}.content{padding:7px 10px 9px}",1)

p.write_text(s)

g=root/'app/build.gradle'
b=g.read_text()
b=re.sub(r"versionCode\s+\d+","versionCode 111",b,count=1)
b=re.sub(r"versionName\s+['\"][^'\"]+['\"]","versionName '1.0.11'",b,count=1)
g.write_text(b)

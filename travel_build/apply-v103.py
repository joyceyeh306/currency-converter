from pathlib import Path
root=Path('/tmp/ajo/ajo_build_min')

# v1.0.3: JSON trip packages, a little more bottom safe spacing, and version bump.
p=root/'app/src/main/assets/index.html'
s=p.read_text()
s=s.replace('accept=".travel,.json,application/json"','accept=".ajo.json,.travel,.json,application/json"')
s=s.replace('`${safe}.travel`','`${safe}.ajo.json`')
# Keep old .travel import compatibility: content validation uses the internal ajo.travel marker, independent of extension.
s=s.replace(
    '.bottom-nav{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(620px,100%);height:56px;background:#fff;border-top:1px solid #ddd9c8;display:grid;grid-template-columns:repeat(5,1fr);z-index:40;padding-bottom:env(safe-area-inset-bottom)}',
    '.bottom-nav{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(620px,100%);height:62px;background:#fff;border-top:1px solid #ddd9c8;display:grid;grid-template-columns:repeat(5,1fr);z-index:40;padding-bottom:max(7px,env(safe-area-inset-bottom));box-sizing:border-box}'
)
s=s.replace(
    '#app{max-width:620px;margin:auto;min-height:100vh;background:#fff;box-shadow:0 0 24px rgba(0,0,0,.06);padding-bottom:58px}',
    '#app{max-width:620px;margin:auto;min-height:100vh;background:#fff;box-shadow:0 0 24px rgba(0,0,0,.06);padding-bottom:64px}'
)
s=s.replace(
    '.fab{position:fixed;right:max(14px,calc((100vw - 620px)/2 + 14px));bottom:62px;',
    '.fab{position:fixed;right:max(14px,calc((100vw - 620px)/2 + 14px));bottom:68px;'
)
p.write_text(s)

p=root/'app/build.gradle'
s=p.read_text()
s=s.replace("versionCode 3\n        versionName '1.0.2'", "versionCode 4\n        versionName '1.0.3'")
p.write_text(s)

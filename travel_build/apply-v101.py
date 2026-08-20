from pathlib import Path
root=Path('/tmp/ajo/ajo_build_min')
p=root/'app/src/main/assets/index.html'
s=p.read_text()
changes={
'#app{max-width:620px;margin:auto;min-height:100vh;background:#fff;box-shadow:0 0 24px rgba(0,0,0,.06);padding-bottom:82px}':'#app{max-width:620px;margin:auto;min-height:100vh;background:#fff;box-shadow:0 0 24px rgba(0,0,0,.06);padding-bottom:66px}',
'.topbar{position:sticky;top:0;z-index:20;background:var(--yellow);padding:14px 16px 13px;border-bottom:1px solid #e3be00;display:flex;align-items:center;gap:10px}':'.topbar{position:sticky;top:0;z-index:20;background:var(--yellow);padding:9px 14px 8px;border-bottom:1px solid #e3be00;display:flex;align-items:center;gap:8px}',
'.topbar h1{font-size:1.23rem;margin:0;flex:1;line-height:1.25;font-weight:400}':'.topbar h1{font-size:1.14rem;margin:0;flex:1;line-height:1.2;font-weight:400}',
'.topbar .back,.iconbtn{border:0;background:rgba(255,255,255,.62);width:40px;height:40px;border-radius:12px;font-weight:400;color:#111}':'.topbar .back,.iconbtn{border:0;background:rgba(255,255,255,.62);width:36px;height:36px;border-radius:11px;font-weight:400;color:#111}',
'.content{padding:16px}':'.content{padding:11px 13px 13px}',
'.section-title{display:flex;align-items:center;justify-content:space-between;margin:7px 2px 10px;font-weight:400;font-size:1rem}':'.section-title{display:flex;align-items:center;justify-content:space-between;margin:4px 2px 6px;font-weight:400;font-size:1rem}',
'.card{background:var(--card);border:1px solid var(--line);border-radius:17px;padding:15px;margin-bottom:11px;box-shadow:var(--shadow)}':'.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:11px 13px;margin-bottom:8px;box-shadow:var(--shadow)}',
'.trip-card h3{margin:0 0 5px;font-size:1.08rem}.trip-card .dates{font-weight:400}.trip-card .meta{margin-top:7px;color:var(--muted);font-size:.85rem}':'.trip-card h3{margin:0 0 3px;font-size:1.08rem}.trip-card .dates{font-weight:400}.trip-card .meta{margin-top:5px;color:var(--muted);font-size:.85rem}',
'.empty{border:1px dashed #d7cfaa;background:var(--yellow3);padding:18px;border-radius:14px;color:#756a35;text-align:center;margin-bottom:15px}':'.empty{border:1px dashed #d7cfaa;background:var(--yellow3);padding:12px 14px;border-radius:13px;color:#756a35;text-align:center;margin-bottom:10px}',
'.fab{position:fixed;right:max(18px,calc((100vw - 620px)/2 + 18px));bottom:90px;border:0;border-radius:18px;background:var(--yellow);width:58px;height:58px;font-size:30px;font-weight:400;box-shadow:0 5px 18px rgba(0,0,0,.2);z-index:25}':'.fab{position:fixed;right:max(16px,calc((100vw - 620px)/2 + 16px));bottom:68px;border:0;border-radius:17px;background:var(--yellow);width:52px;height:52px;font-size:28px;font-weight:400;box-shadow:0 5px 16px rgba(0,0,0,.18);z-index:25}',
'.bottom-nav{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(620px,100%);height:72px;background:#fff;border-top:1px solid #ddd9c8;display:grid;grid-template-columns:repeat(5,1fr);z-index:40;padding-bottom:env(safe-area-inset-bottom)}':'.bottom-nav{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(620px,100%);height:60px;background:#fff;border-top:1px solid #ddd9c8;display:grid;grid-template-columns:repeat(5,1fr);z-index:40;padding-bottom:env(safe-area-inset-bottom)}',
'.navbtn{border:0;background:#fff;color:#686868;font-size:.74rem;font-weight:400;padding:7px 2px}.navbtn .nicon{font-size:1.26rem;display:block;margin-bottom:2px}.navbtn.active{color:#111;background:var(--yellow3)}':'.navbtn{border:0;background:#fff;color:#686868;font-size:.7rem;font-weight:400;padding:4px 2px}.navbtn .nicon{font-size:1.12rem;display:block;margin-bottom:1px}.navbtn.active{color:#111;background:var(--yellow3)}',
'@media(max-width:390px){.content{padding:13px}':'@media(max-width:390px){.content{padding:10px 12px 12px}'
}
for a,b in changes.items(): s=s.replace(a,b)
p.write_text(s)

p=root/'app/build.gradle'
s=p.read_text().replace("versionCode 1\n        versionName '1.0.0'", "versionCode 2\n        versionName '1.0.1'")
p.write_text(s)

p=root/'app/src/main/java/tw/ajo/travelnotebook/MainActivity.java'
s=p.read_text()
if 'import android.os.Build;' not in s:
    s=s.replace('import android.os.Bundle;', 'import android.os.Bundle;\nimport android.os.Build;\nimport android.view.WindowInsets;')
old='        webView = new WebView(this);\n        setContentView(webView);'
new='''        webView = new WebView(this);\n        webView.setBackgroundColor(Color.rgb(255,214,0));\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            webView.setOnApplyWindowInsetsListener((v, insets) -> {\n                int topInset = insets.getInsets(WindowInsets.Type.statusBars()).top;\n                v.setPadding(0, topInset, 0, 0);\n                return insets;\n            });\n        }\n        setContentView(webView);'''
if old in s: s=s.replace(old,new)
p.write_text(s)

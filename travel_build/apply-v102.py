from pathlib import Path
root=Path('/tmp/ajo/ajo_build_min')

# Compact mobile layout further.
p=root/'app/src/main/assets/index.html'
s=p.read_text()
repls={
"#app{max-width:620px;margin:auto;min-height:100vh;background:#fff;box-shadow:0 0 24px rgba(0,0,0,.06);padding-bottom:66px}":"#app{max-width:620px;margin:auto;min-height:100vh;background:#fff;box-shadow:0 0 24px rgba(0,0,0,.06);padding-bottom:58px}",
".topbar{position:sticky;top:0;z-index:20;background:var(--yellow);padding:9px 14px 8px;border-bottom:1px solid #e3be00;display:flex;align-items:center;gap:8px}":".topbar{position:sticky;top:0;z-index:20;background:var(--yellow);min-height:48px;padding:6px 12px;border-bottom:1px solid #e3be00;display:flex;align-items:center;gap:7px}",
".topbar h1{font-size:1.14rem;margin:0;flex:1;line-height:1.2;font-weight:400}":".topbar h1{font-size:1.08rem;margin:0;flex:1;line-height:1.15;font-weight:400;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}",
".topbar .back,.iconbtn{border:0;background:rgba(255,255,255,.62);width:36px;height:36px;border-radius:11px;font-weight:400;color:#111}":".topbar .back,.iconbtn{border:0;background:rgba(255,255,255,.66);width:38px;height:38px;min-width:38px;border-radius:11px;font-weight:400;color:#111;font-size:1.25rem}",
".content{padding:11px 13px 13px}":".content{padding:8px 12px 10px}",
".section-title{display:flex;align-items:center;justify-content:space-between;margin:4px 2px 6px;font-weight:400;font-size:1rem}":".section-title{display:flex;align-items:center;justify-content:space-between;margin:2px 2px 4px;font-weight:400;font-size:.98rem}",
".card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:11px 13px;margin-bottom:8px;box-shadow:var(--shadow)}":".card{background:var(--card);border:1px solid var(--line);border-radius:15px;padding:9px 11px;margin-bottom:6px;box-shadow:var(--shadow)}",
".empty{border:1px dashed #d7cfaa;background:var(--yellow3);padding:12px 14px;border-radius:13px;color:#756a35;text-align:center;margin-bottom:10px}":".empty{border:1px dashed #d7cfaa;background:var(--yellow3);padding:8px 12px;border-radius:12px;color:#756a35;text-align:center;margin-bottom:7px}",
".primary,.secondary,.dangerbtn{border:0;border-radius:13px;padding:12px 15px;font-weight:400}":".primary,.secondary,.dangerbtn{border:0;border-radius:12px;padding:9px 13px;font-weight:400}",
".fab{position:fixed;right:max(16px,calc((100vw - 620px)/2 + 16px));bottom:68px;border:0;border-radius:17px;background:var(--yellow);width:52px;height:52px;font-size:28px;font-weight:400;box-shadow:0 5px 16px rgba(0,0,0,.18);z-index:25}":".fab{position:fixed;right:max(14px,calc((100vw - 620px)/2 + 14px));bottom:62px;border:0;border-radius:16px;background:var(--yellow);width:48px;height:48px;font-size:26px;font-weight:400;box-shadow:0 4px 14px rgba(0,0,0,.16);z-index:25}",
".bottom-nav{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(620px,100%);height:60px;background:#fff;border-top:1px solid #ddd9c8;display:grid;grid-template-columns:repeat(5,1fr);z-index:40;padding-bottom:env(safe-area-inset-bottom)}":".bottom-nav{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(620px,100%);height:56px;background:#fff;border-top:1px solid #ddd9c8;display:grid;grid-template-columns:repeat(5,1fr);z-index:40;padding-bottom:env(safe-area-inset-bottom)}",
".navbtn{border:0;background:#fff;color:#686868;font-size:.7rem;font-weight:400;padding:4px 2px}.navbtn .nicon{font-size:1.12rem;display:block;margin-bottom:1px}.navbtn.active{color:#111;background:var(--yellow3)}":".navbtn{border:0;background:#fff;color:#686868;font-size:.68rem;font-weight:400;padding:2px}.navbtn .nicon{font-size:1.02rem;display:block;margin-bottom:0}.navbtn.active{color:#111;background:var(--yellow3)}",
".day-row{display:grid;grid-template-columns:77px minmax(0,1fr) 38px;gap:8px;align-items:center;padding:11px 5px;border-bottom:1px solid #eee9d8;cursor:pointer}":".day-row{display:grid;grid-template-columns:72px minmax(0,1fr) 38px;gap:7px;align-items:center;padding:8px 4px;border-bottom:1px solid #eee9d8;cursor:pointer}",
".trip-mini-meta{display:flex;gap:8px;align-items:center;flex-wrap:wrap;color:var(--muted);font-size:.82rem;margin:-4px 2px 9px}":".trip-mini-meta{display:flex;gap:7px;align-items:center;flex-wrap:wrap;color:var(--muted);font-size:.8rem;margin:-2px 2px 6px}",
".item{display:grid;grid-template-columns:62px 1fr auto;gap:9px;padding:14px 12px;border-bottom:1px solid #eee9dc;align-items:start}":".item{display:grid;grid-template-columns:58px 1fr auto;gap:7px;padding:10px 9px;border-bottom:1px solid #eee9dc;align-items:start}",
".qbtn{border:0;background:#f5f2e8;border-radius:10px;min-width:35px;height:35px;font-size:.9rem;padding:0 8px;font-weight:400}":".qbtn{border:0;background:#f5f2e8;border-radius:9px;min-width:33px;height:33px;font-size:.86rem;padding:0 7px;font-weight:400}",
".addline{width:100%;margin:11px 0 18px}":".addline{width:100%;margin:8px 0 12px}",
".meals{margin-top:16px}":".meals{margin-top:10px}",
".stay-card{background:#fffbea;border:1px solid #edd45c;border-left:6px solid var(--yellow);border-radius:15px;padding:12px 12px;margin-bottom:10px}":".stay-card{background:#fffbea;border:1px solid #edd45c;border-left:5px solid var(--yellow);border-radius:14px;padding:9px 10px;margin-bottom:7px}",
"@media(max-width:390px){.content{padding:10px 12px 12px}.item{grid-template-columns:54px 1fr auto;padding:13px 9px}":"@media(max-width:390px){.content{padding:7px 10px 9px}.item{grid-template-columns:52px 1fr auto;padding:9px 7px}"
}
for a,b in repls.items():
    s=s.replace(a,b)
p.write_text(s)

# Version + target SDK 34: Android 15/16 then keeps content below the status bar.
p=root/'app/build.gradle'
s=p.read_text()
s=s.replace('targetSdk 35','targetSdk 34')
s=s.replace("versionCode 2\n        versionName '1.0.1'", "versionCode 3\n        versionName '1.0.2'")
p.write_text(s)

# Remove the v1.0.1 WebView inset workaround to avoid double spacing now that targetSdk 34 handles the system bar.
p=root/'app/src/main/java/tw/ajo/travelnotebook/MainActivity.java'
s=p.read_text()
s=s.replace('import android.os.Build;\n','').replace('import android.view.WindowInsets;\n','')
old='''        webView = new WebView(this);\n        webView.setBackgroundColor(Color.rgb(255,214,0));\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            webView.setOnApplyWindowInsetsListener((v, insets) -> {\n                int topInset = insets.getInsets(WindowInsets.Type.statusBars()).top;\n                v.setPadding(0, topInset, 0, 0);\n                return insets;\n            });\n        }\n        setContentView(webView);'''
new='''        webView = new WebView(this);\n        webView.setBackgroundColor(Color.WHITE);\n        setContentView(webView);'''
s=s.replace(old,new)
p.write_text(s)

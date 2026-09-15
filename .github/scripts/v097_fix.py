from pathlib import Path
import base64
import hashlib
import re

# Build on the verified v0.9.6 runtime patch chain.
base = Path('.github/scripts/v096_runner.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v096_runner.py', 'exec'), {'__name__': '__main__'})

java_path = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s = java_path.read_text(encoding='utf-8')


def sub1(pattern, replacement, label):
    global s
    s2, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'v0.9.7 patch failed for {label}: matched {n}')
    s = s2


# ---------------------------------------------------------------------------
# 1) Mode cycle follows the requested daily-use order:
#    forward: Cangjie -> Zhuyin -> English -> Cangjie
#    backward: Cangjie -> English -> Zhuyin -> Cangjie
#    Temporary English (spacebar down gesture) remains independent.
# ---------------------------------------------------------------------------
new_cycles = '''    private void cycleMode(){ temporaryEnglish=false; if(mode==Mode.CANGJIE)mode=Mode.ZHUYIN;else if(mode==Mode.ZHUYIN)mode=Mode.ENGLISH;else mode=Mode.CANGJIE; page=Page.MAIN;cjCode="";zyCode="";expanded=false;candOff=0;syncComposition();invalidate(); }\n    private void cycleModeBackward(){ temporaryEnglish=false; if(mode==Mode.CANGJIE)mode=Mode.ENGLISH;else if(mode==Mode.ENGLISH)mode=Mode.ZHUYIN;else mode=Mode.CANGJIE; page=Page.MAIN;cjCode="";zyCode="";expanded=false;candOff=0;syncComposition();invalidate(); }\n'''
sub1(r'    private void cycleMode\(\)\{.*?\}\n    private void cycleModeBackward\(\)\{.*?\}\n', new_cycles, 'mode cycle order')

# ---------------------------------------------------------------------------
# 2) Emoji / kaomoji: move the keyboard-return control into the fixed helper
#    row and give the full remaining panel height to continuously scrollable
#    content. No large blank return area remains at the bottom.
# ---------------------------------------------------------------------------
new_emoji_max = '''    private float emojiMaxScroll(){\n        float top=dp(72),listBottom=getHeight(),cellH=dp(48);\n        int rows=(emojiItems().length+6)/7; return Math.max(0f,rows*cellH-(listBottom-top));\n    }\n'''
sub1(r'    private float emojiMaxScroll\(\)\{.*?\n    \}', new_emoji_max.rstrip(), 'emoji scroll viewport')

new_kao_max = '''    private float kaoMaxScroll(){\n        float top=dp(38),listBottom=getHeight(),rowH=dp(46);\n        int rows=(kaos.length+2)/3; return Math.max(0f,rows*rowH-(listBottom-top));\n    }\n'''
sub1(r'    private float kaoMaxScroll\(\)\{.*?\n    \}', new_kao_max.rstrip(), 'kaomoji scroll viewport')

new_draw_emoji = r'''    private void drawEmoji(Canvas c){
        p.setColor(bgColor()); c.drawRect(0,0,getWidth(),getHeight(),p);
        String[] cats={"最近","表情","人物","動物","食物","旅行","物品","活動","符號","旗幟"};
        float catH=dp(40),helperH=dp(32),catW=getWidth()/10f;
        for(int i=0;i<10;i++){
            RectF r=new RectF(i*catW,0,(i+1)*catW,catH);
            if(i==emojiCategory){ p.setColor(selectedCandidateColor()); c.drawRoundRect(r,dp(8),dp(8),p); }
            drawTextCentered(c,cats[i],r,13*keyScale());
            hits.add(new Hit(r,"EMOJI_CAT",String.valueOf(i)));
        }
        RectF back=new RectF(0,catH,dp(54),catH+helperH);
        hits.add(new Hit(back,"MAIN",""));
        drawTextCentered(c,"⌨",back,18*keyScale());
        t.setTextAlign(Paint.Align.LEFT); t.setTextSize(dp(14)); t.setColor(secondaryTextColor());
        c.drawText("上下滑動瀏覽",dp(62),catH+dp(22),t);

        float top=catH+helperH,listBottom=getHeight(),cellH=dp(48);
        int cols=7; String[] list=emojiItems();
        emojiScrollY=Math.max(0f,Math.min(emojiScrollY,emojiMaxScroll()));
        int firstRow=(int)Math.floor(emojiScrollY/cellH);
        float offset=-(emojiScrollY-firstRow*cellH),cellW=getWidth()/(float)cols;
        c.save(); c.clipRect(0,top,getWidth(),listBottom);
        int visualRow=0;
        for(float y=top+offset;y<listBottom+cellH;y+=cellH,visualRow++){
            int dataRow=firstRow+visualRow;
            for(int col=0;col<cols;col++){
                int idx=dataRow*cols+col; if(idx>=list.length) break;
                RectF r=new RectF(col*cellW,y,(col+1)*cellW,y+cellH);
                drawTextCentered(c,list[idx],r,25*keyScale());
                RectF hr=new RectF(r);
                if(hr.bottom>top&&hr.top<listBottom){
                    hr.top=Math.max(hr.top,top); hr.bottom=Math.min(hr.bottom,listBottom);
                    hits.add(new Hit(hr,"EMOJI_CHAR",list[idx]));
                }
            }
        }
        c.restore();
    }
'''
sub1(r'    private void drawEmoji\(Canvas c\)\{.*?\n    \}\n(?=    private void drawKaomoji)', new_draw_emoji, 'full-height emoji panel')

new_draw_kao = r'''    private void drawKaomoji(Canvas c){
        p.setColor(bgColor()); c.drawRect(0,0,getWidth(),getHeight(),p);
        float helperH=dp(38),top=helperH,rowH=dp(46),listBottom=getHeight();
        int cols=3; float cellW=getWidth()/(float)cols;

        RectF back=new RectF(0,0,dp(54),helperH);
        hits.add(new Hit(back,"MAIN",""));
        drawTextCentered(c,"⌨",back,18*keyScale());
        t.setTextAlign(Paint.Align.LEFT); t.setTextSize(dp(14)); t.setColor(secondaryTextColor());
        c.drawText("上下滑動瀏覽",dp(62),dp(25),t);

        kaoScrollY=Math.max(0f,Math.min(kaoScrollY,kaoMaxScroll()));
        int firstRow=(int)Math.floor(kaoScrollY/rowH);
        float offset=-(kaoScrollY-firstRow*rowH);
        c.save(); c.clipRect(0,top,getWidth(),listBottom);
        int visualRow=0;
        for(float y=top+offset;y<listBottom+rowH;y+=rowH,visualRow++){
            int dataRow=firstRow+visualRow;
            for(int col=0;col<cols;col++){
                int idx=dataRow*cols+col; if(idx>=kaos.length) break;
                RectF r=new RectF(col*cellW,y,(col+1)*cellW,y+rowH);
                p.setColor(dividerColor()); c.drawRect(r.left,r.bottom-1,r.right,r.bottom,p);
                drawTextCentered(c,kaos[idx],r,15*keyScale());
                RectF hr=new RectF(r);
                if(hr.bottom>top&&hr.top<listBottom){
                    hr.top=Math.max(hr.top,top); hr.bottom=Math.min(hr.bottom,listBottom);
                    hits.add(new Hit(hr,"KAO_CHAR",kaos[idx]));
                }
            }
        }
        c.restore();
    }
'''
sub1(r'    private void drawKaomoji\(Canvas c\)\{.*?\n    \}\n(?=\n    private List<String> recentEmoji)', new_draw_kao, 'full-height kaomoji panel')

old_panel_drag = '''            if(page==Page.EMOJI||page==Page.KAOMOJI){\n                if(downY>dp(55)&&downY<getHeight()-dp(112)){\n'''
new_panel_drag = '''            if(page==Page.EMOJI||page==Page.KAOMOJI){\n                float panelTop=page==Page.EMOJI?dp(72):dp(38);\n                if(downY>panelTop&&downY<getHeight()){\n'''
if old_panel_drag not in s:
    raise SystemExit('v0.9.7 patch failed: panel drag bounds source not found')
s = s.replace(old_panel_drag,new_panel_drag,1)

java_path.write_text(s,encoding='utf-8')

# ---------------------------------------------------------------------------
# 3) Replace the truncated v0.9.5 PNG with a validated copy of the exact
#    selected green 倉 design. Use WebP to keep the repository/build compact.
#    Adaptive icon gets an opaque matching green background so ColorOS always
#    has a valid launcher surface instead of falling back to the Android robot.
# ---------------------------------------------------------------------------
icon_b64_path=Path('.github/assets/ajo_icon_144.webp.b64')
icon_data=base64.b64decode(icon_b64_path.read_text(encoding='ascii').strip(),validate=True)
expected_sha='fcc5763d332070c751a8e39d84b2f87dd5f5944c3181d09cff2b983ec64244a5'
if hashlib.sha256(icon_data).hexdigest()!=expected_sha:
    raise SystemExit('v0.9.7 patch failed: selected launcher icon digest mismatch')
if len(icon_data)!=10120 or icon_data[:4]!=b'RIFF' or icon_data[8:12]!=b'WEBP':
    raise SystemExit('v0.9.7 patch failed: selected launcher icon is not the verified WebP')

res=Path('imeapp/app/src/main/res')
for old in [res/'drawable-nodpi'/'ic_launcher_ajo.png',res/'mipmap'/'ic_launcher.png',res/'mipmap'/'ic_launcher_round.png']:
    if old.exists(): old.unlink()
(res/'drawable-nodpi').mkdir(parents=True,exist_ok=True)
(res/'mipmap').mkdir(parents=True,exist_ok=True)
(res/'drawable-nodpi'/'ic_launcher_ajo.webp').write_bytes(icon_data)
(res/'mipmap'/'ic_launcher.webp').write_bytes(icon_data)
(res/'mipmap'/'ic_launcher_round.webp').write_bytes(icon_data)

(res/'mipmap-anydpi-v26').mkdir(parents=True,exist_ok=True)
adaptive='''<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/ic_launcher_green" />\n    <foreground android:drawable="@drawable/ic_launcher_ajo" />\n</adaptive-icon>\n'''
(res/'mipmap-anydpi-v26'/'ic_launcher.xml').write_text(adaptive,encoding='utf-8')
(res/'mipmap-anydpi-v26'/'ic_launcher_round.xml').write_text(adaptive,encoding='utf-8')
(res/'values').mkdir(parents=True,exist_ok=True)
(res/'values'/'ic_launcher_colors.xml').write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources><color name="ic_launcher_green">#0B8B62</color></resources>\n',
    encoding='utf-8')
manifest=Path('imeapp/app/src/main/AndroidManifest.xml')
ms=manifest.read_text(encoding='utf-8')
ms=ms.replace('android:icon="@drawable/ic_launcher_ajo"','android:icon="@mipmap/ic_launcher"')
ms=ms.replace('android:roundIcon="@drawable/ic_launcher_ajo"','android:roundIcon="@mipmap/ic_launcher_round"')
if 'android:icon="@mipmap/ic_launcher"' not in ms or 'android:roundIcon="@mipmap/ic_launcher_round"' not in ms:
    raise SystemExit('v0.9.7 patch failed: launcher manifest mapping missing')
manifest.write_text(ms,encoding='utf-8')

# ---------------------------------------------------------------------------
# 4) Version / settings wording. Dark mode and candidate highlight are kept.
# ---------------------------------------------------------------------------
gradle=Path('imeapp/app/build.gradle')
g=gradle.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 17',g,count=1)
g=re.sub(r"versionName\s+'[^']+'","versionName '0.9.7'",g,count=1)
gradle.write_text(g,encoding='utf-8')

main=Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m=main.read_text(encoding='utf-8')
m=re.sub(r'version\.setText\("[^"]*"\);','version.setText("v0.9.7｜顯示與操作修正版");',m,count=1)
m=m.replace('倉頡 → English → 注音 → 倉頡','倉頡 → 注音 → English → 倉頡')
m=re.sub(
    r'intro\.setText\("[^"]*"\);',
    'intro.setText("保留 v0.9.6 已改善的夜間配色、第一候選反白、台灣日常候選排序與盲打容錯；本版修正 Launcher 圖示資源，輸入法循環改為倉頡 → 注音 → English → 倉頡。Emoji／顏文字把返回鍵盤移到上方操作列，下方整片空間改為可連續上下滑動的內容區，減少無效留白。");',
    m,count=1)
main.write_text(m,encoding='utf-8')

# Sanity checks.
assert "versionCode 17" in g and "versionName '0.9.7'" in g
assert 'if(mode==Mode.CANGJIE)mode=Mode.ZHUYIN' in s
assert 'float panelTop=page==Page.EMOJI?dp(72):dp(38);' in s
assert 'listBottom=getHeight()' in s
assert 'selectedCandidateColor()' in s and 'isDark()' in s
assert (res/'drawable-nodpi'/'ic_launcher_ajo.webp').read_bytes()==icon_data
assert not (res/'drawable-nodpi'/'ic_launcher_ajo.png').exists()
assert '倉頡 → 注音 → English → 倉頡' in m
print('v0.9.7 display, panel-space, mode-order and launcher-icon correction applied')

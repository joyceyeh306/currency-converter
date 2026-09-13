from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)

kb_path = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
kb = kb_path.read_text(encoding='utf-8')

kb = replace_once(
    kb,
    'import android.graphics.Paint;\nimport android.graphics.RectF;',
    'import android.graphics.Paint;\nimport android.graphics.Rect;\nimport android.graphics.RectF;',
    'add Rect import')

kb = replace_once(
    kb,
    '    private long downAt = 0;\n    private Hit downHit = null;\n    private boolean backspaceRepeating = false;',
    '    private long downAt = 0;\n    private Hit downHit = null;\n    private float downX = 0f;\n    private float downY = 0f;\n    private boolean backspaceRepeating = false;',
    'touch fields')

old_draw_key = '''    private void drawKey(Canvas c, float x1, float y1, float x2, float y2,\n                         String label, float size, String action, String value) {\n        RectF r = rect(x1, y1, x2, y2);\n        p.setColor(Color.WHITE);\n        c.drawRoundRect(r, dp(8), dp(8), p);\n        if (label != null && !label.isEmpty()) {\n            t.setColor(Color.BLACK);\n            t.setTextAlign(Paint.Align.CENTER);\n            t.setTextSize(dp(size * keyScale()));\n            Paint.FontMetrics fm = t.getFontMetrics();\n            c.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, t);\n        }\n        if (action != null) hits.add(new Hit(new RectF(r), action, value));\n    }\n'''
new_draw_key = '''    private void drawKey(Canvas c, float x1, float y1, float x2, float y2,\n                         String label, float size, String action, String value) {\n        RectF r = rect(x1, y1, x2, y2);\n        p.setColor(Color.WHITE);\n        c.drawRoundRect(r, dp(8), dp(8), p);\n        if (label != null && !label.isEmpty()) {\n            t.setColor(Color.BLACK);\n            t.setTextAlign(Paint.Align.CENTER);\n            t.setTextSize(dp(size * keyScale()));\n            // Centre by the actual glyph bounds rather than only the font metrics.\n            // This keeps Cangjie roots, punctuation and emoji visually balanced on all four sides.\n            Rect bounds = new Rect();\n            t.getTextBounds(label, 0, label.length(), bounds);\n            float baseline = r.centerY() - (bounds.top + bounds.bottom) / 2f;\n            c.drawText(label, r.centerX(), baseline, t);\n        }\n        if (action != null) hits.add(new Hit(new RectF(r), action, value));\n    }\n'''
kb = replace_once(kb, old_draw_key, new_draw_key, 'drawKey visual centering')

old_cj = '''    private void drawCangjie(Canvas c) {\n        // Keep the keycaps unchanged; only Cangjie root glyphs are 80% of the previous size.\n        for (int i=0;i<10;i++) { float x=18+i*116.4f; drawKey(c,x,0,x+96,122,c1[i],20.8f,"CJ",String.valueOf(k1[i])); }\n        for (int i=0;i<9;i++) { float x=76+i*114.8f; drawKey(c,x,155,x+96,282,c2[i],20.8f,"CJ",String.valueOf(k2[i])); }\n        for (int i=0;i<7;i++) { float x=116+i*116.5f; drawKey(c,x,318,x+96,444,c3[i],20f,"CJ",String.valueOf(k3[i])); }\n        drawKey(c,1017,318,1150,444,"⌫",24,"BACK","");\n        drawBottom(c,"123","倉");\n    }\n'''
new_cj = '''    private void drawCangjie(Canvas c) {\n        // v0.7: keycaps stay exactly the same size; only the root glyphs become 85% of v0.6.\n        for (int i=0;i<10;i++) { float x=18+i*116.4f; drawKey(c,x,0,x+96,122,c1[i],17.68f,"CJ",String.valueOf(k1[i])); }\n        for (int i=0;i<9;i++) { float x=76+i*114.8f; drawKey(c,x,155,x+96,282,c2[i],17.68f,"CJ",String.valueOf(k2[i])); }\n\n        // Third row: add ， to the left of 重 and 。 to the right of 一.\n        // All nine normal keycaps remain 96 design-pixels wide; Backspace remains 133 wide.\n        final float thirdGap = 137f / 9f;\n        float x = 18f;\n        drawKey(c,x,318,x+96,444,"，",21f,"PUNCT","，");\n        x += 96f + thirdGap;\n        for (int i=0;i<7;i++) {\n            drawKey(c,x,318,x+96,444,c3[i],17.0f,"CJ",String.valueOf(k3[i]));\n            x += 96f + thirdGap;\n        }\n        drawKey(c,x,318,x+96,444,"。",21f,"PUNCT","。");\n        x += 96f + thirdGap;\n        drawKey(c,x,318,x+133,444,"⌫",24,"BACK","");\n        drawBottom(c,"123","倉");\n    }\n'''
kb = replace_once(kb, old_cj, new_cj, 'Cangjie third row and root size')

kb = replace_once(
    kb,
    'new String[]{"#+=",".",",","?","!","\'"} :\n                new String[]{"#+=","。","，","、","？","！","．"};',
    'new String[]{"🔣",".",",","?","!","\'"} :\n                new String[]{"🔣","。","，","、","？","！","．"};',
    'symbol emoji label')

old_footer = '''    private void drawFooter(Canvas c) {\n        // Leave clear ColorOS safe zones at both sides. The visible icons and hit boxes\n        // share these same RectF objects, so the buttons cannot drift away from touch.\n        RectF globe=rect(145,682,295,838);\n        RectF settingsButton=rect(715,682,845,838);\n        RectF mic=rect(855,682,1005,838);\n        hits.add(new Hit(globe,"GLOBE",""));\n        hits.add(new Hit(settingsButton,"SETTINGS",""));\n        hits.add(new Hit(mic,"MIC",""));\n\n        drawGlobeIcon(c, globe.centerX(), globe.centerY());\n        drawSettingsIcon(c, settingsButton.centerX(), settingsButton.centerY());\n        drawMicIcon(c, mic.centerX(), mic.centerY());\n    }\n'''
new_footer = '''    private void drawFooter(Canvas c) {\n        // Input-mode switching is now a left/right swipe on the spacebar.\n        // Keep the footer uncluttered and leave ColorOS system controls clear.\n        RectF mic=rect(855,682,1005,838);\n        hits.add(new Hit(mic,"MIC",""));\n        t.setColor(Color.BLACK);\n        t.setTextAlign(Paint.Align.CENTER);\n        t.setTextSize(dp(22));\n        Rect bounds = new Rect();\n        String micLabel = "🎙️";\n        t.getTextBounds(micLabel,0,micLabel.length(),bounds);\n        c.drawText(micLabel,mic.centerX(),mic.centerY()-(bounds.top+bounds.bottom)/2f,t);\n    }\n'''
kb = replace_once(kb, old_footer, new_footer, 'footer icons')

old_emoji = '''    private void drawEmoji(Canvas c) {\n        p.setColor(BG);\n        c.drawRect(0,0,getWidth(),getHeight(),p);\n        t.setColor(Color.DKGRAY);\n        t.setTextSize(dp(17));\n        t.setTextAlign(Paint.Align.LEFT);\n        c.drawText("搜尋表情符號",dp(18),dp(31),t);\n        int cols=7;\n        float top=dp(48), cellW=getWidth()/(float)cols, cellH=dp(48);\n        int max=Math.min(emojis.length, 42);\n        for(int i=0;i<max;i++) {\n            int row=i/cols,col=i%cols;\n            RectF r=new RectF(col*cellW,top+row*cellH,(col+1)*cellW,top+(row+1)*cellH);\n            t.setTextAlign(Paint.Align.CENTER);\n            t.setTextSize(dp(25));\n            t.setColor(Color.BLACK);\n            c.drawText(emojis[i],r.centerX(),r.centerY()+dp(9),t);\n            hits.add(new Hit(r,"EMOJI_CHAR",emojis[i]));\n        }\n        RectF back=new RectF(0,getHeight()-dp(62),dp(110),getHeight());\n        hits.add(new Hit(back,"MAIN","");\n        t.setTextAlign(Paint.Align.CENTER); t.setTextSize(dp(24)); t.setColor(Color.BLACK);\n        c.drawText("⌨",back.centerX(),back.centerY()+dp(8),t);\n    }\n'''
new_emoji = '''    private void drawEmoji(Canvas c) {\n        p.setColor(BG);\n        c.drawRect(0,0,getWidth(),getHeight(),p);\n        t.setColor(Color.DKGRAY);\n        t.setTextSize(dp(17));\n        t.setTextAlign(Paint.Align.LEFT);\n        c.drawText("搜尋表情符號",dp(18),dp(31),t);\n        int cols=7;\n        float top=dp(48), cellW=getWidth()/(float)cols, cellH=dp(48);\n        float safeBottom = getHeight()-dp(70);\n        float backH = dp(48);\n        float listBottom = safeBottom-backH;\n        int rows=Math.max(1,(int)Math.floor((listBottom-top)/cellH));\n        int max=Math.min(emojis.length, rows*cols);\n        for(int i=0;i<max;i++) {\n            int row=i/cols,col=i%cols;\n            RectF r=new RectF(col*cellW,top+row*cellH,(col+1)*cellW,top+(row+1)*cellH);\n            t.setTextAlign(Paint.Align.CENTER);\n            t.setTextSize(dp(25));\n            t.setColor(Color.BLACK);\n            c.drawText(emojis[i],r.centerX(),r.centerY()+dp(9),t);\n            hits.add(new Hit(r,"EMOJI_CHAR",emojis[i]));\n        }\n        RectF back=new RectF(0,listBottom,dp(110),safeBottom);\n        hits.add(new Hit(back,"MAIN","");\n        t.setTextAlign(Paint.Align.CENTER); t.setTextSize(dp(22)); t.setColor(Color.BLACK);\n        Rect b=new Rect(); String label="⌨"; t.getTextBounds(label,0,label.length(),b);\n        c.drawText(label,back.centerX(),back.centerY()-(b.top+b.bottom)/2f,t);\n    }\n'''
kb = replace_once(kb, old_emoji, new_emoji, 'emoji bottom safe area')

start = kb.index('    private void drawKaomoji(Canvas c) {')
end = kb.index('    private void drawModeMenu(Canvas c) {', start)
new_kao = '''    private int kaomojiVisibleRows() {\n        float top=dp(8), rowH=dp(46);\n        float safeBottom=getHeight()-dp(70);\n        float backH=dp(48);\n        return Math.max(1,(int)Math.floor((safeBottom-backH-top)/rowH));\n    }\n\n    private int kaomojiPageSize() { return kaomojiVisibleRows()*3; }\n\n    private void scrollKaomoji(boolean forward) {\n        int pageSize=kaomojiPageSize();\n        int maxStart=Math.max(0, ((kaos.length-1)/3 - kaomojiVisibleRows()+1)*3);\n        if(forward) kaoOff=Math.min(maxStart,kaoOff+pageSize);\n        else kaoOff=Math.max(0,kaoOff-pageSize);\n        invalidate();\n    }\n\n    private void drawKaomoji(Canvas c) {\n        p.setColor(BG);\n        c.drawRect(0,0,getWidth(),getHeight(),p);\n        int cols=3;\n        float top=dp(8), rowH=dp(46), cellW=getWidth()/(float)cols;\n        int rows=kaomojiVisibleRows();\n        int count=Math.min(rows*cols,kaos.length-kaoOff);\n        for(int i=0;i<count;i++) {\n            int row=i/cols,col=i%cols;\n            RectF r=new RectF(col*cellW,top+row*rowH,(col+1)*cellW,top+(row+1)*rowH);\n            p.setColor(Color.rgb(205,206,211));\n            c.drawRect(r.left,r.bottom-1,r.right,r.bottom,p);\n            t.setColor(Color.BLACK); t.setTextAlign(Paint.Align.CENTER); t.setTextSize(dp(15));\n            Rect b=new Rect(); String label=kaos[kaoOff+i]; t.getTextBounds(label,0,label.length(),b);\n            c.drawText(label,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t);\n            hits.add(new Hit(r,"KAO_CHAR",label));\n        }\n        float safeBottom=getHeight()-dp(70);\n        float backTop=safeBottom-dp(48);\n        RectF back=new RectF(0,backTop,dp(110),safeBottom);\n        hits.add(new Hit(back,"MAIN",""));\n        t.setTextAlign(Paint.Align.CENTER); t.setTextSize(dp(22)); t.setColor(Color.BLACK);\n        Rect b=new Rect(); String label="⌨"; t.getTextBounds(label,0,label.length(),b);\n        c.drawText(label,back.centerX(),back.centerY()-(b.top+b.bottom)/2f,t);\n    }\n\n'''
kb = kb[:start] + new_kao + kb[end:]

old_find = '''    private Hit findHit(float x,float y) {\n        for(int i=hits.size()-1;i>=0;i--) if(hits.get(i).r.contains(x,y)) return hits.get(i);\n        return null;\n    }\n'''
new_find = '''    private boolean canUseExpandedTouch(Hit h) {\n        String a=h.action;\n        return "CJ".equals(a)||"ZY".equals(a)||"LETTER".equals(a)||"SHIFT".equals(a)||\n                "BACK".equals(a)||"NUMBER".equals(a)||"PUNCT".equals(a)||"NUM".equals(a)||\n                "SYM".equals(a)||"MAIN".equals(a)||"EMOJI".equals(a)||"SPACE".equals(a)||\n                "ENTER".equals(a);\n    }\n\n    private Hit findHit(float x,float y) {\n        // Exact visible key wins first.\n        for(int i=hits.size()-1;i>=0;i--) if(hits.get(i).r.contains(x,y)) return hits.get(i);\n\n        // If the finger lands in a visual gap, assign it to the nearest keyboard key.\n        // Keycaps do not grow; only the invisible touch target reaches into the gap.\n        Hit best=null;\n        float bestD=Float.MAX_VALUE;\n        float padX=dp(12), padY=dp(10);\n        for(int i=hits.size()-1;i>=0;i--) {\n            Hit h=hits.get(i);\n            if(!canUseExpandedTouch(h)) continue;\n            RectF rr=new RectF(h.r);\n            rr.inset(-padX,-padY);\n            if(!rr.contains(x,y)) continue;\n            float dx=Math.max(Math.max(h.r.left-x,0f),x-h.r.right);\n            float dy=Math.max(Math.max(h.r.top-y,0f),y-h.r.bottom);\n            float d=dx*dx+dy*dy;\n            if(d<bestD) { bestD=d; best=h; }\n        }\n        return best;\n    }\n\n    private boolean sameHit(Hit a, Hit b) {\n        if(a==null||b==null) return false;\n        if(!a.action.equals(b.action)) return false;\n        if(a.value==null) return b.value==null;\n        return a.value.equals(b.value);\n    }\n'''
kb = replace_once(kb, old_find, new_find, 'expanded touch targets')

start = kb.index('    @Override public boolean onTouchEvent(MotionEvent e) {')
end = kb.index('    private final Runnable repeatBackspace=', start)
new_touch = '''    @Override public boolean onTouchEvent(MotionEvent e) {\n        float x=e.getX(),y=e.getY();\n        if(e.getAction()==MotionEvent.ACTION_DOWN) {\n            downAt=SystemClock.uptimeMillis();\n            downX=x; downY=y;\n            downHit=findHit(x,y);\n            backspaceRepeating=false;\n            if(downHit!=null&&"BACK".equals(downHit.action)) {\n                repeatHandler.postDelayed(repeatBackspace,420);\n            }\n            return true;\n        }\n        if(e.getAction()==MotionEvent.ACTION_CANCEL) {\n            repeatHandler.removeCallbacks(repeatBackspace);\n            downHit=null;\n            return true;\n        }\n        if(e.getAction()!=MotionEvent.ACTION_UP) return true;\n\n        repeatHandler.removeCallbacks(repeatBackspace);\n        float dx=x-downX, dy=y-downY;\n        float adx=Math.abs(dx), ady=Math.abs(dy);\n\n        if(page==Page.KAOMOJI && ady>dp(34) && ady>adx*1.25f) {\n            feedback();\n            scrollKaomoji(dy<0);\n            downHit=null;\n            return true;\n        }\n\n        if(downHit!=null && "SPACE".equals(downHit.action)) {\n            float threshold=Math.max(dp(42),downHit.r.width()*0.16f);\n            if(adx>threshold && adx>ady*1.5f) {\n                feedback();\n                if(dx<0) cycleMode(); else cycleModeBackward();\n                downHit=null;\n                return true;\n            }\n        }\n\n        Hit up=findHit(x,y);\n        float move=(float)Math.hypot(dx,dy);\n        if(downHit!=null) {\n            if("BACK".equals(downHit.action)&&backspaceRepeating) {\n                // Long-press repeat already handled the deletion.\n            } else if(move<=dp(28) || sameHit(downHit,up)) {\n                // Lock a normal tap to the key selected on ACTION_DOWN.\n                // Small finger drift on release no longer makes the key feel unresponsive.\n                feedback();\n                act(downHit);\n            }\n        }\n        downHit=null;\n        return true;\n    }\n\n'''
kb = kb[:start] + new_touch + kb[end:]

old_cycle = '''    private void cycleMode() {\n        // User-confirmed order: 倉頡 → English → 注音 → 倉頡\n        if(mode==Mode.CANGJIE) mode=Mode.ENGLISH;\n        else if(mode==Mode.ENGLISH) mode=Mode.ZHUYIN;\n        else mode=Mode.CANGJIE;\n        page=Page.MAIN;\n        cjCode=""; zyCode=""; expanded=false; modeMenu=false;\n        syncComposition(); invalidate();\n    }\n'''
new_cycle = '''    private void cycleMode() {\n        // Swipe left on Space: 倉頡 → English → 注音 → 倉頡\n        if(mode==Mode.CANGJIE) mode=Mode.ENGLISH;\n        else if(mode==Mode.ENGLISH) mode=Mode.ZHUYIN;\n        else mode=Mode.CANGJIE;\n        page=Page.MAIN;\n        cjCode=""; zyCode=""; expanded=false; modeMenu=false;\n        syncComposition(); invalidate();\n    }\n\n    private void cycleModeBackward() {\n        // Swipe right on Space: reverse the same order.\n        if(mode==Mode.CANGJIE) mode=Mode.ZHUYIN;\n        else if(mode==Mode.ZHUYIN) mode=Mode.ENGLISH;\n        else mode=Mode.CANGJIE;\n        page=Page.MAIN;\n        cjCode=""; zyCode=""; expanded=false; modeMenu=false;\n        syncComposition(); invalidate();\n    }\n'''
kb = replace_once(kb, old_cycle, new_cycle, 'spacebar mode swipe')

kb_path.write_text(kb, encoding='utf-8')

svc_path = Path('imeapp/app/src/main/java/tw/ajo/ime/AjoImeService.java')
svc = svc_path.read_text(encoding='utf-8')
svc = replace_once(
    svc,
    '''        if ("keyboard_height".equals(key)\n                || "key_text_size".equals(key)\n                || "candidate_text_size".equals(key)) {\n            keyboard.post(keyboard::refreshSettings);\n        } else {\n            keyboard.post(keyboard::invalidate);\n        }''',
    '''        if ("keyboard_height".equals(key)) {\n            keyboard.post(this::rebuildKeyboardForHeight);\n        } else if ("key_text_size".equals(key)\n                || "candidate_text_size".equals(key)) {\n            keyboard.post(keyboard::refreshSettings);\n        } else {\n            keyboard.post(keyboard::invalidate);\n        }''',
    'height preference listener')

anchor = '''    @Override public View onCreateInputView() {\n        keyboard = new PreciseKeyboardView(this, this);\n        return keyboard;\n    }\n'''
insert = anchor + '''\n    private void rebuildKeyboardForHeight() {\n        updateComposition("");\n        PreciseKeyboardView fresh = new PreciseKeyboardView(this, this);\n        EditorInfo info = getCurrentInputEditorInfo();\n        if (info != null) fresh.onEditorChanged(info);\n        keyboard = fresh;\n        setInputView(fresh);\n        fresh.requestLayout();\n    }\n'''
svc = replace_once(svc, anchor, insert, 'force keyboard height relayout')
svc_path.write_text(svc, encoding='utf-8')

main_path = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
main = main_path.read_text(encoding='utf-8')
main = replace_once(main, 'version.setText("v0.6｜倉頡三代與版面修正版");', 'version.setText("v0.7｜觸控與版面修正版");', 'settings version')
main = replace_once(
    main,
    'intro.setText("這版改為純倉頡三代碼表，並保留精準按鍵觸控。字根／注音組字改成真正浮在鍵盤上方，不再讓鍵盤往上跳；鍵盤高度設定會重新套用。底部切換、設定與麥克風圖示也重新配置，避開 ColorOS 系統按鍵。");',
    'intro.setText("純倉頡三代。按鍵外框大小維持不變，字根縮小並做視覺置中；第三排新增逗號與句號。實際觸控範圍延伸到按鍵間隙，空白鍵左右滑切換倉頡／English／注音。顏文字頁保留 ColorOS 底部安全區，麥克風改用較簡潔圖示。");',
    'settings intro')
main = replace_once(
    main,
    'note.setText("倉頡版本：第三代。\\n切換順序：倉頡 → English → 注音 → 倉頡。\\n數字頁輸入數字會留在 123；輸入標點後自動回主鍵盤。\\n個人學習資料只保存在這支手機內。");',
    'note.setText("倉頡版本：第三代。\\n空白鍵向左滑：倉頡 → English → 注音 → 倉頡；向右滑為反方向。\\n數字頁輸入數字會留在 123；輸入標點後自動回主鍵盤。\\n個人學習資料只保存在這支手機內。");',
    'settings note')
main_path.write_text(main, encoding='utf-8')

print('Applied v0.7 keyboard, touch, footer, kaomoji and settings patches.')

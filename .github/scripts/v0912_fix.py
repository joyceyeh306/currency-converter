from pathlib import Path
import re

base=Path('.github/scripts/v0911_fix.py').read_text(encoding='utf-8')
exec(compile(base,'.github/scripts/v0911_fix.py','exec'),{'__name__':'__main__'})

java_path=Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s=java_path.read_text(encoding='utf-8')

def sub1(pattern,replacement,label):
    global s
    s2,n=re.subn(pattern,replacement,s,count=1,flags=re.S)
    if n!=1:
        raise SystemExit(f'v0.9.12 patch failed: {label} matched {n}')
    s=s2

def replace_once(old,new,label):
    global s
    if old not in s:
        raise SystemExit(f'v0.9.12 patch failed: {label} source not found')
    s=s.replace(old,new,1)

# 1) Restore the original Cangjie backspace geometry and leave the old period
#    position physically empty. The bottom-row period remains.
replace_once(
'''        drawFixedKey(c,932,318,1150,444,"⌫",24,"BACK","");
''',
'''        drawFixedKey(c,1017,318,1150,444,"⌫",24,"BACK","");
''',
'original Cangjie backspace size'
)

# 2) Only Cangjie gets the bottom-row Chinese full stop. Zhuyin and English
#    keep the original wide spacebar.
sub1(
r'''    private void drawBottom\(Canvas c,String left,String mark\)\{.*?\n    \}''',
r'''    private void drawBottom(Canvas c,String left,String mark){
        drawFixedKey(c,18,518,145,647,left,22,"NUM","");
        drawFixedKey(c,160,518,287,647,"☺",23,"EMOJI","");
        if(mode==Mode.CANGJIE){
            drawFixedKey(c,303,518,760,647,"",22,"SPACE","");
            drawInputKey(c,776,518,864,647,"。",17.68f,"PUNCT","。");
            drawFixedKey(c,880,518,1150,647,"↩",25,"ENTER","");
            t.setColor(secondaryTextColor()); t.setTextSize(dp(12)); t.setTextAlign(Paint.Align.RIGHT); c.drawText(mark,sx(738),sy(626),t);
        }else{
            drawFixedKey(c,303,518,864,647,"",22,"SPACE","");
            drawFixedKey(c,880,518,1150,647,"↩",25,"ENTER","");
            t.setColor(secondaryTextColor()); t.setTextSize(dp(12)); t.setTextAlign(Paint.Align.RIGHT); c.drawText(mark,sx(842),sy(626),t);
        }
    }''',
'bottom row split by input mode'
)

# 3) Keep useful two-root exact characters such as 干/甘 at the front without
#    promoting every obscure two-root exact character.
m=re.search(r'    private static final String COMMON_CHARS = "([^"]*)";',s)
if not m:
    raise SystemExit('v0.9.12 patch failed: COMMON_CHARS missing')
chars=m.group(1)
for ch in '干甘':
    if ch not in chars:
        chars+=ch
s=s[:m.start(1)]+chars+s[m.end(1):]

replace_once(
'''        // From two roots onward, an ordinary BMP Han exact match is a real
        // complete Cangjie code and stays ahead of prefix completions.
        if(code!=null&&code.length()>=2&&text.codePointCount(0,text.length())==1){
''',
'''        // For 3+ roots, ordinary exact Han matches stay ahead. Two-root
        // exacts are promoted only when they are common/learned/root-exact.
        if(code!=null&&code.length()>=3&&text.codePointCount(0,text.length())==1){
''',
'rare two-root exact demotion'
)

# 4) Expanded candidates become a full-height scrollable lookup panel.
#    There is no 21-candidate cap; candOff is moved by rows until the list ends.
new_draw_candidates=r'''    private void drawCandidates(Canvas c){
        int h=candH();
        int rows=expanded?Math.max(3,Math.max(1,getHeight()/h)):1;
        int panelH=expanded?getHeight():h;

        p.setColor(expanded?candidateExpandedColor():candidateBgColor());
        c.drawRect(0,0,getWidth(),panelH,p);

        List<String> items=candidates();
        float arrowW=dp(46),usable=getWidth()-arrowW;
        int pageSize=7*rows;
        if(candOff<0) candOff=0;
        if(candOff>=items.size()) candOff=0;
        if(expanded) candOff=(candOff/7)*7;

        int n=Math.max(0,Math.min(pageSize,items.size()-candOff));
        boolean composing=(mode==Mode.CANGJIE&&!cjCode.isEmpty())||(mode==Mode.ZHUYIN&&!zyCode.isEmpty());

        if(composing){
            float cw=usable/7f;
            for(int i=0;i<n;i++){
                int row=i/7,col=i%7;
                RectF r=new RectF(col*cw,row*h,(col+1)*cw,Math.min(panelH,(row+1)*h));
                if(i==0&&candOff==0){
                    p.setColor(selectedCandidateColor());
                    c.drawRoundRect(new RectF(r.left+dp(3),r.top+dp(3),r.right-dp(3),r.bottom-dp(3)),dp(9),dp(9),p);
                }
                String item=items.get(candOff+i);
                t.setTextSize(dp(22*candScale())); t.setTextAlign(Paint.Align.CENTER); t.setColor(textColor());
                android.graphics.Rect b=new android.graphics.Rect(); t.getTextBounds(item,0,item.length(),b);
                c.drawText(item,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t);
                hits.add(new Hit(r,"CAND",item));
            }
        }else{
            for(int row=0;row<rows;row++){
                int rowStart=row*7,rowCount=Math.min(7,n-rowStart); if(rowCount<=0) break;
                float totalWeight=0f;
                for(int j=0;j<rowCount;j++) totalWeight+=candidateWeight(items.get(candOff+rowStart+j));
                float left=0f;
                for(int j=0;j<rowCount;j++){
                    String item=items.get(candOff+rowStart+j);
                    float w=(j==rowCount-1)?(usable-left):(usable*candidateWeight(item)/totalWeight);
                    RectF r=new RectF(left,row*h,left+w,Math.min(panelH,(row+1)*h));
                    int cps=item.codePointCount(0,item.length());
                    float sz=cps<=1?22f:(cps==2?19f:(cps==3?16.5f:14.5f));
                    t.setTextSize(dp(sz*candScale()));
                    float maxText=Math.max(dp(18),r.width()-dp(18)),measured=t.measureText(item);
                    if(measured>maxText&&measured>0f) t.setTextSize(t.getTextSize()*(maxText/measured));
                    t.setTextAlign(Paint.Align.CENTER); t.setColor(textColor());
                    android.graphics.Rect b=new android.graphics.Rect(); t.getTextBounds(item,0,item.length(),b);
                    c.drawText(item,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t);
                    hits.add(new Hit(r,"CAND",item));
                    if(j<rowCount-1){
                        String next=items.get(candOff+rowStart+j+1);
                        boolean phrase=cps>1||next.codePointCount(0,next.length())>1;
                        if(phrase){
                            p.setColor(dividerColor());
                            c.drawRect(r.right-dp(.5f),row*h+dp(9),r.right+dp(.5f),Math.min(panelH,(row+1)*h)-dp(9),p);
                        }
                    }
                    left+=w;
                }
            }
        }

        RectF ar=new RectF(getWidth()-arrowW,0,getWidth(),h);
        drawTextCentered(c,expanded?"⌃":"⌄",ar,21);
        hits.add(new Hit(ar,"EXPAND",""));
    }
'''
sub1(r'    private void drawCandidates\(Canvas c\)\{.*?\n    \}\n(?=\n    private void drawFooter)',new_draw_candidates,'unlimited candidate panel')

scroll_helper=r'''
    private void scrollExpandedCandidates(int rowDelta){
        if(!expanded||rowDelta==0) return;
        List<String> items=candidates();
        int visibleRows=Math.max(3,Math.max(1,getHeight()/candH()));
        int totalRows=(items.size()+6)/7;
        int maxStart=Math.max(0,(totalRows-visibleRows)*7);
        candOff+=(rowDelta*7);
        candOff=Math.max(0,Math.min(maxStart,candOff));
        candOff=(candOff/7)*7;
        invalidate();
    }
'''
anchor='    private void pageCandidates(boolean forward){'
idx=s.find(anchor)
if idx<0:
    raise SystemExit('v0.9.12 patch failed: pageCandidates missing')
s=s[:idx]+scroll_helper+s[idx:]

# Replace only the candidate gesture portion: expanded mode scrolls vertically;
# collapsed mode keeps the existing horizontal seven-candidate paging.
old_gesture='''        if(page!=Page.EMOJI&&page!=Page.KAOMOJI&&downY<candH()*(expanded?3:1)&&adx>dp(34)&&adx>ady*1.25f){ feedback(); pageCandidates(dx<0); downHit=null; return true; }
'''
new_gesture='''        if(expanded&&page!=Page.EMOJI&&page!=Page.KAOMOJI&&ady>dp(28)&&ady>adx*1.10f){
            int rows=Math.max(1,Math.round(ady/Math.max(1f,candH())));
            feedback();
            scrollExpandedCandidates(dy<0?rows:-rows);
            downHit=null;
            return true;
        }
        if(!expanded&&page!=Page.EMOJI&&page!=Page.KAOMOJI&&downY<candH()&&adx>dp(34)&&adx>ady*1.25f){ feedback(); pageCandidates(dx<0); downHit=null; return true; }
'''
replace_once(old_gesture,new_gesture,'candidate scroll gesture')

java_path.write_text(s,encoding='utf-8')

# 5) Voice: remove the 10-second automatic shutdown. Voice now ends only when
#    the user stops it or the input view/input session actually ends. Also keep
#    recognizer segments alive longer and show Android partial results as
#    temporary composing text.
service_path=Path('imeapp/app/src/main/java/tw/ajo/ime/AjoImeService.java')
v=service_path.read_text(encoding='utf-8')

def vsub1(pattern,replacement,label):
    global v
    v2,n=re.subn(pattern,replacement,v,count=1,flags=re.S)
    if n!=1:
        raise SystemExit(f'v0.9.12 voice patch failed: {label} matched {n}')
    v=v2

def vreplace(old,new,label):
    global v
    if old not in v:
        raise SystemExit(f'v0.9.12 voice patch failed: {label} source not found')
    v=v.replace(old,new,1)

vsub1(
r'''    private final Handler voiceHandler=new Handler\(Looper.getMainLooper\(\)\);\n    private static final long VOICE_IDLE_TIMEOUT_MS=10000L;\n    private final Runnable voiceIdleTimeout=\(\)->\{\n        if\(voiceSessionActive\) stopVoiceSession\(\);\n    \};''',
'''    private final Handler voiceHandler=new Handler(Looper.getMainLooper());
    private boolean voiceComposing=false;
    private String voiceComposingText="";''',
'remove 10-second timeout fields'
)

vreplace(
'''        setVoiceUi(false);
        armVoiceIdleTimeout();
''',
'''        setVoiceUi(false);
''',
'remove start idle timer'
)

vsub1(
r'''    private void armVoiceIdleTimeout\(\)\{.*?\n    \}\n\n    private void suspendVoiceIdleTimeout\(\)\{.*?\n    \}\n\n''',
'',
'remove idle timer helpers'
)

vreplace(
'''        voiceSessionActive=false;
        voiceHandler.removeCallbacksAndMessages(null);
''',
'''        finishVoiceComposition(null,true);
        voiceSessionActive=false;
        voiceHandler.removeCallbacksAndMessages(null);
''',
'finalize partial on voice stop'
)

# Helpers for live partial composing text.
helper=r'''
    private void showVoicePartial(String text){
        if(text==null||text.isEmpty()) return;
        InputConnection ic=getCurrentInputConnection();
        if(ic==null) return;
        ic.setComposingText(text,1);
        voiceComposing=true;
        voiceComposingText=text;
    }

    private String finishVoiceComposition(String finalText,boolean keepPartialWhenNoFinal){
        InputConnection ic=getCurrentInputConnection();
        String out=(finalText==null||finalText.isEmpty())?(keepPartialWhenNoFinal?voiceComposingText:""):finalText;
        if(ic!=null&&voiceComposing){
            if(out.isEmpty()) ic.setComposingText("",1);
            else ic.setComposingText(out,1);
            ic.finishComposingText();
        }else if(ic!=null&&!out.isEmpty()){
            ic.commitText(out,1);
        }
        voiceComposing=false;
        voiceComposingText="";
        return out;
    }

'''
anchor='    private void createVoiceRecognizer(boolean onDevice){'
idx=v.find(anchor)
if idx<0:
    raise SystemExit('v0.9.12 voice patch failed: createVoiceRecognizer missing')
v=v[:idx]+helper+v[idx:]

vreplace(
'''            @Override public void onBeginningOfSpeech(){
                if(!valid()) return;
                setVoiceUi(true);
                suspendVoiceIdleTimeout();
            }
''',
'''            @Override public void onBeginningOfSpeech(){
                if(!valid()) return;
                setVoiceUi(true);
            }
''',
'remove speech-start timer suspend'
)

vreplace(
'''            @Override public void onEndOfSpeech(){
                if(valid()) armVoiceIdleTimeout();
            }
''',
'''            @Override public void onEndOfSpeech(){}
''',
'remove speech-end timer'
)

# Preserve any live partial if Android reports a transient error, then restart.
vreplace(
'''            @Override public void onError(int error){
                if(!valid()) return;
                setVoiceUi(false);
''',
'''            @Override public void onError(int error){
                if(!valid()) return;
                setVoiceUi(false);
                String partial=finishVoiceComposition(null,true);
                if(!partial.isEmpty()&&keyboard!=null){
                    final String shown=partial;
                    keyboard.post(()->keyboard.onVoiceResult(shown));
                }
''',
'voice error partial finalize'
)

# Final results replace the temporary composing text rather than duplicating it.
vsub1(
r'''            @Override public void onResults\(Bundle results\)\{.*?                armVoiceIdleTimeout\(\);\n                restartVoiceSoon\(100\);\n            \}\n\n            @Override public void onPartialResults\(Bundle partialResults\)\{.*?\n            \}''',
r'''            @Override public void onResults(Bundle results){
                if(!valid()) return;
                setVoiceUi(false);
                ArrayList<String> list=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text="";
                if(list!=null&&!list.isEmpty()){
                    String raw=list.get(0);
                    if(raw!=null&&!raw.trim().isEmpty()) text=prepareVoiceText(raw,currentVoiceLocale.startsWith("zh"));
                }
                String committed=finishVoiceComposition(text,true);
                if(!committed.isEmpty()&&keyboard!=null){
                    final String shown=committed;
                    keyboard.post(()->keyboard.onVoiceResult(shown));
                }
                restartVoiceSoon(100);
            }

            @Override public void onPartialResults(Bundle partialResults){
                if(!valid()||partialResults==null) return;
                ArrayList<String> list=partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(list==null||list.isEmpty()) return;
                String raw=list.get(0);
                if(raw==null||raw.trim().isEmpty()) return;
                String text=prepareVoiceText(raw,currentVoiceLocale.startsWith("zh"));
                if(!text.isEmpty()) showVoicePartial(text);
            }''',
'live partial results'
)

# Give Android more silence before it closes a segment. Recognition services
# may ignore these hints, but when honored they reduce segment restarts.
vreplace(
'''            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
''',
'''            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,1800L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,2600L);
''',
'longer segment silence'
)

# Stop voice as soon as the keyboard/input view is actually hidden, including
# switching to another system input method.
finish_anchor='''    @Override public void onFinishInput() {
        updateComposition("");
        stopVoiceSession();
        super.onFinishInput();
    }
'''
if finish_anchor not in v:
    raise SystemExit('v0.9.12 voice patch failed: onFinishInput anchor missing')
v=v.replace(finish_anchor,finish_anchor+'''
    @Override public void onFinishInputView(boolean finishingInput) {
        stopVoiceSession();
        super.onFinishInputView(finishingInput);
    }
''',1)

service_path.write_text(v,encoding='utf-8')

# 6) Version text.
main_path=Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m=main_path.read_text(encoding='utf-8')
m=re.sub(r'version\.setText\("[^"]*"\);',
         'version.setText("v0.9.12｜候選與連續語音修正版");',m,count=1)
m=re.sub(r'intro\.setText\("[^"]*"\);',
         'intro.setText("保留 v0.9.11 的注音首符號詞組。倉頡第三排恢復原本刪除鍵大小並保留句號舊位置為空白；句號只放在倉頡空白鍵與換行鍵之間，注音不增加句號鍵。候選展開後可持續上下滑動查看完整清單，不再限制 21 個；二碼常用完整字如干、甘保留在前面，但罕見完整碼不再擠進第一排。語音取消 10 秒自動關閉，改為手動停止或輸入法／鍵盤關閉時才結束，並加入即時暫存辨識文字與較長的分段靜音時間。");',
         m,count=1)
main_path.write_text(m,encoding='utf-8')

gradle=Path('imeapp/app/build.gradle')
g=gradle.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 22',g,count=1)
g=re.sub(r"versionName\s+'[^']+'","versionName '0.9.12'",g,count=1)
gradle.write_text(g,encoding='utf-8')

assert "versionCode 22" in g and "versionName '0.9.12'" in g
assert 'drawFixedKey(c,1017,318,1150,444,"⌫"' in s
assert 'drawFixedKey(c,932,318,1150,444,"⌫"' not in s
assert 'if(mode==Mode.CANGJIE)' in s
assert 'scrollExpandedCandidates' in s
assert 'expanded?Math.max(3' in s
assert 'code.length()>=3' in s
assert '干' in chars and '甘' in chars
assert 'VOICE_IDLE_TIMEOUT_MS' not in v
assert 'onFinishInputView' in v
assert 'showVoicePartial' in v
assert 'EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS' in v
assert 'v0.9.12｜候選與連續語音修正版' in m
print('v0.9.12 candidate scrolling, Cangjie layout and persistent voice session applied')

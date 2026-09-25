from pathlib import Path
import re

base=Path('.github/scripts/v0912_fix.py').read_text(encoding='utf-8')
exec(compile(base,'.github/scripts/v0912_fix.py','exec'),{'__name__':'__main__'})

# ---------------------------------------------------------------------------
# A) Real continuous candidate scrolling (fix v0.9.12 row-jump behavior)
# ---------------------------------------------------------------------------
java_path=Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s=java_path.read_text(encoding='utf-8')

def sub1(pattern,replacement,label):
    global s
    s2,n=re.subn(pattern,replacement,s,count=1,flags=re.S)
    if n!=1:
        raise SystemExit(f'v0.9.13 keyboard patch failed: {label} matched {n}')
    s=s2

def replace_once(old,new,label):
    global s
    if old not in s:
        raise SystemExit(f'v0.9.13 keyboard patch failed: {label} source not found')
    s=s.replace(old,new,1)

replace_once(
'''    private float panelLastY = 0f;
    private boolean panelDragging = false;
''',
'''    private float panelLastY = 0f;
    private boolean panelDragging = false;
    private float candidateScrollY = 0f;
    private float candidateLastY = 0f;
    private boolean candidateDragging = false;
''',
'candidate scroll state'
)

new_draw_candidates=r'''    private void drawCandidates(Canvas c){
        int h=candH();
        int panelH=expanded?getHeight():h;

        p.setColor(expanded?candidateExpandedColor():candidateBgColor());
        c.drawRect(0,0,getWidth(),panelH,p);

        List<String> items=candidates();
        float arrowW=dp(46),usable=getWidth()-arrowW;
        boolean composing=(mode==Mode.CANGJIE&&!cjCode.isEmpty())||(mode==Mode.ZHUYIN&&!zyCode.isEmpty());

        if(!expanded){
            if(candOff<0) candOff=0;
            if(candOff>=items.size()) candOff=0;
            int n=Math.max(0,Math.min(7,items.size()-candOff));

            if(composing){
                float cw=usable/7f;
                for(int i=0;i<n;i++){
                    RectF r=new RectF(i*cw,0,(i+1)*cw,h);
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
                int rowCount=n;
                float totalWeight=0f;
                for(int j=0;j<rowCount;j++) totalWeight+=candidateWeight(items.get(candOff+j));
                float left=0f;
                for(int j=0;j<rowCount;j++){
                    String item=items.get(candOff+j);
                    float w=(j==rowCount-1)?(usable-left):(usable*candidateWeight(item)/Math.max(1f,totalWeight));
                    RectF r=new RectF(left,0,left+w,h);
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
                        String next=items.get(candOff+j+1);
                        boolean phrase=cps>1||next.codePointCount(0,next.length())>1;
                        if(phrase){
                            p.setColor(dividerColor());
                            c.drawRect(r.right-dp(.5f),dp(9),r.right+dp(.5f),h-dp(9),p);
                        }
                    }
                    left+=w;
                }
            }
        }else{
            candOff=0;
            int totalRows=(items.size()+6)/7;
            float maxScroll=Math.max(0f,totalRows*h-panelH);
            candidateScrollY=Math.max(0f,Math.min(maxScroll,candidateScrollY));
            int firstRow=(int)Math.floor(candidateScrollY/Math.max(1f,h));
            float offsetY=-(candidateScrollY-firstRow*h);
            int visibleRows=(int)Math.ceil((panelH-offsetY)/Math.max(1f,h))+1;

            for(int vr=0;vr<visibleRows;vr++){
                int row=firstRow+vr;
                if(row>=totalRows) break;
                int rowStart=row*7;
                int rowCount=Math.min(7,items.size()-rowStart);
                float top=offsetY+vr*h;
                float bottom=top+h;
                if(bottom<=0||top>=panelH) continue;

                if(composing){
                    float cw=usable/7f;
                    for(int col=0;col<rowCount;col++){
                        int idx=rowStart+col;
                        RectF r=new RectF(col*cw,top,(col+1)*cw,bottom);
                        if(idx==0){
                            p.setColor(selectedCandidateColor());
                            c.drawRoundRect(new RectF(r.left+dp(3),r.top+dp(3),r.right-dp(3),r.bottom-dp(3)),dp(9),dp(9),p);
                        }
                        String item=items.get(idx);
                        t.setTextSize(dp(22*candScale())); t.setTextAlign(Paint.Align.CENTER); t.setColor(textColor());
                        android.graphics.Rect b=new android.graphics.Rect(); t.getTextBounds(item,0,item.length(),b);
                        c.drawText(item,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t);
                        RectF hr=new RectF(r);
                        hr.top=Math.max(0,hr.top); hr.bottom=Math.min(panelH,hr.bottom);
                        if(hr.bottom>hr.top) hits.add(new Hit(hr,"CAND",item));
                    }
                }else{
                    float totalWeight=0f;
                    for(int j=0;j<rowCount;j++) totalWeight+=candidateWeight(items.get(rowStart+j));
                    float left=0f;
                    for(int j=0;j<rowCount;j++){
                        String item=items.get(rowStart+j);
                        float w=(j==rowCount-1)?(usable-left):(usable*candidateWeight(item)/Math.max(1f,totalWeight));
                        RectF r=new RectF(left,top,left+w,bottom);
                        int cps=item.codePointCount(0,item.length());
                        float sz=cps<=1?22f:(cps==2?19f:(cps==3?16.5f:14.5f));
                        t.setTextSize(dp(sz*candScale()));
                        float maxText=Math.max(dp(18),r.width()-dp(18)),measured=t.measureText(item);
                        if(measured>maxText&&measured>0f) t.setTextSize(t.getTextSize()*(maxText/measured));
                        t.setTextAlign(Paint.Align.CENTER); t.setColor(textColor());
                        android.graphics.Rect b=new android.graphics.Rect(); t.getTextBounds(item,0,item.length(),b);
                        c.drawText(item,r.centerX(),r.centerY()-(b.top+b.bottom)/2f,t);
                        RectF hr=new RectF(r);
                        hr.top=Math.max(0,hr.top); hr.bottom=Math.min(panelH,hr.bottom);
                        if(hr.bottom>hr.top) hits.add(new Hit(hr,"CAND",item));
                        left+=w;
                    }
                }
            }
        }

        RectF ar=new RectF(getWidth()-arrowW,0,getWidth(),h);
        drawTextCentered(c,expanded?"⌃":"⌄",ar,21);
        hits.add(new Hit(ar,"EXPAND",""));
    }
'''
sub1(r'    private void drawCandidates\(Canvas c\)\{.*?\n    \}\n(?=\n    private void drawFooter)',new_draw_candidates,'continuous candidate drawing')

sub1(
r'''    private void scrollExpandedCandidates\(int rowDelta\)\{.*?\n    \}\n''',
r'''    private void clampCandidateScroll(){
        if(!expanded){ candidateScrollY=0f; return; }
        List<String> items=candidates();
        int totalRows=(items.size()+6)/7;
        float maxScroll=Math.max(0f,totalRows*candH()-getHeight());
        candidateScrollY=Math.max(0f,Math.min(maxScroll,candidateScrollY));
    }
''',
'candidate scroll helper'
)

# Reset scroll whenever candidate context resets.
s=s.replace('candOff=0;','candOff=0; candidateScrollY=0f;')

# ACTION_DOWN / CANCEL / MOVE / UP: drag follows the finger continuously.
replace_once(
'''            downAt=SystemClock.uptimeMillis(); downX=x; downY=y; panelLastY=y; panelDragging=false; downHit=findHit(x,y); backspaceRepeating=false; longPressDirect=false; spaceCursorMode=false; cursorCarry=0f;
''',
'''            downAt=SystemClock.uptimeMillis(); downX=x; downY=y; panelLastY=y; panelDragging=false; candidateLastY=y; candidateDragging=false; downHit=findHit(x,y); backspaceRepeating=false; longPressDirect=false; spaceCursorMode=false; cursorCarry=0f;
''',
'candidate drag ACTION_DOWN'
)

replace_once(
'''        if(e.getAction()==MotionEvent.ACTION_CANCEL){ handler.removeCallbacks(repeatBackspace); handler.removeCallbacks(directSymbolLongPress); handler.removeCallbacks(spaceCursorLongPress); downHit=null; panelDragging=false; spaceCursorMode=false; return true; }
''',
'''        if(e.getAction()==MotionEvent.ACTION_CANCEL){ handler.removeCallbacks(repeatBackspace); handler.removeCallbacks(directSymbolLongPress); handler.removeCallbacks(spaceCursorLongPress); downHit=null; panelDragging=false; candidateDragging=false; spaceCursorMode=false; return true; }
''',
'candidate drag ACTION_CANCEL'
)

replace_once(
'''        if(e.getAction()==MotionEvent.ACTION_MOVE){
            if(page==Page.EMOJI||page==Page.KAOMOJI){
''',
'''        if(e.getAction()==MotionEvent.ACTION_MOVE){
            if(expanded&&page!=Page.EMOJI&&page!=Page.KAOMOJI){
                float total=Math.abs(y-downY);
                if(total>dp(4)) candidateDragging=true;
                if(candidateDragging){
                    handler.removeCallbacks(directSymbolLongPress);
                    handler.removeCallbacks(spaceCursorLongPress);
                    candidateScrollY+=candidateLastY-y;
                    clampCandidateScroll();
                    candidateLastY=y;
                    invalidate();
                    return true;
                }
            }
            if(page==Page.EMOJI||page==Page.KAOMOJI){
''',
'candidate drag ACTION_MOVE'
)

# Remove v0.9.12 release-to-jump scrolling block.
sub1(
r'''        if\(expanded&&page!=Page\.EMOJI&&page!=Page\.KAOMOJI&&ady>dp\(28\)&&ady>adx\*1\.10f\)\{.*?            return true;\n        \}\n''',
'',
'remove row-jump candidate gesture'
)

replace_once(
'''        if(panelDragging){ panelDragging=false; downHit=null; return true; }
        if(spaceCursorMode){ spaceCursorMode=false; downHit=null; return true; }
''',
'''        if(panelDragging){ panelDragging=false; downHit=null; return true; }
        if(candidateDragging){ candidateDragging=false; downHit=null; return true; }
        if(spaceCursorMode){ spaceCursorMode=false; downHit=null; return true; }
''',
'candidate drag ACTION_UP'
)

java_path.write_text(s,encoding='utf-8')

# ---------------------------------------------------------------------------
# B) Voice v0.9.13: prefer Android segmented recognition on API 33+.
#    This keeps one recognizer session alive and receives multiple segments
#    without repeatedly closing/opening the microphone between normal pauses.
#    Providers that ignore segmented mode automatically fall back to normal
#    onResults + restart behavior.
# ---------------------------------------------------------------------------
service_path=Path('imeapp/app/src/main/java/tw/ajo/ime/AjoImeService.java')
v=service_path.read_text(encoding='utf-8')

def vsub1(pattern,replacement,label):
    global v
    v2,n=re.subn(pattern,replacement,v,count=1,flags=re.S)
    if n!=1:
        raise SystemExit(f'v0.9.13 voice patch failed: {label} matched {n}')
    v=v2

def vreplace(old,new,label):
    global v
    if old not in v:
        raise SystemExit(f'v0.9.13 voice patch failed: {label} source not found')
    v=v.replace(old,new,1)

new_create=r'''    private void createVoiceRecognizer(boolean onDevice){
        voiceRecognizerGeneration++;
        final int generation=voiceRecognizerGeneration;
        if(speechRecognizer!=null){
            try{ speechRecognizer.destroy(); }catch(Exception ignored){}
            speechRecognizer=null;
        }
        voiceUsingOnDevice=onDevice;
        try{
            if(onDevice&&Build.VERSION.SDK_INT>=31) speechRecognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            else speechRecognizer=SpeechRecognizer.createSpeechRecognizer(this);
        }catch(Exception e){
            speechRecognizer=null;
        }
        if(speechRecognizer==null){
            if(onDevice&&!voiceFallbackTried){
                voiceFallbackTried=true;
                createVoiceRecognizer(false);
                return;
            }
            Toast.makeText(this,"這支手機目前沒有可用的語音辨識服務",Toast.LENGTH_LONG).show();
            stopVoiceSession();
            return;
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener(){
            private boolean segmentDelivered=false;
            private boolean valid(){ return generation==voiceRecognizerGeneration&&voiceSessionActive; }

            private String prepared(Bundle bundle){
                ArrayList<String> list=bundle==null?null:bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(list==null||list.isEmpty()) return "";
                String raw=list.get(0);
                if(raw==null||raw.trim().isEmpty()) return "";
                return prepareVoiceText(raw,currentVoiceLocale.startsWith("zh"));
            }

            private void commitBundle(Bundle bundle){
                String text=prepared(bundle);
                String committed=finishVoiceComposition(text,true);
                if(!committed.isEmpty()&&keyboard!=null){
                    final String shown=committed;
                    keyboard.post(()->keyboard.onVoiceResult(shown));
                }
            }

            @Override public void onReadyForSpeech(Bundle params){
                if(valid()) setVoiceUi(true);
            }
            @Override public void onBeginningOfSpeech(){
                if(valid()) setVoiceUi(true);
            }
            @Override public void onRmsChanged(float rmsdB){}
            @Override public void onBufferReceived(byte[] buffer){}
            @Override public void onEndOfSpeech(){}

            @Override public void onError(int error){
                if(!valid()) return;
                setVoiceUi(false);
                String partial=finishVoiceComposition(null,true);
                if(!partial.isEmpty()&&keyboard!=null){
                    final String shown=partial;
                    keyboard.post(()->keyboard.onVoiceResult(shown));
                }
                if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){
                    Toast.makeText(AjoImeService.this,"需要麥克風權限才能使用語音輸入",Toast.LENGTH_SHORT).show();
                    stopVoiceSession();
                    return;
                }
                if(voiceUsingOnDevice&&!voiceFallbackTried){
                    voiceFallbackTried=true;
                    createVoiceRecognizer(false);
                    restartVoiceSoon(120);
                    return;
                }
                restartVoiceSoon(error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY?500:120);
            }

            @Override public void onResults(Bundle results){
                if(!valid()) return;
                // If segmented callbacks were delivered, they already committed
                // each piece. Ignore a possible aggregate final result to avoid
                // duplicating the entire dictation.
                if(segmentDelivered) return;
                setVoiceUi(false);
                commitBundle(results);
                restartVoiceSoon(80);
            }

            @Override public void onPartialResults(Bundle partialResults){
                if(!valid()||partialResults==null) return;
                String text=prepared(partialResults);
                if(!text.isEmpty()) showVoicePartial(text);
            }

            @Override public void onSegmentResults(Bundle segmentResults){
                if(!valid()) return;
                segmentDelivered=true;
                commitBundle(segmentResults);
                // The same recognition session stays open. Do NOT restart here.
                setVoiceUi(true);
            }

            @Override public void onEndOfSegmentedSession(){
                if(!valid()) return;
                setVoiceUi(false);
                String partial=finishVoiceComposition(null,true);
                if(!partial.isEmpty()&&keyboard!=null){
                    final String shown=partial;
                    keyboard.post(()->keyboard.onVoiceResult(shown));
                }
                restartVoiceSoon(80);
            }

            @Override public void onEvent(int eventType,Bundle params){}
        });
    }
'''
vsub1(r'    private void createVoiceRecognizer\(boolean onDevice\)\{.*?\n    \}\n\n    private void restartVoiceSoon',new_create+'\n    private void restartVoiceSoon','segmented recognition listener')

new_start=r'''    private void startListeningOnce(){
        if(!voiceSessionActive) return;
        if(speechRecognizer==null){
            createVoiceRecognizer(false);
            if(speechRecognizer==null) return;
        }
        try{
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,currentVoiceLocale);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);

            // Long-pause dictation. On Android 13+ ask for segmented-session
            // mode so multiple spoken segments can arrive while one mic session
            // stays open. Minimum session length is 10 minutes; manual stop,
            // hiding the keyboard, or switching IME still cancels immediately.
            if(Build.VERSION.SDK_INT>=33){
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,600000L);
                intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                        RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,4000L);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,5000L);
            }else{
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,4000L);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,5000L);
            }

            if(voiceUsingOnDevice) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
            setVoiceUi(false);
            speechRecognizer.startListening(intent);
        }catch(Exception e){
            if(voiceSessionActive) restartVoiceSoon(180);
        }
    }
'''
vsub1(r'    private void startListeningOnce\(\)\{.*?\n    \}\n\n    private String prepareVoiceText',new_start+'\n    private String prepareVoiceText','segmented start intent')

service_path.write_text(v,encoding='utf-8')

# ---------------------------------------------------------------------------
# C) Version metadata/settings text
# ---------------------------------------------------------------------------
main_path=Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m=main_path.read_text(encoding='utf-8')
m=re.sub(r'version\.setText\("[^"]*"\);',
         'version.setText("v0.9.13｜長停頓語音修正版");',m,count=1)
m=re.sub(r'intro\.setText\("[^"]*"\);',
         'intro.setText("語音在 Android 13 以上優先使用分段辨識工作階段：一次開啟麥克風後可接收多段辨識結果，降低停頓後反覆關閉／重啟造成的開頭漏字；若手機辨識服務不支援則自動沿用一般連續重啟方式。停頓判定也放寬，語音仍只在手動停止、鍵盤收起或切換輸入法時結束。候選展開頁同步修正為真正跟著手指連續上下捲動，候選總數不設上限。");',
         m,count=1)
main_path.write_text(m,encoding='utf-8')

gradle=Path('imeapp/app/build.gradle')
g=gradle.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 23',g,count=1)
g=re.sub(r"versionName\s+'[^']+'","versionName '0.9.13'",g,count=1)
gradle.write_text(g,encoding='utf-8')

assert "versionCode 23" in g and "versionName '0.9.13'" in g
assert 'candidateScrollY' in s and 'candidateDragging' in s
assert 'scrollExpandedCandidates' not in s
assert 'onSegmentResults' in v and 'onEndOfSegmentedSession' in v
assert 'EXTRA_SEGMENTED_SESSION' in v
assert 'EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,600000L' in v
assert 'VOICE_IDLE_TIMEOUT_MS' not in v
assert 'v0.9.13｜長停頓語音修正版' in m
print('v0.9.13 segmented long-pause voice + continuous candidate scrolling applied')

from pathlib import Path
import re

# Build on the verified v0.9.9 patch chain.
base = Path('.github/scripts/v099_fix.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v099_fix.py', 'exec'), {'__name__': '__main__'})

service_path=Path('imeapp/app/src/main/java/tw/ajo/ime/AjoImeService.java')
s=service_path.read_text(encoding='utf-8')

def replace_once(old,new,label):
    global s
    if old not in s:
        raise SystemExit(f'v0.9.10 patch failed: {label} source not found')
    s=s.replace(old,new,1)

# ---------------------------------------------------------------------------
# 1) 10-second voice inactivity timeout.
#    The timer starts when continuous voice mode starts, is suspended while
#    actual speech is in progress, and restarts after speech/partial/final text.
# ---------------------------------------------------------------------------
replace_once(
'''    private final Handler voiceHandler=new Handler(Looper.getMainLooper());
''',
'''    private final Handler voiceHandler=new Handler(Looper.getMainLooper());
    private static final long VOICE_IDLE_TIMEOUT_MS=10000L;
    private final Runnable voiceIdleTimeout=()->{
        if(voiceSessionActive) stopVoiceSession();
    };
''',
'idle timeout fields'
)

replace_once(
'''    private void startVoiceInput(String locale){
        currentVoiceLocale=(locale==null||locale.isEmpty())?"zh-TW":locale;
        voiceSessionActive=true;
        voiceFallbackTried=false;
        setVoiceUi(true);
        boolean onDevice=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        createVoiceRecognizer(onDevice);
        startListeningOnce();
    }

    private void stopVoiceSession(){
        voiceSessionActive=false;
        voiceHandler.removeCallbacksAndMessages(null);
''',
'''    private void startVoiceInput(String locale){
        currentVoiceLocale=(locale==null||locale.isEmpty())?"zh-TW":locale;
        voiceSessionActive=true;
        voiceFallbackTried=false;
        // Do not show the stop icon until Android confirms that the recognizer
        // is actually ready. This avoids encouraging speech during start-up.
        setVoiceUi(false);
        armVoiceIdleTimeout();
        boolean onDevice=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        createVoiceRecognizer(onDevice);
        startListeningOnce();
    }

    private void armVoiceIdleTimeout(){
        if(!voiceSessionActive) return;
        voiceHandler.removeCallbacks(voiceIdleTimeout);
        voiceHandler.postDelayed(voiceIdleTimeout,VOICE_IDLE_TIMEOUT_MS);
    }

    private void suspendVoiceIdleTimeout(){
        voiceHandler.removeCallbacks(voiceIdleTimeout);
    }

    private void stopVoiceSession(){
        voiceSessionActive=false;
        voiceHandler.removeCallbacksAndMessages(null);
''',
'idle timeout helpers'
)

# ---------------------------------------------------------------------------
# 2) Better recognizer-ready behavior and shorter inter-segment gap.
#    The visible stop icon now means the recognizer has reported ready.
# ---------------------------------------------------------------------------
replace_once(
'''            @Override public void onReadyForSpeech(Bundle params){ if(valid()) setVoiceUi(true); }
            @Override public void onBeginningOfSpeech(){ if(valid()) setVoiceUi(true); }
            @Override public void onRmsChanged(float rmsdB){}
            @Override public void onBufferReceived(byte[] buffer){}
            @Override public void onEndOfSpeech(){}
''',
'''            @Override public void onReadyForSpeech(Bundle params){
                if(valid()) setVoiceUi(true);
            }
            @Override public void onBeginningOfSpeech(){
                if(!valid()) return;
                setVoiceUi(true);
                suspendVoiceIdleTimeout();
            }
            @Override public void onRmsChanged(float rmsdB){}
            @Override public void onBufferReceived(byte[] buffer){}
            @Override public void onEndOfSpeech(){
                if(valid()) armVoiceIdleTimeout();
            }
''',
'ready/speech callbacks'
)

replace_once(
'''            @Override public void onError(int error){
                if(!valid()) return;
                if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){
''',
'''            @Override public void onError(int error){
                if(!valid()) return;
                setVoiceUi(false);
                if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){
''',
'error readiness state'
)

replace_once(
'''                    createVoiceRecognizer(false);
                    restartVoiceSoon(250);
''',
'''                    createVoiceRecognizer(false);
                    restartVoiceSoon(120);
''',
'on-device fallback restart'
)

replace_once(
'''                restartVoiceSoon(error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY?700:350);
''',
'''                restartVoiceSoon(error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY?500:180);
''',
'transient error restart'
)

replace_once(
'''            @Override public void onResults(Bundle results){
                if(!valid()) return;
                ArrayList<String> list=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
''',
'''            @Override public void onResults(Bundle results){
                if(!valid()) return;
                setVoiceUi(false);
                ArrayList<String> list=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
''',
'result readiness state'
)

replace_once(
'''                restartVoiceSoon(350);
            }

            @Override public void onPartialResults(Bundle partialResults){}
''',
'''                armVoiceIdleTimeout();
                restartVoiceSoon(100);
            }

            @Override public void onPartialResults(Bundle partialResults){
                if(!valid()||partialResults==null) return;
                ArrayList<String> list=partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(list!=null&&!list.isEmpty()&&list.get(0)!=null&&!list.get(0).trim().isEmpty()){
                    armVoiceIdleTimeout();
                }
            }
''',
'fast restart and partial activity'
)

# Do not advertise listening before onReadyForSpeech.
replace_once(
'''            if(voiceUsingOnDevice) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
            setVoiceUi(true);
            speechRecognizer.startListening(intent);
''',
'''            if(voiceUsingOnDevice) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
            setVoiceUi(false);
            speechRecognizer.startListening(intent);
''',
'start readiness indicator'
)

# ---------------------------------------------------------------------------
# 3) Stop explicitly requesting Android automatic punctuation/formatting.
#    Spoken punctuation and VoiceRuleStore remain unchanged.
# ---------------------------------------------------------------------------
replace_once(
'''            if(Build.VERSION.SDK_INT>=33){
                intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING,RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
            }
''',
'',
'disable automatic formatting request'
)

service_path.write_text(s,encoding='utf-8')

# ---------------------------------------------------------------------------
# 4) Settings/version text.
# ---------------------------------------------------------------------------
main_path=Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m=main_path.read_text(encoding='utf-8')
m=re.sub(r'version\.setText\("[^"]*"\);',
         'version.setText("v0.9.10｜語音等待與收音修正版");',m,count=1)
m=re.sub(r'intro\.setText\("[^"]*"\);',
         'intro.setText("保留 v0.9.9 的連續語音與語音自訂轉換。本版針對實際測試修正收音時機：每段完成後約 0.1 秒重新開始辨識，降低下一句開頭漏字；麥克風停止圖示只在系統回報已準備收音後顯示；連續約 10 秒沒有語音活動會自動關閉語音模式。另取消主動要求系統自動標點，口述標點與自訂轉換維持原本方式。");',
         m,count=1)
main_path.write_text(m,encoding='utf-8')

gradle=Path('imeapp/app/build.gradle')
g=gradle.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 20',g,count=1)
g=re.sub(r"versionName\s+'[^']+'","versionName '0.9.10'",g,count=1)
gradle.write_text(g,encoding='utf-8')

assert "versionCode 20" in g and "versionName '0.9.10'" in g
assert 'VOICE_IDLE_TIMEOUT_MS=10000L' in s
assert 'restartVoiceSoon(100)' in s
assert 'setVoiceUi(false);' in s
assert 'EXTRA_ENABLE_FORMATTING' not in s
assert 'VoiceRuleStore.apply(this,base)' in s
assert 'v0.9.10｜語音等待與收音修正版' in m
print('v0.9.10 voice readiness, 10-second idle stop and faster restart applied')

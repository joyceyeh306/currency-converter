from pathlib import Path
import re

# Build on the verified v0.9.7 patch chain.
base = Path('.github/scripts/v097_fix.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v097_fix.py', 'exec'), {'__name__': '__main__'})

java_path = Path('imeapp/app/src/main/java/tw/ajo/ime/PreciseKeyboardView.java')
s = java_path.read_text(encoding='utf-8')


def replace_once(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'v0.9.8 patch failed: {label} source not found')
    s = s.replace(old, new, 1)


def sub1(pattern, replacement, label):
    global s
    s2, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'v0.9.8 patch failed for {label}: matched {n}')
    s = s2


# ---------------------------------------------------------------------------
# 1) iOS-like Cangjie candidate structure.
#
# The primary candidate is no longer simply the winner of one mixed score pool.
# We first expose useful exact-code candidates in dictionary order, then rank
# prefix continuations by Taiwan/common/context/personal usage, and only then
# leave unusual exact-code candidates later in the list.
#
# This reproduces the observed progression:
#   H    -> 竹 | 的 ...
#   HA   -> 白 | 的 ...
#   HAP  -> 皂 | 皀 | 的 ...
#   HAPI -> 的
# while VN does not put the supplementary-plane 𠃑 in front of 好.
# ---------------------------------------------------------------------------
old_rank = r'''    private List<String> rankCangjie(List<String> exact,List<String> prefix){
        LinkedHashSet<String> all=new LinkedHashSet<>();
        if(exact!=null) all.addAll(exact);
        if(prefix!=null) all.addAll(prefix);
        ArrayList<String> out=new ArrayList<>();
        for(String x:all) if(!hiddenRareCandidate(x)) out.add(x);
        final LinkedHashSet<String> exactSet=new LinkedHashSet<>();
        if(exact!=null) for(String x:exact) if(!hiddenRareCandidate(x)) exactSet.add(x);
        out.sort((a,b)->{
            long sa=commonScore(a), sb=commonScore(b);
            // Useful exact codes get a strong but not absolute advantage. This makes
            // 一一 -> 二 and 竹手 -> 牛, while a hidden rare exact code never beats 好.
            if(exactSet.contains(a)) sa += builtinCommonRank(a)>0 ? 160000L : 25000L;
            if(exactSet.contains(b)) sb += builtinCommonRank(b)>0 ? 160000L : 25000L;
            return Long.compare(sb,sa);
        });
        return out;
    }
'''
new_rank = r'''    private boolean rootExactCandidate(String code,String text){
        if(code==null||text==null) return false;
        switch(code){
            case "q": return "手".equals(text); case "w": return "田".equals(text); case "e": return "水".equals(text);
            case "r": return "口".equals(text); case "t": return "廿".equals(text); case "y": return "卜".equals(text);
            case "u": return "山".equals(text); case "i": return "戈".equals(text); case "o": return "人".equals(text);
            case "p": return "心".equals(text); case "a": return "日".equals(text); case "s": return "尸".equals(text);
            case "d": return "木".equals(text); case "f": return "火".equals(text); case "g": return "土".equals(text);
            case "h": return "竹".equals(text); case "j": return "十".equals(text); case "k": return "大".equals(text);
            case "l": return "中".equals(text); case "z": return "重".equals(text); case "x": return "難".equals(text);
            case "c": return "金".equals(text); case "v": return "女".equals(text); case "b": return "月".equals(text);
            case "n": return "弓".equals(text); case "m": return "一".equals(text); default: return false;
        }
    }
    private boolean usefulExactCandidate(String code,String text){
        if(text==null||text.isEmpty()||hiddenRareCandidate(text)) return false;
        if(builtinCommonRank(text)>0||personalFrequency(text)>0||learnedContextFrequency(text)>0) return true;
        if(rootExactCandidate(code,text)) return true;
        // With 3+ roots the user has expressed much more intent. Keep ordinary
        // BMP Han exact matches (e.g. HAP -> 皂, 皀) ahead of prefix completions.
        if(code!=null&&code.length()>=3&&text.codePointCount(0,text.length())==1){
            int cp=text.codePointAt(0);
            return cp>=0x4E00&&cp<=0x9FFF;
        }
        return false;
    }
    private List<String> rankCangjie(String code,List<String> exact,List<String> prefix){
        LinkedHashSet<String> exactAll=new LinkedHashSet<>();
        if(exact!=null) exactAll.addAll(exact);

        ArrayList<String> primaryExact=new ArrayList<>();
        ArrayList<String> deferredExact=new ArrayList<>();
        for(String x:exactAll){
            if(hiddenRareCandidate(x)) continue;
            if(usefulExactCandidate(code,x)) primaryExact.add(x);
            else deferredExact.add(x);
        }

        ArrayList<String> prefixOnly=new ArrayList<>();
        if(prefix!=null){
            for(String x:new LinkedHashSet<>(prefix)){
                if(hiddenRareCandidate(x)||exactAll.contains(x)) continue;
                prefixOnly.add(x);
            }
        }
        prefixOnly=new ArrayList<>(rankCommon(prefixOnly));

        LinkedHashSet<String> out=new LinkedHashSet<>();
        out.addAll(primaryExact);
        out.addAll(prefixOnly);
        out.addAll(deferredExact);
        return new ArrayList<>(out);
    }
'''
replace_once(old_rank, new_rank, 'iOS-like exact/prefix candidate layers')
replace_once('List<String> ranked=rankCangjie(ex,pr);',
             'List<String> ranked=rankCangjie(cjCode,ex,pr);',
             'Cangjie layered ranking call')

# ---------------------------------------------------------------------------
# 2) Rebalance 一 / 。 / Backspace hit testing.
#
# The visible period itself is always period again. The gap is shared more
# evenly, and the period borrows a narrow strip from the left edge of the wide
# backspace key. Visual geometry remains unchanged.
# ---------------------------------------------------------------------------
old_period_override = r'''                // The left portion of the visible period key is treated as 一.
                if(page==Page.MAIN&&mode==Mode.CANGJIE&&"PUNCT".equals(h.action)&&"。".equals(h.value)
                        &&x<h.r.left+h.r.width()*0.42f){
                    for(int j=hits.size()-1;j>=0;j--){ Hit root=hits.get(j); if("CJ".equals(root.action)&&"m".equals(root.value)) return root; }
                }
                return h;
'''
new_period_override = r'''                // Borrow only a slim strip from the wide backspace key for 。.
                // A tap actually inside the visible 。 key always remains 。.
                if(page==Page.MAIN&&mode==Mode.CANGJIE&&"BACK".equals(h.action)&&x<h.r.left+dp(10)){
                    for(int j=hits.size()-1;j>=0;j--){
                        Hit period=hits.get(j);
                        if("PUNCT".equals(period.action)&&"。".equals(period.value)) return period;
                    }
                }
                return h;
'''
replace_once(old_period_override, new_period_override, 'period/backspace touch rebalance')

old_expand = r'''            float px=sidePunct(h)?dp(2):dp(12),py=sidePunct(h)?dp(4):dp(10);
            if(page==Page.MAIN&&mode==Mode.CANGJIE&&"CJ".equals(h.action)&&"m".equals(h.value)) px=dp(18);
'''
new_expand = r'''            float px=sidePunct(h)?dp(2):dp(12),py=sidePunct(h)?dp(4):dp(10);
            if(page==Page.MAIN&&mode==Mode.CANGJIE&&"CJ".equals(h.action)&&"m".equals(h.value)) px=dp(12);
            if(page==Page.MAIN&&mode==Mode.CANGJIE&&"PUNCT".equals(h.action)&&"。".equals(h.value)) px=dp(10);
'''
replace_once(old_expand, new_expand, 'gap hit tolerance rebalance')

# ---------------------------------------------------------------------------
# 3) Voice UI hook in the keyboard.
# ---------------------------------------------------------------------------
state_anchor = '    private final Map<String,String> continuationFull = new HashMap<>();\n'
replace_once(state_anchor, state_anchor + '    private boolean voiceListening = false;\n', 'voice state')

sub1(
    r'    private void drawFooter\(Canvas c\)\{.*?\n    \}',
    r'''    private void drawFooter(Canvas c){
        RectF mic=rect(855,682,1005,838);
        hits.add(new Hit(mic,"MIC",""));
        drawTextCentered(c,voiceListening?"⏹":"🎙️",mic,22);
    }''',
    'voice microphone indicator'
)

replace_once(
    '            case "MIC": svc.voiceComingSoon(); break;',
    '''            case "MIC":
                if((mode==Mode.CANGJIE&&!cjCode.isEmpty())||(mode==Mode.ZHUYIN&&!zyCode.isEmpty())) doSpace();
                svc.toggleVoiceInput(mode==Mode.ENGLISH?"en-US":"zh-TW");
                break;''',
    'voice microphone action'
)

refresh_anchor = '    public void refreshSettings(){ requestLayout(); invalidate(); post(() -> svc.repositionCompositionPopup()); }\n'
voice_methods = r'''    public void setVoiceListening(boolean listening){
        voiceListening=listening;
        invalidate();
    }
    public void onVoiceResult(String text){
        if(text==null||text.isEmpty()) return;
        cjCode=""; zyCode=""; candOff=0; expanded=false;
        int end=text.length();
        int cp=text.codePointBefore(end);
        lastCommitted=text.substring(end-Character.charCount(cp));
        syncComposition();
        invalidate();
    }

'''
replace_once(refresh_anchor, voice_methods + refresh_anchor, 'voice view callbacks')
java_path.write_text(s, encoding='utf-8')

# ---------------------------------------------------------------------------
# 4) SpeechRecognizer in the IME service. Prefer Android on-device recognition
#    when available; fall back once to the system recognizer if needed.
# ---------------------------------------------------------------------------
service_path = Path('imeapp/app/src/main/java/tw/ajo/ime/AjoImeService.java')
service = service_path.read_text(encoding='utf-8')

imports_anchor = 'import android.content.Intent;\n'
voice_imports = '''import android.Manifest;\nimport android.content.BroadcastReceiver;\nimport android.content.Context;\nimport android.content.Intent;\nimport android.content.IntentFilter;\nimport android.content.pm.PackageManager;\nimport android.os.Build;\nimport android.os.Bundle;\nimport android.speech.RecognitionListener;\nimport android.speech.RecognizerIntent;\nimport android.speech.SpeechRecognizer;\n'''
if imports_anchor not in service:
    raise SystemExit('v0.9.8 patch failed: service import anchor missing')
service = service.replace(imports_anchor, voice_imports, 1)
service = service.replace('import android.widget.Toast;\n', 'import android.widget.Toast;\n\nimport java.util.ArrayList;\n', 1)

class_anchor = 'public class AjoImeService extends InputMethodService {\n'
voice_fields = r'''public class AjoImeService extends InputMethodService {
    public static final String ACTION_MIC_PERMISSION_RESULT="tw.ajo.ime.MIC_PERMISSION_RESULT";
    private SpeechRecognizer speechRecognizer;
    private boolean voiceListening=false;
    private boolean voiceUsingOnDevice=false;
    private boolean voiceFallbackTried=false;
    private String currentVoiceLocale="zh-TW";

    private final BroadcastReceiver micPermissionReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context, Intent intent){
            if(intent==null||!ACTION_MIC_PERMISSION_RESULT.equals(intent.getAction())) return;
            boolean granted=intent.getBooleanExtra("granted",false);
            String locale=intent.getStringExtra("locale");
            if(granted) startVoiceInput(locale==null?currentVoiceLocale:locale);
            else setVoiceUi(false);
        }
    };
'''
if class_anchor not in service:
    raise SystemExit('v0.9.8 patch failed: service class anchor missing')
service = service.replace(class_anchor, voice_fields, 1)

oncreate_old = '''    @Override public void onCreate() {
        super.onCreate();
        settings = getSharedPreferences("ime_settings", MODE_PRIVATE);
        settings.registerOnSharedPreferenceChangeListener(settingsListener);
    }
'''
oncreate_new = '''    @Override public void onCreate() {
        super.onCreate();
        settings = getSharedPreferences("ime_settings", MODE_PRIVATE);
        settings.registerOnSharedPreferenceChangeListener(settingsListener);
        IntentFilter filter=new IntentFilter(ACTION_MIC_PERMISSION_RESULT);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(micPermissionReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(micPermissionReceiver,filter);
    }
'''
if oncreate_old not in service:
    raise SystemExit('v0.9.8 patch failed: service onCreate block missing')
service = service.replace(oncreate_old, oncreate_new, 1)

ondestroy_old = '''    @Override public void onDestroy() {
        dismissCompositionPopup();
        if (settings != null) settings.unregisterOnSharedPreferenceChangeListener(settingsListener);
        super.onDestroy();
    }
'''
ondestroy_new = '''    @Override public void onDestroy() {
        dismissCompositionPopup();
        setVoiceUi(false);
        if(speechRecognizer!=null){ try{ speechRecognizer.destroy(); }catch(Exception ignored){} speechRecognizer=null; }
        try{ unregisterReceiver(micPermissionReceiver); }catch(Exception ignored){}
        if (settings != null) settings.unregisterOnSharedPreferenceChangeListener(settingsListener);
        super.onDestroy();
    }
'''
if ondestroy_old not in service:
    raise SystemExit('v0.9.8 patch failed: service onDestroy block missing')
service = service.replace(ondestroy_old, ondestroy_new, 1)

old_voice = '''    public void voiceComingSoon() {
        Toast.makeText(this, "語音輸入會在第二階段加入", Toast.LENGTH_SHORT).show();
    }
'''
new_voice = r'''    private void setVoiceUi(boolean listening){
        voiceListening=listening;
        if(keyboard!=null) keyboard.post(()->keyboard.setVoiceListening(listening));
    }

    public void toggleVoiceInput(String locale){
        if(!settings.getBoolean("voice_enabled",true)){
            Toast.makeText(this,"語音輸入目前在設定中關閉",Toast.LENGTH_SHORT).show();
            return;
        }
        if(voiceListening){
            if(speechRecognizer!=null) try{ speechRecognizer.stopListening(); }catch(Exception ignored){}
            setVoiceUi(false);
            return;
        }
        currentVoiceLocale=(locale==null||locale.isEmpty())?"zh-TW":locale;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            Intent i=new Intent(this,MicPermissionActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("locale",currentVoiceLocale);
            startActivity(i);
            return;
        }
        startVoiceInput(currentVoiceLocale);
    }

    private void startVoiceInput(String locale){
        currentVoiceLocale=(locale==null||locale.isEmpty())?"zh-TW":locale;
        voiceFallbackTried=false;
        boolean onDevice=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        startVoiceRecognizer(onDevice);
    }

    private void startVoiceRecognizer(boolean onDevice){
        if(!SpeechRecognizer.isRecognitionAvailable(this)&&!onDevice){
            Toast.makeText(this,"這支手機目前沒有可用的語音辨識服務",Toast.LENGTH_LONG).show();
            setVoiceUi(false);
            return;
        }
        try{
            if(speechRecognizer!=null){ speechRecognizer.destroy(); speechRecognizer=null; }
            voiceUsingOnDevice=onDevice;
            if(onDevice&&Build.VERSION.SDK_INT>=31) speechRecognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            else speechRecognizer=SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle params){ setVoiceUi(true); }
                @Override public void onBeginningOfSpeech(){ setVoiceUi(true); }
                @Override public void onRmsChanged(float rmsdB){}
                @Override public void onBufferReceived(byte[] buffer){}
                @Override public void onEndOfSpeech(){}
                @Override public void onError(int error){
                    boolean fallback=voiceUsingOnDevice&&!voiceFallbackTried&&
                            error!=SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS&&
                            error!=SpeechRecognizer.ERROR_CLIENT;
                    setVoiceUi(false);
                    if(fallback){
                        voiceFallbackTried=true;
                        startVoiceRecognizer(false);
                        return;
                    }
                    String msg=(error==SpeechRecognizer.ERROR_NO_MATCH)?"沒有聽清楚，請再試一次":
                            (error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)?"沒有偵測到語音":
                            (error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)?"需要麥克風權限才能使用語音輸入":
                            "語音辨識暫時無法使用";
                    Toast.makeText(AjoImeService.this,msg,Toast.LENGTH_SHORT).show();
                }
                @Override public void onResults(Bundle results){
                    setVoiceUi(false);
                    ArrayList<String> list=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(list!=null&&!list.isEmpty()){
                        String text=list.get(0);
                        if(text!=null&&!text.trim().isEmpty()){
                            commit(text);
                            if(keyboard!=null) keyboard.post(()->keyboard.onVoiceResult(text));
                        }
                    }
                }
                @Override public void onPartialResults(Bundle partialResults){}
                @Override public void onEvent(int eventType,Bundle params){}
            });

            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,currentVoiceLocale);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
            if(onDevice) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
            setVoiceUi(true);
            speechRecognizer.startListening(intent);
        }catch(Exception e){
            setVoiceUi(false);
            if(onDevice&&!voiceFallbackTried){
                voiceFallbackTried=true;
                startVoiceRecognizer(false);
            }else{
                Toast.makeText(this,"無法啟動語音辨識",Toast.LENGTH_SHORT).show();
            }
        }
    }
'''
if old_voice not in service:
    raise SystemExit('v0.9.8 patch failed: voice placeholder missing')
service = service.replace(old_voice, new_voice, 1)
service_path.write_text(service, encoding='utf-8')

# ---------------------------------------------------------------------------
# 5) Lightweight permission activity. A Service cannot show the Android runtime
#    permission prompt itself, so this tiny Activity asks only on the first mic
#    use and reports the result back to the active IME.
# ---------------------------------------------------------------------------
permission_path = Path('imeapp/app/src/main/java/tw/ajo/ime/MicPermissionActivity.java')
permission_path.write_text(r'''package tw.ajo.ime;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

public class MicPermissionActivity extends Activity {
    private static final int REQ_MIC=9801;
    private String locale="zh-TW";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        String requested=getIntent()==null?null:getIntent().getStringExtra("locale");
        if(requested!=null&&!requested.isEmpty()) locale=requested;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
            report(true);
        }else{
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode!=REQ_MIC) return;
        boolean granted=grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED;
        if(!granted) Toast.makeText(this,"未取得麥克風權限，語音輸入未啟用",Toast.LENGTH_SHORT).show();
        report(granted);
    }

    private void report(boolean granted){
        Intent i=new Intent(AjoImeService.ACTION_MIC_PERMISSION_RESULT);
        i.setPackage(getPackageName());
        i.putExtra("granted",granted);
        i.putExtra("locale",locale);
        sendBroadcast(i);
        finish();
    }
}
''',encoding='utf-8')

# ---------------------------------------------------------------------------
# 6) Manifest: recognizer-service visibility and private permission Activity.
# RECORD_AUDIO already exists in the project manifest.
# ---------------------------------------------------------------------------
manifest = Path('imeapp/app/src/main/AndroidManifest.xml')
ms = manifest.read_text(encoding='utf-8')
if '<uses-permission android:name="android.permission.RECORD_AUDIO" />' not in ms:
    ms = ms.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n',
                    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n',1)
if 'android.speech.RecognitionService' not in ms:
    ms = ms.replace('    <application\n',
'''    <queries>
        <intent>
            <action android:name="android.speech.RecognitionService" />
        </intent>
    </queries>
    <application
''',1)
activity_anchor = '        <activity\n            android:name=".MainActivity"\n'
if '.MicPermissionActivity' not in ms:
    if activity_anchor not in ms:
        raise SystemExit('v0.9.8 patch failed: manifest activity anchor missing')
    mic_activity = '''        <activity
            android:name=".MicPermissionActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:theme="@android:style/Theme.Material.Light.Dialog.Alert" />
'''
    ms = ms.replace(activity_anchor, mic_activity + activity_anchor,1)
manifest.write_text(ms,encoding='utf-8')

# ---------------------------------------------------------------------------
# 7) Settings wording and version.
# ---------------------------------------------------------------------------
main = Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m = main.read_text(encoding='utf-8')
m = m.replace('addSwitch(root, "語音輸入（第二階段）", "voice_enabled", false, false);',
              'addSwitch(root, "語音輸入", "voice_enabled", true, true);',1)
m = re.sub(r'version\.setText\("[^"]*"\);',
           'version.setText("v0.9.8｜候選與語音輸入修正版");',m,count=1)
m = re.sub(r'intro\.setText\("[^"]*"\);',
           'intro.setText("本版把倉頡候選改為更接近 iOS 的分層方式：先放實際完整碼中的正常候選，再接目前字根前綴的常用字；第一格主候選反白與夜間配色保留。重新平衡「一／。」觸控範圍，句號改向右借用倒退鍵左側的小區域。語音輸入正式上線：中文模式使用台灣繁中、English 使用英文，第一次使用才詢問麥克風權限，系統支援時優先使用裝置端辨識。");',
           m,count=1)
m = m.replace('語音輸入仍保留第二階段，不在本版啟用。','語音輸入已啟用。',1)
# Update any current v0.9.7 note with a concise voice note while preserving other gesture help.
note_match = re.search(r'note\.setText\("(.*?)"\);',m,re.S)
if note_match and '語音' not in note_match.group(1):
    note_text=note_match.group(1)
    note_text += '\\n語音輸入：按麥克風開始；倉頡／注音使用台灣繁中，English 使用英文。第一次使用會要求麥克風權限；裝置不支援本機辨識時，會改用手機系統提供的語音辨識服務。'
    m=m[:note_match.start(1)]+note_text+m[note_match.end(1):]
main.write_text(m,encoding='utf-8')

gradle = Path('imeapp/app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+','versionCode 18',g,count=1)
g = re.sub(r"versionName\s+'[^']+'","versionName '0.9.8'",g,count=1)
gradle.write_text(g,encoding='utf-8')

# Sanity checks.
assert "versionCode 18" in g and "versionName '0.9.8'" in g
assert 'rankCangjie(cjCode,ex,pr)' in s
assert 'rootExactCandidate' in s and 'usefulExactCandidate' in s
assert 'voiceListening?"⏹":"🎙️"' in s
assert 'svc.toggleVoiceInput(mode==Mode.ENGLISH?"en-US":"zh-TW")' in s
assert 'SpeechRecognizer.createSpeechRecognizer' in service
assert 'SpeechRecognizer.createOnDeviceSpeechRecognizer' in service
assert 'MicPermissionActivity.class' in service
assert permission_path.exists()
assert 'android.speech.RecognitionService' in ms and '.MicPermissionActivity' in ms
assert 'addSwitch(root, "語音輸入", "voice_enabled", true, true);' in m
print('v0.9.8 iOS-like candidate layers, period touch rebalance and voice input applied')

from pathlib import Path
import re

# Build on the verified v0.9.8 patch chain.
base = Path('.github/scripts/v098_fix.py').read_text(encoding='utf-8')
exec(compile(base, '.github/scripts/v098_fix.py', 'exec'), {'__name__': '__main__'})

# ---------------------------------------------------------------------------
# 1) Voice rule store:
#    - built-in spoken punctuation aliases
#    - user add/edit/delete/disable
#    - longest spoken phrase wins first
#    - arbitrary output is allowed (punctuation, emoji, kaomoji, fixed text)
# ---------------------------------------------------------------------------
rule_store = Path('imeapp/app/src/main/java/tw/ajo/ime/VoiceRuleStore.java')
rule_store.write_text(r'''package tw.ajo.ime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class VoiceRuleStore {
    private static final String PREFS="voice_rules";
    private static final String KEY="rules_json";

    public static final class Rule {
        public String spoken;
        public String output;
        public boolean enabled;
        public Rule(String spoken,String output,boolean enabled){
            this.spoken=spoken==null?"":spoken;
            this.output=output==null?"":output;
            this.enabled=enabled;
        }
    }

    private VoiceRuleStore(){}

    public static List<Rule> defaults(){
        ArrayList<Rule> r=new ArrayList<>();
        r.add(new Rule("逗號","，",true));
        r.add(new Rule("句號","。",true));
        r.add(new Rule("頓號","、",true));
        r.add(new Rule("問號符號","？",true));
        r.add(new Rule("問號","？",true));
        r.add(new Rule("驚嘆號","！",true));
        r.add(new Rule("感嘆號","！",true));
        r.add(new Rule("冒號","：",true));
        r.add(new Rule("分號","；",true));
        r.add(new Rule("省略號","……",true));
        r.add(new Rule("點點點","……",true));
        r.add(new Rule("左括號","（",true));
        r.add(new Rule("右括號","）",true));
        r.add(new Rule("左引號","「",true));
        r.add(new Rule("右引號","」",true));
        r.add(new Rule("換行","\n",true));
        return r;
    }

    public static List<Rule> load(Context c){
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!p.contains(KEY)){
            List<Rule> d=defaults();
            save(c,d);
            return d;
        }
        String raw=p.getString(KEY,"");
        ArrayList<Rule> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);
                if(o==null) continue;
                String spoken=o.optString("spoken","");
                String output=o.optString("output","");
                boolean enabled=o.optBoolean("enabled",true);
                if(!spoken.isEmpty()) out.add(new Rule(spoken,output,enabled));
            }
        }catch(Exception ignored){}
        if(out.isEmpty()&&raw.trim().isEmpty()){
            out.addAll(defaults());
            save(c,out);
        }
        return out;
    }

    public static void save(Context c,List<Rule> rules){
        JSONArray a=new JSONArray();
        try{
            for(Rule r:rules){
                JSONObject o=new JSONObject();
                o.put("spoken",r.spoken);
                o.put("output",r.output);
                o.put("enabled",r.enabled);
                a.put(o);
            }
        }catch(Exception ignored){}
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply();
    }

    public static void resetDefaults(Context c){ save(c,defaults()); }

    public static String apply(Context c,String input){
        if(input==null||input.isEmpty()) return input==null?"":input;
        ArrayList<Rule> rules=new ArrayList<>(load(c));
        Collections.sort(rules,new Comparator<Rule>(){
            @Override public int compare(Rule a,Rule b){
                int n=Integer.compare(b.spoken.length(),a.spoken.length());
                return n!=0?n:0;
            }
        });
        String out=input;
        for(Rule r:rules){
            if(!r.enabled||r.spoken.isEmpty()) continue;
            out=out.replace(r.spoken,r.output);
        }
        return out;
    }
}
''',encoding='utf-8')

# ---------------------------------------------------------------------------
# 2) Voice custom-conversion settings activity.
# ---------------------------------------------------------------------------
rules_activity = Path('imeapp/app/src/main/java/tw/ajo/ime/VoiceRulesActivity.java')
rules_activity.write_text(r'''package tw.ajo.ime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class VoiceRulesActivity extends Activity {
    private final ArrayList<VoiceRuleStore.Rule> rules=new ArrayList<>();
    private LinearLayout listBox;

    private int dp(float v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        rules.addAll(VoiceRuleStore.load(this));
        build();
    }

    private void build(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(22),dp(20),dp(30));
        root.setBackgroundColor(Color.rgb(247,247,249));
        scroll.addView(root);

        TextView title=new TextView(this);
        title.setText("語音自訂轉換");
        title.setTextSize(27);
        title.setTextColor(Color.BLACK);
        title.setTypeface(null,1);
        root.addView(title);

        TextView help=new TextView(this);
        help.setText("把「妳說的內容」轉成「實際輸出內容」。可用於標點、Emoji、顏文字、固定文字或常被辨錯的詞。較長的口述名稱會優先比對。\\n\\n例如：點點點 → ……　笑哭 → 😂　勝利 → (^_^)v");
        help.setTextSize(15);
        help.setTextColor(Color.DKGRAY);
        help.setPadding(0,dp(8),0,dp(12));
        help.setLineSpacing(0,1.15f);
        root.addView(help);

        Button add=button("＋ 新增口述轉換");
        add.setOnClickListener(v->editRule(-1));
        root.addView(add);

        listBox=new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);
        refreshRows();

        Button reset=button("恢復預設口述轉換");
        reset.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("恢復預設？")
                .setMessage("會移除妳目前所有自訂項目，恢復內建的口述標點與「點點點」。")
                .setNegativeButton("取消",null)
                .setPositiveButton("恢復",(d,w)->{
                    rules.clear();
                    rules.addAll(VoiceRuleStore.defaults());
                    saveAndRefresh();
                }).show());
        root.addView(reset);

        setContentView(scroll);
    }

    private Button button(String text){
        Button b=new Button(this);
        b.setText(text);
        b.setTextSize(17);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));
        lp.topMargin=dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private String visibleOutput(String s){
        if(s==null) return "";
        return s.replace("\n","↵ 換行");
    }

    private void refreshRows(){
        listBox.removeAllViews();
        if(rules.isEmpty()){
            TextView none=new TextView(this);
            none.setText("目前沒有口述轉換規則。");
            none.setTextSize(15);
            none.setTextColor(Color.GRAY);
            none.setPadding(0,dp(18),0,dp(8));
            listBox.addView(none);
            return;
        }
        for(int i=0;i<rules.size();i++){
            final int index=i;
            VoiceRuleStore.Rule r=rules.get(i);

            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(2),dp(12),dp(2),dp(10));

            Switch sw=new Switch(this);
            sw.setText(r.spoken+"  →  "+visibleOutput(r.output));
            sw.setTextSize(17);
            sw.setTextColor(Color.BLACK);
            sw.setChecked(r.enabled);
            sw.setOnCheckedChangeListener((buttonView,isChecked)->{
                rules.get(index).enabled=isChecked;
                VoiceRuleStore.save(this,rules);
            });
            row.addView(sw);

            LinearLayout actions=new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END);

            Button edit=new Button(this);
            edit.setText("修改");
            edit.setTextSize(14);
            edit.setAllCaps(false);
            edit.setOnClickListener(v->editRule(index));
            actions.addView(edit,new LinearLayout.LayoutParams(dp(92),dp(46)));

            Button del=new Button(this);
            del.setText("刪除");
            del.setTextSize(14);
            del.setAllCaps(false);
            del.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("刪除這筆轉換？")
                    .setMessage(r.spoken+" → "+visibleOutput(r.output))
                    .setNegativeButton("取消",null)
                    .setPositiveButton("刪除",(d,w)->{
                        rules.remove(index);
                        saveAndRefresh();
                    }).show());
            actions.addView(del,new LinearLayout.LayoutParams(dp(92),dp(46)));
            row.addView(actions);

            View line=new View(this);
            line.setBackgroundColor(Color.rgb(220,220,224));
            row.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
            listBox.addView(row);
        }
    }

    private void editRule(int index){
        boolean editing=index>=0;
        VoiceRuleStore.Rule old=editing?rules.get(index):new VoiceRuleStore.Rule("","",true);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(4),dp(20),0);

        TextView a=new TextView(this);
        a.setText("口述內容");
        a.setTextSize(14);
        a.setTextColor(Color.DKGRAY);
        box.addView(a);

        EditText spoken=new EditText(this);
        spoken.setSingleLine(true);
        spoken.setText(old.spoken);
        spoken.setHint("例如：笑哭");
        box.addView(spoken,new LinearLayout.LayoutParams(-1,dp(54)));

        TextView b=new TextView(this);
        b.setText("輸出內容");
        b.setTextSize(14);
        b.setTextColor(Color.DKGRAY);
        b.setPadding(0,dp(10),0,0);
        box.addView(b);

        EditText output=new EditText(this);
        output.setSingleLine(false);
        output.setMinLines(1);
        output.setMaxLines(3);
        output.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        output.setText(old.output.replace("\n","\\n"));
        output.setHint("例如：😂　（輸入 \\n 代表換行）");
        box.addView(output,new LinearLayout.LayoutParams(-1,dp(72)));

        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle(editing?"修改口述轉換":"新增口述轉換")
                .setView(box)
                .setNegativeButton("取消",null)
                .setPositiveButton("儲存",null)
                .create();
        dlg.setOnShowListener(x->dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            String sp=spoken.getText().toString().trim();
            String out=output.getText().toString().replace("\\n","\n");
            if(sp.isEmpty()){
                Toast.makeText(this,"請輸入口述內容",Toast.LENGTH_SHORT).show();
                return;
            }
            for(int i=0;i<rules.size();i++){
                if(i!=index&&sp.equals(rules.get(i).spoken)){
                    Toast.makeText(this,"這個口述名稱已經存在",Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            if(editing){
                VoiceRuleStore.Rule r=rules.get(index);
                r.spoken=sp; r.output=out;
            }else{
                rules.add(new VoiceRuleStore.Rule(sp,out,true));
            }
            saveAndRefresh();
            dlg.dismiss();
        }));
        dlg.show();
    }

    private void saveAndRefresh(){
        VoiceRuleStore.save(this,rules);
        refreshRows();
    }
}
''',encoding='utf-8')

# ---------------------------------------------------------------------------
# 3) Continuous speech session + automatic punctuation whitelist.
#    A pause finalizes one segment, commits it, then listening restarts.
#    Only a second mic tap (or leaving the input field) ends the whole session.
# ---------------------------------------------------------------------------
service_path=Path('imeapp/app/src/main/java/tw/ajo/ime/AjoImeService.java')
service=service_path.read_text(encoding='utf-8')

if 'import android.os.Handler;' not in service:
    service=service.replace('import android.os.Build;\n',
                            'import android.os.Build;\nimport android.os.Handler;\nimport android.os.Looper;\n',1)

old_fields = r'''    private SpeechRecognizer speechRecognizer;
    private boolean voiceListening=false;
    private boolean voiceUsingOnDevice=false;
    private boolean voiceFallbackTried=false;
    private String currentVoiceLocale="zh-TW";
'''
new_fields = r'''    private SpeechRecognizer speechRecognizer;
    private boolean voiceSessionActive=false;
    private boolean voiceUsingOnDevice=false;
    private boolean voiceFallbackTried=false;
    private int voiceRecognizerGeneration=0;
    private String currentVoiceLocale="zh-TW";
    private final Handler voiceHandler=new Handler(Looper.getMainLooper());
'''
if old_fields not in service:
    raise SystemExit('v0.9.9 patch failed: v0.9.8 voice fields not found')
service=service.replace(old_fields,new_fields,1)

# Leaving the editor must end a continuous microphone session.
old_finish = '''    @Override public void onFinishInput() {
        updateComposition("");
        super.onFinishInput();
    }
'''
new_finish = '''    @Override public void onFinishInput() {
        updateComposition("");
        stopVoiceSession();
        super.onFinishInput();
    }
'''
if old_finish not in service:
    raise SystemExit('v0.9.9 patch failed: onFinishInput block not found')
service=service.replace(old_finish,new_finish,1)

# onDestroy should use the common stop routine.
service=service.replace(
'''        setVoiceUi(false);
        if(speechRecognizer!=null){ try{ speechRecognizer.destroy(); }catch(Exception ignored){} speechRecognizer=null; }
''',
'''        stopVoiceSession();
''',1)

voice_block = r'''    private void setVoiceUi(boolean active){
        if(keyboard!=null) keyboard.post(()->keyboard.setVoiceListening(active));
    }

    public void toggleVoiceInput(String locale){
        if(!settings.getBoolean("voice_enabled",true)){
            Toast.makeText(this,"語音輸入目前在設定中關閉",Toast.LENGTH_SHORT).show();
            return;
        }
        if(voiceSessionActive){
            stopVoiceSession();
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
        voiceRecognizerGeneration++;
        if(speechRecognizer!=null){
            try{ speechRecognizer.cancel(); }catch(Exception ignored){}
            try{ speechRecognizer.destroy(); }catch(Exception ignored){}
            speechRecognizer=null;
        }
        setVoiceUi(false);
    }

    private void createVoiceRecognizer(boolean onDevice){
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
            private boolean valid(){ return generation==voiceRecognizerGeneration&&voiceSessionActive; }

            @Override public void onReadyForSpeech(Bundle params){ if(valid()) setVoiceUi(true); }
            @Override public void onBeginningOfSpeech(){ if(valid()) setVoiceUi(true); }
            @Override public void onRmsChanged(float rmsdB){}
            @Override public void onBufferReceived(byte[] buffer){}
            @Override public void onEndOfSpeech(){}

            @Override public void onError(int error){
                if(!valid()) return;
                if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){
                    Toast.makeText(AjoImeService.this,"需要麥克風權限才能使用語音輸入",Toast.LENGTH_SHORT).show();
                    stopVoiceSession();
                    return;
                }
                if(voiceUsingOnDevice&&!voiceFallbackTried){
                    voiceFallbackTried=true;
                    createVoiceRecognizer(false);
                    restartVoiceSoon(250);
                    return;
                }
                // A pause with no usable words, timeout, temporary busy state, or a
                // transient recognition error should not end continuous dictation.
                restartVoiceSoon(error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY?700:350);
            }

            @Override public void onResults(Bundle results){
                if(!valid()) return;
                ArrayList<String> list=results==null?null:results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(list!=null&&!list.isEmpty()){
                    String raw=list.get(0);
                    if(raw!=null&&!raw.trim().isEmpty()){
                        final String text=prepareVoiceText(raw,currentVoiceLocale.startsWith("zh"));
                        if(!text.isEmpty()){
                            commit(text);
                            if(keyboard!=null) keyboard.post(()->keyboard.onVoiceResult(text));
                        }
                    }
                }
                restartVoiceSoon(350);
            }

            @Override public void onPartialResults(Bundle partialResults){}
            @Override public void onEvent(int eventType,Bundle params){}
        });
    }

    private void restartVoiceSoon(long delayMs){
        if(!voiceSessionActive) return;
        voiceHandler.postDelayed(()->{
            if(voiceSessionActive) startListeningOnce();
        },delayMs);
    }

    private void startListeningOnce(){
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
            if(Build.VERSION.SDK_INT>=33){
                intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING,RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
            }
            if(voiceUsingOnDevice) intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
            setVoiceUi(true);
            speechRecognizer.startListening(intent);
        }catch(Exception e){
            if(voiceSessionActive) restartVoiceSoon(650);
        }
    }

    private String prepareVoiceText(String raw,boolean chinese){
        String base=chinese?filterChineseAutoPunctuation(raw):raw;
        return VoiceRuleStore.apply(this,base);
    }

    private boolean allowedChinesePunctuation(int cp){
        return cp=='，'||cp=='。'||cp=='、'||cp=='？'||cp=='！'||cp=='：'||cp=='；'||
                cp=='（'||cp=='）'||cp=='「'||cp=='」';
    }

    private boolean unicodePunctuation(int cp){
        int type=Character.getType(cp);
        return type==Character.CONNECTOR_PUNCTUATION||
                type==Character.DASH_PUNCTUATION||
                type==Character.START_PUNCTUATION||
                type==Character.END_PUNCTUATION||
                type==Character.INITIAL_QUOTE_PUNCTUATION||
                type==Character.FINAL_QUOTE_PUNCTUATION||
                type==Character.OTHER_PUNCTUATION;
    }

    private String filterChineseAutoPunctuation(String input){
        if(input==null||input.isEmpty()) return "";
        String s=input.replaceAll("\\.{2,}","……");
        StringBuilder out=new StringBuilder();
        for(int i=0;i<s.length();){
            int cp=s.codePointAt(i);
            int step=Character.charCount(cp);

            if(cp=='…'){
                out.append("……");
                i+=step;
                while(i<s.length()&&s.codePointAt(i)=='…') i+=Character.charCount(s.codePointAt(i));
                continue;
            }
            if(cp=='\\n'||cp=='\\r'){
                if(cp=='\\r'&&i+step<s.length()&&s.codePointAt(i+step)=='\\n') i+=step;
                out.append('\\n');
                i+=step;
                continue;
            }

            switch(cp){
                case ',': cp='，'; break;
                case '.': cp='。'; break;
                case '?': cp='？'; break;
                case '!': cp='！'; break;
                case ':': cp='：'; break;
                case ';': cp='；'; break;
                case '(': cp='（'; break;
                case ')': cp='）'; break;
                case '“': cp='「'; break;
                case '”': cp='」'; break;
                default: break;
            }

            if(allowedChinesePunctuation(cp)){
                out.appendCodePoint(cp);
            }else if(!unicodePunctuation(cp)){
                out.appendCodePoint(cp);
            }
            i+=step;
        }
        return out.toString();
    }

    // Compatibility for older experimental keyboard views still compiled in the app.
    public void voiceComingSoon(){ toggleVoiceInput("zh-TW"); }
'''
service,n=re.subn(
    r'    private void setVoiceUi\(boolean listening\)\{.*?    public void voiceComingSoon\(\)\{ toggleVoiceInput\("zh-TW"\); \}\n?',
    lambda match: voice_block,
    service,
    count=1,
    flags=re.S
)
if n!=1:
    raise SystemExit(f'v0.9.9 patch failed: voice block matched {n}')
service_path.write_text(service,encoding='utf-8')

# ---------------------------------------------------------------------------
# 4) Main settings page: entry point for voice custom conversions.
# ---------------------------------------------------------------------------
main_path=Path('imeapp/app/src/main/java/tw/ajo/ime/MainActivity.java')
m=main_path.read_text(encoding='utf-8')

voice_switch='        addSwitch(root, "語音輸入", "voice_enabled", true, true);\n'
if voice_switch not in m:
    raise SystemExit('v0.9.9 patch failed: voice switch not found')
voice_settings = voice_switch + '''        Button voiceRules = button("語音自訂轉換");
        voiceRules.setOnClickListener(v -> startActivity(new Intent(this, VoiceRulesActivity.class)));
        root.addView(voiceRules);

        TextView voiceHint = new TextView(this);
        voiceHint.setText("可自訂「口述內容 → 輸出內容」，用於標點、Emoji、顏文字、固定文字或常被辨錯的詞。");
        voiceHint.setTextSize(13);
        voiceHint.setTextColor(Color.GRAY);
        voiceHint.setPadding(0, dp(4), 0, dp(8));
        root.addView(voiceHint);
'''
m=m.replace(voice_switch,voice_settings,1)

m=re.sub(r'version\.setText\("[^"]*"\);',
         'version.setText("v0.9.9｜連續語音與自訂轉換版");',m,count=1)
m=re.sub(r'intro\.setText\("[^"]*"\);',
         'intro.setText("保留 v0.9.8 已穩定的候選、觸控與鍵盤操作。本版把語音改為連續聽寫：停頓只送出當前一段，之後會自動繼續收音，直到再次按麥克風才停止。中文語音可使用系統自動標點，但只保留指定的中文標點；新增「語音自訂轉換」，可自行建立口述文字到標點、Emoji、顏文字、固定文字或更正詞的轉換規則。");',
         m,count=1)
main_path.write_text(m,encoding='utf-8')

# ---------------------------------------------------------------------------
# 5) Manifest activities.
# ---------------------------------------------------------------------------
manifest_path=Path('imeapp/app/src/main/AndroidManifest.xml')
ms=manifest_path.read_text(encoding='utf-8')
anchor='        <activity\n            android:name=".MicPermissionActivity"\n'
if '.VoiceRulesActivity' not in ms:
    idx=ms.find(anchor)
    if idx<0:
        raise SystemExit('v0.9.9 patch failed: MicPermissionActivity manifest anchor missing')
    rules_manifest='''        <activity
            android:name=".VoiceRulesActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Material.Light.NoActionBar" />
'''
    ms=ms[:idx]+rules_manifest+ms[idx:]
manifest_path.write_text(ms,encoding='utf-8')

# ---------------------------------------------------------------------------
# 6) Version metadata.
# ---------------------------------------------------------------------------
gradle=Path('imeapp/app/build.gradle')
g=gradle.read_text(encoding='utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 19',g,count=1)
g=re.sub(r"versionName\s+'[^']+'","versionName '0.9.9'",g,count=1)
gradle.write_text(g,encoding='utf-8')

# Sanity checks.
assert "versionCode 19" in g and "versionName '0.9.9'" in g
assert 'voiceSessionActive' in service
assert 'restartVoiceSoon(350)' in service
assert 'EXTRA_ENABLE_FORMATTING' in service
assert 'filterChineseAutoPunctuation' in service
assert 'VoiceRuleStore.apply(this,base)' in service
assert '點點點' in rule_store.read_text(encoding='utf-8')
assert '較長的口述名稱會優先比對' in rules_activity.read_text(encoding='utf-8')
assert 'VoiceRulesActivity.class' in m
assert '.VoiceRulesActivity' in ms
print('v0.9.9 continuous dictation, punctuation whitelist and voice custom conversion applied')

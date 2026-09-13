package tw.ajo.ime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences settings;
    private SharedPreferences learning;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        settings = getSharedPreferences("ime_settings", MODE_PRIVATE);
        learning = getSharedPreferences("ime_learning", MODE_PRIVATE);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(34));
        root.setBackgroundColor(Color.rgb(247,247,249));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("阿喬輸入法");
        title.setTextSize(30);
        title.setTextColor(Color.BLACK);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView version = new TextView(this);
        version.setText("v0.8｜輸入細節與候選修正版");
        version.setTextSize(16);
        version.setTextColor(Color.DKGRAY);
        version.setPadding(0, dp(8), 0, dp(18));
        root.addView(version);

        TextView intro = new TextView(this);
        intro.setText("純倉頡三代。倉頡、English、注音三種主鍵盤共用同一套文字縮放比例；長按倉頡字根或注音符號可直接輸出鍵面文字。候選列加入常用句尾標點、完整碼優先與左右滑翻頁；空白鍵切換模式時不會打斷正在組字的內容。");
        intro.setTextSize(17);
        intro.setTextColor(Color.rgb(35,35,38));
        intro.setLineSpacing(0, 1.16f);
        root.addView(intro);

        Button enable = button("① 啟用阿喬輸入法");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);

        Button choose = button("② 選擇阿喬輸入法");
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(choose);

        section(root, "鍵盤設定");
        addSeek(root, "鍵盤高度", "調整整個鍵盤與格子的高度", "keyboard_height", 80, 110, 90, "%");
        addSeek(root, "主鍵盤文字大小", "倉頡字根、English、注音同步調整；按鍵外框大小不變", "key_text_size", 70, 100, 100, "%");
        addSeek(root, "候選字大小", "調整上方候選列的字體", "candidate_text_size", 85, 125, 100, "%");
        addSwitch(root, "按鍵音", "key_sound", true, true);
        addSwitch(root, "按鍵震動", "key_vibration", false, true);
        addSwitch(root, "個人常用字學習", "learning_enabled", true, true);
        addSwitch(root, "語音輸入（第二階段）", "voice_enabled", false, false);

        Button clear = button("清除個人學習紀錄");
        clear.setOnClickListener(v -> {
            learning.edit().clear().apply();
            Toast.makeText(this, "個人常用字紀錄已清除", Toast.LENGTH_SHORT).show();
        });
        root.addView(clear);

        TextView note = new TextView(this);
        note.setText("倉頡版本：第三代。\n空白鍵向左滑：倉頡 → English → 注音 → 倉頡；向右滑為反方向。正在組字時不切換。\n長按倉頡字根或注音符號：直接輸出鍵面文字，不進入組字。\n候選列可左右滑翻頁；句尾常用字後會提示合適的全形標點。\n數字頁輸入數字會留在 123；輸入標點後自動回主鍵盤。\n個人學習資料只保存在這支手機內。");
        note.setTextSize(15);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(26), 0, 0);
        note.setLineSpacing(0,1.15f);
        root.addView(note);

        setContentView(sv);
    }

    private void section(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(21);
        t.setTextColor(Color.BLACK);
        t.setTypeface(null, 1);
        t.setPadding(0, dp(30), 0, dp(8));
        root.addView(t);
    }

    private void addSeek(LinearLayout root, String title, String sub, String key, int min, int max, int def, String suffix) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(10), 0, dp(10));

        TextView label = new TextView(this);
        label.setTextSize(17);
        label.setTextColor(Color.BLACK);
        box.addView(label);

        TextView hint = new TextView(this);
        hint.setText(sub);
        hint.setTextSize(13);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, dp(2), 0, dp(3));
        box.addView(hint);

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        int stored = settings.getInt(key, def);
        int current = Math.max(min, Math.min(max, stored));
        if (current != stored) settings.edit().putInt(key, current).apply();
        seek.setProgress(current - min);
        label.setText(title + "  " + current + suffix);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int value = min + progress;
                label.setText(title + "  " + value + suffix);
                if (fromUser) settings.edit().putInt(key, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(seek);
        root.addView(box);
    }

    private void addSwitch(LinearLayout root, String title, String key, boolean def, boolean enabled) {
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextSize(17);
        sw.setTextColor(enabled ? Color.BLACK : Color.GRAY);
        sw.setPadding(0, dp(10), 0, dp(10));
        sw.setChecked(enabled && settings.getBoolean(key, def));
        sw.setEnabled(enabled);
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                settings.edit().putBoolean(key, isChecked).apply());
        root.addView(sw);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.topMargin = dp(14);
        b.setLayoutParams(lp);
        return b;
    }
}

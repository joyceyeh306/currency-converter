package tw.ajo.ime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(34), dp(24), dp(34));
        root.setBackgroundColor(Color.rgb(247,247,249));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("阿喬輸入法");
        title.setTextSize(30);
        title.setTextColor(Color.BLACK);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView version = new TextView(this);
        version.setText("v0.1｜iOS 版面與操作測試版");
        version.setTextSize(16);
        version.setTextColor(Color.DKGRAY);
        version.setPadding(0, dp(8), 0, dp(24));
        root.addView(version);

        TextView intro = new TextView(this);
        intro.setText("這一版先讓妳在 OPPO 上直接測試鍵盤比例、按鍵位置、倉頡／注音／英文切換、123／#+=、Emoji、顏文字，以及倉頡提前候選。\n\n第一次安裝後，請先啟用「阿喬輸入法」，再選成目前鍵盤。");
        intro.setTextSize(18);
        intro.setTextColor(Color.rgb(35,35,38));
        intro.setLineSpacing(0, 1.18f);
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

        TextView note = new TextView(this);
        note.setText("v0.1 已可實際當 Android 系統鍵盤使用。倉頡單字碼表會在建置時內建；注音完整詞庫、個人字頻、設定頁與語音辨識會在後續版本逐步補齊。\n\n左下地球：短按 倉頡 → 注音 → English；長按可直接選三種模式。\n數字頁：輸入數字會留在 123；輸入標點後自動回主鍵盤。");
        note.setTextSize(15);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(28), 0, 0);
        note.setLineSpacing(0,1.15f);
        root.addView(note);

        setContentView(sv);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.topMargin = dp(16);
        b.setLayoutParams(lp);
        return b;
    }
}

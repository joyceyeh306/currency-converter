package tw.ajo.whereami;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 100;
    private static final int REQ_NOTIFY = 101;
    private static final String VIEWER_BASE = "https://joyceyeh306.github.io/currency-converter/";

    private TextView statusText;
    private TextView lastText;
    private TextView codeText;
    private Button startStopButton;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String msg = intent.getStringExtra(LocationService.EXTRA_MESSAGE);
            if (!TextUtils.isEmpty(msg)) Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        View contentView = buildUi();
        setContentView(contentView);
        applySystemBarInsets(contentView);
        requestNotificationIfNeeded();
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter f = new IntentFilter(LocationService.ACTION_STATUS);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, f);
            receiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(receiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 250, 255));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("ㄚ喬在哪裡", 30, true, Color.rgb(24,48,78));
        root.addView(title);
        TextView sub = text("給人類看的私人定位", 16, false, Color.rgb(82,105,135));
        sub.setPadding(0, dp(3), 0, dp(18));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(18),dp(18),dp(18));
        android.graphics.drawable.GradientDrawable cbg = new android.graphics.drawable.GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(18));
        cbg.setStroke(dp(1), Color.rgb(220,232,245));
        card.setBackground(cbg);

        statusText = text("", 19, true, Color.rgb(28,58,92));
        lastText = text("", 15, false, Color.rgb(87,105,126));
        lastText.setPadding(0, dp(8), 0, 0);
        card.addView(statusText);
        card.addView(lastText);
        root.addView(card);

        TextView freq = text("位置更新頻率", 15, true, Color.rgb(54,74,98));
        freq.setPadding(0, dp(20), 0, dp(8));
        root.addView(freq);

        Spinner spinner = new Spinner(this);
        String[] opts = {"1 分鐘", "3 分鐘", "5 分鐘", "10 分鐘"};
        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, opts));
        int current = Prefs.intervalMin(this);
        spinner.setSelection(current == 1 ? 0 : current == 5 ? 2 : current == 10 ? 3 : 1);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                int[] values = {1,3,5,10};
                Prefs.setIntervalMin(MainActivity.this, values[pos]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        root.addView(spinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        startStopButton = primary("開始分享位置");
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        p1.topMargin = dp(18);
        root.addView(startStopButton, p1);
        startStopButton.setOnClickListener(v -> {
            if (Prefs.running(this)) stopSharing();
            else startSharingFlow();
        });

        Button copy = secondary("複製給人類的查看網址");
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p2.topMargin = dp(12);
        root.addView(copy, p2);
        copy.setOnClickListener(v -> copyViewerUrl());

        Button battery = secondary("允許 OPPO 背景持續定位");
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p3.topMargin = dp(10);
        root.addView(battery, p3);
        battery.setOnClickListener(v -> requestBatteryExemption());

        Button settings = secondary("開啟 App 系統設定");
        LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p4.topMargin = dp(10);
        root.addView(settings, p4);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));

        TextView label = text("私人代碼", 15, true, Color.rgb(54,74,98));
        label.setPadding(0, dp(22), 0, dp(6));
        root.addView(label);

        codeText = text(Prefs.topic(this), 12, false, Color.rgb(87,105,126));
        codeText.setTextIsSelectable(true);
        root.addView(codeText);

        Button regen = new Button(this);
        regen.setText("重新產生私人代碼");
        regen.setAllCaps(false);
        regen.setTextSize(13);
        regen.setTextColor(Color.rgb(160,58,58));
        regen.setBackgroundColor(Color.TRANSPARENT);
        root.addView(regen);
        regen.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("要換新的私人代碼嗎？")
                .setMessage("人類手機原本的查看網址會立刻失效，需要重新傳一次新網址。")
                .setNegativeButton("取消", null)
                .setPositiveButton("換新的", (d,w) -> {
                    if (Prefs.running(this)) stopSharing();
                    Prefs.regenerateTopic(this);
                    codeText.setText(Prefs.topic(this));
                    Toast.makeText(this, "已產生新的私人代碼", Toast.LENGTH_SHORT).show();
                }).show());

        TextView note = text("隱私說明：這一版只傳送最新位置、更新時間與手機電量，不建立完整歷史軌跡。定位資料會經由 ntfy.sh 暫存轉送；請不要把查看網址給其他人。", 13, false, Color.rgb(104,119,138));
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        return scroll;
    }

    private void applySystemBarInsets(View view) {
        final int baseLeft = view.getPaddingLeft();
        final int baseTop = view.getPaddingTop();
        final int baseRight = view.getPaddingRight();
        final int baseBottom = view.getPaddingBottom();

        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int left;
            int top;
            int right;
            int bottom;

            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }

            v.setPadding(
                    baseLeft + left,
                    baseTop + top,
                    baseRight + right,
                    baseBottom + bottom
            );
            return insets;
        });

        view.requestApplyInsets();
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button primary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(43,108,176));
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        return b;
    }

    private Button secondary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.rgb(43,108,176));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(182,207,232));
        b.setBackground(bg);
        return b;
    }

    private void startSharingFlow() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        startSharing();
    }

    private void startSharing() {
        try {
            Intent i = new Intent(this, LocationService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            Prefs.setRunning(this, true);
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "無法開始背景定位：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopSharing() {
        Intent i = new Intent(this, LocationService.class).setAction(LocationService.ACTION_STOP);
        try { startService(i); }
        catch (Exception e) { stopService(new Intent(this, LocationService.class)); }
        Prefs.setRunning(this, false);
        refresh();
    }

    private void requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_LOCATION) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startSharing();
            } else {
                Toast.makeText(this, "需要定位權限才能分享位置", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestBatteryExemption() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } else {
                Toast.makeText(this, "目前已允許背景執行", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void copyViewerUrl() {
        String url = VIEWER_BASE + "?code=" + Uri.encode(Prefs.topic(this)) + "&v=108";
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("ㄚ喬在哪裡", url));
        Toast.makeText(this, "查看網址已複製，傳給人類即可", Toast.LENGTH_LONG).show();
    }

    private void refresh() {
        boolean running = Prefs.running(this);
        statusText.setText(running ? "● 正在分享位置" : "○ 目前沒有分享位置");
        startStopButton.setText(running ? "停止分享位置" : "開始分享位置");

        long t = Prefs.lastUpload(this);
        if (t <= 0) {
            lastText.setText("尚未成功上傳位置");
        } else {
            String time = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.TAIWAN).format(new Date(t));
            String acc = Prefs.lastAccuracy(this) > 0 ? "　精度約 " + Math.round(Prefs.lastAccuracy(this)) + " m" : "";
            lastText.setText("最後更新：" + time + "\n" + Prefs.lastLat(this) + ", " + Prefs.lastLon(this) + acc);
        }
        if (codeText != null) codeText.setText(Prefs.topic(this));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

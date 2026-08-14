package com.joyce.currencyconverter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String PREFS = "fx_prefs";
    private static final String KEY_RATES = "rates_json";
    private static final String KEY_UPDATED = "updated_text";
    private static final String API = "https://open.er-api.com/v6/latest/USD";

    private final String[] codes = {"TWD", "USD", "EUR", "JPY", "KRW"};
    private final String[] spinnerItems = {
            "🇹🇼 台幣 TWD", "🇺🇸 美金 USD", "🇪🇺 歐元 EUR", "🇯🇵 日圓 JPY", "🇰🇷 韓元 KRW"
    };
    private final String[] names = {"台幣", "美金", "歐元", "日圓", "韓元"};
    private final String[] flags = {"🇹🇼", "🇺🇸", "🇪🇺", "🇯🇵", "🇰🇷"};

    private final Map<String, Double> rates = new LinkedHashMap<>();
    private Spinner fromSpinner;
    private EditText amountInput;
    private TextView statusText;
    private final Map<String, TextView> resultViews = new LinkedHashMap<>();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadRates();
        buildUi();
        renderResults();
    }

    private void loadRates() {
        String saved = prefs.getString(KEY_RATES, null);
        if (saved != null) {
            try {
                JSONObject obj = new JSONObject(saved);
                for (String code : codes) rates.put(code, obj.getDouble(code));
                return;
            } catch (Exception ignored) { }
        }

        // 內建參考匯率：USD = 1，資料基準為 2026-08-10。
        rates.put("USD", 1.0);
        rates.put("TWD", 32.180064);
        rates.put("EUR", 0.865507);
        rates.put("JPY", 157.880961);
        rates.put("KRW", 1409.640365);
    }

    private void buildUi() {
        int bg = Color.rgb(245, 246, 248);
        int text = Color.rgb(23, 25, 28);
        int muted = Color.rgb(107, 114, 128);
        int blue = Color.rgb(31, 111, 235);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("旅行匯率換算");
        title.setTextSize(28);
        title.setTextColor(text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("輸入一個幣別與金額，同時換算成台幣、美金、歐元、日圓、韓元。\n沒有網路也可以沿用最後一次儲存的匯率。");
        subtitle.setTextSize(14);
        subtitle.setTextColor(muted);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(Color.WHITE, 18));
        root.addView(card, lpMatchWrap(dp(14)));

        TextView fromLabel = label("輸入幣別", muted);
        card.addView(fromLabel);

        fromSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextSize(18);
                v.setTextColor(text);
                v.setPadding(dp(12), dp(12), dp(12), dp(12));
                return v;
            }
        };
        fromSpinner.setAdapter(adapter);
        fromSpinner.setBackground(roundStroke(Color.WHITE, Color.rgb(230,232,236), 14));
        card.addView(fromSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView amountLabel = label("金額", muted);
        amountLabel.setPadding(0, dp(14), 0, dp(8));
        card.addView(amountLabel);

        amountInput = new EditText(this);
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setSingleLine(true);
        amountInput.setText("1000");
        amountInput.setTextSize(28);
        amountInput.setTypeface(Typeface.DEFAULT_BOLD);
        amountInput.setTextColor(text);
        amountInput.setPadding(dp(14), 0, dp(14), 0);
        amountInput.setBackground(roundStroke(Color.WHITE, Color.rgb(230,232,236), 14));
        card.addView(amountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(14), 0, 0);
        card.addView(buttons);

        Button refresh = new Button(this);
        refresh.setText("更新匯率");
        refresh.setTextColor(Color.WHITE);
        refresh.setTypeface(Typeface.DEFAULT_BOLD);
        refresh.setBackground(roundRect(blue, 14));
        buttons.addView(refresh, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button clear = new Button(this);
        clear.setText("清除");
        clear.setTypeface(Typeface.DEFAULT_BOLD);
        clear.setBackground(roundRect(Color.rgb(238, 240, 243), 14));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, dp(52), 0.65f);
        clearLp.setMargins(dp(10), 0, 0, 0);
        buttons.addView(clear, clearLp);

        statusText = new TextView(this);
        statusText.setTextSize(13);
        statusText.setTextColor(muted);
        statusText.setPadding(0, dp(12), 0, 0);
        String updated = prefs.getString(KEY_UPDATED, null);
        if (updated != null) statusText.setText("目前使用已儲存匯率｜" + updated);
        else statusText.setText("目前使用內建參考匯率｜可按「更新匯率」取得較新資料");
        card.addView(statusText);

        for (int i = 0; i < codes.length; i++) {
            root.addView(makeResultRow(codes[i], names[i], flags[i], text, muted), lpMatchWrap(dp(10)));
        }

        TextView foot = new TextView(this);
        foot.setText("匯率僅供快速估算。刷卡、現鈔與銀行實際成交價可能不同。");
        foot.setTextSize(11);
        foot.setTextColor(muted);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(dp(10), dp(16), dp(10), 0);
        root.addView(foot);

        fromSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { renderResults(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderResults(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        clear.setOnClickListener(v -> {
            amountInput.setText("");
            amountInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(amountInput, InputMethodManager.SHOW_IMPLICIT);
        });

        refresh.setOnClickListener(v -> updateRates(refresh));

        setContentView(scroll);
    }

    private TextView makeResultRow(String code, String name, String flag, int textColor, int mutedColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(roundRect(Color.WHITE, 16));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.HORIZONTAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView flagView = new TextView(this);
        flagView.setText(flag);
        flagView.setTextSize(25);
        left.addView(flagView);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, 0, 0);
        left.addView(labels);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(15);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        nameView.setTextColor(textColor);
        labels.addView(nameView);

        TextView codeView = new TextView(this);
        codeView.setText(code);
        codeView.setTextSize(12);
        codeView.setTextColor(mutedColor);
        labels.addView(codeView);

        TextView value = new TextView(this);
        value.setText("—");
        value.setTextSize(21);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextColor(textColor);
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        resultViews.put(code, value);

        return row;
    }

    private void renderResults() {
        if (amountInput == null || fromSpinner == null || resultViews.isEmpty()) return;
        String raw = amountInput.getText().toString().trim();
        if (raw.isEmpty()) {
            for (TextView v : resultViews.values()) v.setText("—");
            return;
        }

        try {
            double amount = Double.parseDouble(raw);
            String from = codes[fromSpinner.getSelectedItemPosition()];
            double usd = amount / rates.get(from);
            for (String code : codes) {
                double converted = usd * rates.get(code);
                resultViews.get(code).setText(format(code, converted));
            }
        } catch (Exception e) {
            for (TextView v : resultViews.values()) v.setText("—");
        }
    }

    private String format(String code, double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.TAIWAN);
        int digits = (code.equals("USD") || code.equals("EUR")) ? 2 : 0;
        nf.setMinimumFractionDigits(digits);
        nf.setMaximumFractionDigits(digits);
        return nf.format(value);
    }

    private void updateRates(Button button) {
        button.setEnabled(false);
        button.setText("更新中…");
        statusText.setTextColor(Color.rgb(107,114,128));
        statusText.setText("正在連線取得匯率…");

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject root = new JSONObject(sb.toString());
                if (!"success".equals(root.getString("result"))) throw new Exception("API error");
                JSONObject apiRates = root.getJSONObject("rates");
                JSONObject save = new JSONObject();
                for (String code : codes) {
                    double value = apiRates.getDouble(code);
                    rates.put(code, value);
                    save.put(code, value);
                }
                String update = root.optString("time_last_update_utc", "已更新");
                prefs.edit().putString(KEY_RATES, save.toString()).putString(KEY_UPDATED, update).apply();

                runOnUiThread(() -> {
                    renderResults();
                    statusText.setTextColor(Color.rgb(19,138,75));
                    statusText.setText("匯率已更新｜" + update);
                    button.setEnabled(true);
                    button.setText("更新匯率");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setTextColor(Color.rgb(178,105,0));
                    statusText.setText("目前無法連線，繼續使用手機裡已儲存的匯率。");
                    button.setEnabled(true);
                    button.setText("更新匯率");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private TextView label(String text, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(14);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, 0, 0, dp(8));
        return v;
    }

    private LinearLayout.LayoutParams lpMatchWrap(int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, bottomMargin);
        return lp;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable roundStroke(int fill, int stroke, int radiusDp) {
        GradientDrawable d = roundRect(fill, radiusDp);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

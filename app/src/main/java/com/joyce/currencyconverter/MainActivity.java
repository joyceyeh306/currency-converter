package com.joyce.currencyconverter;

import android.app.Activity;
import android.content.Context;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

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
    private static final String KEY_RATES = "rates_json_v2";
    private static final String KEY_UPDATED = "updated_text_v2";
    private static final String API = "https://open.er-api.com/v6/latest/USD";

    private final String[] codes = {"TWD", "USD", "EUR", "JPY", "KRW", "GBP", "NOK", "CHF"};
    private final String[] names = {"台幣", "美金", "歐元", "日圓", "韓元", "英鎊", "挪威克朗", "瑞士法郎"};
    private final String[] flags = {"🇹🇼", "🇺🇸", "🇪🇺", "🇯🇵", "🇰🇷", "🇬🇧", "🇳🇴", "🇨🇭"};
    private final String[] spinnerItems = {
            "🇹🇼 台幣 TWD", "🇺🇸 美金 USD", "🇪🇺 歐元 EUR", "🇯🇵 日圓 JPY",
            "🇰🇷 韓元 KRW", "🇬🇧 英鎊 GBP", "🇳🇴 挪威克朗 NOK", "🇨🇭 瑞士法郎 CHF"
    };

    private final Map<String, Double> rates = new LinkedHashMap<>();
    private final Map<String, TextView> resultViews = new LinkedHashMap<>();
    private final Map<String, LinearLayout> resultCards = new LinkedHashMap<>();

    private Spinner fromSpinner;
    private EditText amountInput;
    private TextView statusText;
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

        // 內建離線參考匯率，USD = 1，基準 2026-08-10。
        rates.put("USD", 1.0);
        rates.put("TWD", 32.180064);
        rates.put("EUR", 0.865507);
        rates.put("JPY", 157.880961);
        rates.put("KRW", 1409.640365);
        rates.put("GBP", 0.741549);
        rates.put("NOK", 9.498365);
        rates.put("CHF", 0.808520);
    }

    private void buildUi() {
        final int bg = Color.rgb(245, 246, 248);
        final int text = Color.rgb(23, 25, 28);
        final int muted = Color.rgb(107, 114, 128);
        final int blue = Color.rgb(31, 111, 235);
        final int line = Color.rgb(230, 232, 236);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(14));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("旅行匯率換算");
        title.setTextSize(21);
        title.setTextColor(text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = lpMatchWrap(dp(7));
        root.addView(title, titleLp);

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        inputCard.setPadding(dp(10), dp(9), dp(10), dp(9));
        inputCard.setBackground(roundRect(Color.WHITE, 15));
        root.addView(inputCard, lpMatchWrap(dp(8)));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputCard.addView(inputRow, lpMatchWrap(dp(8)));

        LinearLayout currencyBox = new LinearLayout(this);
        currencyBox.setOrientation(LinearLayout.VERTICAL);
        inputRow.addView(currencyBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.18f));

        LinearLayout amountBox = new LinearLayout(this);
        amountBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams amountBoxLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.82f);
        amountBoxLp.setMargins(dp(8), 0, 0, 0);
        inputRow.addView(amountBox, amountBoxLp);

        currencyBox.addView(label("幣別", muted));
        fromSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, spinnerItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextSize(14);
                v.setTextColor(text);
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setPadding(dp(8), 0, dp(8), 0);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextSize(16);
                v.setPadding(dp(12), dp(12), dp(12), dp(12));
                return v;
            }
        };
        fromSpinner.setAdapter(adapter);
        fromSpinner.setBackground(roundStroke(Color.WHITE, line, 12));
        currencyBox.addView(fromSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        amountBox.addView(label("金額", muted));
        amountInput = new EditText(this);
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setSingleLine(true);
        amountInput.setText("1000");
        amountInput.setTextSize(22);
        amountInput.setTypeface(Typeface.DEFAULT_BOLD);
        amountInput.setTextColor(text);
        amountInput.setGravity(Gravity.CENTER_VERTICAL);
        amountInput.setPadding(dp(10), 0, dp(8), 0);
        amountInput.setBackground(roundStroke(Color.WHITE, line, 12));
        amountBox.addView(amountInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        inputCard.addView(actionRow, lpMatchWrap(0));

        Button refresh = new Button(this);
        refresh.setText("更新匯率");
        refresh.setTextSize(14);
        refresh.setTextColor(Color.WHITE);
        refresh.setTypeface(Typeface.DEFAULT_BOLD);
        refresh.setMinHeight(0);
        refresh.setMinimumHeight(0);
        refresh.setPadding(0, 0, 0, 0);
        refresh.setBackground(roundRect(blue, 12));
        actionRow.addView(refresh, new LinearLayout.LayoutParams(0, dp(40), 1f));

        Button clear = new Button(this);
        clear.setText("清除");
        clear.setTextSize(14);
        clear.setTypeface(Typeface.DEFAULT_BOLD);
        clear.setMinHeight(0);
        clear.setMinimumHeight(0);
        clear.setPadding(0, 0, 0, 0);
        clear.setBackground(roundRect(Color.rgb(238, 240, 243), 12));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, dp(40), 0.58f);
        clearLp.setMargins(dp(7), 0, 0, 0);
        actionRow.addView(clear, clearLp);

        statusText = new TextView(this);
        statusText.setTextSize(10.5f);
        statusText.setTextColor(muted);
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        statusText.setSingleLine(true);
        String updated = prefs.getString(KEY_UPDATED, null);
        statusText.setText(updated == null ? "離線可用｜按更新匯率同步" : "已儲存｜" + shortUpdate(updated));
        LinearLayout.LayoutParams statusLp = lpMatchWrap(0);
        statusLp.topMargin = dp(5);
        inputCard.addView(statusText, statusLp);

        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout pair = new LinearLayout(this);
            pair.setOrientation(LinearLayout.HORIZONTAL);
            int first = rowIndex * 2;
            int second = first + 1;

            LinearLayout leftCard = makeCurrencyCard(
                    codes[first], names[first], flags[first], text, muted);
            pair.addView(leftCard, new LinearLayout.LayoutParams(0, dp(76), 1f));

            LinearLayout rightCard = makeCurrencyCard(
                    codes[second], names[second], flags[second], text, muted);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, dp(76), 1f);
            rightLp.setMargins(dp(7), 0, 0, 0);
            pair.addView(rightCard, rightLp);

            root.addView(pair, lpMatchWrap(rowIndex == 3 ? 0 : dp(7)));
        }

        fromSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                renderResults();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderResults();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        clear.setOnClickListener(v -> {
            amountInput.setText("");
            amountInput.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(amountInput, InputMethodManager.SHOW_IMPLICIT);
        });

        refresh.setOnClickListener(v -> updateRates(refresh));
        setContentView(scroll);
    }

    private LinearLayout makeCurrencyCard(String code, String name, String flag,
                                          int textColor, int mutedColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(7), dp(11), dp(7));
        card.setBackground(roundRect(Color.WHITE, 14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView flagView = new TextView(this);
        flagView.setText(flag);
        flagView.setTextSize(18);
        top.addView(flagView);

        TextView nameView = new TextView(this);
        nameView.setText(name + "  " + code);
        nameView.setTextSize(name.length() > 3 ? 11.5f : 12.5f);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        nameView.setTextColor(mutedColor);
        nameView.setSingleLine(true);
        nameView.setPadding(dp(6), 0, 0, 0);
        top.addView(nameView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setText("—");
        value.setTextSize(20);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextColor(textColor);
        value.setGravity(Gravity.END);
        value.setSingleLine(true);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueLp.topMargin = dp(2);
        card.addView(value, valueLp);

        resultViews.put(code, value);
        resultCards.put(code, card);
        return card;
    }

    private void renderResults() {
        if (amountInput == null || fromSpinner == null || resultViews.isEmpty()) return;

        String selected = codes[fromSpinner.getSelectedItemPosition()];
        for (String code : codes) {
            LinearLayout card = resultCards.get(code);
            if (card == null) continue;
            if (code.equals(selected)) {
                card.setBackground(roundStroke(
                        Color.rgb(234, 242, 255), Color.rgb(156, 196, 255), 14));
            } else {
                card.setBackground(roundRect(Color.WHITE, 14));
            }
        }

        String raw = amountInput.getText().toString().trim();
        if (raw.isEmpty()) {
            for (TextView v : resultViews.values()) v.setText("—");
            return;
        }

        try {
            double amount = Double.parseDouble(raw);
            double usd = amount / rates.get(selected);
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
        int digits = (code.equals("TWD") || code.equals("JPY") || code.equals("KRW")) ? 0 : 2;
        nf.setMinimumFractionDigits(digits);
        nf.setMaximumFractionDigits(digits);
        return nf.format(value);
    }

    private void updateRates(Button button) {
        button.setEnabled(false);
        button.setText("更新中…");
        statusText.setTextColor(Color.rgb(107, 114, 128));
        statusText.setText("正在更新匯率…");

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject root = new JSONObject(sb.toString());
                if (!"success".equals(root.getString("result"))) {
                    throw new Exception("API error");
                }

                JSONObject apiRates = root.getJSONObject("rates");
                JSONObject save = new JSONObject();
                for (String code : codes) {
                    double value = apiRates.getDouble(code);
                    rates.put(code, value);
                    save.put(code, value);
                }

                String update = root.optString("time_last_update_utc", "已更新");
                prefs.edit()
                        .putString(KEY_RATES, save.toString())
                        .putString(KEY_UPDATED, update)
                        .apply();

                runOnUiThread(() -> {
                    renderResults();
                    statusText.setTextColor(Color.rgb(19, 138, 75));
                    statusText.setText("已更新｜" + shortUpdate(update));
                    button.setEnabled(true);
                    button.setText("更新匯率");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setTextColor(Color.rgb(178, 105, 0));
                    statusText.setText("更新失敗｜沿用已儲存匯率");
                    button.setEnabled(true);
                    button.setText("更新匯率");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private String shortUpdate(String text) {
        if (text == null) return "";
        return text.replace(" +0000", "").replace(" 00:", " ");
    }

    private TextView label(String text, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(11.5f);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, 0, 0, dp(4));
        return v;
    }

    private LinearLayout.LayoutParams lpMatchWrap(int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

package com.joyce.currencyconverter;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String PREFS = "fx_prefs";
    private static final String KEY_RATES = "rates_json_v3";
    private static final String KEY_UPDATED = "updated_text_v3";
    private static final String KEY_SELECTED = "selected_currencies_v1";
    private static final String KEY_FROM = "last_from_currency_v1";
    private static final String KEY_AMOUNT = "last_amount_v1";
    private static final String API = "https://open.er-api.com/v6/latest/USD";
    private static final int MAX_HOME_CURRENCIES = 10;

    private static final int BG = Color.rgb(245, 246, 248);
    private static final int TEXT = Color.rgb(23, 25, 28);
    private static final int MUTED = Color.rgb(107, 114, 128);
    private static final int BLUE = Color.rgb(31, 111, 235);
    private static final int LINE = Color.rgb(230, 232, 236);

    private final String[] allCodes = {
            "TWD", "USD", "EUR", "JPY", "KRW", "GBP", "CHF", "NOK",
            "CNY", "HKD", "SGD", "AUD", "NZD", "CAD", "THB", "MYR",
            "IDR", "PHP", "VND", "INR", "AED", "SAR", "TRY", "SEK",
            "DKK", "PLN", "CZK", "HUF", "MXN", "ZAR"
    };

    private final String[] allNames = {
            "台幣", "美金", "歐元", "日圓", "韓元", "英鎊", "瑞士法郎", "挪威克朗",
            "人民幣", "港幣", "新加坡幣", "澳幣", "紐西蘭幣", "加拿大幣", "泰銖", "馬來西亞令吉",
            "印尼盾", "菲律賓披索", "越南盾", "印度盧比", "阿聯酋迪拉姆", "沙烏地里亞里", "土耳其里拉", "瑞典克朗",
            "丹麥克朗", "波蘭茲羅提", "捷克克朗", "匈牙利福林", "墨西哥披索", "南非蘭特"
    };

    private final String[] allFlags = {
            "🇹🇼", "🇺🇸", "🇪🇺", "🇯🇵", "🇰🇷", "🇬🇧", "🇨🇭", "🇳🇴",
            "🇨🇳", "🇭🇰", "🇸🇬", "🇦🇺", "🇳🇿", "🇨🇦", "🇹🇭", "🇲🇾",
            "🇮🇩", "🇵🇭", "🇻🇳", "🇮🇳", "🇦🇪", "🇸🇦", "🇹🇷", "🇸🇪",
            "🇩🇰", "🇵🇱", "🇨🇿", "🇭🇺", "🇲🇽", "🇿🇦"
    };

    private final Map<String, Double> rates = new LinkedHashMap<>();
    private final Map<String, TextView> resultViews = new LinkedHashMap<>();
    private final Map<String, LinearLayout> resultCards = new LinkedHashMap<>();
    private final List<String> selectedOrder = new ArrayList<>();
    private final List<String> spinnerCodes = new ArrayList<>();

    private Spinner fromSpinner;
    private EditText amountInput;
    private TextView statusText;
    private TextView settingsCountText;
    private LinearLayout cardsContainer;
    private SharedPreferences prefs;
    private boolean showingSettings = false;
    private boolean dragOrderChanged = false;
    private String currentFromCode = "TWD";
    private String currentAmountText = "1000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureSystemBars();
        loadSelection();
        loadRates();
        currentFromCode = prefs.getString(KEY_FROM, "TWD");
        currentAmountText = prefs.getString(KEY_AMOUNT, "1000");
        if (!isSupported(currentFromCode)) currentFromCode = "TWD";
        buildHomeUi();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void applySystemInsets(View root, int baseLeft, int baseTop, int baseRight, int baseBottom) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                topInset = bars.top;
                bottomInset = bars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(baseLeft, baseTop + topInset, baseRight, baseBottom + bottomInset);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void loadSelection() {
        selectedOrder.clear();
        String saved = prefs.getString(KEY_SELECTED, null);
        if (saved != null && !saved.trim().isEmpty()) {
            for (String code : saved.split(",")) {
                if (isSupported(code) && !selectedOrder.contains(code)) selectedOrder.add(code);
                if (selectedOrder.size() == MAX_HOME_CURRENCIES) break;
            }
        }
        if (selectedOrder.isEmpty()) {
            selectedOrder.addAll(Arrays.asList("TWD", "USD", "EUR", "JPY", "KRW", "GBP", "NOK", "CHF"));
            saveSelection();
        }
    }

    private void saveSelection() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedOrder.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(selectedOrder.get(i));
        }
        prefs.edit().putString(KEY_SELECTED, sb.toString()).apply();
    }

    private void loadRates() {
        String saved = prefs.getString(KEY_RATES, null);
        if (saved != null) {
            try {
                JSONObject obj = new JSONObject(saved);
                boolean complete = true;
                for (String code : allCodes) {
                    if (!obj.has(code)) {
                        complete = false;
                        break;
                    }
                }
                if (complete) {
                    for (String code : allCodes) rates.put(code, obj.getDouble(code));
                    return;
                }
            } catch (Exception ignored) { }
        }

        putRate("USD", 1.0);
        putRate("TWD", 32.180064);
        putRate("EUR", 0.865507);
        putRate("JPY", 157.880961);
        putRate("KRW", 1409.640365);
        putRate("GBP", 0.741549);
        putRate("CHF", 0.808520);
        putRate("NOK", 9.498365);
        putRate("CNY", 6.752312);
        putRate("HKD", 7.845552);
        putRate("SGD", 1.279137);
        putRate("AUD", 1.416444);
        putRate("NZD", 1.698185);
        putRate("CAD", 1.395428);
        putRate("THB", 33.024294);
        putRate("MYR", 4.091060);
        putRate("IDR", 17862.192486);
        putRate("PHP", 60.834378);
        putRate("VND", 26127.558406);
        putRate("INR", 95.253496);
        putRate("AED", 3.672500);
        putRate("SAR", 3.750000);
        putRate("TRY", 47.705820);
        putRate("SEK", 9.481468);
        putRate("DKK", 6.457903);
        putRate("PLN", 3.720938);
        putRate("CZK", 20.995446);
        putRate("HUF", 314.849876);
        putRate("MXN", 17.140913);
        putRate("ZAR", 16.164503);
    }

    private void putRate(String code, double value) {
        rates.put(code, value);
    }

    private void buildHomeUi() {
        showingSettings = false;
        resultViews.clear();
        resultCards.clear();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applySystemInsets(root, dp(12), dp(8), dp(12), dp(14));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleRow, lpMatchWrap(dp(7)));

        TextView title = new TextView(this);
        title.setText("旅行匯率");
        title.setTextSize(21);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView settings = new TextView(this);
        settings.setText("⚙ 設定");
        settings.setTextSize(13);
        settings.setTextColor(MUTED);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(10), dp(6), dp(10), dp(6));
        settings.setBackground(roundRect(Color.WHITE, 12));
        titleRow.addView(settings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        settings.setOnClickListener(v -> {
            captureCurrentInput();
            buildSettingsUi();
        });

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

        currencyBox.addView(label("幣別", MUTED));
        fromSpinner = new Spinner(this);
        fromSpinner.setBackground(roundStroke(Color.WHITE, LINE, 12));
        currencyBox.addView(fromSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        amountBox.addView(label("金額", MUTED));
        amountInput = new EditText(this);
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setSingleLine(true);
        amountInput.setText(currentAmountText);
        amountInput.setTextSize(22);
        amountInput.setTypeface(Typeface.DEFAULT_BOLD);
        amountInput.setTextColor(TEXT);
        amountInput.setGravity(Gravity.CENTER_VERTICAL);
        amountInput.setPadding(dp(10), 0, dp(8), 0);
        amountInput.setBackground(roundStroke(Color.WHITE, LINE, 12));
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
        refresh.setBackground(roundRect(BLUE, 12));
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
        statusText.setTextColor(MUTED);
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        statusText.setSingleLine(true);
        String updated = prefs.getString(KEY_UPDATED, null);
        statusText.setText(updated == null ? "離線可用｜按更新匯率同步" : "已儲存｜" + shortUpdate(updated));
        LinearLayout.LayoutParams statusLp = lpMatchWrap(0);
        statusLp.topMargin = dp(5);
        inputCard.addView(statusText, statusLp);

        cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(cardsContainer, lpMatchWrap(0));

        refreshSpinnerAdapterPreservingSelection();
        rebuildCurrencyCards();

        fromSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < spinnerCodes.size()) {
                    currentFromCode = spinnerCodes.get(position);
                    prefs.edit().putString(KEY_FROM, currentFromCode).apply();
                }
                renderResults();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentAmountText = s.toString();
                prefs.edit().putString(KEY_AMOUNT, currentAmountText).apply();
                renderResults();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        clear.setOnClickListener(v -> {
            amountInput.setText("");
            amountInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(amountInput, InputMethodManager.SHOW_IMPLICIT);
        });

        refresh.setOnClickListener(v -> updateRates(refresh));
        setContentView(scroll);
        renderResults();
    }

    private void buildSettingsUi() {
        showingSettings = true;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applySystemInsets(root, dp(12), dp(8), dp(12), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleRow, lpMatchWrap(dp(8)));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setTextColor(TEXT);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(2), 0, dp(10), 0);
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        back.setOnClickListener(v -> buildHomeUi());

        TextView title = new TextView(this);
        title.setText("設定");
        title.setTextSize(20);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        settingsCountText = new TextView(this);
        settingsCountText.setTextSize(12.5f);
        settingsCountText.setTextColor(MUTED);
        settingsCountText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        titleRow.addView(settingsCountText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateSettingsCount();

        TextView hint = new TextView(this);
        hint.setText("勾選要顯示在首頁的幣別，最多 10 種。首頁可長按卡片拖曳排序。");
        hint.setTextSize(12.5f);
        hint.setTextColor(MUTED);
        hint.setPadding(dp(4), 0, dp(4), dp(8));
        root.addView(hint);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(roundRect(Color.WHITE, 15));
        root.addView(list, lpMatchWrap(0));

        for (int i = 0; i < allCodes.length; i++) {
            final String code = allCodes[i];
            final String display = allFlags[i] + "  " + allNames[i] + "  " + code;

            CheckBox cb = new CheckBox(this);
            cb.setText(display);
            cb.setTextSize(14.5f);
            cb.setTextColor(TEXT);
            cb.setGravity(Gravity.CENTER_VERTICAL);
            cb.setPadding(dp(10), 0, dp(8), 0);
            cb.setChecked(selectedOrder.contains(code));
            list.addView(cb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

            if (i < allCodes.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(239, 240, 243));
                LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dividerLp.setMargins(dp(46), 0, dp(10), 0);
                list.addView(divider, dividerLp);
            }

            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (selectedOrder.contains(code)) return;
                    if (selectedOrder.size() >= MAX_HOME_CURRENCIES) {
                        Toast.makeText(this, "首頁最多顯示 10 種幣別", Toast.LENGTH_SHORT).show();
                        buttonView.setChecked(false);
                        return;
                    }
                    selectedOrder.add(code);
                } else {
                    if (!selectedOrder.contains(code)) return;
                    if (selectedOrder.size() <= 1) {
                        Toast.makeText(this, "首頁至少要保留 1 種幣別", Toast.LENGTH_SHORT).show();
                        buttonView.setChecked(true);
                        return;
                    }
                    selectedOrder.remove(code);
                }
                saveSelection();
                updateSettingsCount();
            });
        }

        setContentView(scroll);
    }

    private void updateSettingsCount() {
        if (settingsCountText != null) {
            settingsCountText.setText("首頁幣別  " + selectedOrder.size() + " / " + MAX_HOME_CURRENCIES);
        }
    }

    private void captureCurrentInput() {
        if (amountInput != null) currentAmountText = amountInput.getText().toString();
        if (fromSpinner != null && fromSpinner.getSelectedItemPosition() >= 0 &&
                fromSpinner.getSelectedItemPosition() < spinnerCodes.size()) {
            currentFromCode = spinnerCodes.get(fromSpinner.getSelectedItemPosition());
        }
    }

    private void refreshSpinnerAdapterPreservingSelection() {
        spinnerCodes.clear();
        spinnerCodes.addAll(selectedOrder);
        for (String code : allCodes) {
            if (!spinnerCodes.contains(code)) spinnerCodes.add(code);
        }

        List<String> items = new ArrayList<>();
        for (String code : spinnerCodes) items.add(displayName(code));

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextSize(14);
                v.setTextColor(TEXT);
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setPadding(dp(8), 0, dp(8), 0);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextSize(15);
                v.setPadding(dp(12), dp(10), dp(12), dp(10));
                return v;
            }
        };
        fromSpinner.setAdapter(adapter);

        int pos = spinnerCodes.indexOf(currentFromCode);
        if (pos < 0) pos = 0;
        fromSpinner.setSelection(pos, false);
    }

    private void rebuildCurrencyCards() {
        if (cardsContainer == null) return;
        cardsContainer.removeAllViews();
        resultViews.clear();
        resultCards.clear();

        int rowCount = (selectedOrder.size() + 1) / 2;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            LinearLayout pair = new LinearLayout(this);
            pair.setOrientation(LinearLayout.HORIZONTAL);

            int first = rowIndex * 2;
            LinearLayout leftCard = makeCurrencyCard(selectedOrder.get(first));
            pair.addView(leftCard, new LinearLayout.LayoutParams(0, dp(76), 1f));

            int second = first + 1;
            if (second < selectedOrder.size()) {
                LinearLayout rightCard = makeCurrencyCard(selectedOrder.get(second));
                LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, dp(76), 1f);
                rightLp.setMargins(dp(7), 0, 0, 0);
                pair.addView(rightCard, rightLp);
            } else {
                View spacer = new View(this);
                LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, dp(76), 1f);
                spacerLp.setMargins(dp(7), 0, 0, 0);
                pair.addView(spacer, spacerLp);
            }

            cardsContainer.addView(pair, lpMatchWrap(rowIndex == rowCount - 1 ? 0 : dp(7)));
        }
        renderResults();
    }

    private LinearLayout makeCurrencyCard(String code) {
        int index = indexOfCode(code);
        String name = allNames[index];
        String flag = allFlags[index];

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(7), dp(11), dp(7));
        card.setBackground(roundRect(Color.WHITE, 14));
        card.setTag(code);

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
        nameView.setTextSize(name.length() > 4 ? 10.5f : (name.length() > 3 ? 11.5f : 12.5f));
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        nameView.setTextColor(MUTED);
        nameView.setSingleLine(true);
        nameView.setPadding(dp(6), 0, 0, 0);
        top.addView(nameView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setText("—");
        value.setTextSize(20);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextColor(TEXT);
        value.setGravity(Gravity.END);
        value.setSingleLine(true);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueLp.topMargin = dp(2);
        card.addView(value, valueLp);

        resultViews.put(code, value);
        resultCards.put(code, card);

        card.setOnLongClickListener(v -> {
            String draggedCode = (String) v.getTag();
            ClipData data = ClipData.newPlainText("currency", draggedCode);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            v.setAlpha(0.55f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(data, shadow, draggedCode, 0);
            } else {
                v.startDrag(data, shadow, draggedCode, 0);
            }
            return true;
        });

        card.setOnDragListener((v, event) -> handleCardDrag(v, event));
        return card;
    }

    private boolean handleCardDrag(View targetView, DragEvent event) {
        Object local = event.getLocalState();
        if (!(local instanceof String)) return false;
        String dragged = (String) local;
        String target = (String) targetView.getTag();

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return selectedOrder.contains(dragged);

            case DragEvent.ACTION_DRAG_ENTERED:
                targetView.animate().scaleX(1.035f).scaleY(1.035f).setDuration(80).start();
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                targetView.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                return true;

            case DragEvent.ACTION_DROP:
                targetView.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                if (!dragged.equals(target)) {
                    int from = selectedOrder.indexOf(dragged);
                    int targetIndex = selectedOrder.indexOf(target);
                    if (from >= 0 && targetIndex >= 0) {
                        selectedOrder.remove(from);
                        int adjustedTarget = selectedOrder.indexOf(target);
                        boolean placeAfter = event.getX() > targetView.getWidth() * 0.58f;
                        int insertAt = adjustedTarget + (placeAfter ? 1 : 0);
                        if (insertAt < 0) insertAt = 0;
                        if (insertAt > selectedOrder.size()) insertAt = selectedOrder.size();
                        selectedOrder.add(insertAt, dragged);
                        saveSelection();
                        dragOrderChanged = true;
                    }
                }
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                for (LinearLayout c : resultCards.values()) {
                    c.setAlpha(1f);
                    c.setScaleX(1f);
                    c.setScaleY(1f);
                }
                if (dragOrderChanged) {
                    dragOrderChanged = false;
                    refreshSpinnerAdapterPreservingSelection();
                    rebuildCurrencyCards();
                }
                return true;

            default:
                return true;
        }
    }

    private void renderResults() {
        if (amountInput == null || fromSpinner == null || resultViews.isEmpty() || spinnerCodes.isEmpty()) return;

        int selectedPos = fromSpinner.getSelectedItemPosition();
        if (selectedPos < 0 || selectedPos >= spinnerCodes.size()) return;
        String selected = spinnerCodes.get(selectedPos);
        currentFromCode = selected;

        for (String code : selectedOrder) {
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
            Double selectedRate = rates.get(selected);
            if (selectedRate == null || selectedRate == 0) throw new Exception("missing rate");
            double usd = amount / selectedRate;
            for (String code : selectedOrder) {
                Double rate = rates.get(code);
                TextView view = resultViews.get(code);
                if (rate != null && view != null) view.setText(format(code, usd * rate));
            }
        } catch (Exception e) {
            for (TextView v : resultViews.values()) v.setText("—");
        }
    }

    private String format(String code, double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.TAIWAN);
        Set<String> zeroDecimals = new LinkedHashSet<>(Arrays.asList(
                "TWD", "JPY", "KRW", "IDR", "VND", "HUF"));
        int digits = zeroDecimals.contains(code) ? 0 : 2;
        nf.setMinimumFractionDigits(digits);
        nf.setMaximumFractionDigits(digits);
        return nf.format(value);
    }

    private void updateRates(Button button) {
        button.setEnabled(false);
        button.setText("更新中…");
        statusText.setTextColor(MUTED);
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
                if (!"success".equals(root.getString("result"))) throw new Exception("API error");

                JSONObject apiRates = root.getJSONObject("rates");
                JSONObject save = new JSONObject();
                for (String code : allCodes) {
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

    private String shortUpdate(String update) {
        if (update == null) return "";
        int comma = update.indexOf(',');
        if (comma >= 0 && comma + 1 < update.length()) {
            String s = update.substring(comma + 1).trim();
            int plus = s.indexOf(" +");
            if (plus > 0) s = s.substring(0, plus);
            return s;
        }
        return update;
    }

    private TextView label(String text, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(12.5f);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, 0, 0, dp(5));
        return v;
    }

    private String displayName(String code) {
        int i = indexOfCode(code);
        if (i < 0) return code;
        return allFlags[i] + "  " + allNames[i] + "  " + code;
    }

    private int indexOfCode(String code) {
        for (int i = 0; i < allCodes.length; i++) {
            if (allCodes[i].equals(code)) return i;
        }
        return -1;
    }

    private boolean isSupported(String code) {
        return indexOfCode(code) >= 0;
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

    @Override
    public void onBackPressed() {
        if (showingSettings) {
            buildHomeUi();
        } else {
            super.onBackPressed();
        }
    }
}

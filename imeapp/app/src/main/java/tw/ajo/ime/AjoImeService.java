package tw.ajo.ime;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

public class AjoImeService extends InputMethodService {
    private PreciseKeyboardView keyboard;
    private PopupWindow compositionPopup;
    private TextView compositionText;
    private String pendingComposition = "";
    private SharedPreferences settings;

    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (sp, key) -> {
        if (keyboard == null) return;
        if ("keyboard_height".equals(key)) {
            keyboard.post(this::rebuildKeyboardForHeight);
        } else if ("key_text_size".equals(key)
                || "candidate_text_size".equals(key)) {
            keyboard.post(keyboard::refreshSettings);
        } else {
            keyboard.post(keyboard::invalidate);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        settings = getSharedPreferences("ime_settings", MODE_PRIVATE);
        settings.registerOnSharedPreferenceChangeListener(settingsListener);
    }

    @Override public View onCreateInputView() {
        keyboard = new PreciseKeyboardView(this, this);
        return keyboard;
    }

    private void rebuildKeyboardForHeight() {
        updateComposition("");
        PreciseKeyboardView fresh = new PreciseKeyboardView(this, this);
        EditorInfo info = getCurrentInputEditorInfo();
        if (info != null) fresh.onEditorChanged(info);
        keyboard = fresh;
        setInputView(fresh);
        fresh.requestLayout();
    }

    @Override public View onCreateCandidatesView() {
        return null;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        updateComposition("");
        if (keyboard != null) {
            keyboard.onEditorChanged(attribute);
            keyboard.refreshSettings();
        }
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard != null) keyboard.refreshSettings();
    }

    @Override public void onFinishInput() {
        updateComposition("");
        super.onFinishInput();
    }

    @Override public void onDestroy() {
        dismissCompositionPopup();
        if (settings != null) settings.unregisterOnSharedPreferenceChangeListener(settingsListener);
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void ensureCompositionPopup() {
        if (compositionPopup != null) return;

        compositionText = new TextView(this);
        compositionText.setTextColor(Color.WHITE);
        compositionText.setTextSize(17);
        compositionText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        compositionText.setSingleLine(true);
        compositionText.setPadding(dp(12), 0, dp(12), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(220, 43, 43, 49));
        bg.setCornerRadius(dp(10));
        compositionText.setBackground(bg);

        compositionPopup = new PopupWindow(compositionText, dp(150), dp(38), false);
        compositionPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        compositionPopup.setTouchable(false);
        compositionPopup.setOutsideTouchable(false);
        compositionPopup.setClippingEnabled(false);
        compositionPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        compositionPopup.setElevation(dp(4));
    }

    private void showCompositionPopup() {
        if (keyboard == null || pendingComposition.isEmpty()) return;
        keyboard.post(() -> {
            if (keyboard == null || keyboard.getWindowToken() == null || pendingComposition.isEmpty()) return;
            ensureCompositionPopup();
            compositionText.setText(pendingComposition);
            if (!compositionPopup.isShowing()) {
                compositionPopup.showAtLocation(
                        keyboard,
                        Gravity.BOTTOM | Gravity.START,
                        dp(18),
                        keyboard.getHeight() + dp(7));
            }
        });
    }

    private void dismissCompositionPopup() {
        if (compositionPopup != null && compositionPopup.isShowing()) compositionPopup.dismiss();
    }

    public void repositionCompositionPopup() {
        if (compositionPopup != null && compositionPopup.isShowing()) {
            compositionPopup.dismiss();
            showCompositionPopup();
        }
    }

    public void updateComposition(String s) {
        pendingComposition = s == null ? "" : s;
        if (pendingComposition.isEmpty()) {
            dismissCompositionPopup();
        } else {
            if (compositionText != null) compositionText.setText(pendingComposition);
            showCompositionPopup();
        }
    }

    public void openSettings() {
        updateComposition("");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    public void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before != null && before.length() > 0) ic.deleteSurroundingText(1, 0);
        else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        }
    }

    public void enter() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        if (ic == null) return;
        int action = info == null ? EditorInfo.IME_ACTION_NONE : (info.imeOptions & EditorInfo.IME_MASK_ACTION);
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
    }

    public void voiceComingSoon() {
        Toast.makeText(this, "語音輸入會在第二階段加入", Toast.LENGTH_SHORT).show();
    }
}

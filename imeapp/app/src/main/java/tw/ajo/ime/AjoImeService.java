package tw.ajo.ime;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.Toast;

public class AjoImeService extends InputMethodService {
    private PreciseKeyboardView keyboard;
    private CompositionCandidatesView compositionView;
    private LinearLayout inputRoot;
    private String pendingComposition = "";

    @Override public View onCreateInputView() {
        inputRoot = new LinearLayout(this);
        inputRoot.setOrientation(LinearLayout.VERTICAL);

        // Put the composition bubble in the IME input view itself instead of the
        // system candidates area. Some OPPO/ColorOS versions do not reliably
        // show or re-measure InputMethodService's candidates view.
        compositionView = new CompositionCandidatesView(this);
        compositionView.setComposition(pendingComposition);
        inputRoot.addView(compositionView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        keyboard = new PreciseKeyboardView(this, this);
        inputRoot.addView(keyboard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return inputRoot;
    }

    @Override public View onCreateCandidatesView() {
        // The keyboard already draws its own candidate row. Keeping this null
        // prevents ColorOS from reserving an extra system candidates frame.
        return null;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        updateComposition("");
        if (keyboard != null) keyboard.onEditorChanged(attribute);
    }

    @Override public void onFinishInput() {
        updateComposition("");
        super.onFinishInput();
    }

    public void updateComposition(String s) {
        pendingComposition = s == null ? "" : s;
        if (compositionView != null) compositionView.setComposition(pendingComposition);
        if (inputRoot != null) inputRoot.requestLayout();
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

package tw.ajo.ime;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

public class AjoImeService extends InputMethodService {
    private IosKeyboardView keyboard;
    private CompositionCandidatesView compositionView;
    private String pendingComposition = "";

    @Override public View onCreateInputView() {
        keyboard = new FloatingKeyboardView(this, this);
        return keyboard;
    }

    @Override public View onCreateCandidatesView() {
        compositionView = new CompositionCandidatesView(this);
        compositionView.setComposition(pendingComposition);
        return compositionView;
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
        setCandidatesViewShown(!pendingComposition.isEmpty());
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
        else ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
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

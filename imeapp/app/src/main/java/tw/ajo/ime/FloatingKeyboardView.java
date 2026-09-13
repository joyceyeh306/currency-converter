package tw.ajo.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * v0.3 presentation layer.
 * Keeps the existing IME engine/interaction logic, but redraws the candidate
 * row at full width and shows the current Cangjie/Zhuyin composition as a
 * small translucent, touch-through floating bubble at the upper-left of the
 * keyboard body.
 */
public class FloatingKeyboardView extends IosKeyboardView {
    private static final int BG = Color.rgb(218, 219, 224);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences settings;

    private Field fPage;
    private Field fMode;
    private Field fCjCode;
    private Field fZyCode;
    private Field fExpanded;
    private Method mCands;
    private Method mChoose;
    private Method mFeedback;

    public FloatingKeyboardView(Context context, AjoImeService service) {
        super(context, service);
        settings = context.getSharedPreferences("ime_settings", Context.MODE_PRIVATE);
        bindReflection();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private float candidateScale() {
        return settings.getInt("candidate_text_size", 100) / 100f;
    }

    private void bindReflection() {
        try {
            Class<?> c = IosKeyboardView.class;
            fPage = c.getDeclaredField("page");
            fMode = c.getDeclaredField("mode");
            fCjCode = c.getDeclaredField("cjCode");
            fZyCode = c.getDeclaredField("zyCode");
            fExpanded = c.getDeclaredField("expanded");
            mCands = c.getDeclaredMethod("cands");
            mChoose = c.getDeclaredMethod("choose", String.class);
            mFeedback = c.getDeclaredMethod("feedback");
            fPage.setAccessible(true);
            fMode.setAccessible(true);
            fCjCode.setAccessible(true);
            fZyCode.setAccessible(true);
            fExpanded.setAccessible(true);
            mCands.setAccessible(true);
            mChoose.setAccessible(true);
            mFeedback.setAccessible(true);
        } catch (Exception ignored) {
        }
    }

    private String enumName(Field f) {
        try {
            Object v = f == null ? null : f.get(this);
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            return "";
        }
    }

    private String stringField(Field f) {
        try {
            Object v = f == null ? null : f.get(this);
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean expanded() {
        try {
            return fExpanded != null && fExpanded.getBoolean(this);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> candidates() {
        try {
            if (mCands == null) return Collections.emptyList();
            Object v = mCands.invoke(this);
            return v instanceof List ? (List<String>) v : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void choose(String s) {
        try {
            if (mChoose != null) mChoose.invoke(this, s);
        } catch (Exception ignored) {
        }
    }

    private void feedback() {
        try {
            if (mFeedback != null) mFeedback.invoke(this);
        } catch (Exception ignored) {
        }
    }

    private void setExpanded(boolean value) {
        try {
            if (fExpanded != null) fExpanded.setBoolean(this, value);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        String page = enumName(fPage);
        if ("EMOJI".equals(page) || "KAOMOJI".equals(page)) return;

        int h = dp(46);
        boolean exp = expanded();
        redrawCandidateBar(canvas, h, exp);
        if (!exp) drawCompositionBubble(canvas, h);
    }

    private void redrawCandidateBar(Canvas c, int h, boolean exp) {
        int totalH = exp ? h * 3 : h;
        p.setColor(exp ? Color.rgb(232, 233, 237) : BG);
        c.drawRect(0, 0, getWidth(), totalH, p);

        List<String> items = candidates();
        float arrowW = dp(46);
        int cols = 7;
        float cw = (getWidth() - arrowW) / cols;
        int n = Math.min(exp ? cols * 3 : cols, items.size());

        t.setColor(Color.BLACK);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(dp(22 * candidateScale()));
        for (int i = 0; i < n; i++) {
            int row = i / cols;
            int col = i % cols;
            c.drawText(items.get(i), col * cw + cw / 2f, row * h + h * 0.67f, t);
        }

        t.setColor(Color.GRAY);
        t.setTextSize(dp(21));
        c.drawText(exp ? "⌃" : "⌄", getWidth() - arrowW / 2f, h * 0.68f, t);

        if (exp) {
            p.setColor(Color.rgb(205, 206, 211));
            c.drawRect(0, h, getWidth(), h + 1, p);
            c.drawRect(0, h * 2, getWidth(), h * 2 + 1, p);
        }
    }

    private void drawCompositionBubble(Canvas c, int h) {
        if (!"MAIN".equals(enumName(fPage))) return;

        String mode = enumName(fMode);
        String comp = "";
        if ("CANGJIE".equals(mode)) {
            String code = stringField(fCjCode);
            if (!code.isEmpty()) comp = roots(code);
        } else if ("ZHUYIN".equals(mode)) {
            comp = stringField(fZyCode);
        }
        if (comp.isEmpty()) return;

        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(dp(17 * candidateScale()));
        float textW = t.measureText(comp);
        float left = dp(7);
        float top = h + dp(7);
        float right = Math.min(getWidth() - dp(7), left + textW + dp(20));
        float bottom = top + dp(33);

        p.setColor(Color.argb(145, 40, 40, 45));
        c.drawRoundRect(new RectF(left, top, right, bottom), dp(10), dp(10), p);

        t.setColor(Color.WHITE);
        Paint.FontMetrics fm = t.getFontMetrics();
        float baseline = (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(comp, left + dp(10), baseline, t);
    }

    private String roots(String code) {
        String letters = "abcdefghijklmnopqrstuvwxyz";
        String roots = "日月金木水火土竹戈十大中一弓人心手口尸廿山女田難卜符";
        StringBuilder b = new StringBuilder();
        for (char ch : code.toCharArray()) {
            int i = letters.indexOf(ch);
            if (i >= 0) b.append(roots.charAt(i));
        }
        return b.toString();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int h = dp(46);
        int rows = expanded() ? 3 : 1;

        // Let the base view receive ACTION_DOWN so long-press timing remains intact.
        if (e.getAction() == MotionEvent.ACTION_DOWN) return super.onTouchEvent(e);

        if (e.getAction() == MotionEvent.ACTION_UP && e.getY() < h * rows
                && !"EMOJI".equals(enumName(fPage)) && !"KAOMOJI".equals(enumName(fPage))) {
            feedback();
            float arrowW = dp(46);
            if (e.getX() > getWidth() - arrowW) {
                setExpanded(!expanded());
                invalidate();
                return true;
            }

            int cols = 7;
            float cw = (getWidth() - arrowW) / cols;
            int row = (int) (e.getY() / h);
            int col = (int) (e.getX() / cw);
            int idx = row * cols + col;
            List<String> items = candidates();
            if (idx >= 0 && idx < items.size()) choose(items.get(idx));
            return true;
        }

        return super.onTouchEvent(e);
    }
}

package tw.ajo.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Presentation/fix layer for the experimental Ajo IME.
 *
 * v0.4 changes:
 * - candidate row always keeps its full width
 * - composition text is sent to InputMethodService and displayed above the keyboard
 * - Zhuyin rows are redrawn with non-overlapping vertical geometry
 * - a full Zhuyin single-character table is loaded from assets
 * - keyboard height is migrated to about 90% of the previous installed value once
 */
public class FloatingKeyboardView extends IosKeyboardView {
    private static final float IW = 1170f;
    private static final float IH = 842f;
    private static final int BG = Color.rgb(218, 219, 224);

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences settings;
    private final AjoImeService service;

    private Field fPage;
    private Field fMode;
    private Field fCjCode;
    private Field fZyCode;
    private Field fExpanded;
    private Field fModeMenu;
    private Field fZyMap;
    private Method mCands;
    private Method mChoose;
    private Method mFeedback;
    private Method mAddZ;
    private Method mBack;
    private Method mBottomTap;

    private String lastCompositionSent = "";

    private static final String[] Z1 = {"ㄅ","ㄉ","ˇ","ˋ","ㄓ","ˊ","˙","ㄚ","ㄞ","ㄢ","ㄦ"};
    private static final String[] Z2 = {"ㄆ","ㄊ","ㄍ","ㄐ","ㄔ","ㄗ","ㄧ","ㄛ","ㄟ","ㄣ"};
    private static final String[] Z3 = {"ㄇ","ㄋ","ㄎ","ㄑ","ㄕ","ㄘ","ㄨ","ㄜ","ㄠ","ㄤ"};
    private static final String[] Z4 = {"ㄈ","ㄌ","ㄏ","ㄒ","ㄖ","ㄙ","ㄩ","ㄝ","ㄡ","ㄥ"};

    public FloatingKeyboardView(Context context, AjoImeService service) {
        super(context, service);
        this.service = service;
        settings = context.getSharedPreferences("ime_settings", Context.MODE_PRIVATE);
        migrateHeightOnce();
        bindReflection();
        loadFullZhuyin();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private float sx(float x) {
        return x / IW * getWidth();
    }

    private float sy(float y, int h) {
        return h + y / IH * (getHeight() - h);
    }

    private float candidateScale() {
        return settings.getInt("candidate_text_size", 100) / 100f;
    }

    private float keyScale() {
        return settings.getInt("key_text_size", 100) / 100f;
    }

    private void migrateHeightOnce() {
        if (settings.getBoolean("v04_height_migrated", false)) return;
        int current = settings.getInt("keyboard_height", 90);
        int target = Math.max(80, Math.round(current * 0.90f));
        settings.edit()
                .putInt("keyboard_height", target)
                .putBoolean("v04_height_migrated", true)
                .apply();
    }

    private void bindReflection() {
        try {
            Class<?> c = IosKeyboardView.class;
            fPage = c.getDeclaredField("page");
            fMode = c.getDeclaredField("mode");
            fCjCode = c.getDeclaredField("cjCode");
            fZyCode = c.getDeclaredField("zyCode");
            fExpanded = c.getDeclaredField("expanded");
            fModeMenu = c.getDeclaredField("modeMenu");
            fZyMap = c.getDeclaredField("zy");
            mCands = c.getDeclaredMethod("cands");
            mChoose = c.getDeclaredMethod("choose", String.class);
            mFeedback = c.getDeclaredMethod("feedback");
            mAddZ = c.getDeclaredMethod("addZ", String.class);
            mBack = c.getDeclaredMethod("back");
            mBottomTap = c.getDeclaredMethod("bottomTap", float.class, float.class);

            for (Field f : Arrays.asList(fPage, fMode, fCjCode, fZyCode, fExpanded, fModeMenu, fZyMap)) {
                f.setAccessible(true);
            }
            for (Method m : Arrays.asList(mCands, mChoose, mFeedback, mAddZ, mBack, mBottomTap)) {
                m.setAccessible(true);
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFullZhuyin() {
        if (fZyMap == null) return;
        try {
            Object o = fZyMap.get(this);
            if (!(o instanceof Map)) return;
            Map<String, List<String>> map = (Map<String, List<String>>) o;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    getContext().getAssets().open("zhuyin.tsv"), StandardCharsets.UTF_8))) {
                map.clear();
                String line;
                while ((line = br.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab <= 0 || tab >= line.length() - 1) continue;
                    String code = line.substring(0, tab);
                    String values = line.substring(tab + 1).trim();
                    if (!values.isEmpty()) map.put(code, Arrays.asList(values.split(" +")));
                }
            }
        } catch (Exception ignored) {
            // Keep the small built-in fallback table from the base class.
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

    private boolean boolField(Field f) {
        try {
            return f != null && f.getBoolean(this);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean expanded() {
        return boolField(fExpanded);
    }

    private boolean modeMenu() {
        return boolField(fModeMenu);
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

    private void addZ(String s) {
        try {
            if (mAddZ != null) mAddZ.invoke(this, s);
        } catch (Exception ignored) {
        }
    }

    private void back() {
        try {
            if (mBack != null) mBack.invoke(this);
        } catch (Exception ignored) {
        }
    }

    private void bottomTap(float x, float y) {
        try {
            if (mBottomTap != null) mBottomTap.invoke(this, x, y);
        } catch (Exception ignored) {
        }
    }

    private String composition() {
        if (!"MAIN".equals(enumName(fPage))) return "";
        String mode = enumName(fMode);
        if ("CANGJIE".equals(mode)) {
            String code = stringField(fCjCode);
            return code.isEmpty() ? "" : roots(code);
        }
        if ("ZHUYIN".equals(mode)) return stringField(fZyCode);
        return "";
    }

    private void sendComposition() {
        String comp = composition();
        if (comp.equals(lastCompositionSent)) return;
        lastCompositionSent = comp;
        service.updateComposition(comp);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        String page = enumName(fPage);
        if ("EMOJI".equals(page) || "KAOMOJI".equals(page)) {
            if (!lastCompositionSent.isEmpty()) {
                lastCompositionSent = "";
                service.updateComposition("");
            }
            return;
        }

        int h = dp(46);
        boolean exp = expanded();

        if ("MAIN".equals(page) && "ZHUYIN".equals(enumName(fMode)) && !modeMenu()) {
            redrawZhuyinBody(canvas, h);
        }

        redrawCandidateBar(canvas, h, exp);
        sendComposition();
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

    private void redrawZhuyinBody(Canvas c, int h) {
        p.setColor(BG);
        c.drawRect(0, h, getWidth(), getHeight(), p);

        for (int i = 0; i < 11; i++) {
            float x = 18 + i * 103.3f;
            drawKey(c, h, x, 0, x + 88, 100, Z1[i], 25);
        }
        for (int i = 0; i < 10; i++) {
            float x = 55 + i * 105.5f;
            drawKey(c, h, x, 115, x + 88, 215, Z2[i], 25);
        }
        for (int i = 0; i < 10; i++) {
            float x = 82 + i * 101.5f;
            drawKey(c, h, x, 230, x + 88, 330, Z3[i], 25);
        }
        for (int i = 0; i < 10; i++) {
            float x = 18 + i * 103.4f;
            drawKey(c, h, x, 345, x + 88, 445, Z4[i], 24);
        }
        drawKey(c, h, 1060, 345, 1150, 445, "⌫", 22);

        drawKey(c, h, 18, 466, 145, 584, "123", 22);
        drawKey(c, h, 160, 466, 287, 584, "☺", 23);
        drawKey(c, h, 303, 466, 864, 584, "", 22);
        drawKey(c, h, 880, 466, 1150, 584, "↩", 25);

        t.setColor(Color.rgb(190, 190, 194));
        t.setTextSize(dp(12));
        t.setTextAlign(Paint.Align.RIGHT);
        c.drawText("注", sx(842), sy(565, h), t);

        drawFooter(c, h);
    }

    private void drawKey(Canvas c, int h, float x1, float y1, float x2, float y2, String s, float size) {
        RectF r = new RectF(sx(x1), sy(y1, h), sx(x2), sy(y2, h));
        p.setColor(Color.WHITE);
        c.drawRoundRect(r, dp(8), dp(8), p);
        t.setColor(Color.BLACK);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(dp(size * keyScale()));
        Paint.FontMetrics fm = t.getFontMetrics();
        c.drawText(s, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, t);
    }

    private void drawFooter(Canvas c, int h) {
        t.setColor(Color.BLACK);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(dp(31));
        c.drawText("◎", sx(92), sy(720, h), t);

        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2.2f));
        float x = sx(970), y = sy(692, h);
        c.drawRoundRect(new RectF(x - dp(5.5f), y - dp(15), x + dp(5.5f), y + dp(8)), dp(5.5f), dp(5.5f), p);
        c.drawArc(new RectF(x - dp(12), y - dp(2), x + dp(12), y + dp(20)), 0, 180, false, p);
        c.drawLine(x, y + dp(18), x, y + dp(28), p);
        p.setStyle(Paint.Style.FILL);
    }

    private boolean inside(float x, float y, float x1, float y1, float x2, float y2) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }

    private int hitRow(float x, float y, float y1, float y2, int count, float start, float step, float width) {
        if (y < y1 || y > y2) return -1;
        for (int i = 0; i < count; i++) {
            float left = start + i * step;
            if (x >= left && x <= left + width) return i;
        }
        return -1;
    }

    private boolean handleZhuyinMainUp(float x, float y) {
        int i = hitRow(x, y, 0, 100, 11, 18, 103.3f, 88);
        if (i >= 0) { addZ(Z1[i]); return true; }

        i = hitRow(x, y, 115, 215, 10, 55, 105.5f, 88);
        if (i >= 0) { addZ(Z2[i]); return true; }

        i = hitRow(x, y, 230, 330, 10, 82, 101.5f, 88);
        if (i >= 0) { addZ(Z3[i]); return true; }

        if (inside(x, y, 1060, 345, 1150, 445)) { back(); return true; }
        i = hitRow(x, y, 345, 445, 10, 18, 103.4f, 88);
        if (i >= 0) { addZ(Z4[i]); return true; }

        if (y >= 466 && y <= 584) {
            bottomTap(x, 520);
            return true;
        }

        return y < 600;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int h = dp(46);
        int candidateRows = expanded() ? 3 : 1;

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(e);
        }

        if (e.getAction() == MotionEvent.ACTION_UP && e.getY() < h * candidateRows
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

        if (e.getAction() == MotionEvent.ACTION_UP
                && "MAIN".equals(enumName(fPage))
                && "ZHUYIN".equals(enumName(fMode))
                && !modeMenu()
                && e.getY() >= h) {
            float x = e.getX() / getWidth() * IW;
            float y = (e.getY() - h) / (getHeight() - h) * IH;
            if (y < 600) {
                feedback();
                return handleZhuyinMainUp(x, y);
            }
        }

        return super.onTouchEvent(e);
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
}

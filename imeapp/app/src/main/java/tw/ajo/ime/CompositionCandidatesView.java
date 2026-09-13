package tw.ajo.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewParent;

/** Floating composition bubble above the keyboard, without occupying the candidate row. */
public class CompositionCandidatesView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text = "";

    public CompositionCandidatesView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    public void setComposition(String value) {
        String next = value == null ? "" : value;
        if (next.equals(text)) return;
        text = next;
        invalidate();
        requestLayout();
        ViewParent parent = getParent();
        if (parent != null) parent.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        // Zero height while idle; when typing it grows upward above the keyboard.
        setMeasuredDimension(w, text.isEmpty() ? 0 : dp(38));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (text.isEmpty()) return;

        t.setTextSize(dp(17));
        t.setTextAlign(Paint.Align.LEFT);
        t.setColor(Color.WHITE);
        float tw = t.measureText(text);

        float left = dp(18);
        float top = dp(3);
        float right = Math.min(getWidth() - dp(12), left + tw + dp(24));
        float bottom = dp(35);

        p.setColor(Color.argb(165, 42, 42, 48));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), dp(10), dp(10), p);

        Paint.FontMetrics fm = t.getFontMetrics();
        float baseline = (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, left + dp(12), baseline, t);
    }
}

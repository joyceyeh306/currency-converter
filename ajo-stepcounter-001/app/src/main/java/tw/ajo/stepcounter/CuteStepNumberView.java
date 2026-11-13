package tw.ajo.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Original chunky, hand-drawn numerals made from paths. No system font fallback:
 * these always look playful on ColorOS, regardless of installed fonts.
 * The value is exposed to accessibility services as real text.
 */
public final class CuteStepNumberView extends View {
    private final Paint outer=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String digits="0";

    public CuteStepNumberView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        outer.setStyle(Paint.Style.STROKE);
        outer.setStrokeWidth(20);
        outer.setStrokeCap(Paint.Cap.ROUND);
        outer.setStrokeJoin(Paint.Join.ROUND);
        outer.setColor(Color.rgb(89,45,77));
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(11);
        inner.setStrokeCap(Paint.Cap.ROUND);
        inner.setStrokeJoin(Paint.Join.ROUND);
        inner.setColor(Color.rgb(255,249,242));
        dots.setStyle(Paint.Style.FILL);
        dots.setColor(Color.rgb(255,249,242));
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    public void setSteps(long value) {
        digits=NumberFormat.getIntegerInstance(Locale.TAIWAN).format(Math.max(0,value));
        setContentDescription("今日步數 "+digits+" 步");
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(getWidth()<=0||getHeight()<=0)return;
        float total=0;
        for(int i=0;i<digits.length();i++)total+=digits.charAt(i)==','?24:69;
        float scale=Math.min((getWidth()-20f)/total,(getHeight()-12f)/112f);
        if(scale<=0)return;
        float used=total*scale;
        canvas.save();
        canvas.translate((getWidth()-used)/2f,(getHeight()-112f*scale)/2f);
        canvas.scale(scale,scale);
        float x=0;
        for(int i=0;i<digits.length();i++) {
            char c=digits.charAt(i);
            if(c==',') {
                dots.setColor(Color.rgb(89,45,77));
                canvas.drawCircle(x+10,88,6,dots);
                dots.setColor(Color.rgb(255,249,242));
                canvas.drawCircle(x+9,86,2.4f,dots);
                x+=24;
            } else {
                canvas.save();
                canvas.translate(x,0);
                Path p=pathFor(c);
                canvas.drawPath(p,outer);
                canvas.drawPath(p,inner);
                canvas.restore();
                x+=69;
            }
        }
        canvas.restore();
    }
    private static Path pathFor(char c) {
        Path p=new Path();
        switch(c) {
            case '0':
                p.moveTo(35,16);
                p.cubicTo(14,14,11,31,11,54);
                p.cubicTo(11,82,18,97,35,96);
                p.cubicTo(55,95,59,78,58,54);
                p.cubicTo(57,31,50,16,35,16);
                p.close();break;
            case '1':
                p.moveTo(15,37);
                p.quadTo(27,27,37,18);
                p.quadTo(39,56,39,95);
                p.moveTo(21,95);
                p.quadTo(38,94,56,95);
                break;
            case '2':
                p.moveTo(13,37);
                p.cubicTo(13,19,25,13,39,16);
                p.cubicTo(58,19,62,39,46,54);
                p.quadTo(30,72,14,92);
                p.quadTo(35,97,58,92);
                break;
            case '3':
                p.moveTo(16,23);
                p.cubicTo(45,7,60,23,54,43);
                p.quadTo(48,53,34,54);
                p.cubicTo(56,51,63,68,54,85);
                p.cubicTo(45,102,25,98,14,87);
                break;
            case '4':
                p.moveTo(48,18);
                p.quadTo(28,46,12,68);
                p.quadTo(34,70,58,68);
                p.moveTo(48,18);
                p.quadTo(46,56,47,96);
                break;
            case '5':
                p.moveTo(54,18);
                p.quadTo(36,20,18,18);
                p.quadTo(16,38,15,51);
                p.cubicTo(50,38,61,55,56,77);
                p.cubicTo(53,98,27,102,13,89);
                break;
            case '6':
                p.moveTo(49,20);
                p.cubicTo(21,17,12,46,13,70);
                p.cubicTo(12,88,21,98,36,96);
                p.cubicTo(55,94,61,76,52,64);
                p.cubicTo(42,51,27,55,15,68);
                break;
            case '7':
                p.moveTo(12,21);
                p.quadTo(34,18,57,21);
                p.cubicTo(49,40,36,62,28,94);
                break;
            case '8':
                p.moveTo(34,54);
                p.cubicTo(11,43,15,15,35,16);
                p.cubicTo(56,15,61,39,34,54);
                p.cubicTo(7,66,12,97,34,97);
                p.cubicTo(59,97,65,65,34,54);
                break;
            case '9':
                p.moveTo(52,45);
                p.cubicTo(45,58,24,58,17,46);
                p.cubicTo(6,28,20,12,37,16);
                p.cubicTo(58,19,60,43,56,65);
                p.quadTo(53,89,24,96);
                break;
            default:break;
        }
        return p;
    }
}

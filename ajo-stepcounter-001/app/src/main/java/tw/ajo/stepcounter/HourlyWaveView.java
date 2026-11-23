package tw.ajo.stepcounter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Actual STEP_DETECTOR counts (24 hourly bins). Smooth paths are a drawing
 * interpolation, not invented intermediary measurements.
 */
public final class HourlyWaveView extends View {
    private final long[] values=new long[24];
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint point=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float scaled;
    public HourlyWaveView(Context context) {
        super(context);
        density=getResources().getDisplayMetrics().density;
        scaled=getResources().getDisplayMetrics().scaledDensity;
        line.setColor(Color.rgb(121,69,197));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeWidth(dp(3.5f));
        grid.setColor(Color.rgb(225,213,237));
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(0.9f));
        grid.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(4)},0));
        ink.setColor(Color.rgb(105,90,119));
        ink.setTextSize(sp(11));
        point.setColor(Color.rgb(225,76,143));
        point.setStyle(Paint.Style.FILL);
        setContentDescription("24 小時步數波型圖，詳細數字列於圖表下方");
    }
    private float dp(float v) { return v*density; }
    private float sp(float v) { return v*scaled; }
    public void setHourly(long[] data) {
        if(data!=null)System.arraycopy(data,0,values,0,Math.min(24,data.length));
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width=getWidth(),height=getHeight();
        float left=dp(46),right=width-dp(12),top=dp(20),bottom=height-dp(35);
        if(right-left<dp(120)||bottom-top<dp(75))return;
        long max=0;
        for(long n:values) max=Math.max(max,n);
        long ceiling;
        if(max<=20) ceiling=20;
        else if(max<=100) ceiling=((max+19)/20)*20;
        else ceiling=((max+99)/100)*100;
        float chartWidth=right-left,chartHeight=bottom-top;
        for(int tick=0;tick<=4;tick++) {
            float y=bottom-chartHeight*tick/4f;
            canvas.drawLine(left,y,right,y,grid);
            ink.setTextAlign(Paint.Align.RIGHT);
            String label=NumberFormat.getIntegerInstance(Locale.TAIWAN)
                    .format(ceiling*tick/4);
            canvas.drawText(label,left-dp(8),y+dp(4),ink);
        }
        ink.setTextAlign(Paint.Align.CENTER);
        for(int hour:new int[]{0,6,12,18,23}) {
            float x=left+chartWidth*hour/23f;
            canvas.drawText(String.format(Locale.TAIWAN,"%02d",hour)+"時",
                    x,bottom+dp(24),ink);
        }
        Path curve=new Path();
        float[] ys=new float[24];
        float[] xs=new float[24];
        for(int i=0;i<24;i++) {
            xs[i]=left+chartWidth*i/23f;
            ys[i]=bottom-chartHeight*Math.min((float)values[i]/ceiling,1f);
        }
        curve.moveTo(xs[0],ys[0]);
        // Midpoint quadratic smoothing does not create new step-count data.
        for(int i=1;i<24;i++) {
            float midX=(xs[i-1]+xs[i])/2f;
            float midY=(ys[i-1]+ys[i])/2f;
            curve.quadTo(xs[i-1],ys[i-1],midX,midY);
        }
        curve.lineTo(xs[23],ys[23]);
        Path area=new Path(curve);
        area.lineTo(xs[23],bottom);
        area.lineTo(xs[0],bottom);
        area.close();
        fill.setShader(new LinearGradient(0,top,0,bottom,
                new int[]{0x66B589F1,0x18C6A8F6},null,Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipRect(left,top,right,bottom);
        canvas.drawPath(area,fill);
        canvas.drawPath(curve,line);
        for(int i=0;i<24;i++) {
            if(values[i]>0)canvas.drawCircle(xs[i],ys[i],dp(2.7f),point);
        }
        canvas.restore();
        fill.setShader(null);
    }
}

package tw.ajo.stepcounter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** No background polling: receives sensor updates and date broadcasts. */
public final class StepWidget extends AppWidgetProvider {
    public static void updateAll(Context c) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        int[] ids=manager.getAppWidgetIds(new ComponentName(c,StepWidget.class));
        if(ids!=null && ids.length>0) update(c,manager,ids);
    }
    private static void update(Context c,AppWidgetManager manager,int[] ids) {
        long steps=0,at=0;
        try(StepStore s=new StepStore(c)) {
            steps=s.day(LocalDate.now());at=s.lastSample();
        }
        String number=NumberFormat.getIntegerInstance(Locale.TAIWAN).format(steps)+" 步";
        String sub=!StepService.enabled(c)?"計步已暫停":at<=0?"等待第一次感測":"截至 "+new SimpleDateFormat("HH:mm",Locale.TAIWAN).format(new Date(at));
        Intent open=new Intent(c,MainActivity.class);
        PendingIntent tap=PendingIntent.getActivity(c,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        for(int id:ids) {
            RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.step_widget);
            view.setTextViewText(R.id.widget_count,number);
            view.setTextViewText(R.id.widget_subtitle,sub);
            view.setOnClickPendingIntent(R.id.widget_root,tap);
            manager.updateAppWidget(id,view);
        }
    }
    @Override public void onUpdate(Context context,AppWidgetManager mgr,int[] ids) { update(context,mgr,ids); }
    @Override public void onEnabled(Context context) { BootReceiver.scheduleMidnight(context); }
}

package tw.ajo.stepcounter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.time.LocalDate;
import java.time.ZoneId;

/** Reboots and day changes. Inexact alarms may be delayed by Doze. */
public final class BootReceiver extends BroadcastReceiver {
    public static final String MIDNIGHT="tw.ajo.stepcounter.MIDNIGHT";
    @Override public void onReceive(Context context,Intent intent) {
        String action=intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            if(StepService.enabled(context)) StepService.start(context);
        }
        if(MIDNIGHT.equals(action) && StepService.running && StepService.sensorReady) {
            try(StepStore store=new StepStore(context)) { store.ensureToday(); }
        }
        StepWidget.updateAll(context);
        scheduleMidnight(context);
    }
    public static void scheduleMidnight(Context context) {
        try {
            long next=LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()+1500L;
            AlarmManager a=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            Intent i=new Intent(context,BootReceiver.class).setAction(MIDNIGHT);
            PendingIntent p=PendingIntent.getBroadcast(context,23,i,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,p);
        } catch(Exception ignored) {}
    }
}

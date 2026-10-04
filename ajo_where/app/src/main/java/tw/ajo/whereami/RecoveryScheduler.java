package tw.ajo.whereami;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public final class RecoveryScheduler {
    public static final String ACTION_CHECK = "tw.ajo.whereami.RECOVERY_CHECK";
    public static final long NORMAL_DELAY_MS = 10L * 60_000L;
    private static final int REQUEST_CODE = 307;

    private RecoveryScheduler() {}

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, RecoveryReceiver.class).setAction(ACTION_CHECK);
        return PendingIntent.getBroadcast(
                c,
                REQUEST_CODE,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static boolean canScheduleExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    public static void schedule(Context c, long delayMs) {
        Context app = c.getApplicationContext();
        if (!Prefs.running(app)) {
            cancel(app);
            return;
        }

        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long triggerAt = SystemClock.elapsedRealtime() + Math.max(60_000L, delayMs);
        PendingIntent pi = pending(app);

        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            }
        } catch (Exception e) {
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                }
            } catch (Exception ignored) {}
        }
    }

    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            try { am.cancel(pending(c)); } catch (Exception ignored) {}
        }
    }
}

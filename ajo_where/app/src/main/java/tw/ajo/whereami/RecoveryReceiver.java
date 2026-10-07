package tw.ajo.whereami;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class RecoveryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.running(context)) {
            RecoveryScheduler.cancel(context);
            return;
        }

        // Always arm the next independent system alarm first, even if this recovery attempt fails.
        RecoveryScheduler.schedule(context, RecoveryScheduler.NORMAL_DELAY_MS);

        long now = System.currentTimeMillis();
        long heartbeat = Prefs.heartbeat(context);
        long lastFixTime = Prefs.lastFixTime(context);
        long uploadStaleMs = Math.max(
                10L * 60_000L,
                Prefs.intervalMin(context) * 3L * 60_000L + 60_000L
        );

        boolean serviceStale = heartbeat <= 0 || now - heartbeat > 3L * 60_000L;
        boolean uploadStale = lastFixTime <= 0 || now - lastFixTime > uploadStaleMs;

        if (!serviceStale && !uploadStale) return;

        if (!hasBackgroundLocation(context)) {
            Prefs.setRecoveryStatus(context, now, "需要「永遠允許」定位權限");
            return;
        }

        try {
            Intent svc = new Intent(context, LocationService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
            else context.startService(svc);
            Prefs.setRecoveryStatus(context, now, "已自動重新啟動背景定位");
        } catch (Exception e) {
            Prefs.setRecoveryStatus(context, now, "自動恢復失敗：" + e.getClass().getSimpleName());
        }
    }

    private boolean hasBackgroundLocation(Context c) {
        if (Build.VERSION.SDK_INT < 29) return true;
        return c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}

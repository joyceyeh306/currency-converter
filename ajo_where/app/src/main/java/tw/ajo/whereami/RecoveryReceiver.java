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

        RecoveryScheduler.schedule(context, RecoveryScheduler.NORMAL_DELAY_MS);

        long now = System.currentTimeMillis();
        long lastDelivery = Prefs.fusedLastDelivery(context);
        long intervalMs = Math.max(60_000L, Prefs.intervalMin(context) * 60_000L);
        long staleMs = Math.max(12L * 60_000L, intervalMs * 4L);

        boolean deliveryStale = lastDelivery <= 0 || now - lastDelivery > staleMs;
        boolean registrationOld = Prefs.fusedRegisteredAt(context) <= 0 ||
                now - Prefs.fusedRegisteredAt(context) > 12L * 60L * 60_000L;

        if (deliveryStale || registrationOld) {
            if (hasBackgroundLocation(context)) {
                LocationSubscription.register(context);
                Prefs.setRecoveryStatus(context, now, "已重新確認 Google 背景定位訂閱");
            } else {
                Prefs.setRecoveryStatus(context, now, "需要「永遠允許」定位權限");
                return;
            }
        }

        // Foreground service is a reliability/battery-management layer, but no longer
        // the only way locations can arrive. Try to restore it when Android allows.
        long heartbeat = Prefs.heartbeat(context);
        if (heartbeat <= 0 || now - heartbeat > 3L * 60_000L) {
            try {
                Intent svc = new Intent(context, LocationService.class);
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
                else context.startService(svc);
                Prefs.setRecoveryStatus(context, now, "已嘗試恢復前景分享服務");
            } catch (Exception e) {
                Prefs.setRecoveryStatus(context, now,
                        "前景服務未恢復，但 Google 背景定位仍可接收");
            }
        }
    }

    private boolean hasBackgroundLocation(Context c) {
        if (Build.VERSION.SDK_INT < 29) return true;
        return c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}

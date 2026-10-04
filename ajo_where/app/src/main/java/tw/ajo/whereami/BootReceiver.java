package tw.ajo.whereami;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.running(context)) return;

        RecoveryScheduler.schedule(context, 60_000L);

        if (!hasBackgroundLocation(context)) {
            Prefs.setRecoveryStatus(context, System.currentTimeMillis(), "重新開機後等待背景定位權限");
            return;
        }

        try {
            Intent svc = new Intent(context, LocationService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
            else context.startService(svc);
            Prefs.setRecoveryStatus(context, System.currentTimeMillis(), "重新開機後已恢復分享");
        } catch (Exception e) {
            Prefs.setRecoveryStatus(context, System.currentTimeMillis(), "重新開機後恢復失敗：" + e.getClass().getSimpleName());
        }
    }

    private boolean hasBackgroundLocation(Context c) {
        if (Build.VERSION.SDK_INT < 29) return true;
        return c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}

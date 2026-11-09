package tw.ajo.whereami;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.google.android.gms.location.LocationResult;

public class LocationUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.running(context)) return;
        if (!LocationSubscription.ACTION_FUSED_LOCATION.equals(intent.getAction())) return;
        if (!LocationResult.hasResult(intent)) return;

        LocationResult result = LocationResult.extractResult(intent);
        if (result == null) return;

        Location loc = result.getLastLocation();
        if (loc == null) return;

        long now = System.currentTimeMillis();
        Prefs.setFusedLastDelivery(context, now);
        Prefs.setLastLocationCallback(context, now);
        Prefs.setFusedStatus(context, "Google 背景定位正常");
        RecoveryScheduler.schedule(context, RecoveryScheduler.NORMAL_DELAY_MS);

        final PendingResult pending = goAsync();
        Context app = context.getApplicationContext();

        new Thread(() -> {
            try {
                boolean ok = LocationUploader.upload(app, loc);
                if (!ok) {
                    Prefs.setFusedStatus(app, "收到位置，但上傳暫時失敗");
                }
            } finally {
                pending.finish();
            }
        }, "AjoLocationUpload").start();
    }
}

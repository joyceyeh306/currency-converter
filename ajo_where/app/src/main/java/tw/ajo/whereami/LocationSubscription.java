package tw.ajo.whereami;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public final class LocationSubscription {
    public static final String ACTION_FUSED_LOCATION = "tw.ajo.whereami.FUSED_LOCATION";
    private static final int REQUEST_CODE = 308;

    private LocationSubscription() {}

    public static boolean playServicesReady(Context c) {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c)
                == ConnectionResult.SUCCESS;
    }

    public static PendingIntent pendingIntent(Context c) {
        Intent i = new Intent(c, LocationUpdateReceiver.class)
                .setAction(ACTION_FUSED_LOCATION)
                .setPackage(c.getPackageName());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(c, REQUEST_CODE, i, flags);
    }

    public static LocationRequest request(Context c) {
        long interval = Math.max(60_000L, Prefs.intervalMin(c) * 60_000L);
        long minInterval = Math.max(60_000L, interval / 2L);

        return new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
                .setMinUpdateIntervalMillis(minInterval)
                .setMaxUpdateDelayMillis(interval)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build();
    }

    public static void register(Context context) {
        Context app = context.getApplicationContext();

        if (!Prefs.running(app)) return;

        if (!hasAnyLocationPermission(app)) {
            Prefs.setFusedStatus(app, "沒有定位權限");
            return;
        }

        if (!playServicesReady(app)) {
            Prefs.setFusedStatus(app, "Google Play 服務不可用");
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(app);
        PendingIntent pi = pendingIntent(app);

        // Remove an old request with the same PendingIntent first so changing 1/3/5/10
        // minute intervals never leaves duplicate subscriptions behind.
        client.removeLocationUpdates(pi).addOnCompleteListener(removeTask ->
                client.requestLocationUpdates(request(app), pi)
                        .addOnSuccessListener(v -> {
                            Prefs.setFusedRegisteredAt(app, System.currentTimeMillis());
                            Prefs.setFusedStatus(app, "Google 背景定位已註冊");
                        })
                        .addOnFailureListener(e ->
                                Prefs.setFusedStatus(app, "註冊失敗：" + e.getClass().getSimpleName()))
        );
    }

    public static void remove(Context context) {
        Context app = context.getApplicationContext();
        if (!playServicesReady(app)) {
            Prefs.setFusedRegisteredAt(app, 0L);
            Prefs.setFusedStatus(app, "已停止");
            return;
        }

        LocationServices.getFusedLocationProviderClient(app)
                .removeLocationUpdates(pendingIntent(app))
                .addOnCompleteListener(t -> {
                    Prefs.setFusedRegisteredAt(app, 0L);
                    Prefs.setFusedStatus(app, "已停止");
                });
    }

    private static boolean hasAnyLocationPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}

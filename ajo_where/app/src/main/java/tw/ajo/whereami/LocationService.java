package tw.ajo.whereami;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocationService extends Service {
    public static final String ACTION_STOP = "tw.ajo.whereami.STOP";
    public static final String ACTION_STATUS = "tw.ajo.whereami.STATUS";
    public static final String EXTRA_MESSAGE = "message";

    private static final String CHANNEL_ID = "ajo_location_sharing";
    private static final int NOTIFICATION_ID = 306;

    // Lightweight heartbeat only; it does not turn on GPS.
    private static final long HEARTBEAT_MS = 60_000L;

    // Low-power one-shot location policy.
    private static final long NETWORK_TIMEOUT_MS = 12_000L;
    private static final long GPS_TIMEOUT_MS = 20_000L;
    private static final long RETRY_GAP_MS = 45_000L;
    private static final float NETWORK_GOOD_ENOUGH_M = 120f;
    private static final long MAX_ACCEPTABLE_NETWORK_AGE_MS = 2L * 60_000L;

    private LocationManager lm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private long lastAttemptAt = 0L;
    private boolean cycleInProgress = false;
    private CancellationSignal currentCancel;
    private LocationListener legacyListener;
    private Runnable requestTimeout;
    private Location networkCandidate;

    private final Runnable heartbeatTask = new Runnable() {
        @Override public void run() {
            if (Prefs.running(LocationService.this)) {
                Prefs.setHeartbeat(LocationService.this, System.currentTimeMillis());
                RecoveryScheduler.schedule(LocationService.this, RecoveryScheduler.NORMAL_DELAY_MS);
                main.postDelayed(this, HEARTBEAT_MS);
            }
        }
    };

    private final Runnable locationCycleTask = new Runnable() {
        @Override public void run() {
            if (!Prefs.running(LocationService.this)) return;
            requestFreshLocation();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Notification n = buildNotification("省電背景定位已啟動");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }

        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Prefs.setHeartbeat(this, System.currentTimeMillis());
        RecoveryScheduler.schedule(this, RecoveryScheduler.NORMAL_DELAY_MS);

        main.removeCallbacks(heartbeatTask);
        main.postDelayed(heartbeatTask, HEARTBEAT_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSharing();
            return START_NOT_STICKY;
        }

        Prefs.setRunning(this, true);
        Prefs.setHeartbeat(this, System.currentTimeMillis());
        RecoveryScheduler.schedule(this, RecoveryScheduler.NORMAL_DELAY_MS);

        // Take one fresh fix shortly after starting, then only on the selected cadence.
        main.removeCallbacks(locationCycleTask);
        main.post(locationCycleTask);
        return START_STICKY;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean providerEnabled(String provider) {
        try {
            return lm != null && lm.isProviderEnabled(provider);
        } catch (Exception e) {
            return false;
        }
    }

    private void requestFreshLocation() {
        if (cycleInProgress || !Prefs.running(this)) return;

        if (!hasLocationPermission()) {
            status("沒有定位權限");
            Prefs.setRunning(this, false);
            Prefs.setHeartbeat(this, 0L);
            RecoveryScheduler.cancel(this);
            stopSelf();
            return;
        }

        cycleInProgress = true;
        networkCandidate = null;
        cancelCurrentRequest();

        if (providerEnabled(LocationManager.NETWORK_PROVIDER)) {
            requestProvider(LocationManager.NETWORK_PROVIDER, NETWORK_TIMEOUT_MS, false);
        } else if (providerEnabled(LocationManager.GPS_PROVIDER)) {
            requestProvider(LocationManager.GPS_PROVIDER, GPS_TIMEOUT_MS, true);
        } else {
            finishCycle(bestLastKnown());
        }
    }

    private void requestProvider(String provider, long timeoutMs, boolean gpsStage) {
        cancelCurrentRequest();

        requestTimeout = () -> {
            if (!cycleInProgress) return;
            if (gpsStage) {
                finishCycle(networkCandidate != null ? networkCandidate : bestLastKnown());
            } else {
                Location last = bestLastKnown();
                if (last != null) networkCandidate = last;
                if (providerEnabled(LocationManager.GPS_PROVIDER)) {
                    requestProvider(LocationManager.GPS_PROVIDER, GPS_TIMEOUT_MS, true);
                } else {
                    finishCycle(networkCandidate);
                }
            }
        };
        main.postDelayed(requestTimeout, timeoutMs);

        if (Build.VERSION.SDK_INT >= 30) {
            currentCancel = new CancellationSignal();
            try {
                lm.getCurrentLocation(
                        provider,
                        currentCancel,
                        getMainExecutor(),
                        loc -> handleProviderResult(provider, loc, gpsStage)
                );
            } catch (Exception e) {
                handleProviderResult(provider, null, gpsStage);
            }
        } else {
            legacyListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    handleProviderResult(provider, location, gpsStage);
                }
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
                @Override public void onStatusChanged(String p, int status, Bundle extras) {}
            };

            try {
                @SuppressWarnings("deprecation")
                boolean ignored = lm.isProviderEnabled(provider);
                lm.requestSingleUpdate(provider, legacyListener, Looper.getMainLooper());
            } catch (Exception e) {
                handleProviderResult(provider, null, gpsStage);
            }
        }
    }

    private void handleProviderResult(String provider, Location loc, boolean gpsStage) {
        if (!cycleInProgress) return;
        clearRequestOnly();

        long now = System.currentTimeMillis();

        if (loc != null) {
            Prefs.setLastLocationCallback(this, now);
            Prefs.setHeartbeat(this, now);
        }

        if (!gpsStage) {
            networkCandidate = loc != null ? new Location(loc) : bestLastKnown();

            boolean freshEnough = networkCandidate != null &&
                    networkCandidate.getTime() > 0 &&
                    now - networkCandidate.getTime() <= MAX_ACCEPTABLE_NETWORK_AGE_MS;

            boolean accurateEnough = networkCandidate != null &&
                    (!networkCandidate.hasAccuracy() || networkCandidate.getAccuracy() <= NETWORK_GOOD_ENOUGH_M);

            if (freshEnough && accurateEnough) {
                finishCycle(networkCandidate);
                return;
            }

            if (providerEnabled(LocationManager.GPS_PROVIDER)) {
                requestProvider(LocationManager.GPS_PROVIDER, GPS_TIMEOUT_MS, true);
            } else {
                finishCycle(networkCandidate);
            }
            return;
        }

        Location chosen = chooseBetter(networkCandidate, loc);
        finishCycle(chosen != null ? chosen : bestLastKnown());
    }

    private Location chooseBetter(Location a, Location b) {
        if (a == null) return b == null ? null : new Location(b);
        if (b == null) return new Location(a);

        long timeDelta = b.getTime() - a.getTime();
        boolean bMuchNewer = timeDelta > 60_000L;
        boolean aMuchNewer = timeDelta < -60_000L;

        if (bMuchNewer) return new Location(b);
        if (aMuchNewer) return new Location(a);

        if (a.hasAccuracy() && b.hasAccuracy()) {
            return new Location(b.getAccuracy() < a.getAccuracy() ? b : a);
        }

        return new Location(b.getTime() >= a.getTime() ? b : a);
    }

    private void finishCycle(Location loc) {
        clearRequestOnly();

        if (loc != null) {
            maybeSend(loc);
        } else {
            notifyText("暫時無法取得位置");
            status("暫時無法取得位置，稍後再試");
        }

        cycleInProgress = false;
        scheduleNextCycle();
    }

    private void scheduleNextCycle() {
        if (!Prefs.running(this)) return;
        long delay = Math.max(60_000L, Prefs.intervalMin(this) * 60_000L);
        main.removeCallbacks(locationCycleTask);
        main.postDelayed(locationCycleTask, delay);
    }

    private void maybeSend(Location location) {
        long now = System.currentTimeMillis();
        if (now - lastAttemptAt < RETRY_GAP_MS) return;
        if (!sending.compareAndSet(false, true)) return;

        lastAttemptAt = now;
        Location copy = new Location(location);
        io.execute(() -> {
            try {
                upload(copy);
            } finally {
                sending.set(false);
            }
        });
    }

    private boolean upload(Location loc) {
        try {
            long now = System.currentTimeMillis();
            long fixTime = loc.getTime() > 0 ? loc.getTime() : now;

            JSONObject j = new JSONObject();
            j.put("lat", loc.getLatitude());
            j.put("lon", loc.getLongitude());
            j.put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : JSONObject.NULL);
            j.put("speed", loc.hasSpeed() ? loc.getSpeed() : JSONObject.NULL);
            j.put("battery", batteryPercent());
            j.put("time", fixTime);

            URL u = new URL("https://ntfy.sh/" + Prefs.topic(this) + "/ajo-location");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(12_000);
            c.setReadTimeout(12_000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            c.setRequestProperty("Title", "ajo-location");
            c.setRequestProperty("Firebase", "no");
            byte[] body = j.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = c.getOutputStream()) { os.write(body); }
            int code = c.getResponseCode();
            c.disconnect();

            if (code >= 200 && code < 300) {
                Prefs.saveLast(this, now, fixTime, loc.getLatitude(), loc.getLongitude(),
                        loc.hasAccuracy() ? loc.getAccuracy() : 0f);
                Prefs.setHeartbeat(this, System.currentTimeMillis());
                notifyText("位置已更新");
                status(String.format(Locale.TAIWAN, "已更新 %.5f, %.5f", loc.getLatitude(), loc.getLongitude()));
                return true;
            } else {
                notifyText("定位上傳失敗（" + code + "）");
                status("上傳失敗：" + code);
                return false;
            }
        } catch (Exception e) {
            notifyText("定位上傳失敗");
            status("上傳失敗：" + e.getClass().getSimpleName());
            return false;
        }
    }

    private Location bestLastKnown() {
        if (!hasLocationPermission() || lm == null) return null;
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location x = lm.getLastKnownLocation(p);
                if (x != null && (best == null || x.getTime() > best.getTime())) best = x;
            }
        } catch (Exception ignored) {}
        return best == null ? null : new Location(best);
    }

    private void clearRequestOnly() {
        if (requestTimeout != null) {
            main.removeCallbacks(requestTimeout);
            requestTimeout = null;
        }

        if (currentCancel != null) {
            try { currentCancel.cancel(); } catch (Exception ignored) {}
            currentCancel = null;
        }

        if (legacyListener != null && lm != null) {
            try { lm.removeUpdates(legacyListener); } catch (Exception ignored) {}
            legacyListener = null;
        }
    }

    private void cancelCurrentRequest() {
        clearRequestOnly();
        networkCandidate = null;
    }

    private int batteryPercent() {
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return -1;
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "ㄚ喬位置分享",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("省電背景定位正在執行");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stop = new Intent(this, LocationService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setSmallIcon(R.drawable.ic_location_notification)
                .setContentTitle("ㄚ喬在哪裡")
                .setContentText(text)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        if (Build.VERSION.SDK_INT >= 23) {
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "停止分享",
                    stopPi
            ).build());
        }

        return b.build();
    }

    private void notifyText(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void status(String message) {
        Intent i = new Intent(ACTION_STATUS).setPackage(getPackageName());
        i.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(i);
    }

    private void stopSharing() {
        Prefs.setRunning(this, false);
        Prefs.setHeartbeat(this, 0L);
        Prefs.setLastLocationCallback(this, 0L);

        RecoveryScheduler.cancel(this);
        main.removeCallbacks(heartbeatTask);
        main.removeCallbacks(locationCycleTask);
        cancelCurrentRequest();
        cycleInProgress = false;

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        status("已停止分享位置");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (Prefs.running(this)) {
            Prefs.setRecoveryStatus(this, System.currentTimeMillis(), "App 被清除，已排程自動恢復");
            RecoveryScheduler.schedule(this, 60_000L);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(heartbeatTask);
        main.removeCallbacks(locationCycleTask);
        cancelCurrentRequest();
        Prefs.setHeartbeat(this, 0L);

        if (Prefs.running(this)) {
            Prefs.setRecoveryStatus(this, System.currentTimeMillis(), "背景服務被停止，已排程自動恢復");
            RecoveryScheduler.schedule(this, 60_000L);
        }

        io.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

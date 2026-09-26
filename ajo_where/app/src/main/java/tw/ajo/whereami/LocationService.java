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

public class LocationService extends Service implements LocationListener {
    public static final String ACTION_STOP = "tw.ajo.whereami.STOP";
    public static final String ACTION_STATUS = "tw.ajo.whereami.STATUS";
    public static final String EXTRA_MESSAGE = "message";

    private static final String CHANNEL_ID = "ajo_location_sharing";
    private static final int NOTIFICATION_ID = 306;
    private static final long WATCHDOG_MS = 60_000L;
    private static final long HEARTBEAT_STALE_MS = 150_000L;
    private static final long RETRY_GAP_MS = 45_000L;

    private LocationManager lm;
    private boolean registered = false;
    private long lastSentAt = 0L;
    private long lastAttemptAt = 0L;
    private Location lastLocation;
    private final AtomicBoolean sending = new AtomicBoolean(false);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler watchdog = new Handler(Looper.getMainLooper());

    private final Runnable watchdogTask = new Runnable() {
        @Override public void run() {
            try {
                if (Prefs.running(LocationService.this)) {
                    long now = System.currentTimeMillis();
                    Prefs.setHeartbeat(LocationService.this, now);

                    long callbackAt = Prefs.lastLocationCallback(LocationService.this);
                    long callbackStaleMs = Math.max(150_000L, Prefs.intervalMin(LocationService.this) * 2L * 60_000L);

                    // If Android/ColorOS stopped delivering fixes while the service still lives,
                    // re-register both providers instead of silently staying "running".
                    if (!registered || callbackAt <= 0 || now - callbackAt > callbackStaleMs) {
                        restartUpdates();
                        notifyText("正在恢復定位…");
                        status("定位中斷，正在自動恢復");
                    }

                    // Even if the phone is stationary, upload on the selected cadence.
                    // minDistance is 0 m in v1.0.10, but this is an extra safety net.
                    long minGap = Prefs.intervalMin(LocationService.this) * 60_000L;
                    if (Prefs.lastUpload(LocationService.this) <= 0 ||
                            now - Prefs.lastUpload(LocationService.this) > minGap + 30_000L) {
                        Location candidate = lastLocation != null ? lastLocation : bestLastKnown();
                        if (candidate != null) maybeSend(candidate, true);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                watchdog.postDelayed(this, WATCHDOG_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Notification n = buildNotification("正在取得定位…");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        lastSentAt = Prefs.lastUpload(this);
        Prefs.setHeartbeat(this, System.currentTimeMillis());
        watchdog.postDelayed(watchdogTask, WATCHDOG_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSharing();
            return START_NOT_STICKY;
        }
        Prefs.setRunning(this, true);
        Prefs.setHeartbeat(this, System.currentTimeMillis());
        startUpdates();
        return START_STICKY;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startUpdates() {
        if (registered) return;
        if (!hasLocationPermission()) {
            status("沒有定位權限");
            Prefs.setRunning(this, false);
            Prefs.setHeartbeat(this, 0L);
            stopSelf();
            return;
        }

        boolean any = false;
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // v1.0.9 used 10 m here. That can stop callbacks while the phone is
                // stationary, making it look as if sharing had been turned off.
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30_000L, 0f, this);
                any = true;
            }
        } catch (Exception ignored) {}

        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 0f, this);
                any = true;
            }
        } catch (Exception ignored) {}

        registered = any;

        Location last = bestLastKnown();
        if (last != null) {
            lastLocation = new Location(last);
            // Upload it, but the payload carries the fix's own timestamp, so an old
            // cached fix will correctly appear as old on the viewer.
            maybeSend(last, true);
        }
    }

    private void restartUpdates() {
        if (lm != null && registered) {
            try { lm.removeUpdates(this); } catch (Exception ignored) {}
        }
        registered = false;
        startUpdates();
    }

    private Location bestLastKnown() {
        if (!hasLocationPermission()) return null;
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location x = lm.getLastKnownLocation(p);
                if (x != null && (best == null || x.getTime() > best.getTime())) best = x;
            }
        } catch (Exception ignored) {}
        return best;
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = new Location(location);
        long now = System.currentTimeMillis();
        Prefs.setLastLocationCallback(this, now);
        Prefs.setHeartbeat(this, now);
        maybeSend(location, false);
    }

    private void maybeSend(Location location, boolean force) {
        long now = System.currentTimeMillis();
        long minGap = Prefs.intervalMin(this) * 60_000L;
        if (!force && now - lastSentAt < minGap) return;
        if (now - lastAttemptAt < RETRY_GAP_MS) return;
        if (!sending.compareAndSet(false, true)) return;

        lastAttemptAt = now;
        Location copy = new Location(location);
        io.execute(() -> {
            try {
                if (upload(copy)) {
                    lastSentAt = System.currentTimeMillis();
                }
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
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "ㄚ喬位置分享", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("背景定位正在執行");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, LocationService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
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
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification(text));
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
        watchdog.removeCallbacks(watchdogTask);
        if (lm != null && registered) {
            try { lm.removeUpdates(this); } catch (Exception ignored) {}
        }
        registered = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        status("已停止分享位置");
    }

    @Override
    public void onDestroy() {
        watchdog.removeCallbacks(watchdogTask);
        Prefs.setHeartbeat(this, 0L);
        if (lm != null && registered) {
            try { lm.removeUpdates(this); } catch (Exception ignored) {}
        }
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onProviderEnabled(String provider) { restartUpdates(); }
    @Override public void onProviderDisabled(String provider) { restartUpdates(); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
}

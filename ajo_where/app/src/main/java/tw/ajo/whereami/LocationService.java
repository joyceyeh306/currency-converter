package tw.ajo.whereami;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationService extends Service {
    public static final String ACTION_STOP = "tw.ajo.whereami.STOP";
    public static final String ACTION_REFRESH = "tw.ajo.whereami.REFRESH";
    public static final String ACTION_STATUS = "tw.ajo.whereami.STATUS";
    public static final String EXTRA_MESSAGE = "message";

    private static final String CHANNEL_ID = "ajo_location_sharing";
    private static final int NOTIFICATION_ID = 306;
    private static final long HEARTBEAT_MS = 60_000L;

    private final Handler main = new Handler(Looper.getMainLooper());

    private final Runnable heartbeatTask = new Runnable() {
        @Override public void run() {
            if (Prefs.running(LocationService.this)) {
                Prefs.setHeartbeat(LocationService.this, System.currentTimeMillis());
                RecoveryScheduler.schedule(LocationService.this, RecoveryScheduler.NORMAL_DELAY_MS);
                main.postDelayed(this, HEARTBEAT_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        Notification n = buildNotification("Google 背景定位正在分享");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }

        Prefs.setHeartbeat(this, System.currentTimeMillis());
        RecoveryScheduler.schedule(this, RecoveryScheduler.NORMAL_DELAY_MS);
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

        LocationSubscription.register(this);
        RecoveryScheduler.schedule(this, RecoveryScheduler.NORMAL_DELAY_MS);

        if (intent == null || !ACTION_REFRESH.equals(intent.getAction())) {
            requestImmediateFix();
        }

        return START_STICKY;
    }

    private void requestImmediateFix() {
        if (!LocationSubscription.playServicesReady(this)) return;

        try {
            CurrentLocationRequest req = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(60_000L)
                    .setDurationMillis(20_000L)
                    .build();

            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(req, null)
                    .addOnSuccessListener(location -> {
                        if (location == null || !Prefs.running(this)) return;
                        long now = System.currentTimeMillis();
                        Prefs.setFusedLastDelivery(this, now);
                        Prefs.setLastLocationCallback(this, now);
                        new Thread(() -> LocationUploader.upload(this, location),
                                "AjoImmediateLocation").start();
                    });
        } catch (SecurityException ignored) {
        }
    }

    private void stopSharing() {
        Prefs.setRunning(this, false);
        Prefs.setHeartbeat(this, 0L);
        Prefs.setLastLocationCallback(this, 0L);

        LocationSubscription.remove(this);
        RecoveryScheduler.cancel(this);
        main.removeCallbacks(heartbeatTask);

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        status("已停止分享位置");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Do not remove the Fused PendingIntent subscription here. This is deliberate:
        // if ColorOS removes the app process, Google Play services can still deliver
        // locations to LocationUpdateReceiver and wake the process for upload.
        if (Prefs.running(this)) {
            Prefs.setRecoveryStatus(this, System.currentTimeMillis(),
                    "App 程序被清除，Google 背景定位仍保留");
            RecoveryScheduler.schedule(this, 60_000L);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(heartbeatTask);
        Prefs.setHeartbeat(this, 0L);

        // Same rule: only explicit Stop removes the PendingIntent subscription.
        if (Prefs.running(this)) {
            Prefs.setRecoveryStatus(this, System.currentTimeMillis(),
                    "前景服務被系統回收，Google 背景定位仍保留");
            RecoveryScheduler.schedule(this, 60_000L);
        }
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "ㄚ喬位置分享",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("位置分享正在背景執行");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
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

    private void status(String message) {
        Intent i = new Intent(ACTION_STATUS).setPackage(getPackageName());
        i.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

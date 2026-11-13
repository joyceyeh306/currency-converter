package tw.ajo.stepcounter;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.Locale;

/** Health foreground service: never schedules a high-frequency polling loop. */
public final class StepService extends Service implements SensorEventListener {
    private static final String CHANNEL="ajo_step_recording";
    private static final int NOTIFICATION_ID=302;
    private SensorManager manager;
    private Sensor counter;
    private long lastUiUpdate=0;
    public static volatile boolean running=false;
    public static volatile boolean sensorReady=false;

    public static boolean enabled(Context context) {
        return context.getSharedPreferences("ajo",Context.MODE_PRIVATE).getBoolean("enabled",false);
    }
    public static void setEnabled(Context context,boolean value) {
        context.getSharedPreferences("ajo",Context.MODE_PRIVATE).edit().putBoolean("enabled",value).commit();
    }
    public static void start(Context context) {
        if(!enabled(context)) return;
        if(context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED) return;
        try {
            Intent i=new Intent(context,StepService.class);
            if(Build.VERSION.SDK_INT>=26) context.startForegroundService(i);
            else context.startService(i);
        } catch(Exception e) {
            context.getSharedPreferences("ajo",Context.MODE_PRIVATE).edit()
                    .putString("last_error",e.getClass().getSimpleName()).apply();
        }
    }
    public static void stop(Context context) {
        context.stopService(new Intent(context,StepService.class));
        StepWidget.updateAll(context);
    }
    @Override public void onCreate() {
        super.onCreate();
        running=true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(!enabled(this) || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,"計步紀錄",NotificationManager.IMPORTANCE_LOW));
            Notification n=notification();
            if(Build.VERSION.SDK_INT>=34) startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            else startForeground(NOTIFICATION_ID,n);
            if(manager==null) {
                manager=(SensorManager)getSystemService(SENSOR_SERVICE);
                counter=manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if(counter!=null) sensorReady=manager.registerListener(this,counter,SensorManager.SENSOR_DELAY_NORMAL,0);
            }
            if(!sensorReady) {
                getSharedPreferences("ajo",MODE_PRIVATE).edit().putString("last_error","無法註冊計步感測器").apply();
                stopSelf();return START_NOT_STICKY;
            }
            try(StepStore store=new StepStore(this)) { store.ensureToday(); }
            BootReceiver.scheduleMidnight(this);
            StepWidget.updateAll(this);
            return START_STICKY;
        } catch(Exception e) {
            getSharedPreferences("ajo",MODE_PRIVATE).edit()
                .putString("last_error",e.getClass().getSimpleName()).apply();
            stopSelf();return START_NOT_STICKY;
        }
    }
    private Notification notification() {
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("ㄚ喬計步器")
                .setContentText("硬體計步器正在安靜地記錄步數")
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType()!=Sensor.TYPE_STEP_COUNTER||event.values.length==0)return;
        long raw=(long)event.values[0];
        long previousToday;
        try(StepStore store=new StepStore(this)) { previousToday=store.day(LocalDate.now()); }
        long today=StepStore.record(this,raw,event.timestamp);
        StepGoals.notifyReached(this,previousToday,today);
        long now=SystemClock.elapsedRealtime();
        if(now-lastUiUpdate>=7000) {
            lastUiUpdate=now;
            StepWidget.updateAll(this);
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {}
    @Override public void onDestroy() {
        if(manager!=null) manager.unregisterListener(this);
        sensorReady=false;running=false;
        StepWidget.updateAll(this);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onTaskRemoved(Intent rootIntent) {
        // FGS continues when the user only removes the activity from Recents.
        super.onTaskRemoved(rootIntent);
    }
}

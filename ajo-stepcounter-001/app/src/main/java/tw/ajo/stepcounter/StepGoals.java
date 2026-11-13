package tw.ajo.stepcounter;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Device-local, user-selected daily milestone reminders. Disabled when the list is empty. */
public final class StepGoals {
    private StepGoals() {}
    private static final String PREF="ajo";
    private static final String GOALS="goal_steps_v2";
    private static final String DAY="goal_sent_date_v2";
    private static final String SENT="goal_sent_steps_v2";
    private static final String CHANNEL="ajo_step_milestones_v2";
    private static final int MAX=200000;

    private static Set<Integer> parse(String csv) {
        Set<Integer> parsed=new TreeSet<>();
        if(csv==null||csv.isEmpty())return parsed;
        for(String part:csv.split(",")) {
            try {
                int value=Integer.parseInt(part.trim());
                if(value>=1&&value<=MAX)parsed.add(value);
            } catch(NumberFormatException ignored) {}
        }
        return parsed;
    }
    private static String serialize(Set<Integer> goals) {
        StringBuilder result=new StringBuilder();
        for(int g:new TreeSet<>(goals)) {
            if(result.length()>0)result.append(',');
            result.append(g);
        }
        return result.toString();
    }
    public static synchronized List<Integer> list(Context context) {
        SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        return new ArrayList<>(parse(p.getString(GOALS,"")));
    }
    public static synchronized boolean save(Context context,List<Integer> thresholds) {
        if(thresholds.size()>30)return false;
        Set<Integer> checked=new TreeSet<>();
        for(Integer n:thresholds) {
            if(n==null||n<1||n>MAX||!checked.add(n))return false;
        }
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString(GOALS,serialize(checked)).commit();
    }

    /** Called after each real hardware sample. Multiple goals crossed in one batch yield ONE notification. */
    public static synchronized void notifyReached(Context context,long before,long after) {
        if(after<=before)return;
        if(Build.VERSION.SDK_INT>=33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            return;
        SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        List<Integer> goals=new ArrayList<>(parse(p.getString(GOALS,"")));
        if(goals.isEmpty())return;
        String today=LocalDate.now().toString();
        Set<Integer> sent=today.equals(p.getString(DAY,""))
                ?new HashSet<>(parse(p.getString(SENT,""))):new HashSet<>();
        List<Integer> reached=GoalMath.crossed(goals,before,after,sent);
        if(reached.isEmpty())return;
        sent.addAll(reached);
        if(!p.edit().putString(DAY,today).putString(SENT,serialize(sent)).commit())return;
        int reachedNow=Collections.max(reached);
        try {
            NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel=new NotificationChannel(CHANNEL,"步數達標提醒",NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("只在達到自己設定的每日步數時通知");
            nm.createNotificationChannel(channel);
            Intent open=new Intent(context,MainActivity.class);
            PendingIntent pi=PendingIntent.getActivity(context,402,open,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            String amount=NumberFormat.getIntegerInstance(Locale.TAIWAN).format(reachedNow);
            String title="🐾 小腳印達標啦！";
            String detail="今天已走到 "+amount+" 步"+(reached.size()>1?"（一次達成 "+reached.size()+" 個目標）":"");
            Notification n=new Notification.Builder(context,CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(detail)
                    .setStyle(new Notification.BigTextStyle().bigText(detail))
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .build();
            nm.notify(50000+reachedNow,n);
        } catch(SecurityException ignored) {
            // A notification permission may be revoked at any moment.
        } catch(Exception ignored) {}
    }
}

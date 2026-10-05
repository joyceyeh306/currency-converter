package tw.ajo.travelnotebook;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent src){
        String id=src.getStringExtra("id"),title=src.getStringExtra("title");
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        String ch="travel_reminders";
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel(ch,"旅行提醒",NotificationManager.IMPORTANCE_HIGH));
        Intent open=new Intent(c,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("reminder_trip",src.getStringExtra("trip"));
        open.putExtra("reminder_day",src.getStringExtra("day"));
        open.putExtra("reminder_type",src.getStringExtra("targetType"));
        open.putExtra("reminder_target",src.getStringExtra("targetId"));
        PendingIntent pi=PendingIntent.getActivity(c,id==null?0:id.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(c,ch):new android.app.Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("自助旅行筆記").setContentText(title==null?"旅行提醒":title).setAutoCancel(true).setContentIntent(pi);
        try{nm.notify(id==null?1:id.hashCode(),b.build());}catch(SecurityException ignored){}
    }
}
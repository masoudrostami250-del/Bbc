package ir.masoud.oilreminder;
import android.app.*; import android.content.*; import android.os.Build;
public class ReminderReceiver extends BroadcastReceiver {
 static final String CHANNEL="maintenance_reminders";
 @Override public void onReceive(Context c, Intent i) { createChannel(c); String title=i.getStringExtra("title"), body=i.getStringExtra("body"); int id=i.getIntExtra("id",1); Intent open=new Intent(c,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title==null?"یادآوری سرویس خودرو":title).setContentText(body==null?"زمان سرویس خودرو رسیده است":body).setAutoCancel(true).setContentIntent(pi).build(); ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id,n); if(id==12) MainActivity.scheduleAnnual(c); }
 static void createChannel(Context c){if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL,"یادآوری‌های خودرو",NotificationManager.IMPORTANCE_DEFAULT);((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);}}
}

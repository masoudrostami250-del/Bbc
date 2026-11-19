package ir.masoud.oilreminder;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import java.util.*;

public class GpsTrackingService extends Service implements LocationListener {
    private static final String CHANNEL="gps_tracking";
    private Location last=null;
    private LocationManager manager;
    private SharedPreferences prefs;
    private static final int NOTIFICATION_ID=77;

    @Override public void onCreate(){
        super.onCreate();
        prefs=getSharedPreferences(MainActivity.PREF,0);
        createChannel();
        Notification n;
        if(Build.VERSION.SDK_INT>=26)n=new Notification.Builder(this,CHANNEL)
            .setContentTitle("ثبت خودکار کیلومتر خودرو")
            .setContentText("GPS روشن است؛ مسافت رانندگی در حال ثبت است")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build();
        else n=new Notification.Builder(this).setContentTitle("ثبت خودکار کیلومتر خودرو")
            .setContentText("GPS روشن است").setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build();
        startForeground(NOTIFICATION_ID,n);
        manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        try{
            if(manager!=null){
                if(manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000,10,this);
                if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,10000,15,this);
            }
        }catch(SecurityException e){stopSelf();}
    }
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"ثبت کیلومتر با GPS",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }
    @Override public void onLocationChanged(Location loc){
        if(loc==null||!loc.hasAccuracy()||loc.getAccuracy()>35)return;
        if(last==null){last=new Location(loc);return;}
        float meters=last.distanceTo(loc);
        long seconds=Math.max(1,(loc.getTime()-last.getTime())/1000);
        float speed=loc.hasSpeed()?loc.getSpeed():meters/seconds;
        if(meters<10)return;
        if(meters>Math.max(250, speed*seconds*2.5f+40)){last=new Location(loc);return;}
        if(speed>0.7f&&meters<2000){
            double km=meters/1000.0;
            double pending=prefs.getFloat("gps_km_remainder",0f)+km;
            int whole=(int)pending;
            int current=prefs.getInt("current_odo",59000);
            if(whole>0){
                prefs.edit().putInt("current_odo",current+whole)
                    .putFloat("gps_km_remainder",(float)(pending-whole)).apply();
                Intent update=new Intent("ir.masoud.oilreminder.GPS_UPDATED");
                sendBroadcast(update);
            }else prefs.edit().putFloat("gps_km_remainder",(float)pending).apply();
            last=new Location(loc);
        }
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_STICKY;}
    @Override public void onDestroy(){
        if(manager!=null)manager.removeUpdates(this);
        prefs.edit().putBoolean("gps_tracking",false).apply();
        stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
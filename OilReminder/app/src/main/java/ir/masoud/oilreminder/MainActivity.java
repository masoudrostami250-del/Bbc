package ir.masoud.oilreminder;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final String PREF="car_service";
    EditText odoInput, averageInput;
    TextView status;
    SharedPreferences p;
    CheckBox notifications;
    TextView gpsStatus;
    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff202020);t.setPadding(0,dp(7),0,dp(7));return t;}
    Button button(String s){Button b=new Button(this);b.setText(s);return b;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        p=getSharedPreferences(PREF,0);
        ReminderReceiver.createChannel(this);
        requestNotif();
        if(!p.contains("next_oil")){
            p.edit().putInt("current_odo",59000).putInt("next_oil",65000)
                .putInt("next_air",81500).putInt("oil_count",0)
                .putLong("gear_date",System.currentTimeMillis())
                .putInt("average_daily_km",225).putBoolean("notifications_enabled",true).apply();
        }
        if(!p.contains("average_daily_km"))p.edit().putInt("average_daily_km",225).apply();
        if(!p.contains("notifications_enabled"))p.edit().putBoolean("notifications_enabled",true).apply();
        scheduleAnnual(this);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(1);root.setPadding(dp(20),dp(22),dp(20),dp(24));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setBackgroundColor(0xfff5f7f5);scroll.addView(root);

        TextView title=text("تعویض روغن ۱۰و۴۰",25);title.setTypeface(null,Typeface.BOLD);title.setTextColor(0xff145c38);root.addView(title);
        root.addView(text("خودرو: پراید ۱۳۸۵ دوگانه‌سوز | روغن ۱۰W-40",15));
        root.addView(text("کیلومتر فعلی خودرو را وارد کن:",16));
        odoInput=new EditText(this);odoInput.setSingleLine(true);odoInput.setInputType(2);
        odoInput.setText(String.valueOf(p.getInt("current_odo",59000)));odoInput.setHint("مثلاً ۶۵۰۰۰");root.addView(odoInput);
        Button update=button("ثبت دستی کیلومتر و بررسی سررسید");root.addView(update);update.setOnClickListener(v->saveOdo());
        root.addView(text("ثبت خودکار کیلومتر با GPS",20));
        root.addView(text("برای محاسبه خودکار، GPS را روشن کن و دکمه شروع را بزن. ثبت کیلومتر حتی با خاموش بودن صفحه ادامه پیدا می‌کند؛ اعلان دائمی نشان داده می‌شود.",14));
        Button gpsStart=button("شروع ثبت خودکار با GPS");root.addView(gpsStart);gpsStart.setOnClickListener(v->startGpsTracking());
        Button gpsStop=button("توقف ثبت خودکار GPS");root.addView(gpsStop);gpsStop.setOnClickListener(v->stopGpsTracking());
        gpsStatus=text("",15);root.addView(gpsStatus);updateGpsStatus();
        status=text("",16);root.addView(status);
        root.addView(text("تنظیم اعلان‌ها",20));
        notifications=new CheckBox(this);notifications.setText("روشن بودن اعلان‌های یادآوری");
        notifications.setTextSize(16);notifications.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff167447));
        notifications.setChecked(p.getBoolean("notifications_enabled",true));root.addView(notifications);
        notifications.setOnCheckedChangeListener((v,checked)->{
            p.edit().putBoolean("notifications_enabled",checked).apply();
            if(checked){requestNotif();scheduleAnnual(this);checkMileageAlerts();}
            else cancelAnnual(this);
            Toast.makeText(this,checked?"اعلان‌ها روشن شد":"اعلان‌ها خاموش شد",Toast.LENGTH_SHORT).show();
        });
        root.addView(text("میانگین کیلومتر رانندگی در روز:",15));
        averageInput=new EditText(this);averageInput.setSingleLine(true);averageInput.setInputType(2);
        averageInput.setText(String.valueOf(p.getInt("average_daily_km",225)));
        averageInput.setHint("مثلاً ۲۲۵");root.addView(averageInput);
        Button saveAverage=button("ثبت میانگین روزانه");root.addView(saveAverage);
        saveAverage.setOnClickListener(v->saveAverage());

        root.addView(text("ثبت سرویس انجام‌شده",20));
        Button oil=button("ثبت انجام تعویض روغن و فیلتر روغن");root.addView(oil);oil.setOnClickListener(v->markOil());
        Button air=button("ثبت انجام تعویض فیلتر هوا");root.addView(air);
        air.setOnClickListener(v->{
            int next=p.getInt("next_air",81500),now=p.getInt("current_odo",59000);
            int newDue=Math.max(next,now)+22000;
            p.edit().putInt("next_air",newDue).putInt("alerted_air_due",0).apply();
            refresh();Toast.makeText(this,"تعویض فیلتر هوا ثبت شد",Toast.LENGTH_SHORT).show();
        });
        Button gear=button("تنظیم تاریخ آخرین تعویض روغن گیربکس");root.addView(gear);gear.setOnClickListener(v->chooseGearDate());
        Button refresh=button("به‌روزرسانی وضعیت");root.addView(refresh);refresh.setOnClickListener(v->refresh());
        root.addView(text("هشدار روغن و فیلترها بر اساس میانگین کیلومتر روزانه، حدود ۳ روز پیش از موعد محاسبه می‌شود. برای به‌روز ماندن هشدارها، کیلومتر فعلی را مرتب ثبت کن.",14));
        root.addView(text("روغن و فیلتر روغن هر ۵۵۰۰ کیلومتر؛ فیلتر هوا هر ۴ نوبت تعویض روغن؛ روغن گیربکس سالی یک‌بار.",14));
        setContentView(scroll);refresh();
    }

    @Override protected void onResume(){super.onResume();if(p!=null){refresh();updateGpsStatus();}}
    void updateGpsStatus(){
        if(gpsStatus!=null)gpsStatus.setText(p.getBoolean("gps_tracking",false)?"وضعیت GPS: ثبت خودکار روشن است":"وضعیت GPS: خاموش؛ ثبت دستی فعال است");
    }
    void startGpsTracking(){
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},21);
            return;
        }
        if(Build.VERSION.SDK_INT>=29&&!android.location.LocationManager.GPS_PROVIDER.equals("")){
            android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
            if(lm==null||!lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)){
                Toast.makeText(this,"ابتدا موقعیت مکانی و GPS گوشی را روشن کن",Toast.LENGTH_LONG).show();return;
            }
        }
        Intent i=new Intent(this,GpsTrackingService.class);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        p.edit().putBoolean("gps_tracking",true).apply();updateGpsStatus();
        Toast.makeText(this,"ثبت خودکار کیلومتر با GPS شروع شد",Toast.LENGTH_LONG).show();
    }
    void stopGpsTracking(){
        stopService(new Intent(this,GpsTrackingService.class));
        p.edit().putBoolean("gps_tracking",false).apply();updateGpsStatus();
        Toast.makeText(this,"ثبت خودکار متوقف شد؛ ثبت دستی همچنان فعال است",Toast.LENGTH_SHORT).show();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==21&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startGpsTracking();
        else if(requestCode==21)Toast.makeText(this,"مجوز موقعیت داده نشد؛ می‌توانی کیلومتر را دستی ثبت کنی",Toast.LENGTH_LONG).show();
    }
    void requestNotif(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},9);
    }
    void saveAverage(){
        try{
            int n=Integer.parseInt(averageInput.getText().toString().replaceAll("[^0-9]",""));
            if(n<1||n>1000)throw new Exception();
            p.edit().putInt("average_daily_km",n).apply();
            checkMileageAlerts();refresh();
            Toast.makeText(this,"میانگین روزانه ثبت شد",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"میانگین را بین ۱ تا ۱۰۰۰ وارد کن",Toast.LENGTH_SHORT).show();}
    }
    void saveOdo(){
        try{
            int n=Integer.parseInt(odoInput.getText().toString().replaceAll("[^0-9]",""));
            if(n<0)throw new Exception();
            p.edit().putInt("current_odo",n).apply();
            checkMileageAlerts();refresh();
        }catch(Exception e){Toast.makeText(this,"عدد کیلومتر را درست وارد کن",Toast.LENGTH_SHORT).show();}
    }
    void checkMileageAlerts(){
        if(!p.getBoolean("notifications_enabled",true))return;
        int now=p.getInt("current_odo",59000);
        int lead=p.getInt("average_daily_km",225)*3;
        int oil=p.getInt("next_oil",65000);
        int air=p.getInt("next_air",81500);
        if(now>=oil-lead && p.getInt("alerted_oil_due",0)!=oil){
            notifyNow(this,10,"نزدیک شدن موعد تعویض روغن","حدود ۳ روز تا تعویض روغن ۱۰W-40 و فیلتر روغن باقی مانده است. کیلومتر موعد: "+oil);
            p.edit().putInt("alerted_oil_due",oil).apply();
        }
        if(now>=air-lead && p.getInt("alerted_air_due",0)!=air){
            notifyNow(this,11,"نزدیک شدن موعد فیلتر هوا","حدود ۳ روز تا موعد تعویض فیلتر هوا باقی مانده است. کیلومتر موعد: "+air);
            p.edit().putInt("alerted_air_due",air).apply();
        }
    }
    void markOil(){
        int now=p.getInt("current_odo",59000),count=p.getInt("oil_count",0)+1;
        int next=now+5500,air=p.getInt("next_air",81500);
        if(count%4==0)air=now;
        p.edit().putInt("oil_count",count).putInt("next_oil",next).putInt("next_air",air)
            .putInt("alerted_oil_due",0).apply();
        refresh();checkMileageAlerts();
        Toast.makeText(this,"تعویض ثبت شد؛ سررسید بعدی "+next,Toast.LENGTH_LONG).show();
    }
    void refresh(){
        if(status==null)return;
        int now=p.getInt("current_odo",59000),oil=p.getInt("next_oil",65000),air=p.getInt("next_air",81500);
        int lead=p.getInt("average_daily_km",225)*3;
        long gd=p.getLong("gear_date",System.currentTimeMillis());
        Calendar c=Calendar.getInstance();c.setTimeInMillis(gd);c.add(Calendar.YEAR,1);
        String date=new SimpleDateFormat("yyyy/MM/dd",Locale.getDefault()).format(c.getTime());
        status.setText("کیلومتر ثبت‌شده: "+now+
            "\nروغن و فیلتر بعدی: "+oil+" (حدود "+(oil-now)+" کیلومتر مانده)"+
            "\nشروع هشدار روغن: "+(oil-lead)+
            "\nفیلتر هوا: "+air+" (حدود "+(air-now)+" کیلومتر مانده)"+
            "\nشروع هشدار فیلتر هوا: "+(air-lead)+
            "\nسررسید سالیانه گیربکس: "+date);
    }
    void chooseGearDate(){
        Calendar c=Calendar.getInstance();
        new DatePickerDialog(this,(v,y,m,d)->{
            Calendar x=Calendar.getInstance();x.set(y,m,d,9,0,0);
            p.edit().putLong("gear_date",x.getTimeInMillis()).apply();
            scheduleAnnual(this);refresh();
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    static void notifyNow(Context c,int id,String title,String body){
        Intent i=new Intent(c,ReminderReceiver.class);i.putExtra("id",id);i.putExtra("title",title);i.putExtra("body",body);c.sendBroadcast(i);
    }
    static void cancelAnnual(Context c){
        Intent i=new Intent(c,ReminderReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(c,12,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)c.getSystemService(ALARM_SERVICE);if(am!=null)am.cancel(pi);
    }
    static void scheduleAnnual(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,0);
        Intent i=new Intent(c,ReminderReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(c,12,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)c.getSystemService(ALARM_SERVICE);if(am==null)return;
        if(!p.getBoolean("notifications_enabled",true)){am.cancel(pi);return;}
        long first=p.getLong("gear_date",System.currentTimeMillis());
        Calendar due=Calendar.getInstance();due.setTimeInMillis(first);due.add(Calendar.YEAR,1);
        while(due.getTimeInMillis()<=System.currentTimeMillis())due.add(Calendar.YEAR,1);
        i.putExtra("id",12);i.putExtra("title","یادآوری روغن گیربکس");
        i.putExtra("body","یک سال از تاریخ ثبت‌شده گذشته؛ روغن گیربکس را بررسی کن.");
        pi=PendingIntent.getBroadcast(c,12,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due.getTimeInMillis(),pi);
        else am.set(AlarmManager.RTC_WAKEUP,due.getTimeInMillis(),pi);
    }
}
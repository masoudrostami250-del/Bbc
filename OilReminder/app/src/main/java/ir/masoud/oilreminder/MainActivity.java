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
    EditText odoInput; TextView status; android.content.SharedPreferences p;
    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff202020);t.setPadding(0,dp(7),0,dp(7));return t;}
    Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);p=getSharedPreferences(PREF,0);ReminderReceiver.createChannel(this);requestNotif();
        if(!p.contains("next_oil")){p.edit().putInt("current_odo",59000).putInt("next_oil",65000).putInt("next_air",81500).putInt("oil_count",0).putLong("gear_date",System.currentTimeMillis()).apply();}
        scheduleAnnual(this);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(22),dp(20),dp(24));root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setBackgroundColor(0xfff5f7f5);scroll.addView(root);
        TextView title=text("تعویض روغن ۱۰و۴۰",25);title.setTypeface(null,Typeface.BOLD);title.setTextColor(0xff145c38);root.addView(title);
        root.addView(text("خودرو: پراید ۱۳۸۵ دوگانه‌سوز | روغن ۱۰W-40",15));root.addView(text("کیلومتر فعلی خودرو را وارد کن:",16));
        odoInput=new EditText(this);odoInput.setSingleLine(true);odoInput.setInputType(2);odoInput.setText(String.valueOf(p.getInt("current_odo",59000)));odoInput.setHint("مثلاً ۶۵۰۰۰");root.addView(odoInput);
        Button update=button("ثبت کیلومتر و بررسی سررسید");root.addView(update);update.setOnClickListener(v->saveOdo());
        status=text("",16);root.addView(status);root.addView(text("وضعیت سرویس‌ها",20));
        Button oil=button("ثبت انجام تعویض روغن و فیلتر روغن");root.addView(oil);oil.setOnClickListener(v->markOil());
        Button air=button("ثبت انجام تعویض فیلتر هوا");root.addView(air);air.setOnClickListener(v->{int next=p.getInt("next_air",81500),now=p.getInt("current_odo",59000);p.edit().putInt("next_air",Math.max(next,now)+22000).apply();refresh();Toast.makeText(this,"تعویض فیلتر هوا ثبت شد",Toast.LENGTH_SHORT).show();});
        Button gear=button("تنظیم تاریخ تعویض روغن گیربکس");root.addView(gear);gear.setOnClickListener(v->chooseGearDate());
        Button refresh=button("به‌روزرسانی وضعیت");root.addView(refresh);refresh.setOnClickListener(v->refresh());
        root.addView(text("یادآوری‌ها: روغن و فیلتر هر ۵۵۰۰ کیلومتر؛ فیلتر هوا هر ۴ نوبت تعویض روغن؛ روغن گیربکس سالی یک‌بار.",14));
        setContentView(scroll);refresh();
    }
    void requestNotif(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},9);}
    void saveOdo(){try{int n=Integer.parseInt(odoInput.getText().toString().replaceAll("[^0-9]",""));if(n<0)throw new Exception();p.edit().putInt("current_odo",n).apply();refresh();if(n>=p.getInt("next_oil",65000))notifyNow(this,10,"موعد تعویض روغن","روغن ۱۰W-40 و فیلتر روغن سررسید شده است.");if(n>=p.getInt("next_air",81500))notifyNow(this,11,"موعد فیلتر هوا","فیلتر هوا سررسید شده است.");}catch(Exception e){Toast.makeText(this,"عدد کیلومتر را درست وارد کن",Toast.LENGTH_SHORT).show();}}
    void markOil(){int now=p.getInt("current_odo",59000),count=p.getInt("oil_count",0)+1,next=now+5500,air=p.getInt("next_air",81500);if(count%4==0)air=now;p.edit().putInt("oil_count",count).putInt("next_oil",next).putInt("next_air",air).apply();refresh();Toast.makeText(this,"تعویض ثبت شد؛ سررسید بعدی "+next,Toast.LENGTH_LONG).show();}
    void refresh(){if(status==null)return;int now=p.getInt("current_odo",59000),oil=p.getInt("next_oil",65000),air=p.getInt("next_air",81500);long gd=p.getLong("gear_date",System.currentTimeMillis());Calendar c=Calendar.getInstance();c.setTimeInMillis(gd);c.add(Calendar.YEAR,1);String date=new SimpleDateFormat("yyyy/MM/dd",Locale.getDefault()).format(c.getTime());status.setText("کیلومتر ثبت‌شده: "+now+"\nروغن و فیلتر بعدی: "+oil+" ("+(oil-now)+" کیلومتر مانده)\nفیلتر هوا: "+air+" ("+(air-now)+" کیلومتر مانده)\nسررسید سالیانه گیربکس: "+date);}
    void chooseGearDate(){Calendar c=Calendar.getInstance();new DatePickerDialog(this,(v,y,m,d)->{Calendar x=Calendar.getInstance();x.set(y,m,d,9,0,0);p.edit().putLong("gear_date",x.getTimeInMillis()).apply();scheduleAnnual(this);refresh();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    static void notifyNow(Context c,int id,String title,String body){Intent i=new Intent(c,ReminderReceiver.class);i.putExtra("id",id);i.putExtra("title",title);i.putExtra("body",body);c.sendBroadcast(i);}
    static void scheduleAnnual(Context c){android.content.SharedPreferences p=c.getSharedPreferences(PREF,0);long first=p.getLong("gear_date",System.currentTimeMillis());Calendar due=Calendar.getInstance();due.setTimeInMillis(first);due.add(Calendar.YEAR,1);while(due.getTimeInMillis()<=System.currentTimeMillis())due.add(Calendar.YEAR,1);Intent i=new Intent(c,ReminderReceiver.class);i.putExtra("id",12);i.putExtra("title","یادآوری روغن گیربکس");i.putExtra("body","یک سال از تاریخ ثبت‌شده گذشته؛ روغن گیربکس را بررسی کن.");PendingIntent pi=PendingIntent.getBroadcast(c,12,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager am=(AlarmManager)c.getSystemService(ALARM_SERVICE);if(am!=null)if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due.getTimeInMillis(),pi);else am.set(AlarmManager.RTC_WAKEUP,due.getTimeInMillis(),pi);}
}
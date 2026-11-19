package ir.masoud.oilreminder;
import android.content.*;
public class BootReceiver extends BroadcastReceiver { @Override public void onReceive(Context c, Intent i) { MainActivity.scheduleAnnual(c); } }

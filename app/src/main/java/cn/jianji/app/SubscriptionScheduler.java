package cn.jianji.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.time.LocalDate;
import java.time.ZoneId;

public final class SubscriptionScheduler {
    public static final String CHANGED="cn.jianji.app.SUBSCRIPTIONS_CHANGED";
    private SubscriptionScheduler(){}
    public static void sync(Context context){
        try(LedgerDb db=new LedgerDb(context.getApplicationContext())){
            db.applyDueSubscriptions(LocalDate.now(),1000);schedule(context,db);
            context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
        }
    }
    public static void schedule(Context context,LedgerDb db){
        AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(alarms==null)return;
        Intent intent=new Intent(context,SubscriptionReceiver.class).setAction("cn.jianji.app.SUBSCRIPTION_DUE");
        PendingIntent pending=PendingIntent.getBroadcast(context,100,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        LocalDate earliest=null;
        if(db.hasBalance())for(Subscription s:db.subscriptions(false)){
            LocalDate due=s.nextDate();if(due!=null&&(earliest==null||due.isBefore(earliest)))earliest=due;
        }
        if(earliest==null){alarms.cancel(pending);return;}
        long at=earliest.atTime(0,5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        at=Math.max(at,System.currentTimeMillis()+60_000L);
        try{alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending);}
        catch(RuntimeException e){android.util.Log.w("JianJi","Background scheduling unavailable; sync on next app open",e);}
    }
}

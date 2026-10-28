package cn.jianji.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SubscriptionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        PendingResult result=goAsync();Context app=context.getApplicationContext();
        new Thread(()->{try{SubscriptionScheduler.sync(app);}catch(Exception e){android.util.Log.e("JianJi","Subscription sync failed",e);}finally{result.finish();}},"jianji-subscriptions").start();
    }
}

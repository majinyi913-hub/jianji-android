package cn.jianji.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.TextView;
import cn.jianji.app.EntryActivity;
import cn.jianji.app.MainActivity;
import cn.jianji.app.Ledger;
import cn.jianji.app.LedgerDb;
import cn.jianji.app.Subscription;
import cn.jianji.app.SubscriptionActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.YearMonth;

/** Runs on an isolated test emulator; do not run against a user's populated phone. */
public final class LedgerInstrumentation extends Instrumentation {
    private int checks;
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        subscriptionTests();balanceTests();dbTests();setupUiTests();uiTests();subscriptionUiTests();result.putString("stream","\nPASS: "+checks+" Android database, subscription, balance, backup and UI checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable t){result.putString("stream","\nFAIL: "+t+"\n"+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
    private void dbTests() throws Exception{
        getTargetContext().deleteDatabase("jianji.db");
        Ledger.Entry e=new Ledger.Entry();e.cents=1234;e.note="自动测试";e.subcategory="早餐";
        String thisMonth=YearMonth.now().toString();String previous=YearMonth.now().minusMonths(1).toString();
        try(LedgerDb db=new LedgerDb(getTargetContext())){
            db.save(e);check(db.count()==1,"insert");check(db.find(e.id).cents==1234,"amount round trip");
            e.cents=2001;db.save(e);check(db.count()==1 && db.find(e.id).cents==2001,"update");
            check(db.list(thisMonth).size()==1 && db.list(previous).isEmpty(),"month filtering");
            db.setBudget(thisMonth,10000);String backup=db.backup();db.delete(e.id);check(db.count()==0,"delete");
            int[] restored=db.restore(backup);check(restored[0]==1 && restored[1]==0,"restore");
            check(new JSONObject(backup).getLong("version")==4 && db.find(e.id).subcategory.equals("早餐"),"subcategory backup round trip");
            e.note="保留本机修改";db.save(e);db.setBudget(thisMonth,25000);restored=db.restore(backup);
            check(restored[0]==0 && restored[1]==1,"duplicate skip");check(db.find(e.id).note.equals("保留本机修改"),"preserve existing entry");check(db.budget(thisMonth)==25000,"preserve budget");
            JSONObject bad=new JSONObject(backup);JSONArray arr=bad.getJSONArray("entries");JSONObject fresh=new JSONObject(arr.getJSONObject(0).toString());fresh.put("id","new-valid-test");arr.put(fresh);JSONObject invalid=new JSONObject(fresh.toString());invalid.put("id","new-invalid-test");invalid.put("cents",0);arr.put(invalid);
            boolean rejected=false;try{db.restore(bad.toString());}catch(Exception expected){rejected=true;}check(rejected && db.count()==1 && db.find("new-valid-test")==null,"atomic invalid import");
            bad=new JSONObject(backup);bad.getJSONArray("entries").getJSONObject(0).put("cents",1.23);rejected=false;try{db.restore(bad.toString());}catch(Exception expected){rejected=true;}check(rejected,"reject fractional cents");
            JSONObject oldBackup=new JSONObject(backup);oldBackup.put("version",1);JSONObject oldEntry=oldBackup.getJSONArray("entries").getJSONObject(0);oldEntry.put("id","legacy-backup");oldEntry.remove("subcategory");
            db.restore(oldBackup.toString());check(db.find("legacy-backup").subcategory.isEmpty(),"version-1 backup compatibility");db.delete("legacy-backup");
            oldBackup=new JSONObject(backup);oldBackup.put("version",2);oldBackup.remove("balance");oldBackup.getJSONArray("entries").getJSONObject(0).put("id","v2-backup");
            db.restore(oldBackup.toString());check(db.find("v2-backup").subcategory.equals("早餐")&&!db.hasBalance(),"version-2 backup compatibility without balance");db.delete("v2-backup");
            oldBackup=new JSONObject(backup);oldBackup.put("version",3);oldBackup.remove("subscriptions");oldBackup.getJSONArray("entries").getJSONObject(0).put("id","home-v3").put("category","居家").put("subcategory","");
            db.restore(oldBackup.toString());check(db.find("home-v3").category.equals("其他支出"),"legacy home category import");db.delete("home-v3");
            e.date=previous+"-01";db.save(e);check(db.list(thisMonth).isEmpty() && db.list(previous).size()==1,"month move after edit");
        }
        try(LedgerDb reopened=new LedgerDb(getTargetContext())){check(reopened.find(e.id).cents==2001,"persists after reopen");reopened.delete(e.id);}
    }
    private void subscriptionTests() throws Exception{
        getTargetContext().deleteDatabase("jianji.db");String snapshot;String planId;
        try(LedgerDb db=new LedgerDb(getTargetContext())){
            db.setCurrentBalance(10000);Subscription s=new Subscription();s.name="视频会员";s.category="娱乐";s.cents=100;s.billingDay=31;s.startMonth="2024-01";s.nextMonth=s.startMonth;planId=s.id;db.saveSubscription(s);
            check(db.applyDueSubscriptions(java.time.LocalDate.of(2024,3,31),1000)==3,"catch up three due months");
            check(db.currentBalance()==9700&&db.count()==3,"automatic charges reduce balance");
            check(db.applyDueSubscriptions(java.time.LocalDate.of(2024,3,31),1000)==0,"repeated scheduler is idempotent");
            String feb=Subscription.entryId(s.id,YearMonth.of(2024,2));check(db.find(feb).date.equals("2024-02-29"),"31st uses leap month end");db.delete(feb);
            check(db.applyDueSubscriptions(java.time.LocalDate.of(2024,3,31),1000)==0&&db.find(feb)==null&&db.currentBalance()==9800,"deleted charge is not regenerated");
            s.cents=200;s.billingDay=15;db.saveSubscription(s);check(db.subscription(s.id).nextMonth.equals("2024-04"),"editing preserves billing cursor");
            check(db.applyDueSubscriptions(java.time.LocalDate.of(2024,4,15),1000)==1&&db.currentBalance()==9600,"new terms apply to next period");
            snapshot=db.backup();
        }
        getTargetContext().deleteDatabase("jianji.db");
        try(LedgerDb db=new LedgerDb(getTargetContext())){
            db.restore(snapshot);check(db.currentBalance()==9600&&db.subscriptions(false).size()==1,"backup restores plan and balance");
            check(db.applyDueSubscriptions(java.time.LocalDate.of(2024,4,30),1000)==0&&db.count()==3,"backup preserves progress and deleted gap");
            db.restore(snapshot);check(db.count()==3&&db.currentBalance()==9600,"duplicate plan import does not double charge");
            db.deleteSubscription(planId);check(db.subscriptions(false).isEmpty()&&db.count()==3&&db.currentBalance()==9600,"plan deletion keeps history and balance");
            check(db.applyDueSubscriptions(java.time.LocalDate.of(2026,12,31),1000)==0,"deleted plan never charges later");
            db.restore(snapshot);check(db.subscriptions(false).isEmpty(),"deleted plan tombstone wins on merge");
            JSONObject bad=new JSONObject(snapshot);bad.getJSONArray("subscriptions").getJSONObject(0).put("billingDay",32);boolean rejected=false;try{db.restore(bad.toString());}catch(Exception expected){rejected=true;}check(rejected&&db.currentBalance()==9600,"invalid plan backup leaves local state intact");
            snapshot=db.backup();
        }
        getTargetContext().deleteDatabase("jianji.db");
        try(LedgerDb db=new LedgerDb(getTargetContext())){db.restore(snapshot);check(db.subscriptions(false).isEmpty()&&db.subscriptions(true).size()==1,"deletion tombstone survives device restore");}
    }
    private void balanceTests() throws Exception {
        getTargetContext().deleteDatabase("jianji.db");String snapshot;
        try(LedgerDb db=new LedgerDb(getTargetContext())){
            check(!db.hasBalance(),"fresh install requires initialization");
            Ledger.Entry old=new Ledger.Entry();old.type="income";old.category="工资";old.cents=1000;db.save(old);
            db.setCurrentBalance(50000);check(db.currentBalance()==50000&&db.count()==1,"initial current balance absorbs old entries");
            Ledger.Entry expense=new Ledger.Entry();expense.cents=300;db.save(expense);check(db.currentBalance()==49700,"expense reduces balance");
            expense.cents=500;db.save(expense);check(db.currentBalance()==49500,"edit amount updates balance");
            expense.type="income";expense.category="奖金";expense.cents=700;db.save(expense);check(db.currentBalance()==50700,"type change updates balance");
            db.delete(expense.id);check(db.currentBalance()==50000,"delete reverses entry effect");
            db.setCurrentBalance(0);check(db.hasBalance()&&db.currentBalance()==0&&db.count()==1,"zero balance remains initialized");
            db.setCurrentBalance(15000);snapshot=db.backup();
        }
        try(LedgerDb db=new LedgerDb(getTargetContext())){check(db.currentBalance()==15000,"balance persists after reopen");}
        getTargetContext().deleteDatabase("jianji.db");
        try(LedgerDb db=new LedgerDb(getTargetContext())){
            db.restore(snapshot);check(db.hasBalance()&&db.currentBalance()==15000,"fresh backup restore restores balance");
            db.restore(snapshot);check(db.currentBalance()==15000&&db.count()==1,"duplicate restore does not double count");
            db.setCurrentBalance(20000);db.restore(snapshot);check(db.currentBalance()==20000,"merge preserves local calibrated baseline");
            JSONObject bad=new JSONObject(snapshot);bad.getJSONObject("balance").put("baseCents",Ledger.MAX_BALANCE_BASE+1);
            boolean rejected=false;try{db.restore(bad.toString());}catch(Exception expected){rejected=true;}
            check(rejected&&db.currentBalance()==20000&&db.count()==1,"invalid balance import leaves local data unchanged");
        }
    }
    private Activity launch(Class<?> cls,String id){Intent i=new Intent(getTargetContext(),cls);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(id!=null)i.putExtra("id",id);return startActivitySync(i);}
    private View text(View v,String label){if(v instanceof TextView && label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=text(g.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private void click(Activity a,String label){runOnMainSync(()->{View v=text(a.getWindow().getDecorView(),label);while(v!=null&&!v.isClickable())v=v.getParent() instanceof View?(View)v.getParent():null;if(v==null)throw new AssertionError("Missing clickable UI: "+label);v.performClick();});waitForIdleSync();}
    private void setupUiTests(){
        Activity main=launch(MainActivity.class,null);waitForIdleSync();
        check(text(main.getWindow().getDecorView(),"初始化余额，开始记账")!=null,"initialization screen shown before ledger");
        check(text(main.getWindow().getDecorView(),"＋  记一笔")==null,"recording unavailable before initialization");
        click(main,"初始化余额，开始记账");try(LedgerDb db=new LedgerDb(getTargetContext())){check(!db.hasBalance(),"blank initialization rejected");}
        runOnMainSync(()->((EditText)main.findViewById(103)).setText("0"));click(main,"初始化余额，开始记账");
        try(LedgerDb db=new LedgerDb(getTargetContext())){check(db.hasBalance()&&db.currentBalance()==0,"explicit zero accepted");}
        check(text(main.getWindow().getDecorView(),"目前存款 · 元")!=null,"current balance displayed on home");runOnMainSync(()->main.finish());waitForIdleSync();
    }
    private void uiTests(){
        Activity add=launch(EntryActivity.class,null);waitForIdleSync();
        check(text(add.getWindow().getDecorView(),"居家")==null&&text(add.getWindow().getDecorView(),Ledger.MONTHLY)!=null,"home removed and subscription option present");
        click(add,"购物");for(String c:new String[]{"日用品","赛车模拟器","钓鱼","电动车","摩托车"})check(text(add.getWindow().getDecorView(),c)!=null,"shopping option "+c);click(add,"餐饮");
        runOnMainSync(()->{TextView label=(TextView)text(add.getWindow().getDecorView(),"餐饮");check((label.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.CENTER_HORIZONTAL,"category text centered");TextView option=(TextView)text(add.getWindow().getDecorView(),"电动车充电");check(option==null,"travel options hidden for dining");check(text(add.getWindow().getDecorView(),"饮用水")!=null,"five dining options present");});
        runOnMainSync(()->((EditText)add.findViewById(101)).setText("0"));click(add,"保存记录");
        try(LedgerDb db=new LedgerDb(getTargetContext())){check(db.count()==0,"invalid form not saved");}
        click(add,"收入");runOnMainSync(()->{((EditText)add.findViewById(101)).setText("888.88");((EditText)add.findViewById(102)).setText("UI 收入测试");});click(add,"保存记录");
        String id;
        try(LedgerDb db=new LedgerDb(getTargetContext())){check(db.count()==1,"UI save");Ledger.Entry saved=db.list(null).get(0);id=saved.id;check(saved.cents==88888 && saved.income() && saved.category.equals("工资"),"UI type and amount");}
        Activity edit=launch(EntryActivity.class,id);waitForIdleSync();runOnMainSync(()->{check(((EditText)edit.findViewById(101)).getText().toString().equals("888.88"),"edit preload");((EditText)edit.findViewById(101)).setText("25.50");});click(edit,"支出");click(edit,"早餐");click(edit,"保存修改");
        try(LedgerDb db=new LedgerDb(getTargetContext())){Ledger.Entry saved=db.find(id);check(db.count()==1 && saved.cents==2550 && !saved.income() && saved.category.equals("餐饮") && saved.subcategory.equals("早餐"),"UI edit updates same record and subcategory");check(db.currentBalance()==-2550,"UI recording updates current balance");}
        Activity tagged=launch(EntryActivity.class,id);waitForIdleSync();runOnMainSync(()->check(text(tagged.getWindow().getDecorView(),"早餐").isSelected(),"subcategory preselected when editing"));runOnMainSync(()->tagged.finish());waitForIdleSync();
        Activity main=launch(MainActivity.class,null);waitForIdleSync();click(main,"统计");check(text(main.getWindow().getDecorView(),"钱都花在哪儿")!=null,"stats screen");click(main,"我的");check(text(main.getWindow().getDecorView(),"导出备份")!=null,"backup screen");click(main,"明细");check(text(main.getWindow().getDecorView(),"餐饮")!=null,"ledger entry visible");
        runOnMainSync(()->main.finish());waitForIdleSync();
    }
    private void subscriptionUiTests(){
        Activity form=launch(SubscriptionActivity.class,null);waitForIdleSync();
        runOnMainSync(()->{((EditText)form.findViewById(201)).setText("UI会员");((EditText)form.findViewById(202)).setText("30.00");});click(form,"保存自动订阅");
        try(LedgerDb db=new LedgerDb(getTargetContext())){check(db.subscriptions(false).size()==1,"UI creates recurring plan");check(db.currentBalance()==-5550,"today billing day updates balance immediately");check(Ledger.filter(db.list(YearMonth.now().toString()),1,"","","",2).size()==1,"automatic charges separately filterable");}
    }
}

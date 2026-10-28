package cn.jianji.app;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SubscriptionTest {
    private static int checks;
    private static void eq(Object a,Object b){checks++;if(!a.equals(b))throw new AssertionError("Expected "+a+", got "+b);}
    private static void reject(Runnable fn){checks++;try{fn.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection");}
    private static Subscription plan(){Subscription s=new Subscription();s.id="test-service";s.name="视频会员";s.category="娱乐";s.cents=2500;s.billingDay=31;s.startMonth="2026-01";s.nextMonth=s.startMonth;return s;}
    public static void main(String[] args){
        eq(false,Arrays.asList(Ledger.EXPENSE).contains("居家"));eq("其他支出",Ledger.normalizeCategory("居家"));eq("购物",Ledger.normalizeCategory("购物"));
        for(String c:Ledger.EXPENSE){eq(true,Arrays.asList(Ledger.subcategories(c)).contains(Ledger.MONTHLY));Ledger.Entry e=new Ledger.Entry();e.cents=1;e.category=c;e.subcategory=Ledger.MONTHLY;Ledger.validate(e);checks++;}
        for(String c:new String[]{"日用品","赛车模拟器","钓鱼","电动车","摩托车"})eq(true,Arrays.asList(Ledger.subcategories("购物")).contains(c));
        eq(9,Ledger.subcategories("购物").length);
        for(int y=1900;y<=2100;y++)for(int m=1;m<=12;m++){
            YearMonth month=YearMonth.of(y,m);eq(month.atEndOfMonth(),Subscription.dueDate(month,31));
        }
        eq(LocalDate.of(2024,2,29),Subscription.dueDate(YearMonth.of(2024,2),29));eq(LocalDate.of(2100,2,28),Subscription.dueDate(YearMonth.of(2100,2),29));
        eq(LocalDate.of(2026,4,30),Subscription.dueDate(YearMonth.of(2026,4),31));eq(LocalDate.of(2026,5,31),Subscription.dueDate(YearMonth.of(2026,5),31));
        reject(()->Subscription.dueDate(YearMonth.now(),0));reject(()->Subscription.dueDate(YearMonth.now(),32));
        eq(YearMonth.of(2026,11),Subscription.nextStart(LocalDate.of(2026,10,28),15));eq(YearMonth.of(2026,10),Subscription.nextStart(LocalDate.of(2026,10,28),28));
        Subscription s=plan();Subscription.validate(s);checks++;
        eq(0,Subscription.dueMonths(s,LocalDate.of(2026,1,30),100).size());eq(1,Subscription.dueMonths(s,LocalDate.of(2026,1,31),100).size());
        eq(Arrays.asList(YearMonth.of(2026,1),YearMonth.of(2026,2),YearMonth.of(2026,3)),Subscription.dueMonths(s,LocalDate.of(2026,3,31),100));
        eq(2,Subscription.dueMonths(s,LocalDate.of(2026,3,30),100).size());eq(2,Subscription.dueMonths(s,LocalDate.of(2026,3,31),2).size());eq(0,Subscription.dueMonths(s,LocalDate.now(),0).size());
        Ledger.Entry charge=Subscription.charge(s,YearMonth.of(2026,2));eq("2026-02-28",charge.date);eq(2500L,charge.cents);eq(true,charge.automated());eq(true,charge.subscription());eq("2026-02",charge.subscriptionMonth);eq("娱乐",charge.category);eq(Ledger.MONTHLY,charge.subcategory);
        eq(charge.id,Subscription.charge(s,YearMonth.of(2026,2)).id);eq(false,charge.id.equals(Subscription.charge(s,YearMonth.of(2026,3)).id));
        s.cents=3100;eq(charge.id,Subscription.charge(s,YearMonth.of(2026,2)).id);eq(3100L,Subscription.charge(s,YearMonth.of(2026,3)).cents);
        s.nextMonth="2026-04";eq(0,Subscription.dueMonths(s,LocalDate.of(2026,3,31),100).size());eq(LocalDate.of(2026,4,30),s.nextDate());
        s.deletedAt=1;eq(0,Subscription.dueMonths(s,LocalDate.of(2028,1,1),100).size());eq(true,s.nextDate()==null);s.deletedAt=0;
        s.nextMonth="2101-01";eq(true,s.nextDate()==null);eq(0,Subscription.dueMonths(s,LocalDate.of(2101,2,1),100).size());
        s=plan();s.name=String.join("",Collections.nCopies(40,"字"));s.note=String.join("",Collections.nCopies(140,"字"));Ledger.Entry longNote=Subscription.charge(s,YearMonth.of(2026,1));eq(true,longNote.note.length()<=200);
        final Subscription bad=plan();bad.name="";reject(()->Subscription.validate(bad));bad.name="服务";bad.cents=0;reject(()->Subscription.validate(bad));bad.cents=1;bad.billingDay=32;reject(()->Subscription.validate(bad));bad.billingDay=1;bad.category="居家";reject(()->Subscription.validate(bad));bad.category="娱乐";bad.startMonth="2026-13";reject(()->Subscription.validate(bad));bad.startMonth="2026-01";bad.nextMonth="2025-12";reject(()->Subscription.validate(bad));bad.nextMonth="2101-02";reject(()->Subscription.validate(bad));bad.nextMonth="2026-01";bad.deletedAt=-1;reject(()->Subscription.validate(bad));
        Ledger.Entry manual=new Ledger.Entry();manual.cents=100;manual.category="购物";manual.subcategory=Ledger.MONTHLY;Ledger.Entry ordinary=new Ledger.Entry();ordinary.cents=200;
        List<Ledger.Entry> rows=Arrays.asList(charge,manual,ordinary);eq(3,Ledger.filter(rows,1,"","","",0).size());eq(2,Ledger.filter(rows,1,"","","",1).size());eq(1,Ledger.filter(rows,1,"","","",2).size());
        eq(2600L,Ledger.totals(Ledger.filter(rows,1,"","","",1))[1]);eq(2500L,Ledger.totals(Ledger.filter(rows,1,"","","",2))[1]);eq(1,Ledger.filter(rows,1,"购物",Ledger.MONTHLY,"",1).size());eq(0,Ledger.filter(rows,1,"购物","","",2).size());eq(1,Ledger.filter(rows,1,"","","视频会员",2).size());
        eq(true,Ledger.csv(rows).contains("自动订阅"));eq(true,Ledger.csv(rows).contains("手动订阅"));eq(true,Ledger.csv(rows).contains("\"2026-02\""));
        charge.id="wrong-id";reject(()->Ledger.validate(charge));
        System.out.println("PASS: "+checks+" subscription, calendar, recurring ID, origin filtering and shopping checks");
    }
}

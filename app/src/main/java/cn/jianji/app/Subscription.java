package cn.jianji.app;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A monthly local bookkeeping plan. The cursor survives deletion of individual charges. */
public final class Subscription {
    public String id=UUID.randomUUID().toString();
    public String name="",category=Ledger.EXPENSE[0],note="";
    public long cents,createdAt=System.currentTimeMillis(),deletedAt;
    public int billingDay=LocalDate.now().getDayOfMonth();
    public String startMonth=YearMonth.now().toString(),nextMonth=startMonth;

    public static LocalDate dueDate(YearMonth month,int day){
        if(day<1||day>31)throw new IllegalArgumentException("扣费日需为 1～31 日");
        return month.atDay(Math.min(day,month.lengthOfMonth()));
    }
    public static YearMonth nextStart(LocalDate today,int day){
        YearMonth m=YearMonth.from(today);return dueDate(m,day).isBefore(today)?m.plusMonths(1):m;
    }
    public static String entryId(String id,YearMonth month){
        return UUID.nameUUIDFromBytes(("jianji-subscription:"+id+":"+month).getBytes(StandardCharsets.UTF_8)).toString();
    }
    public static void validate(Subscription s){
        if(s.id==null||!s.id.matches("[A-Za-z0-9-]{1,64}"))throw new IllegalArgumentException("订阅标识无效");
        if(s.name==null||s.name.trim().isEmpty()||s.name.length()>40)throw new IllegalArgumentException("服务名称需为 1～40 字");
        boolean valid=false;for(String c:Ledger.EXPENSE)if(c.equals(s.category))valid=true;
        if(!valid)throw new IllegalArgumentException("订阅分类无效");
        if(s.cents<=0||s.cents>Ledger.MAX_CENTS)throw new IllegalArgumentException("订阅金额无效");
        if(s.billingDay<1||s.billingDay>31)throw new IllegalArgumentException("扣费日需为 1～31 日");
        if(!Ledger.validMonth(s.startMonth))throw new IllegalArgumentException("首次扣费月份无效");
        try{
            YearMonth next=YearMonth.parse(s.nextMonth);
            if(!s.nextMonth.matches("[0-9]{4}-[0-9]{2}")||next.isBefore(YearMonth.parse(s.startMonth))||next.isAfter(YearMonth.of(2101,1)))throw new IllegalArgumentException("订阅进度无效");
        }catch(java.time.format.DateTimeParseException|NullPointerException e){throw new IllegalArgumentException("订阅进度无效");}
        if(s.note==null||s.note.length()>140)throw new IllegalArgumentException("订阅备注最多 140 字");
        if(s.createdAt<0||s.deletedAt<0)throw new IllegalArgumentException("订阅时间无效");
    }
    public static List<YearMonth> dueMonths(Subscription s,LocalDate today,int limit){
        validate(s);List<YearMonth> result=new ArrayList<>();
        if(s.deletedAt>0||limit<=0)return result;
        YearMonth m=YearMonth.parse(s.nextMonth),end=YearMonth.from(today);
        while(!m.isAfter(end)&&m.getYear()<=2100&&result.size()<limit){
            if(dueDate(m,s.billingDay).isAfter(today))break;
            result.add(m);m=m.plusMonths(1);
        }
        return result;
    }
    public static Ledger.Entry charge(Subscription s,YearMonth month){
        validate(s);Ledger.Entry e=new Ledger.Entry();e.id=entryId(s.id,month);e.category=s.category;e.subcategory=Ledger.MONTHLY;e.cents=s.cents;
        e.date=dueDate(month,s.billingDay).toString();e.subscriptionId=s.id;e.subscriptionMonth=month.toString();
        e.note="自动订阅 · "+s.name+(s.note.isEmpty()?"":" · "+s.note);
        e.createdAt=LocalDate.parse(e.date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // Older selected dates can precede Unix epoch; creation time still represents this new record.
        e.createdAt=Math.max(0,e.createdAt);Ledger.validate(e);return e;
    }
    public LocalDate nextDate(){
        if(deletedAt>0||YearMonth.parse(nextMonth).getYear()>2100)return null;
        return dueDate(YearMonth.parse(nextMonth),billingDay);
    }
}

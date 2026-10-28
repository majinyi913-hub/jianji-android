package cn.jianji.app;

import java.util.Arrays;
import java.util.Collections;

public final class LedgerTest {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static void reject(Runnable fn){checks++;try{fn.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args){
        equal(1L,Ledger.parseAmount("0.01"));equal(12345L,Ledger.parseAmount("123.45"));equal(100L,Ledger.parseAmount(" 1 "));equal(9999999999L,Ledger.parseAmount("99999999.99"));
        for(String bad:new String[]{"","0","0.00","-1","1.234",".5","1.","1e2","1,000","NaN","100000000.00","￥12"})reject(()->Ledger.parseAmount(bad));
        equal("1,234.56",Ledger.money(123456));equal("-0.01",Ledger.money(-1));equal("0.01",Ledger.decimal(1));
        Ledger.Entry e=new Ledger.Entry();e.cents=10;e.date="2024-02-29";Ledger.validate(e);checks++;
        e.date="2025-02-29";reject(()->Ledger.validate(e));e.date="2026-10-28";
        e.cents=0;reject(()->Ledger.validate(e));e.cents=10;e.category="工资";reject(()->Ledger.validate(e));e.category="餐饮";
        e.note=String.join("",Collections.nCopies(201,"字"));reject(()->Ledger.validate(e));e.note="咖啡,\"拿铁\"\n第二行";
        Ledger.Entry inc=new Ledger.Entry();inc.type="income";inc.category="工资";inc.cents=101;
        long[] sum=Ledger.totals(Arrays.asList(e,inc));equal(101L,sum[0]);equal(10L,sum[1]);equal(91L,sum[2]);equal(10L,Ledger.categories(Arrays.asList(e,inc),false).get("餐饮"));equal(101L,Ledger.categories(Arrays.asList(e,inc),true).get("工资"));
        String csv=Ledger.csv(Arrays.asList(e,inc));equal(true,csv.startsWith("\uFEFF日期,"));equal(true,csv.contains("\"咖啡,\"\"拿铁\"\"\n第二行\""));inc.note="  =1+1";equal(true,Ledger.csv(Arrays.asList(inc)).contains("\"'  =1+1\""));
        equal(true,Ledger.validMonth("2026-02"));equal(false,Ledger.validMonth("2026-13"));equal(false,Ledger.validMonth("1899-01"));equal(false,Ledger.validMonth("2026-1"));
        Ledger.Entry a=new Ledger.Entry(),b=new Ledger.Entry();a.cents=1;b.cents=2;equal(3L,Ledger.totals(Arrays.asList(a,b))[1]);
        a.cents=Ledger.MAX_CENTS;equal(19999999998L,Ledger.totals(Arrays.asList(a,a))[1]);
        for(String child:Ledger.DINING){Ledger.Entry tagged=entry("餐饮",child,100);Ledger.validate(tagged);checks++;}
        for(String child:Ledger.TRAVEL){Ledger.Entry tagged=entry("交通",child,100);Ledger.validate(tagged);checks++;}
        equal(6,Ledger.subcategories("餐饮").length);equal(13,Ledger.subcategories("交通").length);equal(0,Ledger.subcategories("工资").length);
        Ledger.Entry bad=entry("餐饮","地铁",100);reject(()->Ledger.validate(bad));bad.category="交通";bad.subcategory="早餐";reject(()->Ledger.validate(bad));
        bad.category="购物";bad.subcategory="饮用水";reject(()->Ledger.validate(bad));bad.type="income";bad.category="工资";reject(()->Ledger.validate(bad));
        bad.subcategory=null;reject(()->Ledger.validate(bad));bad.subcategory="";Ledger.validate(bad);checks++;
        Ledger.Entry breakfast=entry("餐饮","早餐",101),breakfast2=entry("餐饮","早餐",109),water=entry("餐饮","饮用水",203),legacy=entry("餐饮","",500),charge=entry("交通","电动车充电",300),metro=entry("交通","地铁",400);
        java.util.List<Ledger.Entry> details=Arrays.asList(breakfast,breakfast2,water,legacy,charge,metro,inc);
        equal(6,Ledger.filter(details,1,"","","").size());equal(1,Ledger.filter(details,2,"","","").size());equal(4,Ledger.filter(details,0,"餐饮","","").size());
        equal(2,Ledger.filter(details,1,"餐饮","早餐","").size());equal(210L,Ledger.totals(Ledger.filter(details,1,"餐饮","早餐",""))[1]);
        equal(1,Ledger.filter(details,1,"餐饮",Ledger.UNSPECIFIED,"").size());equal(500L,Ledger.totals(Ledger.filter(details,1,"餐饮",Ledger.UNSPECIFIED,""))[1]);
        equal(1,Ledger.filter(details,1,"交通","电动车充电","").size());equal(0,Ledger.filter(details,1,"交通","早餐","").size());
        equal(1,Ledger.filter(details,0,"","","饮用水").size());equal(1,Ledger.filter(details,0,"","","充电").size());equal(0,Ledger.filter(details,2,"餐饮","","").size());
        java.util.Map<String,Long> dining=Ledger.breakdown(details,false,"餐饮");equal(210L,dining.get("早餐"));equal(203L,dining.get("饮用水"));equal(500L,dining.get(Ledger.UNSPECIFIED));equal(0L,dining.get("午餐"));
        equal(913L,Ledger.breakdown(details,false,"").get("餐饮"));equal(700L,Ledger.breakdown(details,false,"").get("交通"));equal(300L,Ledger.breakdown(details,false,"交通").get("电动车充电"));
        equal(210L,Ledger.breakdown(Ledger.filter(details,1,"餐饮","早餐",""),false,"餐饮").get("早餐"));
        equal("餐饮 · 早餐",Ledger.categoryLabel(breakfast));equal("餐饮",Ledger.categoryLabel(legacy));
        equal(true,Ledger.csv(details).startsWith("\uFEFF日期,类型,分类,细分分类,"));equal(true,Ledger.csv(details).contains("\"交通\",\"电动车充电\",3.00"));
        equal(0L,Ledger.parseBalance("0"));equal(0L,Ledger.parseBalance("0.00"));equal(-12345L,Ledger.parseBalance("-123.45"));equal(12345L,Ledger.parseBalance(" 123.45 "));
        equal(Ledger.MAX_BALANCE_CENTS,Ledger.parseBalance("999999999999.99"));equal(-Ledger.MAX_BALANCE_CENTS,Ledger.parseBalance("-999999999999.99"));
        for(String invalid:new String[]{"","-",".5","1.","1.234","1e3","NaN","1,000","1000000000000","--1"})reject(()->Ledger.parseBalance(invalid));
        Ledger.Entry oldIncome=entry("工资","",1000);oldIncome.type="income";Ledger.Entry oldExpense=entry("餐饮","早餐",200);Ledger.Entry change=entry("交通","地铁",300);
        java.util.List<Ledger.Entry> old=Arrays.asList(oldIncome,oldExpense);
        long base=Ledger.balanceBase(50000,Ledger.totals(old)[2]);equal(50000L,Ledger.balance(base,Ledger.totals(old)[2]));
        java.util.List<Ledger.Entry> updated=Arrays.asList(oldIncome,oldExpense,change);
        equal(49700L,Ledger.balance(base,Ledger.totals(updated)[2]));change.cents=500;equal(49500L,Ledger.balance(base,Ledger.totals(updated)[2]));
        change.type="income";change.category="工资";change.subcategory="";change.cents=700;equal(50700L,Ledger.balance(base,Ledger.totals(updated)[2]));
        equal(50000L,Ledger.balance(base,Ledger.totals(old)[2]));
        equal(0L,Ledger.balance(Ledger.balanceBase(0,Ledger.totals(old)[2]),Ledger.totals(old)[2]));
        equal(-12345L,Ledger.balance(Ledger.balanceBase(-12345,Ledger.totals(old)[2]),Ledger.totals(old)[2]));
        equal(1000L,Ledger.totals(old)[0]);equal(200L,Ledger.totals(old)[1]);
        reject(()->Ledger.balanceBase(Ledger.MAX_BALANCE_BASE+1,0));
        equal("—",Ledger.savingsRate(0,0));equal("—",Ledger.savingsRate(0,2500));
        equal("100.0%",Ledger.savingsRate(100000,0));equal("40.0%",Ledger.savingsRate(100000,60000));
        equal("0.0%",Ledger.savingsRate(100000,100000));equal("-50.0%",Ledger.savingsRate(100000,150000));
        equal("33.3%",Ledger.savingsRate(3,2));equal("66.7%",Ledger.savingsRate(3,1));
        equal("0.1%",Ledger.savingsRate(2000,1999));equal("-0.1%",Ledger.savingsRate(2000,2001));
        equal("-99999900.0%",Ledger.savingsRate(1,1000000));equal("50.0%",Ledger.savingsRate(Long.MAX_VALUE,Long.MAX_VALUE/2));
        System.out.println("PASS: "+checks+" finance, savings rate, balance initialization/updates, subcategory, filtering, CSV and date checks");
    }
    private static Ledger.Entry entry(String category,String subcategory,long cents){Ledger.Entry e=new Ledger.Entry();e.category=category;e.subcategory=subcategory;e.cents=cents;return e;}
}

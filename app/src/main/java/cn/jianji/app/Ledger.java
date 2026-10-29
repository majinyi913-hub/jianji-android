package cn.jianji.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Money is always stored as integer cents; dates are local calendar dates. */
public final class Ledger {
    private Ledger() {}
    public static final long MAX_CENTS = 9_999_999_999L;
    public static final long MAX_BALANCE_CENTS = 99_999_999_999_999L;
    public static final long MAX_BALANCE_BASE = 9_000_000_000_000_000L;
    public static final String[] EXPENSE = {"餐饮", "交通", "购物", "房租水电", "娱乐", "医疗", "学习", "其他支出"};
    public static final String[] INCOME = {"工资", "奖金", "兼职", "理财", "红包", "其他收入"};
    public static final String[] DINING = {"早餐", "午餐", "晚餐", "饮用水", "额外支出"};
    public static final String[] TRAVEL = {"电动车充电", "地铁", "高铁", "公交", "网约车", "出租车", "共享单车", "加油", "停车", "飞机", "普铁", "其他出行"};
    public static final String[] SHOPPING = {"日用品", "赛车模拟器", "钓鱼", "电动车", "摩托车", "数码产品", "服饰鞋包", "其他购物"};
    public static final String MONTHLY = "每月订阅服务";
    public static final String UNSPECIFIED = "未细分";

    public static class Entry {
        public String id = UUID.randomUUID().toString();
        public String type = "expense";
        public long cents;
        public String category = EXPENSE[0];
        public String subcategory = "";
        public String subscriptionId = "", subscriptionMonth = "";
        public String date = LocalDate.now().toString();
        public String note = "";
        public long createdAt = System.currentTimeMillis();
        public boolean income() { return "income".equals(type); }
        public boolean automated() { return !subscriptionId.isEmpty(); }
        public boolean subscription() { return MONTHLY.equals(subcategory) || automated(); }
    }

    public static long parseAmount(String text) {
        String value = text == null ? "" : text.trim();
        if (!value.matches("[0-9]{1,8}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("请输入有效金额，最多两位小数");
        long cents = new BigDecimal(value).movePointRight(2).longValueExact();
        if (cents <= 0 || cents > MAX_CENTS) throw new IllegalArgumentException("金额需在 0.01～99,999,999.99 元之间");
        return cents;
    }

    public static long parseBalance(String text) {
        String value=text==null?"":text.trim();
        if(!value.matches("-?[0-9]{1,12}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("请输入余额，最多两位小数，可填 0 或负数");
        long cents=new BigDecimal(value).movePointRight(2).longValueExact();
        if(cents < -MAX_BALANCE_CENTS || cents > MAX_BALANCE_CENTS)
            throw new IllegalArgumentException("余额超出支持范围");
        return cents;
    }

    /** Existing entries are absorbed into the baseline, so initialization never double counts them. */
    public static long balanceBase(long actualBalance,long existingNet) {
        long base=Math.subtractExact(actualBalance,existingNet);
        if(base < -MAX_BALANCE_BASE || base > MAX_BALANCE_BASE)
            throw new IllegalArgumentException("余额起点超出支持范围");
        return base;
    }

    public static long balance(long base,long net) { return Math.addExact(base,net); }

    public static String money(long cents) {
        return String.format(Locale.CHINA, "%,.2f", BigDecimal.valueOf(cents, 2));
    }

    public static String decimal(long cents) { return BigDecimal.valueOf(cents, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }

    public static void validate(Entry e) {
        if (e.id == null || !e.id.matches("[A-Za-z0-9-]{1,64}")) throw new IllegalArgumentException("记录标识无效");
        if (!"expense".equals(e.type) && !"income".equals(e.type)) throw new IllegalArgumentException("收支类型无效");
        if (e.cents <= 0 || e.cents > MAX_CENTS) throw new IllegalArgumentException("记录金额超出范围");
        boolean found = false;
        for (String c : e.income() ? INCOME : EXPENSE) if (c.equals(e.category)) found = true;
        if (!found) throw new IllegalArgumentException("记录分类无效");
        if (e.subcategory == null) throw new IllegalArgumentException("细分分类无效");
        if (!e.subcategory.isEmpty()) {
            boolean valid = false;
            for (String c : subcategories(e.category)) if (c.equals(e.subcategory)) valid = true;
            if (e.income() || !valid) throw new IllegalArgumentException("细分分类与主分类不匹配");
        }
        try {
            if (e.date == null || !e.date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("日期无效");
            LocalDate d = LocalDate.parse(e.date);
            if (d.getYear() < 1900 || d.getYear() > 2100) throw new IllegalArgumentException("日期需在 1900～2100 年之间");
        } catch (DateTimeParseException ex) { throw new IllegalArgumentException("日期无效"); }
        if (e.note == null || e.note.length() > 200) throw new IllegalArgumentException("备注最多 200 字");
        if (e.createdAt < 0) throw new IllegalArgumentException("创建时间无效");
        if(e.subscriptionId==null||e.subscriptionMonth==null)throw new IllegalArgumentException("订阅来源无效");
        if(e.automated()){
            if(e.income()||!MONTHLY.equals(e.subcategory)||!e.subscriptionId.matches("[A-Za-z0-9-]{1,64}")||!validMonth(e.subscriptionMonth))throw new IllegalArgumentException("自动订阅扣费记录无效");
            if(!Subscription.entryId(e.subscriptionId,YearMonth.parse(e.subscriptionMonth)).equals(e.id))throw new IllegalArgumentException("订阅扣费标识无效");
        }else if(!e.subscriptionMonth.isEmpty())throw new IllegalArgumentException("订阅扣费月份无效");
    }

    public static long[] totals(List<Entry> entries) {
        long income = 0, expense = 0;
        for (Entry e : entries) { if (e.income()) income += e.cents; else expense += e.cents; }
        return new long[]{income, expense, income - expense};
    }

    /** Monthly surplus / income; a month without income has no defined rate. */
    public static String savingsRate(long income, long expense) {
        if (income <= 0) return "—";
        return BigDecimal.valueOf(income).subtract(BigDecimal.valueOf(expense))
            .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(income), 1, RoundingMode.HALF_UP)
            .toPlainString() + "%";
    }

    public static Map<String, Long> categories(List<Entry> entries, boolean income) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String c : income ? INCOME : EXPENSE) result.put(c, 0L);
        for (Entry e : entries) if (e.income() == income) result.put(e.category, result.get(e.category) + e.cents);
        return result;
    }

    public static String[] subcategories(String category) {
        String[] base="餐饮".equals(category)?DINING:"交通".equals(category)?TRAVEL:"购物".equals(category)?SHOPPING:new String[0];
        for(String expense:EXPENSE)if(expense.equals(category)){
            String[] result=java.util.Arrays.copyOf(base,base.length+1);result[base.length]=MONTHLY;return result;
        }
        return base.clone();
    }
    public static String normalizeCategory(String category){return "居家".equals(category)?"其他支出":category;}

    public static String categoryLabel(Entry e) {
        return e.category + (e.subcategory.isEmpty() ? "" : " · " + e.subcategory);
    }

    /** Empty category/detail selects all; UNSPECIFIED selects legacy/unclassified details. */
    public static List<Entry> filter(List<Entry> entries, int type, String category, String detail, String search) {
        return filter(entries,type,category,detail,search,0);
    }
    public static List<Entry> filter(List<Entry> entries, int type, String category, String detail, String search,int subscriptionMode) {
        List<Entry> result = new ArrayList<>();
        String needle = search.trim().toLowerCase(Locale.ROOT);
        for (Entry e : entries) {
            if(subscriptionMode==1&&!e.subscription()||subscriptionMode==2&&!e.automated())continue;
            if ((type == 1 && e.income()) || (type == 2 && !e.income())) continue;
            if (!category.isEmpty() && !category.equals(e.category)) continue;
            if (!detail.isEmpty() && !(UNSPECIFIED.equals(detail) ? e.subcategory.isEmpty() : detail.equals(e.subcategory))) continue;
            if (!needle.isEmpty() && !(e.category + " " + e.subcategory + " " + e.note).toLowerCase(Locale.ROOT).contains(needle)) continue;
            result.add(e);
        }
        return result;
    }

    public static Map<String, Long> breakdown(List<Entry> entries, boolean income, String category) {
        if (category.isEmpty()) return categories(entries, income);
        Map<String, Long> result = new LinkedHashMap<>();
        String[] details = subcategories(category);
        if (details.length == 0) result.put(category, 0L);
        else { for (String detail : details) result.put(detail, 0L); result.put(UNSPECIFIED, 0L); }
        for (Entry e : entries) {
            if (e.income() != income || !category.equals(e.category)) continue;
            String key = details.length == 0 ? category : e.subcategory.isEmpty() ? UNSPECIFIED : e.subcategory;
            result.put(key, result.get(key) + e.cents);
        }
        return result;
    }

    public static String csv(List<Entry> entries) {
        StringBuilder out = new StringBuilder("\uFEFF日期,类型,分类,细分分类,金额（元）,备注,记账来源,订阅扣费月份\r\n");
        for (Entry e : entries) out.append(cell(e.date)).append(',').append(e.income() ? "收入" : "支出").append(',')
            .append(cell(e.category)).append(',').append(cell(e.subcategory)).append(',').append(decimal(e.cents)).append(',').append(cell(e.note)).append(',')
            .append(e.automated()?"自动订阅":e.subscription()?"手动订阅":"普通记账").append(',').append(cell(e.subscriptionMonth)).append("\r\n");
        return out.toString();
    }

    private static String cell(String s) {
        // Guard spreadsheet formula interpretation, including whitespace prefixes.
        String t = s.trim();
        if (t.startsWith("=") || t.startsWith("+") || t.startsWith("-") || t.startsWith("@") || s.startsWith("\t") || s.startsWith("\r")) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public static boolean validMonth(String month) {
        try { YearMonth m = YearMonth.parse(month); return m.getYear() >= 1900 && m.getYear() <= 2100; }
        catch (Exception e) { return false; }
    }
}

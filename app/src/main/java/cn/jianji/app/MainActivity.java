package cn.jianji.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private LedgerDb db;
    private LinearLayout root, body, records;
    private YearMonth month = YearMonth.now();
    private int tab = 0, filter = 0;
    private int recordSource=0,statsSource=0;
    private boolean statsIncome = false;
    private String query = "";
    private String initialBalanceInput="";
    private String recordCategory="", recordDetail="", statsCategory="", statsDetail="";
    private TextView recordSummary;
    private int visibleLimit = 100;
    private List<Ledger.Entry> entries = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean observingSubscriptions;
    private final android.content.BroadcastReceiver subscriptionChanges=new android.content.BroadcastReceiver(){
        @Override public void onReceive(android.content.Context c,Intent i){if(!isFinishing()&&!isDestroyed())render();}
    };
    private static final int BACKUP = 301, CSV = 302, RESTORE = 303;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); db = new LedgerDb(this);
        if (state != null) { month = YearMonth.parse(state.getString("month",month.toString())); tab = state.getInt("tab"); filter = state.getInt("filter"); query = state.getString("query",""); statsIncome = state.getBoolean("statsIncome"); }
        if(state!=null){recordCategory=state.getString("recordCategory","");recordDetail=state.getString("recordDetail","");statsCategory=state.getString("statsCategory","");statsDetail=state.getString("statsDetail","");}
        if(state!=null)initialBalanceInput=state.getString("initialBalanceInput","");
        if(state!=null){recordSource=state.getInt("recordSource");statsSource=state.getInt("statsSource");recordCategory=Ledger.normalizeCategory(recordCategory);statsCategory=Ledger.normalizeCategory(statsCategory);}
        render();
    }
    @Override protected void onResume() { super.onResume(); if (db != null) render(); }
    @Override protected void onStart(){super.onStart();android.content.IntentFilter changes=new android.content.IntentFilter(SubscriptionScheduler.CHANGED);changes.addAction(Intent.ACTION_DATE_CHANGED);if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(subscriptionChanges,changes,android.content.Context.RECEIVER_NOT_EXPORTED);else registerReceiver(subscriptionChanges,changes);observingSubscriptions=true;}
    @Override protected void onStop(){if(observingSubscriptions){unregisterReceiver(subscriptionChanges);observingSubscriptions=false;}super.onStop();}
    @Override protected void onSaveInstanceState(Bundle s) { super.onSaveInstanceState(s); s.putString("month",month.toString()); s.putInt("tab",tab); s.putInt("filter",filter); s.putString("query",query); s.putBoolean("statsIncome",statsIncome);s.putString("recordCategory",recordCategory);s.putString("recordDetail",recordDetail);s.putString("statsCategory",statsCategory);s.putString("statsDetail",statsDetail);s.putString("initialBalanceInput",initialBalanceInput);s.putInt("recordSource",recordSource);s.putInt("statsSource",statsSource); }
    @Override protected void onDestroy() { io.shutdown(); if(db != null) db.close(); super.onDestroy(); }

    private void render() {
        db.applyDueSubscriptions(LocalDate.now(),1000);SubscriptionScheduler.schedule(this,db);
        entries = db.list(month.toString());
        root = Ui.col(this); root.setBackgroundColor(Ui.BG); setContentView(root); Ui.applyInsets(this,root);
        LinearLayout header = Ui.row(this); Ui.pad(header,22,16,22,12);
        LinearLayout brand = Ui.col(this); brand.addView(Ui.text(this,"简记",28,Ui.INK,true)); Ui.space(brand,5); brand.addView(Ui.text(this,"把生活，记清楚",12,Ui.MUTED,false)); Ui.weight(header,brand);
        TextView mark = Ui.text(this,"J",22,Ui.GREEN,true); mark.setGravity(Gravity.CENTER); mark.setBackground(Ui.bg(Ui.PALE,18,this)); header.addView(mark,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48))); root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        body = Ui.col(this); Ui.pad(body,20,4,20,20); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(!db.hasBalance()){balanceSetup();return;}
        if (tab == 0) ledgerPage(); else if (tab == 1) statsPage(); else if(tab==3)subscriptionsPage();else settingsPage();
        LinearLayout footer = Ui.col(this); footer.setBackgroundColor(Ui.WHITE); Ui.pad(footer,20,10,20,2);
        Button add = Ui.button(this,"＋  记一笔",true); add.setContentDescription("新增收支记录"); add.setOnClickListener(v -> openEditor(null)); footer.addView(add,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));
        LinearLayout nav = Ui.row(this); String[] names = {"明细", "统计", "自动订阅", "我的"};int[] pages={0,1,3,2};
        for (int i = 0; i < names.length; i++) { final int n = pages[i]; TextView t = Ui.text(this,names[i],14,tab == n ? Ui.GREEN : Ui.MUTED,tab == n); t.setGravity(Gravity.CENTER); t.setMinHeight(Ui.dp(this,52)); t.setContentDescription(names[i]+(tab==n ? "，已选中" : "")); t.setBackground(Ui.bg(Ui.WHITE,10,this)); t.setOnClickListener(v -> { tab=n; render(); }); nav.addView(t,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1)); }
        footer.addView(nav); root.addView(footer);
    }

    private EditText balanceInput(String hint) {
        EditText input=Ui.input(this,hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(16)});
        input.setContentDescription("目前余额（元）");return input;
    }
    private void balanceSetup() {
        Ui.space(body,24);body.addView(Ui.text(this,"先记下你的起点",26,Ui.INK,true));Ui.space(body,12);
        TextView intro=Ui.text(this,"第一次使用，请先初始化目前余额。之后每一笔收支，都会更新你的存款。",15,Ui.MUTED,false);intro.setLineSpacing(Ui.dp(this,6),1);body.addView(intro);Ui.space(body,26);
        LinearLayout card=Ui.card(this);card.addView(Ui.text(this,"目前余额 · 元",14,Ui.INK,true));Ui.space(card,12);
        EditText input=balanceInput("请输入实际余额");input.setId(103);input.setText(initialBalanceInput);input.setTextSize(28);card.addView(input,new LinearLayout.LayoutParams(-1,Ui.dp(this,68)));Ui.space(card,12);
        card.addView(Ui.text(this,"可填 0 或负数，最多两位小数",12,Ui.MUTED,false));body.addView(card);Ui.space(body,18);
        long count=db.count();TextView note=Ui.text(this,count>0?"已有 "+count+" 笔账目。请填写你现在的实际余额，已有收支不会再重复加减。":"初始化金额只作为余额起点，不会计入收入统计。",13,Ui.MUTED,false);note.setLineSpacing(Ui.dp(this,5),1);body.addView(note);Ui.space(body,24);
        Button save=Ui.button(this,"初始化余额，开始记账",true);save.setOnClickListener(v->{
            final long cents;try{cents=Ledger.parseBalance(input.getText().toString());}catch(IllegalArgumentException e){input.setError(e.getMessage());return;}
            try{db.setCurrentBalance(cents);initialBalanceInput="";((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);tab=0;render();}
            catch(Exception e){message("保存失败","无法保存余额，请检查存储空间后重试。");}
        });body.addView(save,new LinearLayout.LayoutParams(-1,Ui.dp(this,56)));Ui.space(body,14);
        Button restore=Ui.button(this,"从已有备份恢复",false);restore.setOnClickListener(v->restoreDialog());body.addView(restore,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int count){initialBalanceInput=s.toString();}public void afterTextChanged(Editable e){}});
    }
    private void balanceCard() {
        long current=db.currentBalance();LinearLayout card=Ui.card(this);LinearLayout heading=Ui.row(this);
        Ui.weight(heading,Ui.text(this,"目前存款 · 元",15,Ui.INK,true));TextView adjust=Ui.text(this,"校准  ›",12,Ui.GREEN,false);heading.addView(adjust);card.addView(heading);Ui.space(card,12);
        TextView amount=Ui.text(this,Ledger.money(current),34,current<0?Ui.RED:Ui.GREEN,true);amount.setContentDescription("目前存款 "+Ledger.money(current)+" 元");amount.setMaxLines(1);amount.setAutoSizeTextTypeUniformWithConfiguration(16,34,1,android.util.TypedValue.COMPLEX_UNIT_SP);card.addView(amount,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));Ui.space(card,10);
        card.addView(Ui.text(this,"累计余额 · 随每笔收支自动更新",12,Ui.MUTED,false));card.setOnClickListener(v->balanceDialog());body.addView(card);Ui.space(body,18);
    }
    private void balanceDialog() {
        EditText input=balanceInput("输入现在的实际余额");input.setText(Ledger.decimal(db.currentBalance()));
        LinearLayout wrap=Ui.col(this);Ui.pad(wrap,24,10,24,4);wrap.addView(input);Ui.space(wrap,12);
        TextView help=Ui.text(this,"只校准当前余额，不会产生收入或支出记录。保存后继续随记账更新。",13,Ui.MUTED,false);help.setLineSpacing(Ui.dp(this,5),1);wrap.addView(help);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("校准目前余额").setView(wrap).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            final long cents;try{cents=Ledger.parseBalance(input.getText().toString());}catch(IllegalArgumentException e){input.setError(e.getMessage());return;}
            try{db.setCurrentBalance(cents);dialog.dismiss();render();}catch(Exception e){message("保存失败","无法校准余额，请检查存储空间后重试。");}
        }));dialog.show();
    }
    private void restoreDialog() {
        String explanation=db.hasBalance()?"将合并账目、预算和订阅，重复记录跳过。保留本机已有订阅及余额起点；新增收支和到期订阅会计入存款。":"将恢复账目、预算及订阅；新版备份会恢复余额。旧版备份没有余额时，导入后仍需填写目前余额。";
        new AlertDialog.Builder(this).setTitle("恢复简记备份").setMessage(explanation).setNegativeButton("取消",null).setPositiveButton("选择文件",(d,w)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startPicker(i,RESTORE);}).show();
    }

    private void monthPicker() {
        LinearLayout row = Ui.row(this);
        Button prev = Ui.button(this,"‹",false); prev.setContentDescription("上个月"); prev.setOnClickListener(v -> changeMonth(-1)); row.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView title = Ui.text(this,month.getYear()+" 年 "+String.format(Locale.CHINA,"%02d",month.getMonthValue())+" 月  ▾",18,Ui.INK,true); title.setGravity(Gravity.CENTER); title.setMinHeight(Ui.dp(this,48)); title.setOnClickListener(v -> selectMonth()); Ui.weight(row,title);
        Button next = Ui.button(this,"›",false); next.setContentDescription("下个月"); next.setOnClickListener(v -> changeMonth(1)); row.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        body.addView(row); Ui.space(body,16);
    }
    private void changeMonth(int delta) { YearMonth m = month.plusMonths(delta); if(m.getYear()>=1900 && m.getYear()<=2100) { month=m; render(); } }
    private void selectMonth() {
        DatePickerDialog picker = new DatePickerDialog(this,(view,y,m,d)->{ month=YearMonth.of(y,m+1); render(); },month.getYear(),month.getMonthValue()-1,1);
        picker.setTitle("选择月份（日期任选）"); picker.getDatePicker().setMinDate(LocalDate.of(1900,1,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        picker.getDatePicker().setMaxDate(LocalDate.of(2100,12,31).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()); picker.show();
    }
    private void ledgerPage() {
        balanceCard();monthPicker(); long[] totals = Ledger.totals(entries);
        LinearLayout hero = Ui.col(this); hero.setBackground(Ui.bg(Ui.GREEN,24,this)); Ui.pad(hero,22,22,22,22);
        hero.addView(Ui.text(this,"本月结余 · 元",13,0xFFD7E6D7,false)); Ui.space(hero,10);
        TextView balance = Ui.text(this,Ledger.money(totals[2]),36,Ui.WHITE,true); balance.setAutoSizeTextTypeUniformWithConfiguration(18,36,1,android.util.TypedValue.COMPLEX_UNIT_SP); balance.setMaxLines(1); hero.addView(balance,new LinearLayout.LayoutParams(-1,Ui.dp(this,48))); Ui.space(hero,22);
        LinearLayout row = Ui.row(this); Ui.weight(row,metric("收入",totals[0],true)); Ui.weight(row,metric("支出",totals[1],true)); hero.addView(row); body.addView(hero); Ui.space(body,14);
        long budget = db.budget(month.toString());
        LinearLayout budgetCard = Ui.card(this); Ui.pad(budgetCard,18,14,18,14);
        LinearLayout br = Ui.row(this); Ui.weight(br,Ui.text(this,"月度预算",14,Ui.INK,true)); TextView b = Ui.text(this,budget==0 ? "设置预算  ›" : "¥ "+Ledger.money(budget)+"  ›",14,Ui.GREEN,true); br.addView(b); budgetCard.addView(br);
        if (budget > 0) { Ui.space(budgetCard,10); budgetCard.addView(progress(totals[1],budget)); Ui.space(budgetCard,8); budgetCard.addView(Ui.text(this,totals[1]>budget ? "已超出 ¥ "+Ledger.money(totals[1]-budget) : "还可支出 ¥ "+Ledger.money(budget-totals[1]),12,totals[1]>budget ? Ui.RED : Ui.MUTED,false)); }
        budgetCard.setOnClickListener(v -> budgetDialog()); body.addView(budgetCard); Ui.space(body,22);
        LinearLayout heading=Ui.row(this); Ui.weight(heading,Ui.text(this,"收支明细",20,Ui.INK,true)); heading.addView(Ui.text(this,entries.size()+" 笔",13,Ui.MUTED,false)); body.addView(heading); Ui.space(body,12);
        EditText search = Ui.input(this,"搜索分类、细分或备注"); search.setTextSize(14); search.setText(query); search.setContentDescription("搜索本月记录"); body.addView(search); Ui.space(body,10);
        LinearLayout filters=Ui.row(this); String[] names={"全部","支出","收入"};
        for(int i=0;i<3;i++) { final int n=i; Button f=Ui.button(this,names[i],filter==i); f.setTextSize(13); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1); if(i>0)p.leftMargin=Ui.dp(this,8); filters.addView(f,p); f.setOnClickListener(v->{filter=n;recordCategory="";recordDetail="";if(n==2)recordSource=0;visibleLimit=100;render();}); }
        body.addView(filters); Ui.space(body,10); categoryFilters(false); Ui.space(body,10);sourceFilters(false);
        recordSummary=Ui.text(this,"",12,Ui.MUTED,false);recordSummary.setLineSpacing(Ui.dp(this,4),1);body.addView(recordSummary);
        Ui.space(body,8);records=Ui.col(this); body.addView(records); renderRecords();
        search.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();visibleLimit=100;renderRecords();} public void afterTextChanged(Editable e){} });
    }
    private LinearLayout metric(String label,long amount,boolean dark) {
        LinearLayout l=Ui.col(this); l.addView(Ui.text(this,label,12,dark?0xFFD7E6D7:Ui.MUTED,false)); Ui.space(l,6); TextView value=Ui.text(this,"¥ "+Ledger.money(amount),18,dark?Ui.WHITE:Ui.INK,true); value.setMaxLines(1); value.setAutoSizeTextTypeUniformWithConfiguration(11,18,1,android.util.TypedValue.COMPLEX_UNIT_SP); l.addView(value,new LinearLayout.LayoutParams(-1,Ui.dp(this,28))); return l;
    }
    private void renderRecords() {
        records.removeAllViews(); String last=""; int count=0; boolean more=false;
        List<Ledger.Entry> filtered=Ledger.filter(entries,filter,recordCategory,recordDetail,query,recordSource);
        long[] selectedTotal=Ledger.totals(filtered);
        recordSummary.setText("筛选结果 "+filtered.size()+" 笔  ·  收入 ¥ "+Ledger.money(selectedTotal[0])+"  ·  支出 ¥ "+Ledger.money(selectedTotal[1]));
        boolean narrowed=filter!=0||!recordCategory.isEmpty()||!recordDetail.isEmpty()||!query.trim().isEmpty()||recordSource>0;
        for(Ledger.Entry e:filtered) {
            if(count>=visibleLimit){more=true;break;}
            if(!last.equals(e.date)) { if(count>0)Ui.space(records,12); LinearLayout dh=Ui.row(this); TextView date=Ui.text(this,dateLabel(e.date),12,Ui.MUTED,true); Ui.pad(date,2,10,0,10); Ui.weight(dh,date);
                long day=0; for(Ledger.Entry x:filtered) if(x.date.equals(e.date))day+=x.income()?x.cents:-x.cents;
                dh.addView(Ui.text(this,(narrowed?"筛选日结余 ":"日结余 ")+(day>0?"+":"")+Ledger.money(day),11,Ui.MUTED,false)); records.addView(dh); last=e.date; }
            LinearLayout card=Ui.row(this); Ui.pad(card,14,14,14,14); card.setBackground(Ui.bg(Ui.WHITE,18,this)); card.setMinimumHeight(Ui.dp(this,78));
            card.addView(Ui.badge(this,e.category,e.income()),new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));
            LinearLayout desc=Ui.col(this); Ui.pad(desc,12,0,8,0);
            TextView label=Ui.text(this,e.category,15,Ui.INK,true);desc.addView(label);Ui.space(desc,6);
            String description=(e.subcategory.isEmpty()?"":e.subcategory+" · ")+(e.note.isEmpty()?"无备注":e.note);
            TextView note=Ui.text(this,description,12,Ui.MUTED,false);note.setMaxLines(2);note.setEllipsize(android.text.TextUtils.TruncateAt.END);desc.addView(note); Ui.weight(card,desc);
            TextView amt=Ui.text(this,(e.income()?"+":"−")+Ledger.money(e.cents),16,e.income()?Ui.GREEN:Ui.INK,true); card.addView(amt); card.setContentDescription(e.date+" "+Ledger.categoryLabel(e)+" "+(e.income()?"收入":"支出")+Ledger.money(e.cents)+"元 "+e.note+"，点击编辑"); card.setOnClickListener(v->openEditor(e.id)); records.addView(card); Ui.space(records,7); count++;
        }
        if(count==0) empty(records,entries.isEmpty()?"这一月，等你写下第一笔":"没有找到记录",entries.isEmpty()?"从一杯咖啡开始，慢慢看见生活的轨迹。":"试试其他关键词、分类或收支类型。");
        if(more){Button load=Ui.button(this,"加载更多记录",false);load.setOnClickListener(v->{visibleLimit+=100;renderRecords();});records.addView(load,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));}
    }
    private String dateLabel(String date) { LocalDate d=LocalDate.parse(date); String day=d.equals(LocalDate.now())?"今天":d.equals(LocalDate.now().minusDays(1))?"昨天":d.format(DateTimeFormatter.ofPattern("MM 月 dd 日")); String[] week={"周一","周二","周三","周四","周五","周六","周日"}; return day+"  ·  "+week[d.getDayOfWeek().getValue()-1]; }
    private View progress(long used,long total) {
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(1000); bar.setProgress((int)Math.min(1000,used*1000/Math.max(1,total))); bar.setProgressTintList(android.content.res.ColorStateList.valueOf(used>total?Ui.RED:Ui.GREEN)); bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE)); bar.setMinimumHeight(Ui.dp(this,7)); return bar;
    }
    private void categoryFilters(boolean statistics) {
        String category=statistics?statsCategory:recordCategory;
        String detail=statistics?statsDetail:recordDetail;
        LinearLayout row=Ui.row(this);
        Button parent=Ui.button(this,(category.isEmpty()?"全部分类":category)+"  ▾",false);
        parent.setTextSize(13);parent.setContentDescription((statistics?"统计":"明细")+"主分类筛选");
        parent.setOnClickListener(v->chooseCategory(statistics));Ui.weight(row,parent);
        if(Ledger.subcategories(category).length>0){
            Button child=Ui.button(this,(detail.isEmpty()?"全部细分":detail)+"  ▾",false);
            child.setTextSize(13);child.setMaxLines(2);child.setContentDescription((statistics?"统计":"明细")+"细分分类筛选");
            child.setOnClickListener(v->chooseDetail(statistics));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,52),1);p.leftMargin=Ui.dp(this,8);row.addView(child,p);
        }
        body.addView(row);
    }
    private void chooseCategory(boolean statistics) {
        int kind=statistics?(statsIncome?2:1):filter;
        if((statistics?statsSource:recordSource)>0)kind=1;
        List<String> choices=new ArrayList<>();choices.add("全部分类");
        if(kind!=2)Collections.addAll(choices,Ledger.EXPENSE);
        if(kind!=1)Collections.addAll(choices,Ledger.INCOME);
        String current=statistics?statsCategory:recordCategory;
        int checked=current.isEmpty()?0:choices.indexOf(current);
        new AlertDialog.Builder(this).setTitle("选择主分类").setSingleChoiceItems(choices.toArray(new String[0]),checked,(d,which)->{
            String selected=which==0?"":choices.get(which);
            if(statistics){statsCategory=selected;statsDetail="";}else{recordCategory=selected;recordDetail="";visibleLimit=100;}
            d.dismiss();render();
        }).setNegativeButton("取消",null).show();
    }
    private void chooseDetail(boolean statistics) {
        String category=statistics?statsCategory:recordCategory;
        String current=statistics?statsDetail:recordDetail;
        List<String> choices=new ArrayList<>();choices.add("全部细分");Collections.addAll(choices,Ledger.subcategories(category));choices.add(Ledger.UNSPECIFIED);
        new AlertDialog.Builder(this).setTitle(category+"细分筛选").setSingleChoiceItems(choices.toArray(new String[0]),current.isEmpty()?0:choices.indexOf(current),(d,which)->{
            String selected=which==0?"":choices.get(which);
            if(statistics)statsDetail=selected;else{recordDetail=selected;visibleLimit=100;}
            d.dismiss();render();
        }).setNegativeButton("取消",null).show();
    }
    private void statsPage() {
        monthPicker();body.addView(Ui.text(this,"钱都花在哪儿",22,Ui.INK,true));Ui.space(body,6);
        body.addView(Ui.text(this,"可按主分类和细分分类查看收支",13,Ui.MUTED,false));Ui.space(body,16);
        LinearLayout tabs=Ui.row(this);Button exp=Ui.button(this,"支出分类",!statsIncome),inc=Ui.button(this,"收入分类",statsIncome);Ui.weight(tabs,exp);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,50),1);p.leftMargin=Ui.dp(this,10);tabs.addView(inc,p);
        exp.setOnClickListener(v->{statsIncome=false;statsCategory="";statsDetail="";render();});
        inc.setOnClickListener(v->{statsIncome=true;statsCategory="";statsDetail="";statsSource=0;render();});body.addView(tabs);Ui.space(body,12);categoryFilters(true);Ui.space(body,12);sourceFilters(true);Ui.space(body,4);
        List<Ledger.Entry> selected=Ledger.filter(entries,statsIncome?2:1,statsCategory,statsDetail,"",statsSource);
        long sum=Ledger.totals(selected)[statsIncome?0:1],grand=Ledger.totals(entries)[statsIncome?0:1];
        String scope=(statsCategory.isEmpty()?"全部分类":statsCategory+(statsDetail.isEmpty()?"":" · "+statsDetail))+(statsSource==1?" · 订阅扣费":statsSource==2?" · 自动订阅":"");
        LinearLayout summary=Ui.card(this);summary.addView(Ui.text(this,(statsIncome?"本月收入":"本月支出")+" · "+scope,13,Ui.MUTED,false));Ui.space(summary,12);
        TextView amount=Ui.text(this,"¥ "+Ledger.money(sum),32,Ui.GREEN,true);amount.setMaxLines(1);amount.setAutoSizeTextTypeUniformWithConfiguration(16,32,1,android.util.TypedValue.COMPLEX_UNIT_SP);summary.addView(amount,new LinearLayout.LayoutParams(-1,Ui.dp(this,44)));Ui.space(summary,12);
        summary.addView(Ui.text(this,"共 "+selected.size()+" 笔  ·  日均 ¥ "+Ledger.money(sum/elapsedDays()),13,Ui.MUTED,false));Ui.space(summary,8);
        summary.addView(Ui.text(this,String.format(Locale.CHINA,"占本月%s %.1f%%",statsIncome?"收入":"支出",grand==0?0:sum*100.0/grand),12,Ui.MUTED,false));body.addView(summary);Ui.space(body,20);
        if(sum==0){empty(body,"当前条件下没有记录","试试切换月份、主分类或细分分类。");return;}
        body.addView(Ui.text(this,Ledger.subcategories(statsCategory).length>0?statsCategory+"细分占比":"分类占比",18,Ui.INK,true));Ui.space(body,7);
        body.addView(Ui.text(this,"占比以当前筛选金额为准",12,Ui.MUTED,false));Ui.space(body,12);
        List<Map.Entry<String,Long>> cats=new ArrayList<>(Ledger.breakdown(selected,statsIncome,statsCategory).entrySet());cats.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
        LinearLayout list=Ui.card(this);int index=0;
        for(Map.Entry<String,Long> c:cats){
            if(c.getValue()==0)continue;if(index++>0)Ui.space(list,16);
            LinearLayout r=Ui.row(this);r.setMinimumHeight(Ui.dp(this,48));Ui.weight(r,Ui.text(this,c.getKey(),15,Ui.INK,true));r.addView(Ui.text(this,"¥ "+Ledger.money(c.getValue()),15,Ui.INK,true));
            if(statsCategory.isEmpty())r.setOnClickListener(v->{statsCategory=c.getKey();statsDetail="";render();});
            else if(Ledger.subcategories(statsCategory).length>0)r.setOnClickListener(v->{statsDetail=c.getKey();render();});
            list.addView(r);list.addView(progress(c.getValue(),sum));Ui.space(list,5);list.addView(Ui.text(this,String.format(Locale.CHINA,"占比 %.1f%%",c.getValue()*100.0/sum),12,Ui.MUTED,false));
        }
        body.addView(list);Ui.space(body,16);Button detail=Ui.button(this,"查看这 "+selected.size()+" 笔明细  ›",false);
        detail.setOnClickListener(v->{tab=0;filter=statsIncome?2:1;recordCategory=statsCategory;recordDetail=statsDetail;recordSource=statsSource;query="";visibleLimit=100;render();});body.addView(detail,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));
    }
    private void sourceFilters(boolean statistics){
        if(statistics?statsIncome:filter==2)return;
        body.addView(Ui.text(this,"记账来源",12,Ui.MUTED,false));Ui.space(body,7);LinearLayout row=Ui.row(this);String[] labels={"全部来源","订阅扣费","自动订阅"};int selected=statistics?statsSource:recordSource;
        for(int i=0;i<labels.length;i++){final int mode=i;Button b=Ui.button(this,labels[i],selected==i);b.setTextSize(12);b.setContentDescription((statistics?"统计":"明细")+"来源筛选："+labels[i]);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);if(i>0)p.leftMargin=Ui.dp(this,7);row.addView(b,p);b.setOnClickListener(v->{if(statistics)statsSource=mode;else{recordSource=mode;visibleLimit=100;}render();});}body.addView(row);Ui.space(body,12);
    }
    private void openSubscription(String id){Intent i=new Intent(this,SubscriptionActivity.class);if(id!=null)i.putExtra("id",id);startActivityForResult(i,101);}
    private void subscriptionsPage(){
        Ui.space(body,8);body.addView(Ui.text(this,"自动订阅",25,Ui.INK,true));Ui.space(body,8);
        TextView intro=Ui.text(this,"到期自动生成本机支出记录，并更新目前存款。",13,Ui.MUTED,false);intro.setLineSpacing(Ui.dp(this,5),1);body.addView(intro);Ui.space(body,20);
        List<Subscription> plans=db.subscriptions(false);long total=0;for(Subscription s:plans)total+=s.cents;
        LinearLayout summary=Ui.card(this);summary.addView(Ui.text(this,"计划每月支出 · "+plans.size()+" 个订阅",13,Ui.MUTED,false));Ui.space(summary,10);summary.addView(Ui.text(this,"¥ "+Ledger.money(total),30,Ui.GREEN,true));body.addView(summary);Ui.space(body,14);
        Button add=Ui.button(this,"＋ 新增自动订阅",true);add.setOnClickListener(v->openSubscription(null));body.addView(add,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));Ui.space(body,10);
        Button stats=Ui.button(this,"查看本月自动订阅扣费",false);stats.setOnClickListener(v->{tab=1;statsIncome=false;statsCategory="";statsDetail="";statsSource=2;render();});body.addView(stats,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));Ui.space(body,18);
        if(plans.isEmpty()){empty(body,"还没有自动订阅","可从上方新增，或在支出分类的“每月订阅服务”中设置。");return;}
        for(Subscription s:plans){
            LinearLayout card=Ui.card(this);LinearLayout title=Ui.row(this);TextView name=Ui.text(this,s.name,17,Ui.INK,true);name.setMaxLines(2);Ui.weight(title,name);title.addView(Ui.text(this,"¥ "+Ledger.money(s.cents),16,Ui.GREEN,true));card.addView(title);Ui.space(card,12);
            card.addView(Ui.text(this,s.category+" · 每月 "+s.billingDay+" 日",13,Ui.MUTED,false));Ui.space(card,8);card.addView(Ui.text(this,s.nextDate()==null?"已完成全部扣费月份":"下一次记账："+s.nextDate(),13,Ui.MUTED,false));Ui.space(card,14);
            LinearLayout actions=Ui.row(this);Button edit=Ui.button(this,"编辑",false);edit.setOnClickListener(v->openSubscription(s.id));Ui.weight(actions,edit);
            Button delete=Ui.button(this,"删除",false);delete.setTextColor(Ui.RED);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,50),1);p.leftMargin=Ui.dp(this,8);actions.addView(delete,p);
            delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("删除“"+s.name+"”？").setMessage("停止后续自动记账，已发生的支出和存款变动保留。").setNegativeButton("保留",null).setPositiveButton("删除",(d,w)->{db.deleteSubscription(s.id);render();}).show());card.addView(actions);body.addView(card);Ui.space(body,12);
        }
        TextView timing=Ui.text(this,"系统后台可能延迟，打开 App 会补记到期费用。每月只记一次；删除历史扣费后也不会自动重新生成。",12,Ui.MUTED,false);timing.setLineSpacing(Ui.dp(this,5),1);body.addView(timing);
    }
    private int elapsedDays(){YearMonth now=YearMonth.now();return month.equals(now)?LocalDate.now().getDayOfMonth():month.isAfter(now)?1:month.lengthOfMonth();}
    private void settingsPage(){
        Ui.space(body,6);body.addView(Ui.text(this,"自己的账本，安心记",23,Ui.INK,true));Ui.space(body,8);body.addView(Ui.text(this,"离线可用 · 无广告 · 无需注册",13,Ui.MUTED,false));Ui.space(body,24);
        balanceCard();
        LinearLayout card=Ui.card(this);card.addView(Ui.text(this,"账本概览",16,Ui.INK,true));Ui.space(card,16);card.addView(Ui.text(this,db.count()+" 笔记录",30,Ui.GREEN,true));Ui.space(card,8);card.addView(Ui.text(this,"货币：人民币（CNY）",13,Ui.MUTED,false));body.addView(card);Ui.space(body,22);
        body.addView(Ui.text(this,"数据管理",18,Ui.INK,true));Ui.space(body,12);
        action("导出备份","账目、预算、余额及订阅 · JSON，可恢复",()->createDocument(BACKUP,"application/json","简记备份-"+LocalDate.now()+".json"));
        action("恢复备份","合并导入，重复记录自动跳过",()->restoreDialog());
        action("导出表格","全部记录 · CSV，可用 Excel 打开",()->createDocument(CSV,"text/csv","简记账单-"+LocalDate.now()+".csv"));
        Ui.space(body,16);LinearLayout note=Ui.card(this);note.setBackground(Ui.bg(Ui.PALE,20,this));note.addView(Ui.text(this,"数据保存在这台手机上",15,Ui.GREEN,true));Ui.space(note,8);note.addView(Ui.text(this,"建议定期导出备份。卸载应用或清除应用数据会删除本机账本；更换手机时，可通过 JSON 备份恢复。",13,Ui.GREEN,false));body.addView(note);Ui.space(body,24);TextView ver=Ui.text(this,"简记 1.3.0  /  认真生活，轻松记录",12,Ui.MUTED,false);ver.setGravity(Gravity.CENTER);body.addView(ver);
    }
    private void action(String title,String subtitle,Runnable fn){LinearLayout c=Ui.card(this);Ui.pad(c,18,16,18,16);LinearLayout r=Ui.row(this);LinearLayout texts=Ui.col(this);texts.addView(Ui.text(this,title,16,Ui.INK,true));Ui.space(texts,7);texts.addView(Ui.text(this,subtitle,12,Ui.MUTED,false));Ui.weight(r,texts);r.addView(Ui.text(this,"›",24,Ui.MUTED,false));c.addView(r);c.setOnClickListener(v->fn.run());body.addView(c);Ui.space(body,10);}
    private void empty(LinearLayout parent,String title,String sub){LinearLayout c=Ui.col(this);Ui.pad(c,14,32,14,30);TextView icon=Ui.text(this,"记",26,Ui.GREEN,true);icon.setGravity(Gravity.CENTER);icon.setBackground(Ui.bg(Ui.PALE,22,this));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(Ui.dp(this,68),Ui.dp(this,68));ip.gravity=Gravity.CENTER_HORIZONTAL;c.addView(icon,ip);Ui.space(c,18);TextView h=Ui.text(this,title,16,Ui.INK,true);h.setGravity(Gravity.CENTER);c.addView(h);Ui.space(c,10);TextView t=Ui.text(this,sub,12,Ui.MUTED,false);t.setGravity(Gravity.CENTER);t.setLineSpacing(Ui.dp(this,5),1);c.addView(t);parent.addView(c);}
    private void budgetDialog(){EditText input=Ui.input(this,"输入预算金额（元）");input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);long current=db.budget(month.toString());if(current>0)input.setText(Ledger.decimal(current));LinearLayout wrap=Ui.col(this);Ui.pad(wrap,24,10,24,2);wrap.addView(input);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(month.getMonthValue()+" 月预算").setView(wrap).setNegativeButton("取消",null).setNeutralButton("取消预算",(d,w)->{db.setBudget(month.toString(),0);render();}).setPositiveButton("保存",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{db.setBudget(month.toString(),Ledger.parseAmount(input.getText().toString()));dialog.dismiss();render();}catch(Exception e){input.setError(e.getMessage());}}));dialog.show();}
    private void openEditor(String id){Intent i=new Intent(this,EntryActivity.class);if(id!=null)i.putExtra("id",id);startActivityForResult(i,100);}
    private void createDocument(int request,String mime,String name){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name);startPicker(i,request);}
    private void startPicker(Intent i,int request){try{startActivityForResult(i,request);}catch(android.content.ActivityNotFoundException e){message("无法打开文件选择器","请在手机上启用系统“文件”应用后重试。");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==101&&result==RESULT_OK){tab=3;return;}if(result!=RESULT_OK||data==null)return;if(request==100){String savedMonth=data.getStringExtra("month");if(Ledger.validMonth(savedMonth)){month=YearMonth.parse(savedMonth);query="";filter=0;recordCategory="";recordDetail="";recordSource=0;visibleLimit=100;}if(data.getBooleanExtra("subscriptionSaved",false))tab=3;return;}if(data.getData()==null)return;final android.net.Uri uri=data.getData();if(request!=BACKUP&&request!=CSV&&request!=RESTORE)return;Toast.makeText(this,"正在处理…",Toast.LENGTH_SHORT).show();
        io.execute(()->{try(LedgerDb worker=new LedgerDb(getApplicationContext())){
            String done;
            if(request==RESTORE){try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取文件");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int len;while((len=in.read(buffer))!=-1){bytes.write(buffer,0,len);if(bytes.size()>64*1024*1024)throw new IOException("备份文件超过 64 MB");}boolean hadBalance=worker.hasBalance();int[] count=worker.restore(bytes.toString("UTF-8"));done="新增 "+count[0]+" 笔记录，跳过 "+count[1]+" 笔重复记录。已有预算保持原值。\n"+(hadBalance?"已保留本机余额起点，新增收支已计入存款。":worker.hasBalance()?"已恢复备份中的余额。":"此备份没有余额，请输入目前实际余额完成初始化。");}}
            else {String content=request==BACKUP?worker.backup():Ledger.csv(worker.list(null));try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入文件");out.write(content.getBytes(StandardCharsets.UTF_8));}done=request==BACKUP?"备份已保存，包含全部记录、预算、余额和订阅进度。":"CSV 已保存，可用 Excel 等表格应用打开。";}
            final String text=done;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){render();message("处理完成",text);}});
        }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())message("处理失败",request==RESTORE?"文件无法读取或内容不符合简记备份格式。本机数据未更改。\n"+safeError(e):"未能完整保存，请重新导出。\n"+safeError(e));});}});
    }
    private String safeError(Exception e){return e instanceof IllegalArgumentException||e instanceof IOException?String.valueOf(e.getMessage()):"请检查文件是否为完整的 JSON 备份，以及存储空间是否足够。";}
    private void message(String title,String text){new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("知道了",null).show();}
}

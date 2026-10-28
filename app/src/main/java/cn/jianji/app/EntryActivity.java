package cn.jianji.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.time.LocalDate;
import java.time.ZoneId;

public class EntryActivity extends Activity {
    private LedgerDb db;
    private Ledger.Entry entry;
    private boolean editing;
    private String type="expense", category=Ledger.EXPENSE[0], subcategory="", date=LocalDate.now().toString(), initial;
    private EditText amount,note;
    private Button expense,income,dateButton;
    private LinearLayout grid, subcategorySection;

    @Override public void onCreate(Bundle s){
        super.onCreate(s);db=new LedgerDb(this);
        if(!db.hasBalance()){Toast.makeText(this,"请先初始化目前余额",Toast.LENGTH_SHORT).show();startActivity(new android.content.Intent(this,MainActivity.class));finish();return;}
        String id=getIntent().getStringExtra("id");
        if(id!=null){entry=db.find(id);if(entry==null){Toast.makeText(this,"记录不存在",Toast.LENGTH_SHORT).show();finish();return;}editing=true;type=entry.type;category=entry.category;subcategory=entry.subcategory;date=entry.date;}
        else entry=new Ledger.Entry();
        if(s!=null){type=s.getString("type",type);category=s.getString("category",category);subcategory=s.getString("subcategory",subcategory);date=s.getString("date",date);entry.id=s.getString("entryId",entry.id);entry.createdAt=s.getLong("createdAt",entry.createdAt);}
        category=Ledger.normalizeCategory(category);
        LinearLayout root=Ui.col(this);root.setBackgroundColor(Ui.BG);setContentView(root);Ui.applyInsets(this,root);
        LinearLayout top=Ui.row(this);Ui.pad(top,16,12,20,8);Button back=Ui.button(this,"‹",false);back.setContentDescription("返回");back.setOnClickListener(v->leave());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));TextView title=Ui.text(this,editing?"编辑记录":"记一笔",21,Ui.INK,true);title.setGravity(Gravity.CENTER);Ui.weight(top,title);if(editing){Button del=Ui.button(this,"删除",false);del.setTextColor(Ui.RED);del.setOnClickListener(v->delete());top.addView(del,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,48)));}else{View blank=new View(this);top.addView(blank,new LinearLayout.LayoutParams(Ui.dp(this,48),1));}root.addView(top);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=Ui.col(this);Ui.pad(body,20,14,20,20);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout switcher=Ui.row(this);expense=Ui.button(this,"支出",!"income".equals(type));income=Ui.button(this,"收入","income".equals(type));Ui.weight(switcher,expense);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,50),1);p.leftMargin=Ui.dp(this,10);switcher.addView(income,p);expense.setOnClickListener(v->setType("expense"));income.setOnClickListener(v->setType("income"));body.addView(switcher);Ui.space(body,26);
        income.setEnabled(!entry.automated());
        body.addView(Ui.text(this,"金额 · 元",13,Ui.MUTED,false));Ui.space(body,8);LinearLayout money=Ui.row(this);money.addView(Ui.text(this,"¥",30,Ui.INK,true));amount=Ui.input(this,"0.00");amount.setId(101);amount.setContentDescription("记账金额");amount.setTextSize(38);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(11)});amount.setBackgroundColor(android.graphics.Color.TRANSPARENT);Ui.pad(amount,12,10,0,10);Ui.weight(money,amount);body.addView(money);Ui.line(body);Ui.space(body,24);
        body.addView(Ui.text(this,"选择分类",15,Ui.INK,true));Ui.space(body,12);grid=Ui.col(this);body.addView(grid);drawCategories();
        subcategorySection=Ui.col(this);body.addView(subcategorySection);drawSubcategories();Ui.space(body,22);
        body.addView(Ui.text(this,"日期",13,Ui.MUTED,false));Ui.space(body,8);dateButton=Ui.button(this,date+"  ▾",false);dateButton.setContentDescription("选择记账日期");dateButton.setOnClickListener(v->chooseDate());body.addView(dateButton,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));Ui.space(body,18);
        body.addView(Ui.text(this,"备注",13,Ui.MUTED,false));Ui.space(body,8);note=Ui.input(this,"记下这笔钱的小故事（选填）");note.setId(102);note.setContentDescription("记账备注");note.setSingleLine(false);note.setMinLines(2);note.setMaxLines(4);note.setGravity(Gravity.TOP);note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});body.addView(note);Ui.space(body,6);body.addView(Ui.text(this,"最多 200 字",11,Ui.MUTED,false));
        LinearLayout bottom=Ui.col(this);Ui.pad(bottom,20,10,20,16);Button save=Ui.button(this,editing?"保存修改":"保存记录",true);save.setOnClickListener(v->save());bottom.addView(save,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));root.addView(bottom);
        if(editing){amount.setText(Ledger.decimal(entry.cents));note.setText(entry.note);}
        if(s!=null){amount.setText(s.getString("amount",""));note.setText(s.getString("note",""));}
        initial=s!=null?s.getString("initial",snapshot()):snapshot();
        root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void setType(String value){if(type.equals(value)||entry.automated())return;type=value;category="income".equals(type)?Ledger.INCOME[0]:Ledger.EXPENSE[0];subcategory="";expense.setBackground(Ui.bg("expense".equals(type)?Ui.GREEN:Ui.PALE,16,this));expense.setTextColor("expense".equals(type)?Ui.WHITE:Ui.GREEN);income.setBackground(Ui.bg("income".equals(type)?Ui.GREEN:Ui.PALE,16,this));income.setTextColor("income".equals(type)?Ui.WHITE:Ui.GREEN);drawCategories();}
    private void drawCategories() {
        grid.removeAllViews();
        String[] cats="income".equals(type)?Ledger.INCOME:Ledger.EXPENSE;
        for(int r=0;r<(cats.length+3)/4;r++) {
            LinearLayout row=Ui.row(this);
            for(int c=0;c<4;c++) {
                int index=r*4+c;
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,92),1);
                if(c>0)p.leftMargin=Ui.dp(this,7);
                if(index>=cats.length){row.addView(new View(this),p);continue;}
                String cat=cats[index]; boolean selected=cat.equals(category);
                LinearLayout item=Ui.col(this); item.setGravity(Gravity.CENTER); Ui.pad(item,4,8,4,8);
                item.setBackground(Ui.bg(selected?Ui.PALE:Ui.WHITE,16,this));
                TextView icon=Ui.text(this,cat.substring(0,1),21,selected?Ui.GREEN:Ui.MUTED,true);
                icon.setGravity(Gravity.CENTER); icon.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                item.addView(icon,new LinearLayout.LayoutParams(-1,-2)); Ui.space(item,7);
                TextView label=Ui.text(this,cat,12,selected?Ui.GREEN:Ui.MUTED,selected);
                label.setGravity(Gravity.CENTER); label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                label.setIncludeFontPadding(true); label.setMaxLines(2);
                label.setAutoSizeTextTypeUniformWithConfiguration(10,12,1,android.util.TypedValue.COMPLEX_UNIT_SP);
                item.addView(label,new LinearLayout.LayoutParams(-1,Ui.dp(this,30)));
                item.setSelected(selected); item.setContentDescription("分类："+cat+(selected?"，已选中":""));
                item.setOnClickListener(v->{if(!category.equals(cat)){category=cat;subcategory=entry.automated()?Ledger.MONTHLY:"";}drawCategories();});
                row.addView(item,p);
            }
            grid.addView(row);Ui.space(grid,8);
        }
        drawSubcategories();
    }
    private void drawSubcategories() {
        if(subcategorySection==null)return;
        subcategorySection.removeAllViews();String[] options=Ledger.subcategories(category);
        subcategorySection.setVisibility(options.length==0?View.GONE:View.VISIBLE);
        if(options.length==0)return;
        Ui.space(subcategorySection,8);LinearLayout heading=Ui.row(this);
        Ui.weight(heading,Ui.text(this,category+"细分（可选）",14,Ui.INK,true));
        TextView clear=Ui.text(this,"不细分",12,Ui.GREEN,false);clear.setGravity(Gravity.CENTER);
        clear.setMinHeight(Ui.dp(this,48));clear.setMinWidth(Ui.dp(this,64));
        clear.setEnabled(!entry.automated());clear.setOnClickListener(v->{subcategory="";drawSubcategories();});heading.addView(clear);subcategorySection.addView(heading);
        for(int r=0;r<(options.length+2)/3;r++) {
            LinearLayout row=Ui.row(this);
            for(int c=0;c<3;c++) {
                int index=r*3+c;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,56),1);
                if(c>0)p.leftMargin=Ui.dp(this,8);
                if(index>=options.length){row.addView(new View(this),p);continue;}
                String option=options[index];TextView choice=Ui.choice(this,option,option.equals(subcategory));
                choice.setEnabled(!entry.automated()||Ledger.MONTHLY.equals(option));
                choice.setContentDescription("细分分类："+option+(option.equals(subcategory)?"，已选中":""));
                choice.setOnClickListener(v->{subcategory=option;drawSubcategories();});row.addView(choice,p);
            }
            subcategorySection.addView(row);Ui.space(subcategorySection,8);
        }
        subcategorySection.addView(Ui.text(this,subcategory.isEmpty()?"尚未细分，可点选上方选项":"已选："+subcategory,12,Ui.MUTED,false));
        if(Ledger.MONTHLY.equals(subcategory)){
            Ui.space(subcategorySection,10);
            if(!editing){Button setup=Ui.button(this,"设置自动订阅  ›",false);setup.setOnClickListener(v->{android.content.Intent intent=new android.content.Intent(this,SubscriptionActivity.class);intent.putExtra("category",category);intent.putExtra("amount",amount.getText().toString());intent.putExtra("note",note.getText().toString());startActivityForResult(intent,101);});subcategorySection.addView(setup,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));Ui.space(subcategorySection,8);subcategorySection.addView(Ui.text(this,"可设置每月扣费日；当前页也可保存单笔订阅支出。",12,Ui.MUTED,false));}
            else if(entry.automated())subcategorySection.addView(Ui.text(this,"自动订阅扣费 · "+entry.subscriptionMonth+"；后续设置可在“自动订阅”中管理。",12,Ui.MUTED,false));
        }
        if("餐饮".equals(category)){Ui.space(subcategorySection,6);subcategorySection.addView(Ui.text(this,"早、中、晚餐均归入下方所选日期",12,Ui.MUTED,false));}
    }
    private void chooseDate(){LocalDate d=LocalDate.parse(date);DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,day)->{date=LocalDate.of(y,m+1,day).toString();dateButton.setText(date+"  ▾");},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());picker.getDatePicker().setMinDate(LocalDate.of(1900,1,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());picker.getDatePicker().setMaxDate(LocalDate.of(2100,12,31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());picker.show();}
    private String snapshot(){return type+"|"+category+"|"+subcategory+"|"+date+"|"+amount.getText()+"|"+note.getText();}
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);if(amount==null)return;s.putString("type",type);s.putString("category",category);s.putString("subcategory",subcategory);s.putString("date",date);s.putString("entryId",entry.id);s.putLong("createdAt",entry.createdAt);s.putString("amount",amount.getText().toString());s.putString("note",note.getText().toString());s.putString("initial",initial);}
    private void save(){try{entry.cents=Ledger.parseAmount(amount.getText().toString());}catch(IllegalArgumentException e){amount.setError(e.getMessage());amount.requestFocus();return;}entry.type=type;entry.category=category;entry.subcategory=subcategory;entry.date=date;entry.note=note.getText().toString().trim();try{db.save(entry);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(amount.getWindowToken(),0);Toast.makeText(this,editing?"修改已保存":"已记下这一笔",Toast.LENGTH_SHORT).show();setResult(RESULT_OK,new android.content.Intent().putExtra("month",date.substring(0,7)));finish();}catch(Exception e){new AlertDialog.Builder(this).setTitle("保存失败").setMessage("请检查剩余存储空间后重试。").setPositiveButton("知道了",null).show();}}
    private void delete(){new AlertDialog.Builder(this).setTitle("删除这笔记录？").setMessage(entry.date+" · "+Ledger.categoryLabel(entry)+" · ¥ "+Ledger.money(entry.cents)+"\n删除后无法撤销。").setNegativeButton("保留",null).setPositiveButton("删除",(d,w)->{try{db.delete(entry.id);Toast.makeText(this,"记录已删除",Toast.LENGTH_SHORT).show();finish();}catch(Exception e){Toast.makeText(this,"删除失败，请重试",Toast.LENGTH_SHORT).show();}}).show();}
    private void leave(){if(amount!=null&&!snapshot().equals(initial))new AlertDialog.Builder(this).setTitle("放弃未保存的内容？").setNegativeButton("继续编辑",null).setPositiveButton("放弃",(d,w)->finish()).show();else finish();}
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(request==101&&result==RESULT_OK){setResult(RESULT_OK,new android.content.Intent().putExtra("month",java.time.YearMonth.now().toString()).putExtra("subscriptionSaved",true));finish();}}
    @Override public void onBackPressed(){leave();}
    @Override protected void onDestroy(){if(db!=null)db.close();super.onDestroy();}
}

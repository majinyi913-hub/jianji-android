package cn.jianji.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;

public final class SubscriptionActivity extends Activity {
    private LedgerDb db;
    private Subscription plan;
    private boolean editing,started;
    private EditText name,amount,note;
    private Button categoryButton,dayButton,startButton;
    private TextView preview;
    private String initial;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);db=new LedgerDb(this);
        if(!db.hasBalance()){startActivity(new android.content.Intent(this,MainActivity.class));finish();return;}
        String id=getIntent().getStringExtra("id");
        if(id!=null){plan=db.subscription(id);editing=true;if(plan==null||plan.deletedAt>0){Toast.makeText(this,"订阅已删除",Toast.LENGTH_SHORT).show();finish();return;}}
        else{
            plan=new Subscription();String category=getIntent().getStringExtra("category");
            if(Arrays.asList(Ledger.EXPENSE).contains(category))plan.category=category;
            plan.startMonth=Subscription.nextStart(LocalDate.now(),plan.billingDay).toString();plan.nextMonth=plan.startMonth;
        }
        started=!plan.startMonth.equals(plan.nextMonth);
        if(state!=null){
            plan.id=state.getString("id",plan.id);plan.createdAt=state.getLong("createdAt",plan.createdAt);
            plan.category=state.getString("category",plan.category);plan.billingDay=state.getInt("day",plan.billingDay);
            if(!started){plan.startMonth=state.getString("start",plan.startMonth);plan.nextMonth=plan.startMonth;}
        }
        LinearLayout root=Ui.col(this);root.setBackgroundColor(Ui.BG);setContentView(root);Ui.applyInsets(this,root);
        LinearLayout header=Ui.row(this);Ui.pad(header,16,12,16,8);
        Button back=Ui.button(this,"‹",false);back.setContentDescription("返回");back.setOnClickListener(v->leave());header.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
        TextView title=Ui.text(this,editing?"编辑自动订阅":"新增自动订阅",21,Ui.INK,true);title.setGravity(Gravity.CENTER);Ui.weight(header,title);
        if(editing){Button delete=Ui.button(this,"删除",false);delete.setTextColor(Ui.RED);delete.setOnClickListener(v->delete());header.addView(delete,new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,48)));}
        else header.addView(new android.view.View(this),new LinearLayout.LayoutParams(Ui.dp(this,48),1));root.addView(header);
        ScrollView scroll=new ScrollView(this);LinearLayout body=Ui.col(this);Ui.pad(body,20,12,20,22);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(Ui.text(this,"到期自动生成支出，更新本机存款",14,Ui.MUTED,false));Ui.space(body,24);
        name=Ui.input(this,"例如：视频会员、公交月卡");name.setId(201);name.setContentDescription("订阅服务名称");name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        field(body,"服务名称",name);
        amount=Ui.input(this,"每月扣费金额（元）");amount.setId(202);amount.setContentDescription("每月订阅金额");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(11)});field(body,"每月金额 · 元",amount);
        categoryButton=Ui.button(this,plan.category+"  ▾",false);categoryButton.setOnClickListener(v->chooseCategory());field(body,"支出一级分类",categoryButton);
        dayButton=Ui.button(this,"每月 "+plan.billingDay+" 日  ▾",false);dayButton.setContentDescription("选择每月扣费日期");dayButton.setOnClickListener(v->chooseDay());field(body,"每月扣费日",dayButton);
        TextView end=Ui.text(this,"当月没有该日期时，按月末记账。",12,Ui.MUTED,false);body.addView(end);Ui.space(body,18);
        startButton=Ui.button(this,plan.startMonth+"  ▾",false);startButton.setOnClickListener(v->chooseStart());startButton.setEnabled(!started);field(body,started?"首次扣费月份（已开始）":"首次扣费月份",startButton);
        note=Ui.input(this,"选填，最多 140 字");note.setId(203);note.setContentDescription("订阅备注");note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(140)});field(body,"备注",note);
        LinearLayout card=Ui.card(this);card.setBackground(Ui.bg(Ui.PALE,20,this));preview=Ui.text(this,"",13,Ui.GREEN,false);preview.setLineSpacing(Ui.dp(this,6),1);card.addView(preview);body.addView(card);Ui.space(body,16);
        TextView help=Ui.text(this,"系统后台可能延后执行，打开 App 时会自动补记；同一订阅每月只记一次。修改设置影响后续扣费，删除订阅保留已发生的账目。",12,Ui.MUTED,false);help.setLineSpacing(Ui.dp(this,5),1);body.addView(help);
        LinearLayout bottom=Ui.col(this);Ui.pad(bottom,20,10,20,16);Button save=Ui.button(this,editing?"保存订阅设置":"保存自动订阅",true);save.setOnClickListener(v->save());bottom.addView(save,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));root.addView(bottom);
        name.setText(state!=null?state.getString("name",""):plan.name);
        amount.setText(state!=null?state.getString("amount",""):editing?Ledger.decimal(plan.cents):getIntent().getStringExtra("amount"));
        note.setText(state!=null?state.getString("note",""):editing?plan.note:getIntent().getStringExtra("note"));
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){updatePreview();}public void afterTextChanged(Editable e){}};
        name.addTextChangedListener(watcher);amount.addTextChangedListener(watcher);note.addTextChangedListener(watcher);updatePreview();
        initial=state==null?snapshot():state.getString("initial",snapshot());root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void field(LinearLayout body,String label,android.view.View input){body.addView(Ui.text(this,label,13,Ui.MUTED,false));Ui.space(body,8);body.addView(input,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));Ui.space(body,18);}
    private Subscription candidate(boolean forPreview){
        Subscription s=new Subscription();s.id=plan.id;s.createdAt=plan.createdAt;s.category=plan.category;s.billingDay=plan.billingDay;s.startMonth=plan.startMonth;s.nextMonth=started?plan.nextMonth:plan.startMonth;
        s.name=name.getText().toString().trim();if(forPreview&&s.name.isEmpty())s.name="订阅";
        s.note=note.getText().toString().trim();s.cents=Ledger.parseAmount(amount.getText().toString());Subscription.validate(s);return s;
    }
    private void updatePreview(){
        try{Subscription s=candidate(true);int due=Subscription.dueMonths(s,LocalDate.now(),2412).size();
            preview.setText((due==0?(s.nextDate()==null?"已完成全部扣费月份\n":"下一次记账："+s.nextDate()+"\n"):"保存后将补记 "+due+" 笔到期支出，合计 ¥ "+Ledger.money(due*s.cents)+"\n")+"每月 "+s.billingDay+" 日 · ¥ "+Ledger.money(s.cents));
        }catch(Exception e){preview.setText("填写服务名称、金额和扣费日期后，按月自动记账。已到期的月份会自动补记。");}
    }
    private void chooseCategory(){new AlertDialog.Builder(this).setTitle("选择订阅分类").setSingleChoiceItems(Ledger.EXPENSE,Arrays.asList(Ledger.EXPENSE).indexOf(plan.category),(d,n)->{plan.category=Ledger.EXPENSE[n];categoryButton.setText(plan.category+"  ▾");d.dismiss();updatePreview();}).setNegativeButton("取消",null).show();}
    private void chooseDay(){NumberPicker picker=new NumberPicker(this);picker.setMinValue(1);picker.setMaxValue(31);picker.setValue(plan.billingDay);picker.setWrapSelectorWheel(false);new AlertDialog.Builder(this).setTitle("每月几号记账？").setView(picker).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{picker.clearFocus();plan.billingDay=picker.getValue();dayButton.setText("每月 "+plan.billingDay+" 日  ▾");updatePreview();}).show();}
    private void chooseStart(){if(started)return;YearMonth m=YearMonth.parse(plan.startMonth);DatePickerDialog picker=new DatePickerDialog(this,(v,y,month,day)->{plan.startMonth=YearMonth.of(y,month+1).toString();plan.nextMonth=plan.startMonth;startButton.setText(plan.startMonth+"  ▾");updatePreview();},m.getYear(),m.getMonthValue()-1,1);picker.setTitle("选择首次扣费月份（日期任选）");picker.getDatePicker().setMinDate(LocalDate.of(1900,1,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());picker.getDatePicker().setMaxDate(LocalDate.of(2100,12,31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());picker.show();}
    private void save(){
        try{Subscription s=candidate(false);db.saveSubscription(s);db.applyDueSubscriptions(LocalDate.now(),1000);SubscriptionScheduler.schedule(this,db);setResult(RESULT_OK);Toast.makeText(this,"自动订阅已保存",Toast.LENGTH_SHORT).show();finish();}
        catch(IllegalArgumentException e){new AlertDialog.Builder(this).setTitle("请检查订阅设置").setMessage(e.getMessage()).setPositiveButton("知道了",null).show();}
        catch(Exception e){new AlertDialog.Builder(this).setTitle("保存失败").setMessage("请检查存储空间后重试。").setPositiveButton("知道了",null).show();}
    }
    private void delete(){new AlertDialog.Builder(this).setTitle("删除这个自动订阅？").setMessage("将停止后续自动记账，已发生的支出和存款变动保留。").setNegativeButton("保留",null).setPositiveButton("删除",(d,w)->{db.deleteSubscription(plan.id);SubscriptionScheduler.schedule(this,db);setResult(RESULT_OK);finish();}).show();}
    private String snapshot(){return name.getText()+"|"+amount.getText()+"|"+note.getText()+"|"+plan.category+"|"+plan.billingDay+"|"+plan.startMonth;}
    private void leave(){if(name!=null&&!snapshot().equals(initial))new AlertDialog.Builder(this).setTitle("放弃未保存的订阅设置？").setNegativeButton("继续编辑",null).setPositiveButton("放弃",(d,w)->finish()).show();else finish();}
    @Override public void onBackPressed(){leave();}
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);if(name==null)return;s.putString("id",plan.id);s.putLong("createdAt",plan.createdAt);s.putString("category",plan.category);s.putInt("day",plan.billingDay);s.putString("start",plan.startMonth);s.putString("name",name.getText().toString());s.putString("amount",amount.getText().toString());s.putString("note",note.getText().toString());s.putString("initial",initial);}
    @Override protected void onDestroy(){if(db!=null)db.close();super.onDestroy();}
}

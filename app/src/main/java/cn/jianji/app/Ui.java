package cn.jianji.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(245,246,241), GREEN = Color.rgb(32,100,79), INK = Color.rgb(34,49,42), MUTED = Color.rgb(114,125,117), LINE = Color.rgb(231,235,226), PALE = Color.rgb(231,239,219), WHITE = Color.WHITE, RED = Color.rgb(184,84,63);
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static TextView text(Context c, String s, int size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setIncludeFontPadding(false); return t;
    }
    static GradientDrawable bg(int color, int radius, Context c) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d; }
    static void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(v.getContext(),l),dp(v.getContext(),t),dp(v.getContext(),r),dp(v.getContext(),b)); }
    static void space(LinearLayout l, int h) { View v = new View(l.getContext()); l.addView(v, new LinearLayout.LayoutParams(1,dp(l.getContext(),h))); }
    static void weight(LinearLayout row, View v) { row.addView(v, new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1)); }
    static void line(LinearLayout l) { View v = new View(l.getContext()); v.setBackgroundColor(LINE); l.addView(v,new LinearLayout.LayoutParams(-1,dp(l.getContext(),1))); }
    static Button button(Context c, String s, boolean primary) {
        Button b = new Button(c); b.setText(s); b.setTextSize(15); b.setAllCaps(false); b.setTextColor(primary ? WHITE : GREEN); b.setMinHeight(dp(c,50)); b.setMinimumHeight(dp(c,50)); b.setMinimumWidth(0); b.setMinWidth(0); b.setStateListAnimator(null);
        b.setGravity(Gravity.CENTER); b.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); b.setIncludeFontPadding(false); pad(b,8,6,8,6);
        b.setTypeface(Typeface.create("sans-serif-medium",0)); b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(25,32,100,79)),bg(primary ? GREEN : PALE,16,c),null));
        return b;
    }
    static TextView choice(Context c, String label, boolean selected) {
        TextView t = text(c,label,14,selected ? GREEN : MUTED,selected);
        t.setGravity(Gravity.CENTER); t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        t.setIncludeFontPadding(true); t.setMaxLines(2); pad(t,6,6,6,6);
        t.setAutoSizeTextTypeUniformWithConfiguration(11,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1820644F),bg(selected ? PALE : WHITE,14,c),null));
        t.setSelected(selected); t.setMinHeight(dp(c,48)); return t;
    }
    static EditText input(Context c, String hint) {
        EditText e = new EditText(c); e.setTextSize(16); e.setTextColor(INK); e.setHintTextColor(MUTED); e.setHint(hint); e.setSingleLine(true); e.setBackground(bg(WHITE,14,c)); pad(e,16,12,16,12); e.setMinHeight(dp(c,50)); return e;
    }
    static LinearLayout card(Context c) { LinearLayout l = col(c); l.setBackground(bg(WHITE,22,c)); pad(l,20,20,20,20); return l; }
    static void applyInsets(Activity a, View root) {
        if (Build.VERSION.SDK_INT >= 30) a.getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(i.left,i.top,i.right,i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }
    static TextView badge(Context c, String category, boolean income) {
        String label = category.substring(0,1);
        TextView t = text(c,label,18,income ? GREEN : INK,true); t.setGravity(Gravity.CENTER); t.setBackground(bg(income ? PALE : BG,16,c));
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return t;
    }
}

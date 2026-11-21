// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Shared compact device screen. All controls retain labelled accessibility targets. */
final class DeviceScreen {
    final Activity activity;
    final LinearLayout root,toolbar,card;
    final TextView title;
    private final ProgressBar progress;
    DeviceScreen(Activity a,String name){
        activity=a;ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);
        // Wider screens centre the column instead of stretching the forms; narrow phones give the
        // margin back to the content.
        boolean compact=AppUi.compact(a);int side=AppUi.side(a,compact?16:20);
        root=new LinearLayout(a);root.setOrientation(1);root.setPadding(side,dp(8),side,dp(24));root.setBackgroundColor(AppUi.BG);scroll.addView(root);a.setContentView(scroll);
        if(Build.VERSION.SDK_INT>=30){a.getWindow().setDecorFitsSystemWindows(false);scroll.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});}
        toolbar=new LinearLayout(a);toolbar.setGravity(Gravity.CENTER_VERTICAL);root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(compact?48:52)));
        icon("‹","返回",()->a.finish());Space spacer=new Space(a);toolbar.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setIndeterminateTintList(ColorStateList.valueOf(AppUi.ACCENT));progress.setVisibility(View.INVISIBLE);progress.setContentDescription("正在加载");root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        title=text(root,name,AppUi.compact(a)?20:22);title.setTypeface(null,Typeface.BOLD);
        card=new LinearLayout(a);card.setOrientation(1);card.setPadding(dp(compact?16:20),dp(16),dp(compact?16:20),dp(16));card.setBackground(background(AppUi.SURFACE,12));
        LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.topMargin=dp(12);cardParams.bottomMargin=dp(20);root.addView(card,cardParams);
    }
    void loading(boolean value){progress.setVisibility(value?View.VISIBLE:View.INVISIBLE);}
    int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(AppUi.INK);t.setLineSpacing(dp(5),1);t.setPadding(0,dp(8),0,dp(8));parent.addView(t);return t;}
    Button icon(String symbol,String description,Runnable action){Button b=new Button(activity);b.setText("");b.setCompoundDrawables(AppUi.icon(activity,symbol.equals("‹")?"back":symbol.equals("↻")?"refresh":"info",AppUi.ACCENT,24),null,null,null);b.setContentDescription(description);if(Build.VERSION.SDK_INT>=26)b.setTooltipText(description);b.setTextColor(AppUi.ACCENT);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),0,dp(12),0);AppUi.press(b,0x00ffffff,10);b.setOnClickListener(v->action.run());toolbar.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));return b;}
    Button primary(String value,Runnable action){Button b=new Button(activity);b.setText(value);b.setTextSize(16);b.setMinHeight(dp(54));b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setAllCaps(false);b.setTypeface(null,Typeface.BOLD);b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff82948b,AppUi.ON_ACCENT}));AppUi.press(b,AppUi.ACCENT,12);b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffe3ebe5,AppUi.ACCENT}));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);lp.bottomMargin=dp(8);root.addView(b,lp);return b;}
    TextView note(String value){TextView t=text(root,value,13);t.setTextColor(AppUi.MUTED);return t;}
}

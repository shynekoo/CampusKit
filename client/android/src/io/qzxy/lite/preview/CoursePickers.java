package io.qzxy.lite.preview;
import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Draft values only become visible to the editor when the user confirms. */
final class CoursePickers {
 /**
  * The last week a term here can have, and the number of cells per row. Twenty weeks in five
  * columns is exactly four rows, so the whole term is one glance instead of a list that scrolled
  * past weeks the calendar never reaches.
  */
 static final int LAST_WEEK=20, WEEK_COLUMNS=5;
 interface TimeResult {void accept(int day,int first,int last);}
 interface WeekResult {void accept(boolean[] weeks);}
 static int dp(Activity a,int n){return AppUi.dp(a,n);}
 static LinearLayout column(Activity a){LinearLayout v=new LinearLayout(a);v.setOrientation(1);return v;}
 static TextView text(Activity a,String value,int size){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(AppUi.INK);v.setGravity(Gravity.CENTER);return v;}
 static Button button(Activity a,String label,boolean primary){Button b=new Button(a);b.setText(label);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(dp(a,48));b.setTextColor(primary?Color.WHITE:AppUi.ACCENT);AppUi.press(b,primary?AppUi.ACCENT:AppUi.SOFT,12);return b;}
 static Dialog sheet(Activity a,String title,LinearLayout body){
  SlidingDialog d=new SlidingDialog(a);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
  LinearLayout root=column(a);root.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,20));
  GradientDrawable bg=new GradientDrawable();bg.setColor(AppUi.BG);float r=dp(a,24);bg.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});root.setBackground(bg);
  View handle=new View(a);handle.setBackground(AppUi.shape(a,AppUi.MUTED,3));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(a,32),dp(a,4));hp.gravity=Gravity.CENTER;hp.bottomMargin=dp(a,20);root.addView(handle,hp);
  TextView heading=text(a,title,22);heading.setTypeface(null,1);root.addView(heading);LinearLayout.LayoutParams content=new LinearLayout.LayoutParams(-1,-2);content.topMargin=dp(a,20);root.addView(body,content);
  ScrollView scroll=new ScrollView(a){@Override protected void onMeasure(int widthSpec,int heightSpec){int max=(int)(getResources().getDisplayMetrics().heightPixels*.85);super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.addView(root);d.setContentView(scroll);d.setCanceledOnTouchOutside(true);
  Window w=d.getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=w.getAttributes();p.gravity=Gravity.BOTTOM;p.dimAmount=0;w.setAttributes(p);w.setWindowAnimations(a.getResources().getIdentifier("InstantWeekPopup","style",a.getPackageName()));
  w.setLayout(-1,-2);d.prepare(scroll);return d;
 }
 static final class SlidingDialog extends Dialog {
  private View panel;private boolean closing;private android.animation.ValueAnimator motion;
  SlidingDialog(Activity a){super(a);}
  void prepare(View view){
   panel=view;
   panel.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
    public boolean onPreDraw(){panel.getViewTreeObserver().removeOnPreDrawListener(this);if(isShowing()&&!closing){panel.setTranslationX(0);panel.setTranslationY(panel.getHeight());animate(0,240,false);}return true;}
   });
  }
  private void animate(float target,long duration,boolean finish){
   if(motion!=null)motion.cancel();float start=panel.getTranslationY();float initialDim=getWindow().getAttributes().dimAmount;
   motion=android.animation.ValueAnimator.ofFloat(0,1);motion.setDuration(duration);motion.setInterpolator(finish?new android.view.animation.AccelerateInterpolator():new android.view.animation.PathInterpolator(.22f,1f,.36f,1f));
   motion.addUpdateListener(v->{float fraction=(float)v.getAnimatedValue();panel.setTranslationY(start+(target-start)*fraction);WindowManager.LayoutParams attributes=getWindow().getAttributes();attributes.dimAmount=initialDim+((finish?0:.32f)-initialDim)*fraction;getWindow().setAttributes(attributes);});
   if(finish)motion.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){SlidingDialog.super.dismiss();closing=false;}});
   motion.start();
  }
  @Override public void dismiss(){
   if(closing)return;
   if(panel==null||!isShowing()||panel.getHeight()==0){super.dismiss();return;}
   closing=true;animate(panel.getHeight(),180,true);
  }
 }

 interface SingleWeekResult {void accept(int week);}
 static Dialog selectWeek(Activity a,int current,SingleWeekResult result){
  return selectWeek(a,current,null,0,result);
 }
 /**
  * The timetable's week sheet, also used by the empty-classroom screen so both read the same way:
  * numbered cells, the chosen one filled in, 本周 offered as one tap. Weeks the school's calendar
  * does not have stay visible but cannot be picked, which is clearer than hiding them.
  */
 static Dialog selectWeek(Activity a,int current,java.util.List<Integer> allowed,int thisWeek,SingleWeekResult result){
  // Five columns, not a count derived from the width: the term is a 5×4 block, and a grid that
  // reflows between four and six columns makes the same week move every time it is opened.
  LinearLayout body=column(a);int columns=WEEK_COLUMNS;
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle("选择周次").setView(body).setNegativeButton("取消",null).create();
  body.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));
  int last=Math.max(1,Math.max(current,thisWeek));
  if(allowed!=null&&!allowed.isEmpty())for(int week:allowed)last=Math.max(last,week);
  else last=Math.max(last,LAST_WEEK);
  boolean bounded=allowed!=null&&!allowed.isEmpty();
  for(int start=1;start<=last;start+=columns){LinearLayout row=new LinearLayout(a);body.addView(row);for(int col=0;col<columns;col++){final int week=start+col;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,48),1);p.setMargins(dp(a,4),dp(a,4),dp(a,4),dp(a,4));if(week>last){row.addView(new View(a),p);continue;}
    boolean open=!bounded||allowed.contains(week);
    Button b=button(a,String.valueOf(week),week==current);b.setMinWidth(0);b.setPadding(0,0,0,0);b.setSelected(week==current);
    if(!open)b.setEnabled(false);
    else if(week==thisWeek&&week!=current)b.setTextColor(AppUi.ACCENT);
    b.setContentDescription("第"+week+"周"+(week==current?"，当前选择":week==thisWeek?"，本周":open?"":"，学校校历没有这一周"));
    row.addView(b,p);
    if(open)b.setOnClickListener(v->{dialog.dismiss();result.accept(week);});}}
  if(bounded&&thisWeek>=1&&thisWeek!=current&&allowed.contains(thisWeek)){
   Button back=button(a,"回本周（第 "+thisWeek+" 周）",false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,48));p.topMargin=dp(a,12);body.addView(back,p);
   back.setOnClickListener(v->{dialog.dismiss();result.accept(thisWeek);});}
  ScrollView scroll=new ScrollView(a);scroll.addView(body);dialog.setView(scroll);dialog.getWindow().setWindowAnimations(a.getResources().getIdentifier("InstantWeekPopup","style",a.getPackageName()));dialog.show();return dialog;
 }
 static NumberPicker wheel(Activity a,LinearLayout row,String label,int min,int max,int value,String[] names){
  LinearLayout col=column(a);row.addView(col,new LinearLayout.LayoutParams(0,-2,1));col.addView(text(a,label,14));NumberPicker p=new NumberPicker(a);p.setMinValue(min);p.setMaxValue(max);if(names!=null)p.setDisplayedValues(names);p.setValue(value);if(android.os.Build.VERSION.SDK_INT>=29){p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,24,a.getResources().getDisplayMetrics()));p.setTextColor(AppUi.ACCENT);p.setSelectionDividerHeight(dp(a,1));}p.setWrapSelectorWheel(false);p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);p.setContentDescription(label);col.addView(p,new LinearLayout.LayoutParams(-1,dp(a,180)));return p;
 }
 static Dialog time(Activity a,int day,int first,int last,TimeResult result){
  LinearLayout body=column(a),row=new LinearLayout(a);body.addView(row);NumberPicker d=wheel(a,row,"星期",0,6,day,new String[]{"周一","周二","周三","周四","周五","周六","周日"});NumberPicker f=wheel(a,row,"开始节次",1,16,first,null),l=wheel(a,row,"结束节次",1,16,last,null);
  f.setOnValueChangedListener((p,o,n)->{if(l.getValue()<n)l.setValue(n);});l.setOnValueChangedListener((p,o,n)->{if(f.getValue()>n)f.setValue(n);});
  Dialog dialog=sheet(a,"选择上课时间",body);Button done=button(a,"确定",true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(a,52));bp.topMargin=dp(a,20);body.addView(done,bp);done.setOnClickListener(v->{result.accept(d.getValue(),f.getValue(),l.getValue());dialog.dismiss();});dialog.show();return dialog;
 }
 static Dialog weeks(Activity a,boolean[] current,WeekResult result){
  boolean[] draft=current.clone();LinearLayout body=column(a);int columns=WEEK_COLUMNS;Button[] cells=new Button[LAST_WEEK];
  Runnable paint=()->{for(int i=0;i<LAST_WEEK;i++){Button b=cells[i];b.setSelected(draft[i]);b.setTextColor(draft[i]?Color.WHITE:AppUi.INK);AppUi.press(b,draft[i]?AppUi.ACCENT:AppUi.SOFT,10);b.setContentDescription("第"+(i+1)+"周，"+(draft[i]?"已选":"未选"));}};
  for(int start=0;start<LAST_WEEK;start+=columns){LinearLayout row=new LinearLayout(a);body.addView(row);for(int col=0;col<columns;col++){int i=start+col;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,48),1);p.setMargins(dp(a,4),dp(a,4),dp(a,4),dp(a,4));if(i>=LAST_WEEK){row.addView(new View(a),p);continue;}Button b=button(a,String.valueOf(i+1),false);b.setPadding(0,0,0,0);b.setMinWidth(0);cells[i]=b;row.addView(b,p);b.setOnClickListener(v->{draft[i]=!draft[i];paint.run();});}}
  paint.run();LinearLayout presets=new LinearLayout(a);body.addView(presets);String[] labels={"全选","单周","双周"};for(int mode=0;mode<3;mode++){final int m=mode;Button b=button(a,labels[mode],false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,48),1);p.setMargins(dp(a,4),dp(a,12),dp(a,4),0);presets.addView(b,p);b.setOnClickListener(v->{for(int i=0;i<draft.length;i++)draft[i]=i<LAST_WEEK&&(m==0||(i+1)%2==(m==1?1:0));paint.run();});}
  TextView error=text(a,"",14);error.setTextColor(0xffb3261e);body.addView(error);Dialog dialog=sheet(a,"选择上课周次",body);LinearLayout actions=new LinearLayout(a);body.addView(actions);Button cancel=button(a,"取消",false),done=button(a,"确定",true);for(Button b:new Button[]{cancel,done}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,52),1);p.setMargins(dp(a,4),0,dp(a,4),0);actions.addView(b,p);}cancel.setOnClickListener(v->dialog.dismiss());done.setOnClickListener(v->{boolean any=false;for(boolean chosen:draft)any|=chosen;if(!any){error.setText("至少选择一周");return;}result.accept(draft.clone());dialog.dismiss();});dialog.show();return dialog;
 }
}

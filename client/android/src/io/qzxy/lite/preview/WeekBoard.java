package io.qzxy.lite.preview;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

/**
 * Seven real view columns, with accessible clickable blocks and animated horizontal week paging.
 *
 * Colours come from a fixed palette of twelve fills, each paired with a matching ink so text stays
 * readable. A course may carry an explicit palette index in its "color" field; otherwise the colour is
 * derived from the course name and de-duplicated against the other course names, so two different
 * courses do not end up looking the same.
 */
final class WeekBoard extends android.view.ViewGroup {
    interface Listener {
        void open(List<JSONObject> courses);
        /** Programmatic week change (buttons): the board still has to animate itself out. */
        void shift(int delta);
        /** Touch-driven week change: the board has already animated itself out. */
        void swipe(int delta);
        boolean canShift(int delta);
        /** A tap on an empty cell: add a course at that day and starting section. */
        void addAt(int day, int section);
    }
    static final int PALETTE = io.qzxy.lite.core.TimetablePalette.SIZE;
    static final int[] FILLS = new int[PALETTE];
    static final int[] INKS = new int[PALETTE];
    static {
        for (int i = 1; i <= PALETTE; i++) { FILLS[i - 1] = io.qzxy.lite.core.TimetablePalette.fill(i); INKS[i - 1] = io.qzxy.lite.core.TimetablePalette.ink(i); }
    }
    private android.graphics.Bitmap photo;
    void setPhoto(android.graphics.Bitmap bitmap){photo=bitmap;invalidate();}
    private final Paint paint = new Paint(3);
    private final List<Block> blocks = new ArrayList<>();
    private final String[] dates;
    private final int today, sections;
    private final Map<String, Integer> autoColors;
    private final Listener listener;
    /** Tap detection for empty cells, which are not child views. */
    private float downX, downY, tapSlop;
    private float axis, head, row, column;
    private static final class Block { int day,first,last; LinearLayout view; TextView title; List<JSONObject> courses=new ArrayList<>(); }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}

    /** Palette index (0-based) for one course: explicit choice first, otherwise the name colour. */
    static int colorIndexOf(JSONObject course, Map<String, Integer> autoColors) {
        int explicit = course.optInt("color", 0);
        if (explicit >= 1 && explicit <= PALETTE) return explicit - 1;
        Integer auto = autoColors == null ? null : autoColors.get(course.optString("name"));
        return auto == null ? 0 : auto;
    }

    /** Palette index for one course: explicit choice first, otherwise the de-duplicated name colour. */
    private int colorIndex(JSONObject course) {
        return colorIndexOf(course, autoColors);
    }

    /**
     * Assigns each distinct course name a palette entry. The rule itself lives in the core palette so it
     * can be tested offline; this only adapts the course list to it.
     */
    static Map<String, Integer> autoColorsFor(List<JSONObject> courses) {
        List<String> names = new ArrayList<>();
        for (JSONObject course : courses) names.add(course.optString("name"));
        List<String> distinct = io.qzxy.lite.core.TimetablePalette.distinctNames(names);
        int[] assigned = io.qzxy.lite.core.TimetablePalette.assign(distinct);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < distinct.size(); i++) result.put(distinct.get(i), assigned[i]);
        return result;
    }

    /** Palette swatch for the editor: index 0 is "auto", 1..PALETTE are the fixed colours. */
    static int fill(int index) { return io.qzxy.lite.core.TimetablePalette.fill(index); }
    static int ink(int index) { return io.qzxy.lite.core.TimetablePalette.ink(index); }

    WeekBoard(Context context,List<JSONObject> courses,String[] dates,int today,Map<String,Integer> autoColors,Listener listener) {
        super(context);this.dates=dates;this.today=today;this.autoColors=autoColors;this.listener=listener;
        setWillNotDraw(false);setBackgroundColor(0xffffffff);
        tapSlop=ViewConfiguration.get(context).getScaledTouchSlop();
        int max=10;
        for(JSONObject c:courses)max=Math.max(max,c.optInt("last"));sections=max;
        for(int day=0;day<7;day++) {
            List<JSONObject> daily=new ArrayList<>();for(JSONObject c:courses)if(c.optInt("day")==day)daily.add(c);
            daily.sort((a,b)->Integer.compare(a.optInt("first"),b.optInt("first")));
            Block group=null;
            for(JSONObject c:daily) {
                if(group==null || c.optInt("first")>group.last) { group=new Block();group.day=day;group.first=c.optInt("first");group.last=c.optInt("last");blocks.add(group); }
                group.last=Math.max(group.last,c.optInt("last"));group.courses.add(c);
            }
        }
        for(Block block:blocks) {
            JSONObject c=block.courses.get(0);int color=colorIndex(c);
            LinearLayout view=new LinearLayout(context);view.setOrientation(LinearLayout.VERTICAL);block.view=view;view.setPadding(dp(6),dp(7),dp(5),dp(6));
            TextView title=new TextView(context);block.title=title;title.setTextSize(12);title.setTextColor(INKS[color]);title.setGravity(Gravity.TOP|Gravity.LEFT);
            title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setIncludeFontPadding(false);title.setText(c.optString("name")+(block.courses.size()>1?" +"+(block.courses.size()-1):""));
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);view.addView(title,new LinearLayout.LayoutParams(-1,0,1));
            TextView room=new TextView(context);room.setTextSize(10);room.setTextColor(INKS[color]);room.setGravity(Gravity.LEFT);room.setMaxLines(2);room.setIncludeFontPadding(false);
            room.setText(c.optString("room").replaceFirst("^([A-Za-z]+[0-9]+)[-－](.+)$","$1\n$2"));room.setEllipsize(android.text.TextUtils.TruncateAt.END);view.addView(room,new LinearLayout.LayoutParams(-1,-2));
            GradientDrawable bg=new GradientDrawable();bg.setColor(FILLS[color]);bg.setCornerRadius(dp(8));view.setBackground(bg);
            String description="周"+new String[]{"一","二","三","四","五","六","日"}[block.day]+" 第"+block.first+"至"+block.last+"节";
            for(JSONObject course:block.courses)description+="，"+course.optString("name")+"，"+course.optString("room");
            view.setContentDescription(description+"，点击查看详情");view.setOnClickListener(v->listener.open(block.courses));view.setFocusable(true);addView(view);
        }
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec),height=Math.max(getMinimumHeight(),dp(56*sections+48));
        if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)height=Math.max(height,MeasureSpec.getSize(heightSpec));
        setMeasuredDimension(width,height);axis=dp(46);head=dp(48);row=(height-head)/sections;column=(width-axis)/7;
        for(Block b:blocks) {
            int h=Math.max(1,Math.round(row*(b.last-b.first+1))-dp(4));
            b.title.setMaxLines(Math.max(1,(h-dp(40))/dp(14)));
            b.view.measure(MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(column)-dp(4)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        for(Block block:blocks) {int x=Math.round(axis+column*block.day)+dp(2),y=Math.round(head+row*(block.first-1))+dp(2);
            block.view.layout(x,y,x+block.view.getMeasuredWidth(),y+block.view.getMeasuredHeight());}
    }
    /**
     * Taps that no course block claimed land here. They are hit-tested against the same axis/head/row/
     * column geometry used for layout, so tapping an empty cell opens the editor pre-filled with that
     * day and section. A drag never reaches this point: the pager intercepts it first.
     */
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
        if(action==MotionEvent.ACTION_UP){
            if(Math.abs(e.getX()-downX)<=tapSlop&&Math.abs(e.getY()-downY)<=tapSlop)tapCell(downX,downY);
            performClick();
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL)return true;
        return super.onTouchEvent(e);
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private void tapCell(float x,float y) {
        if(column<=0||row<=0)return;
        int day=(int)Math.floor((x-axis)/column);
        int section=(int)Math.floor((y-head)/row)+1;
        if(day<0||day>6||section<1||section>sections)return;
        listener.addAt(day,section);
    }
    private void label(Canvas c,String text,float x,float y,int size,int color,boolean bold) {
        paint.setColor(color);paint.setTextSize(dp(size));paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);paint.setTextAlign(Paint.Align.CENTER);c.drawText(text,x,y,paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(photo!=null&&!photo.isRecycled()){
            float scale=Math.max((float)getWidth()/photo.getWidth(),(float)getHeight()/photo.getHeight());
            float w=photo.getWidth()*scale,h=photo.getHeight()*scale;
            canvas.drawBitmap(photo,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),null);
            canvas.drawColor(0xbfffffff);
        }
        if(today>=0){paint.setColor(photo==null?0xfff5f8fd:0x60e6edfa);canvas.drawRect(axis+today*column,head,axis+(today+1)*column,getHeight(),paint);paint.setColor(AppUi.ACCENT);canvas.drawRoundRect(axis+today*column+dp(3),dp(4),axis+(today+1)*column-dp(3),dp(43),dp(10),dp(10),paint);}
        paint.setColor(0xffeff2f6);paint.setStrokeWidth(dp(0.6f));
        for(int n=0;n<=sections;n++)canvas.drawLine(axis,head+n*row,getWidth(),head+n*row,paint);
        label(canvas,"节",axis/2,dp(28),10,AppUi.MUTED,false);
        for(int n=0;n<7;n++) {float x=axis+(n+.5f)*column;label(canvas,"周"+new String[]{"一","二","三","四","五","六","日"}[n],x,dp(20),11,n==today?0xffffffff:AppUi.INK,true);
            label(canvas,dates[n],x,dp(37),10,n==today?0xe6ffffff:AppUi.MUTED,false);}
        for(int n=1;n<=sections;n++) {
            float center=head+(n-.5f)*row;
            label(canvas,String.valueOf(n),axis/2,center-dp(10),12,AppUi.INK,true);
            label(canvas,ClassTimes.start(n),axis/2,center+dp(5),10,AppUi.MUTED,false);
            label(canvas,ClassTimes.end(n),axis/2,center+dp(18),10,AppUi.MUTED,false);
        }
    }

    /**
     * Horizontal week paging. The board follows the finger while dragging (with resistance at the first
     * and last week), then settles onto the current or adjacent week. The touch container stays fixed
     * so child translations cannot feed back into gesture coordinates.
     */
    static final class Pager extends FrameLayout {
        interface Factory { WeekBoard create(int delta); }
        private final Listener listener;
        private final ScrollView[] pages = new ScrollView[3];
        private float downX,downY,offset;
        private final float slop;
        private boolean horizontal,vertical,animating;
        private VelocityTracker velocity;
        private android.animation.ValueAnimator motion;
        Pager(Context context, Factory factory, Listener listener) {
            super(context); this.listener=listener;
            slop=ViewConfiguration.get(context).getScaledTouchSlop();
            setClipChildren(true); setClipToPadding(true);
            for(int i=0;i<3;i++) {
                if(i!=1 && !listener.canShift(i-1))continue;
                ScrollView page=new ScrollView(context);page.setFillViewport(true);page.setVerticalScrollBarEnabled(false);
                WeekBoard board=factory.create(i-1); page.addView(board,new ScrollView.LayoutParams(-1,-2));
                pages[i]=page;addView(page,new FrameLayout.LayoutParams(-1,-1));
                if(i!=1)page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                page.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->board.setMinimumHeight(b-t));
            }
        }
        private void position(float value) {
            offset=value;
            for(int i=0;i<3;i++)if(pages[i]!=null)pages[i].setTranslationX((i-1)*getWidth()+value);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
            super.onLayout(changed,l,t,r,b);position(offset);
        }
        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();
                downX=e.getRawX();downY=e.getRawY();horizontal=false;vertical=false;
            }
            if(velocity!=null)velocity.addMovement(e);
            boolean result=super.dispatchTouchEvent(e);
            if(e.getActionMasked()==MotionEvent.ACTION_UP || e.getActionMasked()==MotionEvent.ACTION_CANCEL) {
                if(velocity!=null){velocity.recycle();velocity=null;}
            }
            return result;
        }
        private void direction(MotionEvent e) {
            if(horizontal||vertical)return;
            float dx=Math.abs(e.getRawX()-downX),dy=Math.abs(e.getRawY()-downY);
            if(dx>slop && dx>1.5f*dy) {
                horizontal=true;
                int scroll=pages[1].getScrollY();
                for(ScrollView page:pages)if(page!=null)page.scrollTo(0,scroll);
            } else if(dy>slop && dy>dx)vertical=true;
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if(animating)return true;
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE)direction(e);
            return horizontal;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(animating)return true;
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_MOVE) {
                direction(e);
                if(horizontal) {
                    float dx=e.getRawX()-downX;
                    int delta=dx<0?1:-1;
                    position(Math.max(-getWidth(),Math.min(getWidth(),listener.canShift(delta)?dx:dx*.25f)));
                }
                return true;
            }
            if(action==MotionEvent.ACTION_CANCEL){settle(0,null);return true;}
            if(action==MotionEvent.ACTION_UP && horizontal) {
                float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=velocity.getXVelocity();}
                int delta=offset<0?1:-1;
                boolean enough=Math.abs(offset)>getWidth()*.20f;
                boolean flick=Math.abs(offset)>18*getResources().getDisplayMetrics().density
                    && Math.abs(speed)>400*getResources().getDisplayMetrics().density && speed*offset>0;
                if(listener.canShift(delta)&&(enough||flick))settle(-delta*getWidth(),()->listener.swipe(delta));
                else settle(0,null);
                return true;
            }
            return true;
        }
        private void settle(float target,Runnable done) {
            if(motion!=null)motion.cancel();animating=true;
            motion=android.animation.ValueAnimator.ofFloat(offset,target);motion.setDuration(190);
            motion.setInterpolator(new android.view.animation.DecelerateInterpolator());
            motion.addUpdateListener(a->position((Float)a.getAnimatedValue()));
            motion.addListener(new android.animation.AnimatorListenerAdapter(){
                boolean cancelled;
                @Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}
                @Override public void onAnimationEnd(android.animation.Animator a){animating=false;if(!cancelled&&done!=null)done.run();}
            });motion.start();
        }
        int verticalPosition(){return pages[1].getScrollY();}
        void restoreVerticalPosition(int value){post(()->{for(ScrollView page:pages)if(page!=null)page.scrollTo(0,value);});}
        void animateOutThen(int delta,Runnable action) {
            if(animating||!listener.canShift(delta))return;
            int scroll=pages[1].getScrollY();for(ScrollView page:pages)if(page!=null)page.scrollTo(0,scroll);
            settle(-delta*getWidth(),action);
        }
        @Override protected void onDetachedFromWindow(){if(motion!=null)motion.cancel();if(velocity!=null){velocity.recycle();velocity=null;}super.onDetachedFromWindow();}
    }
}

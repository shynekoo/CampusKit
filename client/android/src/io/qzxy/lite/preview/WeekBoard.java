package io.qzxy.lite.preview;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import io.qzxy.lite.core.BoardFit;
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
    /**
     * Colours come from {@link Theme} on every call rather than from a copy taken at class load:
     * 课程配色 can be changed while the timetable is on screen, and a stale copy is exactly the bug
     * where the settings preview updates but the board behind it does not.
     *
     * <p>{@code fill}/{@code ink} take the editor's 1-based choice (0 meaning 自动); {@code fillAt}/
     * {@code inkAt} take the board's 0-based palette slot.</p>
     */
    static int fillAt(int slot) { return Theme.cellFill(slot); }
    static int inkAt(int slot) { return Theme.inkFor(slot); }
    static int fill(int choice) {
        return choice < 1 || choice > PALETTE ? io.qzxy.lite.core.TimetablePalette.AUTO_FILL : fillAt(choice - 1);
    }
    static int ink(int choice) {
        return choice < 1 || choice > PALETTE ? io.qzxy.lite.core.TimetablePalette.AUTO_INK : inkAt(choice - 1);
    }
    private android.graphics.Bitmap photo;
    void setPhoto(android.graphics.Bitmap bitmap){photo=bitmap;invalidate();}
    private final Paint paint = new Paint(3);
    /** Measures the font the device actually draws with, so a theme font cannot break the fit. */
    private final android.text.TextPaint measurer = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final List<Block> blocks = new ArrayList<>();
    private final String[] dates;
    private final int today, sections;
    private final Map<String, Integer> autoColors;
    private final Listener listener;
    /** Tap detection for empty cells, which are not child views. */
    private float downX, downY, tapSlop;
    private float axis, head, row, column;
    /** The width the cell type was last sized for, so a rotation or split-screen resize re-fits it. */
    private int fittedWidth;
    /**
     * The height one week's page really has, handed down by {@link Pager} before this board is
     * measured. Zero until then; the board fills it instead of guessing a row height, and only asks
     * for a scroll when the rows would fall below {@link #minReadableRow}.
     */
    private int viewportHeight;
    void setViewportHeight(int px) { viewportHeight = Math.max(0, px); }
    private static final class Block { int day,first,last; LinearLayout view; TextView title,room; String roomText=""; List<JSONObject> courses=new ArrayList<>(); }
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

    WeekBoard(Context context,List<JSONObject> courses,String[] dates,int today,Map<String,Integer> autoColors,Listener listener) {
        super(context);this.dates=dates;this.today=today;this.autoColors=autoColors;this.listener=listener;
        setWillNotDraw(false);setBackgroundColor(AppUi.SURFACE);
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
            LinearLayout view=new LinearLayout(context);view.setOrientation(LinearLayout.VERTICAL);block.view=view;
            TextView title=new TextView(context);block.title=title;title.setTextColor(inkAt(color));title.setGravity(Gravity.TOP|Gravity.LEFT);
            title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setIncludeFontPadding(false);title.setText(c.optString("name")+(block.courses.size()>1?" +"+(block.courses.size()-1):""));
            // Greedy breaking: the default balanced strategy moves a whole character to the next
            // line to even the block out, which on a narrow column costs a third of the width.
            title.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
            title.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);view.addView(title,new LinearLayout.LayoutParams(-1,-2));
            TextView room=new TextView(context);block.room=room;room.setTextColor(inkAt(color));room.setGravity(Gravity.LEFT);room.setIncludeFontPadding(false);
            block.roomText=c.optString("room").replaceFirst("^([A-Za-z]+[0-9]+)[-－](.+)$","$1\n$2");
            room.setText(block.roomText);room.setEllipsize(android.text.TextUtils.TruncateAt.END);
            // The room sits right under the name rather than pinned to the bottom of the block: a
            // two-section block is tall, and a pinned room left a lake of colour between the two.
            LinearLayout.LayoutParams roomParams=new LinearLayout.LayoutParams(-1,-2);roomParams.topMargin=dp(2);view.addView(room,roomParams);
            GradientDrawable bg=new GradientDrawable();bg.setColor(fillAt(color));bg.setCornerRadius(dp(Theme.radius()));view.setBackground(bg);
            String description="周"+new String[]{"一","二","三","四","五","六","日"}[block.day]+" 第"+block.first+"至"+block.last+"节";
            for(JSONObject course:block.courses)description+="，"+course.optString("name")+"，"+course.optString("room");
            view.setContentDescription(description+"，点击查看详情");view.setOnClickListener(v->listener.open(block.courses));view.setFocusable(true);addView(view);
        }
    }
    /**
     * Sizes one week's cells to the width they actually got.
     *
     * <p>Geometry comes from {@link io.qzxy.lite.core.BoardFit}, so the gutter, the padding and the
     * type shrink together on a narrow phone instead of leaving three characters per line. The type
     * is set in px rather than sp on purpose: the board is a fixed seven-column matrix, and a reader
     * who raises the system font size should still see seven columns, not four cells of clipping.
     * Line counts are then taken from the measured line height, so nothing is cut off.</p>
     *
     * <p>The nominal size is only a starting point: it is trimmed again against the width of one
     * character in the font the device really draws with. A phone theme can swap in a font whose
     * glyphs are half again as wide as the system one, and without that measurement the same cell
     * that holds three characters on one phone holds two on the next.</p>
     */
    private void fitCells(int width) {
        float density=Math.max(1f,getResources().getDisplayMetrics().density);
        int columnDp=Math.round(column/density);
        int pad=dp(BoardFit.cellPad(columnDp)), size=BoardFit.titleSize(columnDp);
        float textPx=Math.max(1,Math.round(column)-dp(BoardFit.gap())-2*pad);
        int titlePx=BoardFit.shrinkToFit(textPx,charWidth(dp(size),true),dp(size),dp(BoardFit.MIN_TITLE-1));
        int roomSize=BoardFit.roomSize(size), roomPx=dp(roomSize);
        roomPx=BoardFit.shrinkToFit(textPx,charWidth(roomPx,false),roomPx,dp(roomSize-1));
        roomPx=Math.min(roomPx,Math.max(dp(7),titlePx-dp(1)));
        fittedWidth=width;
        for(Block block:blocks) {
            // A little more air above and below than at the sides: a rounded corner eats the first
            // and last line otherwise, and the sides are what the name needs every pixel of.
            block.view.setPadding(pad,pad+dp(1),pad,pad+dp(1));
            block.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,titlePx);
            block.room.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,roomPx);
        }
    }
    /** Width of one Chinese character at this size, in the font this device draws. */
    private float charWidth(int px,boolean bold) {
        measurer.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        measurer.setTextSize(px);
        return measurer.measureText("课");
    }
    /** Whether this view can draw that text on one line inside the given width. */
    private boolean fits(TextView view,String text,int widthPx) {
        measurer.setTypeface(view.getTypeface());
        measurer.setTextSize(view.getTextSize());
        return measurer.measureText(text)<=widthPx;
    }

    /**
     * The shortest row that still draws everything a row carries: the section number and the two
     * times in the gutter, and one line of a course name inside a block.
     *
     * <p>Compressing rows is how the board avoids a scrollbar on a normal phone, but it has to stop
     * somewhere. Below this the gutter's three labels collide and a name loses its own line, so the
     * board keeps this height and the page scrolls instead of squeezing further.</p>
     */
    private float minReadableRow(int columnDp) {
        measurer.setTypeface(Typeface.DEFAULT_BOLD); measurer.setTextSize(dp(12));
        Paint.FontMetrics number = measurer.getFontMetrics();
        measurer.setTypeface(Typeface.DEFAULT); measurer.setTextSize(dp(9));
        Paint.FontMetrics time = measurer.getFontMetrics();
        // Mirrors the baselines in onDraw(): the number 10dp above the row's centre, the two times
        // 5dp and 18dp below it. Ascent is negative, hence the signs.
        float half = Math.max(dp(10) - number.ascent, dp(18) + time.descent);
        float gutter = 2 * half + dp(2);
        float name = 2 * dp(BoardFit.cellPad(columnDp)) + dp(BoardFit.titleSize(columnDp)) + dp(10);
        return Math.max(gutter, name);
    }

    /**
     * Height comes from the page, not from a constant: whatever {@link Pager} was given is what the
     * board fills, so ten sections on a normal phone stretch or compress to the space between the
     * header and the bottom bar instead of always asking for 56dp a row and scrolling the excess.
     * A page too short for {@link #minReadableRow} tall rows keeps that height and scrolls.
     */
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec);
        float density=Math.max(1f,getResources().getDisplayMetrics().density);
        axis=dp(BoardFit.axis(Math.round(width/density)));head=dp(48);column=(width-axis)/7f;
        int columnDp=Math.round(column/density);
        int minimum=Math.round(head+sections*minReadableRow(columnDp));
        int height=Math.max(minimum,viewportHeight);
        int mode=MeasureSpec.getMode(heightSpec);
        if(mode==MeasureSpec.EXACTLY)height=Math.max(minimum,MeasureSpec.getSize(heightSpec));
        else if(mode==MeasureSpec.AT_MOST)height=Math.max(minimum,Math.min(height,MeasureSpec.getSize(heightSpec)));
        setMeasuredDimension(width,height);
        row=(float)(height-head)/sections;
        if(width!=fittedWidth)fitCells(width);
        for(Block b:blocks) {
            int h=Math.max(1,Math.round(row*(b.last-b.first+1))-dp(4));
            int space=Math.max(0,h-b.view.getPaddingTop()-b.view.getPaddingBottom());
            int nameLine=Math.max(1,b.title.getLineHeight()),roomLine=Math.max(1,b.room.getLineHeight());
            // The room is optional and the name is not: room lines are shown only while a line of the
            // name still fits beside them. The heights are the measured line heights, not the nominal
            // type size — a phone font is usually 1.2–1.4 times taller than its point size.
            int rooms=BoardFit.roomLines(space/density,nameLine/density,roomLine/density);
            String oneLine=b.roomText.replace('\n',' ');
            // A single room line only earns its place if the whole room fits on it: "A11 3…" is
            // worse than no room at all, because the name then gets that line back.
            if(rooms==1&&!fits(b.room,oneLine,Math.round(column)-dp(BoardFit.gap())-2*b.view.getPaddingLeft()))rooms=0;
            b.room.setVisibility(rooms==0?View.GONE:View.VISIBLE);
            b.room.setMaxLines(Math.max(1,rooms));
            b.room.setText(rooms==1?oneLine:b.roomText);
            int reserved=rooms==0?0:roomLine*rooms+dp(2);
            int lines=Math.min(BoardFit.titleCap(columnDp),(space-reserved)/nameLine);
            b.title.setMaxLines(Math.max(1,lines));
            b.view.measure(MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(column)-dp(BoardFit.gap())),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
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
            // 背景图 under 深色 theme: the veil has to be the page's colour, not paper, or the whole
            // board turns into a bright rectangle in an otherwise dark app.
            canvas.drawColor(Theme.dark()?0xc0101419:0xbfffffff);
        }
        if(today>=0){paint.setColor(photo==null?(Theme.dark()?0xff1c2531:0xfff5f8fd):0x60e6edfa);canvas.drawRect(axis+today*column,head,axis+(today+1)*column,getHeight(),paint);paint.setColor(AppUi.ACCENT);canvas.drawRoundRect(axis+today*column+dp(3),dp(4),axis+(today+1)*column-dp(3),dp(43),dp(10),dp(10),paint);}
        // 显示网格线: on, the table is ruled in both directions; off, the cells are separated by the
        // space between them alone. The rule also draws the header baseline, which is why the header
        // keeps its line either way — without it the dates float above nothing.
        if(Theme.grid()){
            paint.setColor(AppUi.LINE);paint.setStrokeWidth(dp(0.6f));
            for(int n=0;n<=sections;n++)canvas.drawLine(axis,head+n*row,getWidth(),head+n*row,paint);
            for(int n=0;n<=7&&axis+n*column<=getWidth();n++)canvas.drawLine(axis+n*column,head,axis+n*column,getHeight(),paint);
        }else{
            paint.setColor(AppUi.LINE);paint.setStrokeWidth(dp(0.6f));
            canvas.drawLine(axis,head,getWidth(),head,paint);
        }
        label(canvas,"节",axis/2,dp(28),10,AppUi.MUTED,false);
        for(int n=0;n<7;n++) {float x=axis+(n+.5f)*column;label(canvas,"周"+new String[]{"一","二","三","四","五","六","日"}[n],x,dp(20),11,n==today?AppUi.ON_ACCENT:AppUi.INK,true);
            label(canvas,dates[n],x,dp(37),10,n==today?(AppUi.ON_ACCENT&0x00ffffff)|0xcc000000:AppUi.MUTED,false);}
        for(int n=1;n<=sections;n++) {
            float center=head+(n-.5f)*row;
            // Times drop a step on a narrow gutter so 08:30 still fits between the rule and the axis.
            int timeSize=(int)Math.round(axis/getResources().getDisplayMetrics().density)<36?9:10;
            label(canvas,String.valueOf(n),axis/2,center-dp(10),12,AppUi.INK,true);
            label(canvas,ClassTimes.start(n),axis/2,center+dp(5),timeSize,AppUi.MUTED,false);
            label(canvas,ClassTimes.end(n),axis/2,center+dp(18),timeSize,AppUi.MUTED,false);
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
                final WeekBoard board=factory.create(i-1);
                // The page knows its own height before the board is measured, so it hands it down:
                // the board then fills exactly that height instead of inventing one, and the page
                // only scrolls when the rows would drop below their readable minimum.
                ScrollView page=new ScrollView(context){
                    @Override protected void onMeasure(int widthSpec,int heightSpec) {
                        boolean bounded=MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED;
                        board.setViewportHeight(bounded?Math.max(0,MeasureSpec.getSize(heightSpec)-getPaddingTop()-getPaddingBottom()):0);
                        super.onMeasure(widthSpec,heightSpec);
                    }
                };
                page.setFillViewport(true);page.setVerticalScrollBarEnabled(false);
                page.addView(board,new ScrollView.LayoutParams(-1,-2));
                pages[i]=page;addView(page,new FrameLayout.LayoutParams(-1,-1));
                if(i!=1)page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
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
            motion=android.animation.ValueAnimator.ofFloat(offset,target);motion.setDuration(MotionSpec.WEEK_SLIDE_DURATION);
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
            // 位移只取一屏：回本周这类一次跨多周的跳转若按 |delta| 屏滑出，会像被甩出好几页；
            // 跨了几周由随后的 applyWeek 落地，动画本身只负责「同一方向滑出一屏」。
            settle(-(delta>0?getWidth():-getWidth()),action);
        }
        @Override protected void onDetachedFromWindow(){if(motion!=null)motion.cancel();if(velocity!=null){velocity.recycle();velocity=null;}super.onDetachedFromWindow();}
    }
}

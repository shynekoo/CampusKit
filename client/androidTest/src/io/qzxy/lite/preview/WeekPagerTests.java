package io.qzxy.lite.preview;

import android.app.Instrumentation;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.JSONObject;

final class WeekPagerTests {
    static int run(Instrumentation instrumentation) {
        final Throwable[] error={null};
        instrumentation.runOnMainSync(()->{
            try {
                WeekBoard.Listener listener=new WeekBoard.Listener(){
                    public void open(List<JSONObject> group){} public void shift(int delta){}
                    public void swipe(int delta){} public boolean canShift(int delta){return true;}
                    public void addAt(int day,int section){}
                };
                WeekBoard.Pager pager=new WeekBoard.Pager(instrumentation.getTargetContext(),delta->
                    new WeekBoard(instrumentation.getTargetContext(),new ArrayList<>(),new String[]{"","","","","","",""},-1,new HashMap<>(),listener),listener);
                pager.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));
                pager.layout(0,0,1000,1000);
                for(int direction:new int[]{-1,1}) {
                    long now=android.os.SystemClock.uptimeMillis();
                    event(pager,now,now,MotionEvent.ACTION_DOWN,500,500);
                    event(pager,now,now+20,MotionEvent.ACTION_MOVE,500+direction*150,500);
                    for(int i=0;i<10;i++) {
                        event(pager,now,now+40+i*20,MotionEvent.ACTION_MOVE,500+direction*150,500);
                        if(pager.getTranslationX()!=0 || pager.getChildAt(1).getTranslationX()!=direction*150)
                            throw new AssertionError("Stationary finger caused movement");
                        if(pager.getChildAt(0).getTranslationX()!=-1000+direction*150
                            || pager.getChildAt(2).getTranslationX()!=1000+direction*150)
                            throw new AssertionError("Adjacent page is not contiguous");
                    }
                    // Finish without triggering asynchronous paging; the next direction uses a fresh origin.
                    event(pager,now,now+260,MotionEvent.ACTION_MOVE,500,500);
                    event(pager,now,now+300,MotionEvent.ACTION_UP,500,500);
                    pager.onDetachedFromWindow();
                }
            }catch(Throwable failure){error[0]=failure;}
        });
        if(error[0]!=null)throw new AssertionError(error[0]);
        return 40;
    }
    private static void event(View view,long down,long time,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(down,time,action,x,y,0);
        view.dispatchTouchEvent(event);event.recycle();
    }
}

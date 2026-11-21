// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import java.util.*;
import org.json.*;
public class ScheduleWidget extends AppWidgetProvider {
    static void updateAll(Context context){AppWidgetManager manager=AppWidgetManager.getInstance(context);for(Class<?> type:new Class<?>[]{ScheduleWidget.class,ScheduleWidgetMedium.class,ScheduleWidgetWide.class}){int[] ids=manager.getAppWidgetIds(new ComponentName(context,type));new ScheduleWidget().onUpdate(context,manager,ids);}}
    @Override public void onReceive(Context context,Intent intent){super.onReceive(context,intent);String action=intent.getAction();if("io.qzxy.lite.REFRESH_SCHEDULE".equals(action)||Intent.ACTION_DATE_CHANGED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action))updateAll(context);}
    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,android.os.Bundle options){onUpdate(context,manager,new int[]{id});}
    static CharSequence courseText(List<JSONObject> courses,int now){
        android.text.SpannableStringBuilder lines=new android.text.SpannableStringBuilder();
        for(JSONObject c:courses){if(lines.length()>0)lines.append("\n\n");lines.append(ClassTimes.range(c.optInt("first"),c.optInt("last"))).append("  ");
            int start=lines.length();lines.append(c.optString("name"));String state=ScheduleInfo.status(c,now);
            if(state.equals("已结束")&&lines.length()>start)lines.setSpan(new android.text.style.StrikethroughSpan(),start,lines.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            String room=c.optString("room");if(!room.isEmpty())lines.append("\n").append(room);
            if(!state.equals("已结束"))lines.append(room.isEmpty()?"\n":" · ").append(state);
        }return lines;
    }
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        SharedPreferences prefs=TimetableStore.open(context);
        String title="今天的课",summary="打开 科信小助手 设置课表";CharSequence content="";
        try{String monday=prefs.getString("monday","");int week=ScheduleInfo.week(monday,ScheduleInfo.date());
            title=ScheduleInfo.date().substring(5)+" · 今天的课";
            if(!monday.isEmpty()&&week>=1&&week<=30){List<JSONObject> courses=ScheduleInfo.today(prefs.getString("courses","[]"),monday,ScheduleInfo.date());summary=ScheduleInfo.summary(courses,ScheduleInfo.minutes());
                courses.sort((a,b)->Boolean.compare(ScheduleInfo.status(a,ScheduleInfo.minutes()).equals("已结束"),ScheduleInfo.status(b,ScheduleInfo.minutes()).equals("已结束")));
                content=courseText(courses,ScheduleInfo.minutes());

            }else if(!monday.isEmpty())summary="今天不在学期课表范围内";
        }catch(Exception invalid){summary="课表无法读取，请打开应用检查";}
        for(int id:ids){RemoteViews view=new RemoteViews(context.getPackageName(),R.layout.schedule_widget);view.setTextViewText(R.id.widget_title,title);view.setTextViewText(R.id.widget_summary,summary);view.setTextViewText(R.id.widget_courses,content);
            // A launcher cell is not a constant: the same widget is 110dp on a small phone and can
            // be shorter than its own header once the reader's font size is raised. Both dimensions
            // decide what is dropped — the footer first, then lines, then a size step.
            android.os.Bundle options=manager.getAppWidgetOptions(id);
            int width=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180);
            int height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110);
            boolean narrow=width<180,shortWidget=height<120,compact=narrow||shortWidget;
            view.setTextViewText(R.id.widget_title,compact?"今日课程":title);
            view.setTextViewTextSize(R.id.widget_title,android.util.TypedValue.COMPLEX_UNIT_SP,compact?14:16);
            view.setTextViewTextSize(R.id.widget_summary,android.util.TypedValue.COMPLEX_UNIT_SP,compact?12:14);
            view.setTextViewTextSize(R.id.widget_courses,android.util.TypedValue.COMPLEX_UNIT_SP,compact?12:13);
            view.setViewVisibility(R.id.widget_courses,android.view.View.VISIBLE);
            view.setInt(R.id.widget_courses,"setMaxLines",compact?2:6);
            view.setInt(R.id.widget_summary,"setMaxLines",compact?1:2);
            view.setViewVisibility(R.id.widget_footer,compact?android.view.View.GONE:android.view.View.VISIBLE);
            float density=context.getResources().getDisplayMetrics().density;
            int pad=Math.round((compact?12:20)*density);
            view.setViewPadding(R.id.widget_root,pad,Math.round(8*density),pad,Math.round(8*density));
            Intent open=new Intent(context,TimetableActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("showToday",true);
            view.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,10,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            Intent refresh=new Intent(context,ScheduleWidget.class).setAction("io.qzxy.lite.REFRESH_SCHEDULE");view.setOnClickPendingIntent(R.id.widget_refresh,PendingIntent.getBroadcast(context,11,refresh,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));manager.updateAppWidget(id,view);}
    }
}

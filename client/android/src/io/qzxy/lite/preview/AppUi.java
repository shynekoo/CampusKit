package io.qzxy.lite.preview;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;

/** The app's light theme and native outline icon family. No font glyph icons. */
final class AppUi {
 static final int BG=0xfff6f7f9,SURFACE=0xffffffff,INK=0xff202735,MUTED=0xff596575,ACCENT=0xff275dad,LINE=0xffe3e7ed,SOFT=0xffedf2f9;
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 /** The installed version, so footers never drift out of date. */
 static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception unknown){return "";}}
 static GradientDrawable shape(Context c,int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));return g;}
 static GradientDrawable outlined(Context c,int fill,int radius,int stroke){GradientDrawable g=shape(c,fill,radius);g.setStroke(Math.max(1,dp(c,1)),stroke);return g;}
 static void press(View v,int fill,int radius){v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1f275dad),shape(v.getContext(),fill,radius),null));v.setFocusable(true);}
 static Drawable icon(Context c,String name,int color,int size){Icon d=new Icon(name,color);d.setBounds(0,0,dp(c,size),dp(c,size));return d;}
 static final class Icon extends Drawable {
  final String name;final Paint p=new Paint(3);int color;
  Icon(String n,int c){name=n;color=c;}
  void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,p);}
  void rect(Canvas c,float a,float b,float x,float y){c.drawRoundRect(a,b,x,y,2,2,p);}
  @Override public void draw(Canvas c){int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
   switch(name){
    case "back":line(c,14,5,7,12);line(c,7,12,14,19);break;
    case "refresh":c.drawArc(4,4,20,20,35,290,false,p);line(c,20,4,20,9);line(c,15,9,20,9);break;
    case "info":c.drawCircle(12,12,9,p);line(c,12,11,12,17);c.drawCircle(12,7.5f,.4f,p);break;
    case "user":c.drawCircle(12,8,3.5f,p);c.drawArc(4,13,20,25,190,160,false,p);break;
    case "calendar":rect(c,3,5,21,21);line(c,3,10,21,10);line(c,8,3,8,7);line(c,16,3,16,7);line(c,7,14,10,14);line(c,14,14,17,14);break;
    case "today":rect(c,4,3,20,21);line(c,8,8,16,8);line(c,8,12,16,12);line(c,8,16,13,16);break;
    case "pay":rect(c,3,5,21,19);line(c,3,10,21,10);line(c,6,15,10,15);break;
    case "parcel":rect(c,4,5,20,20);line(c,4,10,20,10);line(c,12,5,12,10);line(c,9,15,15,15);break;
    case "water":Path drop=new Path();drop.moveTo(12,2);drop.cubicTo(9,7,5,10,5,15);drop.cubicTo(5,24,19,24,19,15);drop.cubicTo(19,10,15,7,12,2);c.drawPath(drop,p);break;
    case "score":rect(c,4,3,20,21);line(c,8,9,16,9);line(c,8,13,13,13);line(c,8,17,13,17);line(c,15,16,16.6f,18);line(c,16.6f,18,19,15);break;
    case "room":rect(c,3,4,21,20);line(c,13,4,13,20);c.drawCircle(16.5f,12,.9f,p);line(c,3,20,21,20);break;
    case "torch":Path bolt=new Path();bolt.moveTo(13.4f,2.5f);bolt.lineTo(6.6f,13.2f);bolt.lineTo(11,13.2f);bolt.lineTo(10.2f,21.5f);bolt.lineTo(17.4f,10.6f);bolt.lineTo(12.8f,10.6f);bolt.close();c.drawPath(bolt,p);break;
    case "down":line(c,7,10,12,15);line(c,12,15,17,10);break;
    default:line(c,3,9,3,3);line(c,3,3,9,3);line(c,15,3,21,3);line(c,21,3,21,9);line(c,3,15,3,21);line(c,3,21,9,21);line(c,15,21,21,21);line(c,21,21,21,15);line(c,7,12,17,12);
   }c.restoreToCount(save);
  }
  @Override public void setAlpha(int a){p.setAlpha(a);}
  @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}

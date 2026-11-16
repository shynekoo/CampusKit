package io.qzxy.lite.preview;
import android.content.Context;
import org.json.JSONObject;
import java.util.*;
/**
 * 服务器会按 {@code VersionCode} 请求头拦客户端：拿官方 3.1.5 的 31005 去打
 * {@code /api/v1/dev/start}，回的是 {@code {"code":-1,"msg":"请升级最新版APP"}}，
 * 而同设备的详情接口（同样的头）却是 {@code code:0}。也就是说凭据和参数都没问题，
 * 它只是嫌这个版本号太旧。
 *
 * <p>官方版本号是 3.1.05 这样拼的（三乘万 + 一乘千 + 修订号）：3.1.5→31005、3.1.10→31010。
 * 官方现役已经是 3.1.10，所以从 31010 开始；万一它哪天又抬门槛，梯子会继续往上试，
 * 第一个不被拦的就记住，下次直接用它。
 *
 * <p>只在服务端明确说「请升级」时才往下试——那句话意味着请求没进业务逻辑，
 * 重发不会多开一次水；其它负数码（-99 过期、-1 参数错）一律不重试。
 */
final class LifeVersion {
 /** 官方 3.1.10 的 VersionCode，也就是到目前为止服务器认可的那一档。 */
 static final int FLOOR=31010;
 private static final int[] LADDER={31010,31020,31100,32000,33000,40000,50000};
 private static final String FILE="life-version-v1",KEY="accepted";
 private LifeVersion(){}
 /** 已知可用的版本排在最前，其余按由低到高补齐；没有记录时就是整条梯子。 */
 static int[] order(Context context){
  int known=known(context);if(known<=0)return LADDER.clone();
  int[] out=new int[LADDER.length];int n=0;out[n++]=known;
  for(int v:LADDER)if(v>known)out[n++]=v;
  return Arrays.copyOf(out,n);
 }
 static int known(Context context){
  if(context==null)return 0;
  try{return context.getSharedPreferences(FILE,Context.MODE_PRIVATE).getInt(KEY,0);}catch(Exception e){return 0;}
 }
 static void remember(Context context,int version){
  if(context==null||version<=0)return;
  try{android.content.SharedPreferences p=context.getSharedPreferences(FILE,Context.MODE_PRIVATE);if(p.getInt(KEY,0)!=version)p.edit().putInt(KEY,version).apply();}catch(Exception e){}
 }
 /** 只认「请升级」这一种说法：它是版本墙，不是业务拒绝。 */
 static boolean upgradeRequired(JSONObject reply){
  if(reply==null)return false;
  final int code;try{code=LifeAuthApi.resultCode(reply);}catch(Exception e){return false;}
  if(code>=0)return false;
  String why=LifeAuthApi.message(reply);
  return why.contains("升级")||why.toLowerCase(Locale.ROOT).contains("upgrade");
 }
 static int ceiling(){return LADDER[LADDER.length-1];}
}

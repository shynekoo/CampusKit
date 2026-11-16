package io.qzxy.lite.preview;
import org.json.*;
/**
 * 只读诊断：让设备自己说清楚「为什么现在不能开」。
 *
 * <p>开启请求已经过了版本墙（{@code -3} 是业务逻辑给的回答），但剩下两个问题从详情里看不出来：
 * 这台机器允许哪种使用方式、以及 {@code gene.status=99} 到底算「空闲」还是「锁定」。
 * 官方客户端用 {@code dev-use-way} 决定按钮与参数，用 {@code dev/status?more=true} 拿更细的运行状态，
 * 这两条都是 GET，加上「有没有未结账单」的只读查询，足以把 {@code -3} 的解释定下来。
 *
 * <p>三个请求各自独立：某个失败只记下原因，不影响其它两个。
 */
final class LifeDiagnostics {
 private LifeDiagnostics(){}
 static JSONObject collect(JSONObject session,String did){
  JSONObject out=new JSONObject();
  read(out,"useWay",LifeDeviceApi.useWayPath(did),session);
  read(out,"status",LifeDeviceApi.statusPath(did),session);
  read(out,"bills",LifeDeviceApi.BILLS_PATH,session);
  return out;
 }
 private static void read(JSONObject out,String key,String path,JSONObject session){
  try{out.put(key,LifeDeviceApi.get(path,session));}
  catch(Exception e){try{out.put(key,"读取失败："+LifeDeviceFailure.describe(e));}catch(Exception ignored){}}
 }
}

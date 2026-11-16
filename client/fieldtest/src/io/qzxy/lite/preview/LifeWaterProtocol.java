package io.qzxy.lite.preview;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
/** Direct online water only. No automatic retry or alternate payment selection. */
final class LifeWaterProtocol {
 static String id(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException("设备编号无效");return id;}
 static String startPath(String did,int pay){if(pay!=91&&pay!=92)throw new IllegalArgumentException("暂不支持此支付方式");return "/api/v1/dev/start?did="+id(did)+"&upgrade=true&ptype="+pay+"&args=&rcp=false&cnt=1";}
 static String endPath(String did){return "/api/v1/dev/end?did="+id(did)+"&rcp=false";}
 static String owner(JSONObject data)throws Exception{if(!data.has("user")||data.isNull("user"))return "";return LifeDeviceProfile.text(data.getJSONObject("user"),"id",128,true);}
 static List<Integer> payments(JSONObject reply,String did,String uid)throws Exception{
  LifeDeviceProfile p=LifeDeviceProfile.refreshed(reply,did);JSONObject data=reply.getJSONObject("data"),dev=data.getJSONObject("device");
  if(p.type!=5&&p.type!=6&&p.type!=8)throw new LifeDeviceFailure("当前仅支持在线水控、淋浴和饮水机");
  if(p.networkStatus!=1||p.status==null||p.status!=99)throw new LifeDeviceFailure("设备未确认在线且可开始使用");
  String user=owner(data);if(!user.isEmpty())throw new LifeDeviceFailure("设备仍有使用者，请先结束原使用记录");
  JSONObject setting=dev.getJSONObject("ep").getJSONObject("setting");
  if(setting.has("payPwd")&&LifeDeviceProfile.integer(setting,"payPwd")!=0)throw new LifeDeviceFailure("此设备要求交易密码，请在原版使用");
  if(data.optBoolean("prepay",false)||dev.optBoolean("prepay",false))throw new LifeDeviceFailure("预付费设备请在原版使用");
  JSONArray types=data.getJSONArray("payItems");List<Integer> out=new ArrayList<>();for(int i=0;i<types.length();i++){int t=LifeDeviceProfile.integer(types.getJSONObject(i),"type");if((t==91||t==92)&&!out.contains(t))out.add(t);}if(out.isEmpty())throw new LifeDeviceFailure("此设备没有支持的余额支付方式，请在原版使用");return out;
 }
 static void canStop(JSONObject reply,String did,String uid)throws Exception{
  LifeDeviceProfile.refreshed(reply,did);String user=owner(reply.getJSONObject("data"));if(!uid.equals(user))throw new LifeDeviceFailure("未确认设备属于当前账号，未发送停止请求");
 }
 /** 只负责把请求发出去并解析回来；「拒没拒绝」由调用方判定，这样它能拿到原始响应做诊断。 */
 static JSONObject send(String path,JSONObject session,android.content.Context context)throws Exception{
  String didPattern="[A-Za-z0-9_-]{1,128}";
  if(!path.matches("/api/v1/dev/start\\?did="+didPattern+"&upgrade=true&ptype=(91|92)&args=&rcp=false&cnt=1")&&!path.matches("/api/v1/dev/end\\?did="+didPattern+"&rcp=false"))throw new IllegalArgumentException();
  String token=LifeAuthApi.credentials(session).getString("token");
  JSONObject reply=LifeProtocolProbe.request(path,token,Collections.emptyMap());
  if(LifeVersion.upgradeRequired(reply)){
   int code=-1;try{code=LifeAuthApi.resultCode(reply);}catch(Exception ignored){}
   throw new LifeDeviceFailure.Rejected(code,"服务器回「"+LifeAuthApi.message(reply)+"」：这条控制接口要求官方 3.1.10 的新协议，本应用暂时发不出来。可先在上方 ⓘ 里跑一次「协议探测」",reply);
  }
  return reply;
 }
}

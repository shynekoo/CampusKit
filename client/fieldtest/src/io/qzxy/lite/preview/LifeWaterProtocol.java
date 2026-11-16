package io.qzxy.lite.preview;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
/** Direct online water only. No automatic retry or alternate payment selection. */
final class LifeWaterProtocol {
 static String id(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException("设备编号无效");return id;}
 /**
  * 3.1.10 起开启走 {@code dev/mode/start}：同样的凭据与参数，{@code dev/start} 只回「请升级最新版APP」，
  * 而这条路径直接进了业务逻辑（探测时回 -3「设备当前状态下，无法操作」——那是设备/账户的状态话，
  * 说明版本协商已经通过）。{@code mode} 取自设备自己的 {@code gene.mode}，不自己编。
  */
 static String startPath(String did,int pay,int mode){
  if(!KNOWN_PAYMENTS.contains(pay))throw new IllegalArgumentException("暂不支持此支付方式");
  if(mode<0||mode>999)throw new IllegalArgumentException("设备模式超出范围");
  return "/api/v1/dev/mode/start?did="+id(did)+"&mode="+mode+"&ptype="+pay+"&args=&rcp=false&cnt=1&upgrade=true";
 }
 /**
  * 官方 3.1.5 的 PaymentType 是商家钱包 91、余额 92、一卡通 93；**21 是 3.1.10 才出现的**，
  * 本机账单里每一笔水费都是它（`type:21`，元为单位的小额直扣）。起初只认 91/92，
  * 于是页面把服务端给的第一个选项（商家钱包，余额 0）当成唯一选择——这就是那个 -3 的来源。
  */
 private static final List<Integer> KNOWN_PAYMENTS=Collections.unmodifiableList(Arrays.asList(21,91,92,93));
 static String paymentName(int type){
  switch(type){case 21:return "支付宝免密";case 91:return "商家钱包";case 92:return "余额";case 93:return "一卡通";default:return "支付方式 "+type;}
 }
 /** 开启请求要带设备当前模式；缺失时按 0 发，越界则不发。 */
 static int mode(JSONObject reply)throws Exception{
  JSONObject dev=reply.getJSONObject("data").getJSONObject("device");
  JSONObject gene=dev.optJSONObject("gene");
  if(gene==null||!gene.has("mode")||gene.isNull("mode"))return 0;
  int mode=LifeDeviceProfile.integer(gene,"mode");
  if(mode<0||mode>999)throw new LifeDeviceFailure("设备模式字段超出范围，未发送开启请求");
  return mode;
 }
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
  JSONArray items=data.getJSONArray("payItems");
  // 服务端按 seq 给优先级（这台机器是 91→100、21→90）。只收已知编码：未知方式可能走我们看不懂的扣费通道。
  // seq 可能缺失，缺失的按出现顺序排在同档；同档多条都不能丢。
  List<int[]> found=new ArrayList<>();
  for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);int type=LifeDeviceProfile.integer(item,"type");if(KNOWN_PAYMENTS.contains(type))found.add(new int[]{item.optInt("seq",0),i,type});}
  Collections.sort(found,(a,b)->b[0]!=a[0]?b[0]-a[0]:a[1]-b[1]);
  List<Integer> out=new ArrayList<>();for(int[] f:found)if(!out.contains(f[2]))out.add(f[2]);
  // 商家钱包余额为 0 还排在第一位，只会让人点一次就被 -3 打回来；把它挪到最后，但保留可选。
  if(out.size()>1&&balance(reply)==0&&out.remove(Integer.valueOf(91)))out.add(91);
  if(out.isEmpty())throw new LifeDeviceFailure("此设备没有本应用支持的支付方式，请在原版使用");
  return out;
 }
 static void canStop(JSONObject reply,String did,String uid)throws Exception{
  LifeDeviceProfile.refreshed(reply,did);String user=owner(reply.getJSONObject("data"));if(!uid.equals(user))throw new LifeDeviceFailure("未确认设备属于当前账号，未发送停止请求");
 }
 /**
  * 余额只用来解释「为什么点了会被拒」，不用来决定能不能点——服务端的计费规则（后付费、赠送额度、
  * 不同商户钱包）我们看不全，替它拦人可能挡掉本来能开的设备。返回 -1 表示服务端没给。
  */
 static long balance(JSONObject reply){
  try{JSONObject wallet=reply.getJSONObject("data").optJSONObject("wallet");return wallet==null?-1:wallet.optLong("total",-1);}catch(Exception e){return -1;}
 }
 /** 只负责把请求发出去并解析回来；「拒没拒绝」由调用方判定，这样它能拿到原始响应做诊断。 */
 static JSONObject send(String path,JSONObject session,android.content.Context context)throws Exception{
  String didPattern="[A-Za-z0-9_-]{1,128}";
  if(!path.matches("/api/v1/dev/mode/start\\?did="+didPattern+"&mode=\\d{1,3}&ptype=(21|91|92|93)&args=&rcp=false&cnt=1&upgrade=true")&&!path.matches("/api/v1/dev/end\\?did="+didPattern+"&rcp=false"))throw new IllegalArgumentException();
  String token=LifeAuthApi.credentials(session).getString("token");
  JSONObject reply=LifeProtocolProbe.request(path,token,Collections.emptyMap());
  if(LifeVersion.upgradeRequired(reply)){
   int code=-1;try{code=LifeAuthApi.resultCode(reply);}catch(Exception ignored){}
   throw new LifeDeviceFailure.Rejected(code,"服务器回「"+LifeAuthApi.message(reply)+"」：这条控制接口要求官方 3.1.10 的新协议，本应用暂时发不出来。可先在上方 ⓘ 里跑一次「协议探测」",reply);
  }
  return reply;
 }
}

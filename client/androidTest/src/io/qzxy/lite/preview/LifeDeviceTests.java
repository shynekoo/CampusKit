// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import org.json.*;
final class LifeDeviceTests {
 static int count;static void check(boolean value){count++;if(!value)throw new AssertionError("Device check "+count);}
 static int run()throws Exception{
  count=0;JSONObject dev=new JSONObject().put("id","synthetic-dev").put("name","测试水表").put("status",1).put("gene",new JSONObject().put("status",99)).put("bm",new JSONObject().put("dtype",5)).put("addr",new JSONObject().put("detail","测试位置"));
  JSONObject reply=new JSONObject().put("code",0).put("data",new JSONObject().put("dev",dev));LifeDeviceProfile p=LifeDeviceProfile.resolved(reply);check(p.id.equals("synthetic-dev"));check(p.statusLabel().equals("锁定"));check(p.location.equals("测试位置"));
  JSONObject refresh=new JSONObject().put("code",0).put("data",new JSONObject().put("device",dev));check(LifeDeviceProfile.refreshed(refresh,p.id).id.equals(p.id));
  boolean rejected=false;try{LifeDeviceProfile.refreshed(refresh,"another-device");}catch(Exception e){rejected=true;}check(rejected);
  for(String key:new String[]{"id","status","bm"}){JSONObject broken=new JSONObject(dev.toString());broken.remove(key);rejected=false;try{LifeDeviceProfile.resolved(new JSONObject().put("code",0).put("data",new JSONObject().put("dev",broken)));}catch(Exception e){rejected=true;}check(rejected);}
  dev.getJSONObject("gene").put("status",1234);check(LifeDeviceProfile.resolved(reply).statusLabel().startsWith("未知状态"));
  check(p.networkLabel().equals("在线"));check(p.status==99);
  dev.put("status",0);dev.getJSONObject("gene").put("status",10);
  p=LifeDeviceProfile.resolved(reply);check(p.networkLabel().equals("离线"));check(p.statusLabel().equals("运行中"));
  dev.remove("gene");p=LifeDeviceProfile.resolved(reply);check(p.status==null);check(p.statusLabel().equals("未提供"));
  dev.put("gene",new JSONObject());check(LifeDeviceProfile.resolved(reply).status==null);
  dev.put("gene",JSONObject.NULL);check(LifeDeviceProfile.resolved(reply).status==null);
  for(Object bad:new Object[]{"running",new JSONObject().put("status","10"),new JSONObject().put("status",1.5)}){dev.put("gene",bad);rejected=false;try{LifeDeviceProfile.resolved(reply);}catch(Exception e){rejected=true;}check(rejected);}
  dev.put("gene",new JSONObject().put("status",98));check(LifeDeviceProfile.resolved(reply).statusLabel().equals("强制禁用"));
  dev.put("status",7);check(LifeDeviceProfile.resolved(reply).networkLabel().equals("未知（7）"));
  dev.getJSONObject("bm").put("dtype",50);rejected=false;try{LifeDeviceProfile.resolved(reply);}catch(Exception e){rejected=true;}check(rejected);
  for(String path:new String[]{"/api/v1/dev/start?did=x","/api/v1/dev/end?did=x","/api/v1/ui/app/dev/command","/api/v1/qr/use?id=x&action=start","https://other.invalid/"}){rejected=false;try{LifeDeviceApi.allowed(path);}catch(Exception e){rejected=true;}check(rejected);}
  LifeDeviceApi.allowed(LifeDeviceApi.resolvePath("123456"));check(true);LifeDeviceApi.allowed(LifeDeviceApi.statusPath("device-1"));check(true);
  rejected=false;try{LifeDeviceApi.resolvePath("a&start=1");}catch(Exception e){rejected=true;}check(rejected);
  JSONObject minimal=new JSONObject().put("code",0).put("data",new JSONObject().put("qr",new JSONObject().put("type",3)).put("dev",new JSONObject().put("id","resolved-id")));
  check(LifeDeviceProfile.resolvedId(minimal).equals("resolved-id"));
  for(int type:new int[]{1,2,4,8}){minimal.getJSONObject("data").getJSONObject("qr").put("type",type);rejected=false;try{LifeDeviceProfile.resolvedId(minimal);}catch(Exception e){rejected=true;}check(rejected);}
  minimal.getJSONObject("data").getJSONObject("qr").put("type",3);minimal.getJSONObject("data").getJSONObject("dev").put("id","x&bad=1");rejected=false;try{LifeDeviceProfile.resolvedId(minimal);}catch(Exception e){rejected=true;}check(rejected);
  minimal.put("code",-99);try{LifeDeviceProfile.resolvedId(minimal);check(false);}catch(LifeDeviceFailure e){check(e.getMessage().contains("-99"));}
  check(LifeDeviceFailure.describe(new java.net.SocketTimeoutException("private-token")).contains("超时"));
  check(!LifeDeviceFailure.describe(new org.json.JSONException("private-token")).contains("private-token"));
  check(!LifeDeviceFailure.describe(new java.io.IOException("private-token")).contains("private-token"));
  try{LifeDeviceProfile.refreshed(new JSONObject().put("code",0).put("data",new JSONObject()),"x");check(false);}catch(LifeDeviceFailure e){check(e.getMessage().contains("data.device"));}
  String detail=LifeDeviceApi.detailPath("device-1");check(detail.equals("/api/v1/ui/app/dev/home/1?did=device-1&apply=6"));LifeDeviceApi.allowed(detail);check(true);
  for(String path:new String[]{detail.replace("apply=6","apply=7"),detail+"&cmd=start","/api/v1/ui/app/dev/home/1?did=x"}){rejected=false;try{LifeDeviceApi.allowed(path);}catch(Exception e){rejected=true;}check(rejected);}
  return count;
 }
}

// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import org.json.*;
import java.util.*;
/**
 * 实验性协议探测：一次把「服务端到底要什么」问清楚。
 *
 * <p><b>不属于发布功能。</b>本文件只在 {@code client/fieldtest/experimental} 下，默认不参与编译；
 * 只有构建时显式传 {@code --experimental-probe} 才会打进包，且构建脚本会对产物做双向断言
 * （正式包必须没有本类与 {@code dev/mode/start}，实验包必须有）。它只用于本地隔离环境，
 * 不要对真实设备执行：其中包含带控制效果的开启请求。
 *
 * <p>判据保留原样：几种最可能的请求形态依次试，只记录服务端的原话；只要不再说「升级」
 * 就说明这道墙过了。命中即停，避免对设备重复下发。传输已移到 {@link LifeDeviceHttp}，
 * 正常用水不再依赖本类。
 */
final class LifeProtocolProbe {
 private LifeProtocolProbe(){}
 static final class Attempt {
  final String name,path;final Map<String,String> headers=new LinkedHashMap<>();
  Attempt(String name,String path,String... pairs){this.name=name;this.path=path;for(int i=0;i+1<pairs.length;i+=2)headers.put(pairs[i],pairs[i+1]);}
 }
 static String run(JSONObject session,String did,int pay,int mode){
  String token;
  try{token=LifeAuthApi.credentials(session).getString("token");}catch(Exception e){return "凭据无效，无法探测";}
  String start="/api/v1/dev/start?did="+did+"&upgrade=true&ptype="+pay+"&args=&rcp=false&cnt=1";
  String modeWith="/api/v1/dev/mode/start?did="+did+"&mode="+mode+"&ptype="+pay+"&args=&rcp=false&cnt=1&upgrade=true";
  String modePlain="/api/v1/dev/mode/start?did="+did+"&mode="+mode+"&ptype="+pay+"&rcp=false&cnt=1";
  List<Attempt> tries=new ArrayList<>();
  tries.add(new Attempt("旧路径 AT 1,1（基线）",start));
  tries.add(new Attempt("旧路径 AT 1,2",start,"ApplicationType","1,2"));
  tries.add(new Attempt("旧路径 AT 2,1",start,"ApplicationType","2,1"));
  tries.add(new Attempt("旧路径 + VersionName",start,"VersionName","3.1.10"));
  tries.add(new Attempt("旧路径 + App-Ver",start,"App-Ver","3.1.10"));
  tries.add(new Attempt("旧路径 + X-Api-Version",start,"X-Api-Version","3"));
  tries.add(new Attempt("旧路径 + UA=okhttp",start,"User-Agent","okhttp/4.12.0"));
  tries.add(new Attempt("mode/start 带 args",modeWith));
  tries.add(new Attempt("mode/start 不带 args",modePlain));
  tries.add(new Attempt("旧路径 + mode 参数",start+"&mode="+mode));
  StringBuilder out=new StringBuilder();
  boolean passed=false;int hit=0;
  for(int i=0;i<tries.size()&&!passed;i++){
   Attempt a=tries.get(i);String line;
   try{
    JSONObject reply=LifeDeviceHttp.get(a.path,token,a.headers);
    int code=LifeAuthApi.resultCode(reply);String msg=LifeAuthApi.message(reply);
    passed=!LifeVersion.upgradeRequired(reply);
    line=(i+1)+". "+a.name+" → "+code+(msg.isEmpty()?"":"："+msg)+(passed?"   ★ 过了这道墙":"");
    if(passed)hit=i+1;
   }catch(Exception e){
    line=(i+1)+". "+a.name+" → "+LifeDeviceFailure.describe(e);
   }
   out.append(line).append('\n');
  }
  out.append('\n');
  if(passed)out.append("第 ").append(hit).append(" 条不再被「请升级」挡住，改用它。");
  else out.append("全部被同一句话挡住：说明它看的不是路径/这些头，而是请求签名一类的协商（需要官方新版本的密钥）。");
  return out.toString();
 }
}

// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
/**
 * 慧生活设备请求的传输层：只把一条 GET 发出去，把服务端的 JSON 读回来。
 *
 * <p>为什么单独成类：这段代码原来写在实验性的协议探测类里，而正常开启/结束用水也在调用它——
 * 于是「去掉探测」会连带打断正常用水。拆开之后，正式包只编译本类，探测类住在
 * {@code client/fieldtest/experimental}，默认不参与构建。
 *
 * <p>约束与原来一致：不跟随重定向（避免被带去未审计的主机）、15s 连接 / 15s 读取、
 * 应答上限 1 MiB；非 200 与网络错误都交给调用方分类，本类不判断业务含义。
 */
final class LifeDeviceHttp {
 private LifeDeviceHttp(){}
 static JSONObject get(String path,String token)throws Exception{return get(path,token,Collections.<String,String>emptyMap());}
 static JSONObject get(String path,String token,Map<String,String> extra)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(LifeAuthApi.HOST+path).openConnection();
  c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setUseCaches(false);
  c.setRequestProperty("Authorization",token);
  c.setRequestProperty("ApplicationType",LifeVersion.APPLICATION_TYPE);
  // 版本头放版本名字符串（「3.1.9」）而不是数字：数字形态会被服务端回「请升级最新版APP」。
  c.setRequestProperty("VersionCode",LifeVersion.DEVICE_VERSION);
  c.setRequestProperty("User-Agent",LifeVersion.DEVICE_UA);
  c.setRequestProperty("Accept-Language",LifeVersion.ACCEPT_LANGUAGE);
  c.setRequestProperty("Content-Type","application/json");
  if(extra!=null)for(Map.Entry<String,String> e:extra.entrySet())c.setRequestProperty(e.getKey(),e.getValue());
  try{
   int http=c.getResponseCode();
   if(http!=200)return new JSONObject().put("code",-1).put("msg","HTTP "+http);
   return new JSONObject(new String(TimetableBackup.read(c.getInputStream(),1024*1024),"UTF-8"));
  }finally{c.disconnect();}
 }
}

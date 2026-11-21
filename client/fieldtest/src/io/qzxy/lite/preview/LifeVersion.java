// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import org.json.JSONObject;
import java.util.Locale;
/**
 * 官方客户端的身份常量。
 *
 * <p>结论来自同类第三方客户端 WaterWidget（github.com/nocookies111/life-798，Kotlin，
 * 2026-10 仍在维护、146 星）的请求层：设备控制（{@code ApplicationType: 1,1}）的
 * {@code VersionCode} 头放的是**版本名字符串「3.1.9」**，不是数字 31009——
 * 我们此前发数字 31005/31010，服务端解析不出可比的版本，才回了那句「请升级最新版APP」。
 * 配套的 UA 也是官方形状 {@code Android_ilife798_3.1.9}，再加一条 iOS 风格的
 * {@code Accept-Language}。设备控制不需要任何签名（{@code sign} 只用于积分任务）。
 */
final class LifeVersion {
 /** 官方 3.1.10 的 versionCode，仅用于诊断输出。 */
 static final int CODE=31010;
 /** 设备控制登录的 ApplicationType。 */
 static final String APPLICATION_TYPE="1,1";
 /** VersionCode 头的取值：版本名字符串，与 WaterWidget 实测可用的一致。 */
 static final String DEVICE_VERSION="3.1.9";
 /** 设备控制请求的 UA；WaterWidget 用这个形状 + 3.1.9 过墙。 */
 static final String DEVICE_UA="Android_ilife798_3.1.9";
 /** iOS 风格语言头，与 WaterWidget 保持一致。 */
 static final String ACCEPT_LANGUAGE="zh-Hans-CN;q=1";
 private LifeVersion(){}
 /** 只认「请升级」这一种说法：它是协议墙，不是业务拒绝。 */
 static boolean upgradeRequired(JSONObject reply){
  if(reply==null)return false;
  final int code;try{code=LifeAuthApi.resultCode(reply);}catch(Exception e){return false;}
  if(code>=0)return false;
  String why=LifeAuthApi.message(reply);
  return why.contains("升级")||why.toLowerCase(Locale.ROOT).contains("upgrade");
 }
}

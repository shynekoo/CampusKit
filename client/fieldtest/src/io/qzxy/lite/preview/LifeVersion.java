package io.qzxy.lite.preview;
import org.json.JSONObject;
import java.util.Locale;
/**
 * 官方客户端的身份常量。
 *
 * <p>3.1.10 的 {@code versionCode} 就是 {@code 31010}（三乘万 + 一乘千 + 修订号），实测把它报上去
 * {@code /api/v1/dev/start} 仍然回「请升级最新版APP」——所以那道门不是「版本号小于某个数」，
 * 抬高数字无用（2.3.5 试到 50000 都是同一句）。真正的差别在于新协议（新路径、新参数或签名），
 * 具体是哪一种，由 {@link LifeProtocolProbe} 一次试出来。
 */
final class LifeVersion {
 /** 官方 3.1.10 的 versionCode。 */
 static final int CODE=31010;
 /** 沿用 3.1.5 的 ApplicationType；探测会试它的其它取值。 */
 static final String APPLICATION_TYPE="1,1";
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

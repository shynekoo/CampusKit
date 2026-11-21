// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import org.json.JSONObject;
/**
 * 「诊断…」菜单项的实验部分：这是**正式构建（默认）**的版本，只有两项。
 *
 * <p>实验性的接口探测会真的向设备发一次带控制效果的开启请求，因此不属于发布功能。
 * 它住在 {@code client/fieldtest/experimental}，由 {@code build_preview.py} 在
 * {@code --experimental-probe} 时用同名类替换本文件（同一 FQCN，二选一编译）。
 * 正式包因此既没有探测类，也没有那条入口文案。
 */
final class LifeProbeEntry {
 private LifeProbeEntry(){}
 static String[] items(){return new String[]{"复制诊断信息","读取设备状态（只读）"};}
 /** 正式包里没有可执行的实验项；参数只为与实验版保持同一调用签名。 */
 static void run(LifeWaterActivity host,String did,int pay,JSONObject detail){}
}

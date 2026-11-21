// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;
import org.json.JSONObject;
/**
 * 「诊断…」菜单的实验部分：**实验构建专用**，与正式版同 FQCN。
 *
 * <p>{@code build_preview.py} 只有在传 {@code --experimental-probe} 时才编译本文件，
 * 并同时排除 {@code client/fieldtest/src} 下的占位版本。发布包不含本类。
 * 探测会真的向设备发一次带控制效果的开启请求，只能在本地隔离环境手动触发。
 */
final class LifeProbeEntry {
 private LifeProbeEntry(){}
 static String[] items(){return new String[]{"复制诊断信息","读取设备状态（只读）","协议探测（会尝试开启一次）"};}
 static void run(final LifeWaterActivity host,final String did,final int pay,final JSONObject detail){
  final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
  Toast.makeText(host,"正在探测接口要求…",Toast.LENGTH_SHORT).show();
  worker.execute(()->{
   String report;
   try{
    JSONObject session=new LifeSessionVault(host).load();
    if(session==null)report="请先登录慧生活";
    else{
     int mode=detail.getJSONObject("data").getJSONObject("device").getJSONObject("gene").optInt("mode",0);
     report=LifeProtocolProbe.run(session,did,pay,mode);
    }
   }catch(Exception e){report="探测未完成："+LifeDeviceFailure.describe(e);}
   final String text=report;
   host.runOnUiThread(()->new AlertDialog.Builder(host).setTitle("协议探测结果").setMessage(text)
    .setPositiveButton("复制",(d,w)->{
     ((ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("慧生活协议探测",text));
     Toast.makeText(host,"已复制",Toast.LENGTH_SHORT).show();
    }).setNegativeButton("关闭",null).show());
  });
 }
}

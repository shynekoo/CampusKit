// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import org.json.*;
import java.io.IOException;
final class LifeDeviceProfile {
    final String id,name,location,enterprise;final int type,networkStatus;final Integer status;
    private LifeDeviceProfile(JSONObject dev)throws Exception{
        id=text(dev,"id",128,true);if(!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IOException("设备标识无效");
        name=text(dev,"name",200,true);type=integer(dev.getJSONObject("bm"),"dtype");networkStatus=integer(dev,"status");
        // DeviceInfo.status is connectivity; operating state lives in gene.status.
        JSONObject gene=null;
        if(dev.has("gene")&&!dev.isNull("gene")){gene=dev.getJSONObject("gene");}
        status=gene==null||!gene.has("status")||gene.isNull("status")?null:integer(gene,"status");
        if(type!=5&&type!=6&&type!=8&&type!=9&&type!=20)throw new IOException("尚不支持此设备类型");
        JSONObject addr=dev.optJSONObject("addr"),ep=dev.optJSONObject("ep");location=addr==null?"未提供位置":text(addr,"detail",300,false);enterprise=ep==null?"未提供运营方":text(ep,"name",200,false);
    }
    static int integer(JSONObject o,String key)throws Exception{Object v=o.get(key);if(!(v instanceof Number))throw new IOException();double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n!=Math.rint(n)||n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new IOException();return (int)n;}
    static String text(JSONObject o,String key,int limit,boolean required)throws Exception{Object v=o.opt(key);if(v==null||v==JSONObject.NULL){if(required)throw new IOException();return "未提供";}if(!(v instanceof String)||((String)v).length()>limit||required&&((String)v).trim().isEmpty())throw new IOException();return (String)v;}
    /** Types /qr/use may return for something we can open: 3 = device, 8 = door lock (per ilife798). */
    private static boolean usableType(int type){return type==3||type==8;}
    static String resolvedId(JSONObject reply)throws Exception{
        LifeDeviceFailure.success(reply);
        try{JSONObject data=reply.getJSONObject("data");
            int type=integer(data.getJSONObject("qr"),"type");
            if(!usableType(type))throw new LifeDeviceFailure("这个二维码指向的不是用水设备（服务器返回类型 "+type+"）");
            String id=text(data.getJSONObject("dev"),"id",128,true);
            if(!id.matches("[A-Za-z0-9_-]{1,128}"))throw new LifeDeviceFailure("设备编号格式不受支持");return id;
        }catch(LifeDeviceFailure e){throw e;}catch(Exception e){throw new LifeDeviceFailure("二维码响应缺少有效的 data.qr.type 或 data.dev.id");}
    }
    static LifeDeviceProfile resolved(JSONObject reply)throws Exception{LifeDeviceFailure.success(reply);return new LifeDeviceProfile(reply.getJSONObject("data").getJSONObject("dev"));}
    static LifeDeviceProfile refreshed(JSONObject reply,String expected)throws Exception{
        LifeDeviceFailure.success(reply);JSONObject dev;
        try{dev=reply.getJSONObject("data").getJSONObject("device");}catch(Exception e){throw new LifeDeviceFailure("详情响应缺少 data.device");}
        LifeDeviceProfile p;
        try{p=new LifeDeviceProfile(dev);}catch(Exception e){
            String[] fields={"id","name","bm","status"};StringBuilder missing=new StringBuilder();for(String key:fields)if(!dev.has(key)||dev.isNull(key)){if(missing.length()>0)missing.append("、");missing.append(key);}
            throw new LifeDeviceFailure(missing.length()>0?"设备详情缺少字段："+missing:"设备详情字段格式或设备类型不受支持");
        }
        if(!p.id.equals(expected))throw new LifeDeviceFailure("详情返回的设备与扫码设备不一致");return p;
    }
    String typeLabel(){switch(type){case 5:return "水控 / 直饮机";case 6:return "水表 / 淋浴器";case 8:return "管线 / 饮水机";case 9:return "净水 / 热水主机";case 20:return "吹风机";default:return "未知类型";}}
    String networkLabel(){switch(networkStatus){case 0:return "离线";case 1:return "在线";default:return "未知（"+networkStatus+"）";}}
    String statusLabel(){if(status==null)return "未提供";switch(status){case 0:return "关闭";case 1:return "开机";case 2:return "异常";case 10:return "运行中";case 20:return "冲洗中";case 30:return "准备中";case 98:return "强制禁用";case 99:return "锁定";default:return "未知状态（"+status+"）";}}
}

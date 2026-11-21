// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

/** Only bounded, locally generated diagnostics; never echo response values or credentials. */
class LifeDeviceFailure extends java.io.IOException {
    private static final long serialVersionUID = 1L;
    LifeDeviceFailure(String message){super(message);}
    /**
     * 服务器跑完了自己的逻辑并明确拒绝。对控制类请求来说这是个有用的区分：拒绝发生在下发之前，
     * 设备状态没有变，本机那条「待核实记录」也就不必再留着把人卡在页面上。
     */
    static final class Rejected extends LifeDeviceFailure {
        private static final long serialVersionUID = 1L;
        final int code;
        /** 原始回复留给诊断页：拒绝原因往往只有服务端那句话说得清。 */
        final org.json.JSONObject reply;
        Rejected(int code,String message){this(code,message,null);}
        Rejected(int code,String message,org.json.JSONObject reply){super(message);this.code=code;this.reply=reply;}
    }
    /**
     * 同类第三方客户端（WaterWidget，github.com/nocookies111/life-798）实测过的业务码含义。
     * 服务端 msg 缺失时兜底；只在它说得出我们不知道的事时才补。
     */
    private static String known(int code){
        switch(code){
            case -19:return "设备准备中，请稍后";
            case -20:return "未绑定一卡通账号";
            case -21:return "设备登录信息未被接受";
            case -52:return "账户欠费，请充值后使用";
            case -82:return "需要支付";
            case -87:return "签约已过期，请重新签约";
            case -88:return "未签约代扣协议";
            default:return null;
        }
    }
    /** 把服务端自己的那句话接在后面：只有「业务码 -1」时读者完全无从判断该改什么。 */
    private static String rejection(org.json.JSONObject reply,int code){
        String why=LifeAuthApi.message(reply);
        if(why.isEmpty()){
            String fallback=known(code);
            return "服务器拒绝请求（业务码 "+code+(fallback==null?"）":"："+fallback+"）");
        }
        return "服务器拒绝请求（业务码 "+code+"："+why+"）";
    }
    static void success(org.json.JSONObject reply)throws Exception {
        final int code;
        try{code=LifeAuthApi.resultCode(reply);}catch(Exception e){throw new LifeDeviceFailure("服务器响应缺少有效状态码");}
        if(code<0)throw new Rejected(code,rejection(reply,code),reply);
    }
    static String describe(Exception e){
        if(e instanceof LifeDeviceFailure)return e.getMessage();
        if(e instanceof java.net.SocketTimeoutException)return "网络请求超时，请稍后重试";
        if(e instanceof java.net.UnknownHostException||e instanceof java.net.ConnectException)return "无法连接慧生活服务器，请检查网络";
        if(e instanceof javax.net.ssl.SSLException)return "安全连接失败，请检查系统时间或网络";
        if(e instanceof org.json.JSONException)return "服务器返回格式与预期不符";
        return "读取未完成，请重试；本机登录未被清除";
    }
}

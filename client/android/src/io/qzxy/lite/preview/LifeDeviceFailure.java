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
        Rejected(int code,String message){super(message);this.code=code;}
    }
    /** 把服务端自己的那句话接在后面：只有「业务码 -1」时读者完全无从判断该改什么。 */
    private static String rejection(org.json.JSONObject reply,int code){
        String why=LifeAuthApi.message(reply);
        return "服务器拒绝请求（业务码 "+code+(why.isEmpty()?"）":"："+why+"）");
    }
    static void success(org.json.JSONObject reply)throws Exception {
        final int code;
        try{code=LifeAuthApi.resultCode(reply);}catch(Exception e){throw new LifeDeviceFailure("服务器响应缺少有效状态码");}
        if(code<0)throw new Rejected(code,rejection(reply,code));
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

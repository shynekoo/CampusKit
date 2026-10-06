package io.qzxy.lite.preview;

/** Only bounded, locally generated diagnostics; never echo response values or credentials. */
final class LifeDeviceFailure extends java.io.IOException {
    LifeDeviceFailure(String message){super(message);}
    static void success(org.json.JSONObject reply)throws Exception {
        final int code;
        try{code=LifeAuthApi.resultCode(reply);}catch(Exception e){throw new LifeDeviceFailure("服务器响应缺少有效状态码");}
        if(code<0)throw new LifeDeviceFailure("服务器拒绝请求（业务码 "+code+"）");
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

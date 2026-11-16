package io.qzxy.lite.preview;
import org.json.*;
import java.net.*;
import java.io.*;
final class LifeAuthApi {
    static final String HOST="https://i.ilife798.com";
    static JSONObject sms(String phone,String captcha,double seed)throws Exception{
        phone(phone);if(captcha==null||!captcha.matches("[A-Za-z0-9]{1,12}")||!(seed>=0&&seed<1))throw new IllegalArgumentException("请填写图形验证码");
        return new JSONObject().put("un",phone).put("authCode",captcha).put("s",seed);
    }
    static JSONObject login(String phone,String code)throws Exception{phone(phone);if(code==null||!code.matches("[0-9]{4,8}"))throw new IllegalArgumentException("请填写短信验证码");return new JSONObject().put("un",phone).put("authCode",code);}
    /**
     * 支付宝授权码换登录：官方登录页把它叫 openCode，服务器拿它去支付宝换取账号。
     * 这里只挡住空值和明显不是 token 的输入，真正的校验在服务器那边。
     */
    static JSONObject alipay(String openCode)throws Exception{
        String code=openCode==null?"":openCode.trim();
        if(code.length()<8||code.length()>512||!code.matches("[!-~]+"))throw new IllegalArgumentException("支付宝没有返回可用的授权码，请重试");
        return new JSONObject().put("openCode",code);
    }
    static void phone(String phone){if(phone==null||!phone.matches("1[3-9][0-9]{9}"))throw new IllegalArgumentException("请填写正确的手机号");}
    static int resultCode(JSONObject response)throws Exception{
        Object value=response.opt("code");if(!(value instanceof Number))throw new IOException("响应缺少状态码");double code=((Number)value).doubleValue();if(Double.isNaN(code)||Double.isInfinite(code)||code!=Math.rint(code)||code<Integer.MIN_VALUE||code>Integer.MAX_VALUE)throw new IOException("响应状态码无效");return (int)code;
    }
    /**
     * The server's own explanation, when it gave one. 慧生活 answers a rejected request with
     * {@code {"code":-2,"msg":"图片验证码错误"}}, and that sentence is worth far more to the reader
     * than "请求未完成"; it is trimmed and capped because it is remote text going into a TextView.
     */
    static String message(JSONObject response){
        Object value=response.opt("msg");if(!(value instanceof String))return "";
        String text=((String)value).trim();return text.length()>80?text.substring(0,80):text;
    }
    static JSONObject credentials(JSONObject al)throws Exception{
        if(!(al.opt("uid") instanceof String)||!(al.opt("token") instanceof String))throw new IOException("凭据格式无效");
        String uid=al.getString("uid"),token=al.getString("token");
        if(!uid.matches("[A-Za-z0-9_-]{1,128}")||!token.matches("[!-~]{1,4096}"))throw new IOException("凭据格式无效");
        return new JSONObject().put("uid",uid).put("token",token);
    }
    static JSONObject session(JSONObject response)throws Exception{
        if(resultCode(response)<0)throw new IOException("登录未成功，请核对验证码后重试");
        return credentials(response.getJSONObject("data").getJSONObject("al"));
    }
    static String captchaPath(double seed,long time){if(!(seed>=0&&seed<1)||time<0)throw new IllegalArgumentException();return "/api/v1/captcha/?s="+seed+"&r="+time;}
    static void allowed(String path,JSONObject body,String token){
        boolean captcha=path!=null&&path.matches("/api/v1/captcha/\\?s=[0-9.Ee+-]+&r=[0-9]+")&&body==null&&token==null;
        boolean auth=("/api/v1/acc/login/code".equals(path)||"/api/v1/acc/login".equals(path))&&body!=null&&token==null;
        boolean status=path!=null&&path.matches("/api/v1/acc/stat(\\?uid=[A-Za-z0-9_-]{1,128})?")&&body==null&&token!=null&&token.matches("[!-~]{1,4096}");
        if(!captcha&&!auth&&!status)throw new IllegalArgumentException("未允许的请求");
    }
    /**
     * 服务端自己的两个负数码，只有第一个跟凭据有关。
     *
     * <p>实测：{@code GET /api/v1/acc/stat} 不带任何凭据回 {@code {"code":-99,"msg":"登录状态已过期"}}，
     * 而 {@code -1} 是这个服务端通用的「请求参数错误」——用空 body 打 {} 登录接口也能拿到同一个 -1。
     * 所以核验回 -1 时能确定的只有「这次请求少给了参数」，并不能推出凭据失效。</p>
     */
    static final int EXPIRED=-99,BAD_REQUEST=-1;
    static boolean expired(int code){return code==EXPIRED;}
    /**
     * 核验登录状态。uid 一并带上：这个接口按账号定位凭据，只给 Authorization 时服务端回的是
     * 参数错误（-1），不是「已过期」（-99）。这一点没有在真机上验证过，带上 uid 是手上的最佳猜测；
     * 万一它仍然不认，调用方按 {@link #expired} 判断，不会把有效凭据当成失效。
     */
    static String statPath(JSONObject session)throws Exception{
        return "/api/v1/acc/stat?uid="+credentials(session).getString("uid");
    }
    static JSONObject stat(JSONObject session)throws Exception{
        return new JSONObject(new String(request(statPath(session),null,credentials(session).getString("token")),"UTF-8"));
    }
    static int verify(JSONObject session)throws Exception{return resultCode(stat(session));}
    static byte[] request(String path,JSONObject body)throws Exception{
        return request(path,body,null);
    }
    private static byte[] request(String path,JSONObject body,String token)throws Exception{
        allowed(path,body,token);
        HttpURLConnection c=(HttpURLConnection)new URL(HOST+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);
        c.setRequestProperty("ApplicationType","1,1");c.setRequestProperty("VersionCode","31005");c.setRequestProperty("User-Agent","CampusKit/0.9.10 (Android; independent client)");
        if(token!=null)c.setRequestProperty("Authorization",token);
        try{if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}}
            if(c.getResponseCode()!=200)throw new IOException("服务暂不可用，请稍后重试");return TimetableBackup.read(c.getInputStream(),1024*1024);
        }finally{c.disconnect();}
    }
    static JSONObject post(String path,JSONObject body)throws Exception{return new JSONObject(new String(request(path,body),"UTF-8"));}
}

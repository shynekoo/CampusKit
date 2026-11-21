// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import java.net.*;
import java.io.*;
import org.json.*;
final class LifeDeviceApi {
    /** Sticker tickets are opaque; the {@code bm?/} shape carries '?' and '/' that must be encoded. */
    private static final String TICKET="[A-Za-z0-9_%+./=~?-]{1,300}";
    private static final String ENCODED_TICKET="[A-Za-z0-9%._~-]{1,900}";
    static String resolvePath(String ticket){
        if(ticket==null||!ticket.matches(TICKET))throw new IllegalArgumentException("识别码格式不受支持");
        return "/api/v1/qr/use?id="+encode(ticket);
    }
    static String statusPath(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException();return "/api/v1/ui/app/dev/status?did="+id+"&more=true";}
    static String detailPath(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException();return "/api/v1/ui/app/dev/home/1?did="+id+"&apply=6";}
    /** 官方用它决定「这台机器该怎么用」，很可能是开启参数（mode/args）的来源。只读。 */
    static String useWayPath(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException();return "/api/v1/ui/app/dev/dev-use-way?did="+id;}
    /** 未结账单会压住开启，查一下比猜「余额为 0」直接。只读。 */
    static final String BILLS_PATH="/api/v1/bill/lst-owner";
    static void allowed(String path){if(path==null||!path.matches("/api/v1/qr/use\\?id="+ENCODED_TICKET)&&!path.matches("/api/v1/ui/app/dev/status\\?did=[A-Za-z0-9_-]{1,128}&more=true")&&!path.matches("/api/v1/ui/app/dev/home/1\\?did=[A-Za-z0-9_-]{1,128}&apply=6")&&!path.matches("/api/v1/ui/app/dev/dev-use-way\\?did=[A-Za-z0-9_-]{1,128}")&&!BILLS_PATH.equals(path))throw new IllegalArgumentException("未允许的设备请求");}
    /** Percent-encodes everything outside the unreserved set, matching how the third-party clients send it. */
    private static String encode(String value){
        StringBuilder out=new StringBuilder();
        try{for(byte raw:value.getBytes("UTF-8")){int c=raw&0xff;
            if((c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='-'||c=='_'||c=='.'||c=='~')out.append((char)c);
            else out.append('%').append(Character.toUpperCase(Character.forDigit(c>>4,16))).append(Character.toUpperCase(Character.forDigit(c&0xf,16)));
        }}catch(UnsupportedEncodingException impossible){throw new IllegalArgumentException("识别码编码失败");}
        return out.toString();
    }
    static JSONObject get(String path,JSONObject session)throws Exception{
        allowed(path);String token=LifeAuthApi.credentials(session).getString("token");HttpURLConnection c=(HttpURLConnection)new URL(LifeAuthApi.HOST+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);
        c.setRequestProperty("ApplicationType","1,1");c.setRequestProperty("VersionCode","31005");c.setRequestProperty("User-Agent","CampusKit/0.9.15 (Android; independent client)");c.setRequestProperty("Authorization",token);
        try{int http=c.getResponseCode();if(http!=200)throw new LifeDeviceFailure(http==401||http==403?"服务器未授权设备访问（HTTP "+http+"），请核验账户":"设备服务返回 HTTP "+http);return new JSONObject(new String(TimetableBackup.read(c.getInputStream(),1024*1024),"UTF-8"));}finally{c.disconnect();}
    }
}

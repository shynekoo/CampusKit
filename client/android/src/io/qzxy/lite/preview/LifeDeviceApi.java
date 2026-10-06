package io.qzxy.lite.preview;
import java.net.*;
import java.io.*;
import org.json.*;
final class LifeDeviceApi {
    static String resolvePath(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException();return "/api/v1/qr/use?id="+id;}
    static String statusPath(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException();return "/api/v1/ui/app/dev/status?did="+id+"&more=true";}
    static String detailPath(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException();return "/api/v1/ui/app/dev/home/1?did="+id+"&apply=6";}
    static void allowed(String path){if(path==null||!path.matches("/api/v1/qr/use\\?id=[A-Za-z0-9_-]{1,64}")&&!path.matches("/api/v1/ui/app/dev/status\\?did=[A-Za-z0-9_-]{1,128}&more=true")&&!path.matches("/api/v1/ui/app/dev/home/1\\?did=[A-Za-z0-9_-]{1,128}&apply=6"))throw new IllegalArgumentException("未允许的设备请求");}
    static JSONObject get(String path,JSONObject session)throws Exception{
        allowed(path);String token=LifeAuthApi.credentials(session).getString("token");HttpURLConnection c=(HttpURLConnection)new URL(LifeAuthApi.HOST+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);
        c.setRequestProperty("ApplicationType","1,1");c.setRequestProperty("VersionCode","31005");c.setRequestProperty("User-Agent","CampusKit/0.9.15 (Android; independent client)");c.setRequestProperty("Authorization",token);
        try{int http=c.getResponseCode();if(http!=200)throw new LifeDeviceFailure(http==401||http==403?"服务器未授权设备访问（HTTP "+http+"），请核验账户":"设备服务返回 HTTP "+http);return new JSONObject(new String(TimetableBackup.read(c.getInputStream(),1024*1024),"UTF-8"));}finally{c.disconnect();}
    }
}

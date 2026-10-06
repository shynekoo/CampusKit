package io.qzxy.lite.preview;
import android.app.Instrumentation;
import org.json.*;
final class LifeAuthTests {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("Life auth check "+n);}
 static int run(Instrumentation runner)throws Exception{
  n=0;JSONObject sms=LifeAuthApi.sms("13800000000","abcd",.25);check(sms.getString("un").equals("13800000000"));check(sms.getDouble("s")==.25);check(!sms.has("token"));
  JSONObject login=LifeAuthApi.login("13800000000","123456");check(login.length()==2);check(!login.has("openCode"));
  boolean rejected=false;try{LifeAuthApi.sms("bad","a",.2);}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{LifeAuthApi.sms("13800000000","a",Double.NaN);}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{LifeAuthApi.session(new JSONObject("{\"code\":-98}"));}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{LifeAuthApi.session(new JSONObject("{\"code\":0,\"data\":{}}"));}catch(Exception e){rejected=true;}check(rejected);
  JSONObject al=new JSONObject().put("uid","synthetic-user").put("token","synthetic-token");JSONObject result=new JSONObject().put("code",0).put("data",new JSONObject().put("al",al));JSONObject session=LifeAuthApi.session(result);check(session.getString("uid").equals("synthetic-user"));
  for(String path:new String[]{"/api/v1/dev/start","/api/v1/dev/end","/api/v1/qr/use","/api/v1/ui/app/dev/command","/api/v1/acc/stat"}){rejected=false;try{LifeAuthApi.allowed(path,null,null);}catch(Exception e){rejected=true;}check(rejected);}
  LifeAuthApi.allowed("/api/v1/acc/stat",null,"synthetic-token");check(true);
  LifeAuthApi.allowed(LifeAuthApi.captchaPath(.25,123),null,null);check(true);
  rejected=false;try{LifeAuthApi.allowed("/api/v1/acc/login",null,null);}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{LifeAuthApi.resultCode(new JSONObject().put("code",.5));}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{LifeAuthApi.credentials(new JSONObject().put("uid","user").put("token","bad\\r\\nheader".replace("\\r","\r").replace("\\n","\n")));}catch(Exception e){rejected=true;}check(rejected);
  LifeSessionVault vault=new LifeSessionVault(runner.getTargetContext());vault.clear();check(!vault.exists());vault.save(session);check(vault.exists());check(vault.load().getString("uid").equals("synthetic-user"));
  java.io.File encrypted=new java.io.File(runner.getTargetContext().getNoBackupFilesDir(),"life798-session.bin");byte[] first=java.nio.file.Files.readAllBytes(encrypted.toPath());vault.save(session);byte[] second=java.nio.file.Files.readAllBytes(encrypted.toPath());check(!java.util.Arrays.equals(first,second));second[second.length-1]^=1;java.nio.file.Files.write(encrypted.toPath(),second);rejected=false;try{vault.load();}catch(Exception e){rejected=true;}check(rejected);vault.clear();check(!vault.exists());return n;
 }
}

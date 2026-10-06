package io.qzxy.lite.preview;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import org.json.*;
/** One outstanding operation across devices; contains identifiers, never credentials. */
final class LifeWaterRecord {
 private final AtomicFile file;
 static synchronized void claim(Context c,String uid,String did,boolean opening)throws Exception{LifeWaterRecord store=new LifeWaterRecord(c);JSONObject old=store.load();if(old!=null&&(opening||!uid.equals(old.getString("uid"))||!did.equals(old.getString("did"))||old.getString("phase").equals("STOP_SENT")))throw new LifeDeviceFailure("存在待核实操作，未重复发送");store.save(uid,did,opening?"START_SENT":"STOP_SENT");}
 LifeWaterRecord(Context c){file=new AtomicFile(new File(c.getNoBackupFilesDir(),"life-water-pending.json"));}
 synchronized JSONObject load()throws Exception{try{return new JSONObject(new String(TimetableBackup.read(file.openRead(),16384),"UTF-8"));}catch(FileNotFoundException e){return null;}}
 synchronized void save(String uid,String did,String phase)throws Exception{JSONObject v=new JSONObject().put("uid",uid).put("did",LifeWaterProtocol.id(did)).put("phase",phase).put("time",System.currentTimeMillis());FileOutputStream out=null;try{out=file.startWrite();out.write(v.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}}
 synchronized void archive(Context c)throws Exception{JSONObject v=load();if(v==null)return;AtomicFile history=new AtomicFile(new File(c.getNoBackupFilesDir(),"life-water-last-reviewed.json"));FileOutputStream out=null;try{out=history.startWrite();out.write(v.put("reviewedByUser",true).toString().getBytes("UTF-8"));history.finishWrite(out);}catch(Exception e){history.failWrite(out);throw e;}file.delete();}
}

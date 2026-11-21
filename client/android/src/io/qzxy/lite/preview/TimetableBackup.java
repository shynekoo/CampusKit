// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;
import android.content.*;
import android.graphics.*;
import android.util.Base64;
import java.io.*;
import org.json.*;
final class TimetableBackup {
    static final String[] KEYS={"courses","monday","import_notes","import_term","background"};
    static byte[] read(InputStream in,int limit)throws Exception{if(in==null)throw new IOException();try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=input.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("文件过大");out.write(buf,0,n);}return out.toByteArray();}}
    static JSONObject exportData(SharedPreferences prefs)throws Exception{JSONObject o=new JSONObject().put("format","CampusKit.timetable").put("version",1);for(String key:KEYS)o.put(key,prefs.getString(key,key.equals("courses")?"[]":""));return o;}
    static JSONObject parse(byte[] bytes)throws Exception{
        JSONObject o=new JSONObject(new String(bytes,"UTF-8"));if(!"CampusKit.timetable".equals(o.getString("format"))||o.getInt("version")!=1)throw new IOException("不支持的备份格式");
        for(String key:KEYS)if(!(o.get(key) instanceof String))throw new IOException("备份字段无效");
        JSONArray all=new JSONArray(o.getString("courses"));if(all.length()>500)throw new IOException("课程过多");for(int i=0;i<all.length();i++)TimetableActivity.validate(all.getJSONObject(i));
        if(!o.getString("monday").isEmpty())TimetableActivity.parseMonday(o.getString("monday"));
        if(o.getString("import_notes").length()>100000||o.getString("import_term").length()>200)throw new IOException("备注过长");
        String image=o.getString("background");if(!image.isEmpty()){Bitmap bitmap=decode(image);if(bitmap==null)throw new IOException("背景图片无效");bitmap.recycle();}return o;
    }
    static boolean restore(SharedPreferences prefs,JSONObject o)throws Exception{SharedPreferences.Editor edit=prefs.edit();for(String key:KEYS)if(!key.equals("background"))edit.putString(key,o.getString(key));return edit.commit();}
    static Bitmap decode(String value){try{byte[] data=Base64.decode(value,Base64.DEFAULT);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>1800||bounds.outHeight>1800)return null;return BitmapFactory.decodeByteArray(data,0,data.length);}catch(Exception e){return null;}}
    static String image(byte[] data)throws Exception{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);if(options.outWidth<1||options.outHeight<1)throw new IOException("图片无法读取");
        options.inSampleSize=1;while(options.outWidth/options.inSampleSize>1600||options.outHeight/options.inSampleSize>1600)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);if(bitmap==null)throw new IOException();
        try{ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,85,out);return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);}finally{bitmap.recycle();}
    }
}

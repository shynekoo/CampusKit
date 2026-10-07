package io.qzxy.lite.preview;
import org.json.*;
final class CourseWeeks {
 static boolean includes(JSONObject c,int week){
  JSONArray a=c.optJSONArray("weeks");
  if(a!=null){for(int i=0;i<a.length();i++)if(a.optInt(i)==week)return true;return false;}
  int parity=c.optInt("parity");return week>=c.optInt("from",1)&&week<=c.optInt("to",20)&&(parity==0||week%2==(parity==1?1:0));
 }
 static boolean[] selection(JSONObject c){boolean[] a=new boolean[30];for(int i=0;i<30;i++)a[i]=includes(c,i+1);return a;}
 static void put(JSONObject c,boolean[] selected)throws JSONException{
  JSONArray a=new JSONArray();for(int i=0;i<selected.length;i++)if(selected[i])a.put(i+1);
  if(a.length()==0)throw new IllegalArgumentException("至少选择一周");
  c.put("weeks",a).put("from",a.getInt(0)).put("to",a.getInt(a.length()-1)).put("parity",0);
 }
 static void validate(JSONObject c)throws JSONException{
  if(!c.has("weeks"))return;JSONArray a=c.getJSONArray("weeks");if(a.length()==0||a.length()>30)throw new IllegalArgumentException();
  int previous=0;for(int i=0;i<a.length();i++){Object value=a.get(i);if(!(value instanceof Number)||((Number)value).doubleValue()!=a.getInt(i))throw new IllegalArgumentException();int w=a.getInt(i);if(w<=previous||w>30)throw new IllegalArgumentException();previous=w;}
 }
 static String label(boolean[] a){StringBuilder out=new StringBuilder();for(int i=0;i<a.length;i++){if(!a[i])continue;int start=i+1;while(i+1<a.length&&a[i+1])i++;if(out.length()>0)out.append("、");out.append(start);if(i+1>start)out.append("–").append(i+1);}return out.length()==0?"请选择上课周次":"第 "+out+" 周";}
 static String label(JSONObject c){return label(selection(c));}
}

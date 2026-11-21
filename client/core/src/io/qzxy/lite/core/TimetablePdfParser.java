// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.util.*;
import java.util.regex.*;

/** Parses positioned text from the university's seven-column PDF export. */
public final class TimetablePdfParser {
    public static final class Glyph {
        public final float x, y, width; public final String text;
        public Glyph(float x, float y, float width, String text) { this.x=x; this.y=y; this.width=width; this.text=text; }
    }
    public static final class Course {
        public String name, room, teacher; public int day, first, last, from, to, parity;
    }
    public static final class Result {
        public final List<Course> courses=new ArrayList<>(); public String notes="", term="";
    }
    private static String compact(String s) { return s.replaceAll("\\s+", "").replace('：', ':'); }
    private static String field(String block, String label) {
        Matcher m=Pattern.compile("/"+label+":([^/]+)").matcher(block);
        if (!m.find()) throw new IllegalArgumentException("课程字段不完整："+label);
        return m.group(1);
    }
    public static Result parse(List<List<Glyph>> pages) {
        Result result=new Result(); float[] centers=null;
        StringBuilder[] columns=new StringBuilder[7]; for(int i=0;i<7;i++) columns[i]=new StringBuilder();
        for(List<Glyph> page:pages) {
            List<Glyph> sorted=new ArrayList<>(page); sorted.sort(Comparator.comparingDouble(g -> g.y));
            List<List<Glyph>> lines=new ArrayList<>();
            for(Glyph g:sorted) {
                if(lines.isEmpty() || Math.abs(lines.get(lines.size()-1).get(0).y-g.y)>2) lines.add(new ArrayList<>());
                lines.get(lines.size()-1).add(g);
            }
            boolean footer=false;
            for(List<Glyph> line:lines) {
                line.sort(Comparator.comparingDouble(g -> g.x)); StringBuilder full=new StringBuilder();
                for(Glyph g:line) full.append(g.text); String text=compact(full.toString());
                Matcher term=Pattern.compile("20\\d{2}-20\\d{2}学年第[12]学期").matcher(text);
                if(term.find()) result.term=term.group();
                if(text.contains("星期一") && text.contains("星期日")) {
                    centers=new float[7]; String[] names={"星期一","星期二","星期三","星期四","星期五","星期六","星期日"};
                    String raw=full.toString();
                    for(int d=0;d<7;d++) {
                        int start=raw.indexOf(names[d]); if(start<0) throw new IllegalArgumentException("星期表头不完整");
                        int offset=0; float left=-1,right=-1;
                        for(Glyph g:line) { int end=offset+g.text.length(); if(end>start && offset<start+3) { if(left<0) left=g.x; right=g.x+g.width; } offset=end; }
                        centers[d]=(left+right)/2;
                        if(d>0 && centers[d]<=centers[d-1]) throw new IllegalArgumentException("星期列无法定位");
                    }
                    continue;
                }
                if(text.startsWith("实践课程:") || text.startsWith("其他课程:")) { footer=true; result.notes+=text+"\n"; continue; }
                if(footer || centers==null) continue;
                float spacing=(centers[6]-centers[0])/6;
                for(Glyph g:line) {
                    float middle=g.x+g.width/2; int d=Math.round((middle-centers[0])/spacing);
                    if(d>=0 && d<7 && Math.abs(middle-centers[d])<=spacing/2) columns[d].append(g.text);
                }
                for(StringBuilder column:columns) column.append('\n');
            }
        }
        if(centers==null) throw new IllegalArgumentException("未找到星期表头，请选择教务系统导出的表格式 PDF");
        Pattern marker=Pattern.compile("[（(](\\d+)(?:-(\\d+))?节[）)]");
        for(int day=0;day<7;day++) {
            String text=compact(columns[day].toString()); Matcher m=marker.matcher(text);
            List<Integer> starts=new ArrayList<>(); while(m.find()) starts.add(m.start());
            for(int i=0;i<starts.size();i++) {
                String block=text.substring(starts.get(i),i+1<starts.size()?starts.get(i+1):text.length());
                m=marker.matcher(block); if(!m.find()) throw new IllegalArgumentException("节次读取失败");
                int first=Integer.parseInt(m.group(1)), last=m.group(2)==null?first:Integer.parseInt(m.group(2));
                int end=block.indexOf("/校区:"); if(end<0) throw new IllegalArgumentException("课程周次不完整");
                String weeks=block.substring(m.end(),end);
                Matcher wm=Pattern.compile("(\\d+)(?:-(\\d+))?周(?:[（(]([单双])(?:周)?[）)])?").matcher(weeks);
                if(!wm.matches()) throw new IllegalArgumentException("暂不支持此周次格式："+weeks);
                Course c=new Course(); c.day=day; c.first=first; c.last=last;
                c.from=Integer.parseInt(wm.group(1)); c.to=wm.group(2)==null?c.from:Integer.parseInt(wm.group(2));
                c.parity=wm.group(3)==null?0:wm.group(3).equals("单")?1:2;
                c.name=field(block,"教学班").replaceFirst("-\\d+$",""); c.room=field(block,"场地"); c.teacher=field(block,"教师");
                if(first<1 || last<first || last>16 || c.from<1 || c.to<c.from || c.to>30 || c.name.length()>80 || c.room.length()>100 || c.teacher.length()>60)
                    throw new IllegalArgumentException("课程信息超出支持范围");
                result.courses.add(c);
            }
        }
        if(result.courses.isEmpty() || result.courses.size()>500) throw new IllegalArgumentException("没有识别到完整课程，扫描图片 PDF 暂不支持");
        return result;
    }
    private TimetablePdfParser() { }
}

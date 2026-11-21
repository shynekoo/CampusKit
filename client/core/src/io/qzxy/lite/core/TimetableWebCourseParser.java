// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

/** Strict text adapter; unsupported/multiple blocks must not be silently truncated. */
public final class TimetableWebCourseParser {
    private static final Pattern SECTIONS = Pattern.compile("[（(](\\d+)(?:-(\\d+))?节[）)]");
    private static final Pattern WEEK = Pattern.compile("(\\d{1,2})(?:-(\\d{1,2}))?周(?:[（(]([单双])(?:周)?[）)])?");
    private static final Pattern SEPARATOR = Pattern.compile("[,，;；、]+");

    /**
     * 调课 rows often carry several week ranges in one cell, e.g. {@code 2-6周(双),11-15周}.
     * Split them into separate courses; every segment still goes through the strict single-course
     * parser below, so an incomplete row can never produce a half-written class.
     */
    public static List<TimetablePdfParser.Course> parseAll(int day, String title, String text) {
        if(day<0 || day>6 || text==null || text.length()>12000) throw new IllegalArgumentException("课程内容无效");
        String compact=text.replaceAll("\\s+", "").replace('：', ':');
        Matcher sections=SECTIONS.matcher(compact);
        if(!sections.find()) throw new IllegalArgumentException("暂不支持此网页节次格式，请使用 PDF 导入");
        String sectionText=sections.group();
        int start=sections.start(), end=sections.end();
        if(sections.find()) throw new IllegalArgumentException("同一格有多门课程，未能完整分离，请使用 PDF 导入");
        List<String> segments=new ArrayList<>();
        List<TimetablePdfParser.Course> courses=new ArrayList<>();
        int pos=end, lastEnd=end;
        while(pos<compact.length() && segments.size()<12) {
            Matcher week=WEEK.matcher(compact);
            week.region(pos, compact.length());
            if(!week.lookingAt()) break;
            segments.add(week.group());
            lastEnd=week.end();
            Matcher separator=SEPARATOR.matcher(compact);
            separator.region(lastEnd, compact.length());
            if(!separator.lookingAt()) break;
            pos=separator.end();
        }
        if(segments.isEmpty()) throw new IllegalArgumentException("暂不支持此网页周次格式，请使用 PDF 导入");
        String suffix=compact.substring(lastEnd);
        String prefix=compact.substring(0,start)+sectionText;
        for(String segment:segments) courses.add(parse(day, title, prefix+segment+suffix));
        return courses;
    }

    public static TimetablePdfParser.Course parse(int day, String title, String text) {
        if(day<0 || day>6 || text==null || text.length()>12000) throw new IllegalArgumentException("课程内容无效");
        String compact=text.replaceAll("\\s+", "").replace('：', ':');
        Matcher sections=Pattern.compile("[（(](\\d+)(?:-(\\d+))?节[）)]").matcher(compact);
        if(!sections.find()) throw new IllegalArgumentException("暂不支持此网页节次格式，请使用 PDF 导入");
        int first=Integer.parseInt(sections.group(1)),last=sections.group(2)==null?first:Integer.parseInt(sections.group(2));
        int start=sections.start(), end=sections.end();
        if(sections.find()) throw new IllegalArgumentException("同一格有多门课程，未能完整分离，请使用 PDF 导入");
        Matcher weeks=Pattern.compile("^(\\d+)(?:-(\\d+))?周(?:[（(]([单双])(?:周)?[）)])?").matcher(compact.substring(end));
        if(!weeks.find()) throw new IllegalArgumentException("暂不支持此网页周次格式，请使用 PDF 导入");
        int after=end+weeks.end();
        if(after<compact.length() && ",，、-0123456789".indexOf(compact.charAt(after))>=0) throw new IllegalArgumentException("多段周次暂不支持，请手动录入");
        TimetablePdfParser.Course c=new TimetablePdfParser.Course(); c.day=day; c.first=first; c.last=last;
        c.from=Integer.parseInt(weeks.group(1)); c.to=weeks.group(2)==null?c.from:Integer.parseInt(weeks.group(2)); c.parity=weeks.group(3)==null?0:weeks.group(3).equals("单")?1:2;
        c.name=title==null?"":title.replaceAll("\\s+", "");
        if(c.name.isEmpty()) { String before=compact.substring(0,start); c.name=before.replaceFirst("^课程名称:", ""); }
        c.room=label(text,"场地|上课地点|教室"); c.teacher=label(text,"教师|任课教师");
        if(c.name.isEmpty() || c.name.length()>80 || c.room.isEmpty() || c.teacher.isEmpty() || first<1 || last<first || last>16 || c.from<1 || c.to<c.from || c.to>30)
            throw new IllegalArgumentException("课程名称、教室、教师或周次不完整，请使用 PDF 导入");
        return c;
    }
    private static String label(String text,String labels) {
        Matcher m=Pattern.compile("(?:"+labels+")\\s*[:：]\\s*([^/\\r\\n]+)").matcher(text);
        if(!m.find()) return "";
        return m.group(1).split("\\s+(?:教师|任课教师|教学班|场地|教室|周次|学分)\\s*[:：]",2)[0].trim();
    }
    private TimetableWebCourseParser() { }
}

// SPDX-License-Identifier: MPL-2.0
import io.qzxy.lite.core.TimetablePdfParser;
import java.nio.file.*;
import java.util.*;
public class PdfParserCheck {
    public static void main(String[] args) throws Exception {
        List<List<TimetablePdfParser.Glyph>> pages=new ArrayList<>();
        for(String line:Files.readAllLines(Paths.get(args[0]),java.nio.charset.StandardCharsets.UTF_8)) {
            String[] a=line.split("\t",5); int p=Integer.parseInt(a[0]); while(pages.size()<=p) pages.add(new ArrayList<>());
            pages.get(p).add(new TimetablePdfParser.Glyph(Float.parseFloat(a[1]),Float.parseFloat(a[2]),Float.parseFloat(a[3]),a[4]));
        }
        TimetablePdfParser.Result r=TimetablePdfParser.parse(pages);
        if(r.courses.size()!=18) throw new AssertionError("Expected 18 entries, got "+r.courses.size());
        int[] days=new int[7]; int evening=0; boolean longClass=false;
        for(TimetablePdfParser.Course c:r.courses) { days[c.day]++; if(c.first==9) evening++; if(c.day==5 && c.first==1 && c.last==4 && c.from==2 && c.to==13) longClass=true; }
        if(!Arrays.equals(days,new int[]{4,1,3,2,2,4,2}) || evening!=2 || !longClass) throw new AssertionError("Schedule mismatch "+Arrays.toString(days));
        if(!r.notes.contains("生产实习") || !r.notes.contains("思想政治理论课实践教学2")) throw new AssertionError("Missing notes");
        if(!r.term.equals("2026-2027学年第1学期")) throw new AssertionError("Term mismatch");
        String[] expected={
            "0|3|4|1|12|自动控制原理|A12-102|教师甲", "0|5|6|2|13|发电厂电气设备|A11-204|教师乙",
            "0|7|8|1|10|汽轮机原理|A12-104|教师丙", "0|9|10|1|8|大学生职业生涯规划（2）|A14-403|教师丁",
            "1|3|4|7|14|数值计算及应用|A12-101|教师戊", "2|3|4|1|12|自动控制原理|A12-104|教师甲",
            "2|5|6|3|6|工程伦理|A12-104|教师己", "2|7|8|2|13|发电厂电气设备|A12-204|教师乙",
            "3|3|4|7|14|数值计算及应用|A12-101|教师戊", "3|7|8|1|10|汽轮机原理|A12-104|教师丙",
            "4|3|4|1|12|电子技术基础|A12-401|教师庚", "4|7|8|1|16|习近平新时代中国特色社会主义思想概论|A11-406|教师辛",
            "5|1|2|14|14|传热学|A12-104|教师子,教师丑", "5|1|4|2|13|传热学|A12-104|教师子,教师丑",
            "5|7|8|1|10|汽轮机原理|A12-104|教师丙", "5|9|10|1|8|习近平新时代中国特色社会主义思想概论|A11-201|教师辛",
            "6|1|2|7|10|形势与政策|A11-406|教师壬,教师癸", "6|3|4|1|12|电子技术基础|A12-101|教师庚"
        };
        for(int i=0;i<expected.length;i++) {
            TimetablePdfParser.Course c=r.courses.get(i);
            String actual=c.day+"|"+c.first+"|"+c.last+"|"+c.from+"|"+c.to+"|"+c.name+"|"+c.room+"|"+c.teacher;
            if(!actual.equals(expected[i]) || c.parity!=0) throw new AssertionError("Course mismatch at "+i);
        }
        List<List<TimetablePdfParser.Glyph>> damaged=new ArrayList<>();
        for(List<TimetablePdfParser.Glyph> p:pages) { List<TimetablePdfParser.Glyph> copy=new ArrayList<>();
            for(TimetablePdfParser.Glyph g:p) if(!g.text.equals("师")) copy.add(g); damaged.add(copy); }
        try { TimetablePdfParser.parse(damaged); throw new AssertionError("Partial data accepted"); } catch(IllegalArgumentException expectedError) { }
        try { TimetablePdfParser.parse(Collections.emptyList()); throw new AssertionError("Empty accepted"); } catch(IllegalArgumentException expectedEmpty) { }
        System.out.println("PASS: 18 entries, all weekdays, two evening entries, Saturday sections, notes, term, empty rejection.");
    }
}


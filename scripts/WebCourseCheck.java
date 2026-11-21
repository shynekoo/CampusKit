// SPDX-License-Identifier: MPL-2.0
import io.qzxy.lite.core.*;
public final class WebCourseCheck {
    public static void main(String[] args) {
        TimetablePdfParser.Course c=TimetableWebCourseParser.parse(5,"测试课程","测试课程\n(1-4节)2-13周\n场地:A12-104\n教师:测试教师\n教学班:测试-001");
        if(c.day!=5 || c.first!=1 || c.last!=4 || c.from!=2 || c.to!=13 || !c.room.equals("A12-104") || !c.teacher.equals("测试教师"))throw new AssertionError();
        c=TimetableWebCourseParser.parse(0,"测试","(3-4节)1-15周(单)/场地:A1/教师:甲/教学班:测试");
        if(c.parity!=1)throw new AssertionError();
        String[] invalid={"(1-2节)1-8周/场地:A1", "(1-2节)1-8周/场地:A1/教师:甲(3-4节)1-8周", "(1-2节)1-8周,10周/场地:A1/教师:甲", "登录页面"};
        for(String s:invalid) { try {TimetableWebCourseParser.parse(0,"测试",s);throw new AssertionError("Invalid accepted");}catch(IllegalArgumentException expected){} }
        System.out.println("PASS: web course fields, odd weeks, missing fields, multiple blocks, disjoint weeks and login rejection.");
    }
}

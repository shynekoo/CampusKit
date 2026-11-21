// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

/** School bell times supplied by the user; unknown sections have no inferred time. */
final class ClassTimes {
    private static final String[] START={"08:30","09:20","10:25","11:15","14:00","14:50","15:55","16:45","19:00","19:50"};
    private static final String[] END={"09:15","10:05","11:10","12:00","14:45","15:35","16:40","17:30","19:45","20:35"};
    static String start(int section){return section>=1&&section<=10?START[section-1]:"";}
    static String end(int section){return section>=1&&section<=10?END[section-1]:"";}
    static String range(int first,int last){return first<=last&&!start(first).isEmpty()&&!end(last).isEmpty()?start(first)+"–"+end(last):"";}
}

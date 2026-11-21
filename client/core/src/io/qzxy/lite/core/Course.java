// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A single timetable course as a typed value, independent of JSON and Android.
 *
 * <p>The persisted format stays a JSONObject with the original keys; this object is the in-memory
 * model that business code can reason about without sprinkling {@code optInt}/{@code optString}
 * through the UI. Weeks are stored as an immutable set of 1-based week numbers so single/double and
 * arbitrary multi-range weeks are one representation, not three special cases.</p>
 */
public final class Course {
    public final String name, room, teacher;
    public final int day, first, last, color;
    private final Set<Integer> weeks;

    Course(String name, String room, String teacher, int day, int first, int last, int color,
           Set<Integer> weeks) {
        this.name = name; this.room = room; this.teacher = teacher;
        this.day = day; this.first = first; this.last = last; this.color = color;
        this.weeks = Collections.unmodifiableSet(new LinkedHashSet<Integer>(weeks));
    }

    public Set<Integer> weeks() { return weeks; }

    public boolean runsOn(int week) { return weeks.contains(Integer.valueOf(week)); }

    /** Static form for tests and callers that hold a map instead of a Course. */
    public static boolean runsOn(Course course, int week) {
        return course != null && course.runsOn(week);
    }

    @Override public String toString() {
        return "Course[" + name + ", day=" + day + ", " + first + "-" + last + ", weeks=" + weeks + "]";
    }
}

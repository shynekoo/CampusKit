// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.util.Collections;
import java.util.List;

/**
 * The complete set of on-screen values that change as the clock moves within a single day on 今日.
 *
 * <p>The activity fills one of these from its existing {@code ScheduleInfo} + course list and then
 * writes it onto the views it kept references to — no view creation, no {@code setContentView}. The
 * value type is deliberately Android-free so the shape of an in-place update is unit-testable.</p>
 */
public final class TodayDynamic {
    /** One today-class row's state: its strike-through/"已结束" flag and its "正在上课" marker. */
    public static final class Row {
        public final String name;
        public final boolean ended;
        public final boolean live;

        public Row(String name, boolean ended, boolean live) {
            this.name = name == null ? "" : name;
            this.ended = ended;
            this.live = live;
        }
    }

    /** "正在上课 · …" / "下一节 …" / "今天没有课" — the summary line above the rows. */
    public final String summary;
    public final List<Row> rows;

    public TodayDynamic(String summary, List<Row> rows) {
        this.summary = summary == null ? "" : summary;
        this.rows = rows == null ? Collections.<Row>emptyList() : Collections.unmodifiableList(rows);
    }
}

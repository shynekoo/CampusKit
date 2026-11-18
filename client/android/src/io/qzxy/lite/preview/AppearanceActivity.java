package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 外观与主题：应用主题与课表外观两页，都在同一屏里切换。
 *
 * <p>每改一项立刻保存并重画，没有「保存」按钮：这一页改的东西眼睛当场就能验证，再加一步确认
 * 只会让人怀疑自己到底改没改。改完回到别的页面，那些页面会自己换成新样子（见 {@link App}）。</p>
 *
 * <p>分成两页不是分类癖：应用主题影响每一屏，课表外观只影响表格。把它们放在一起，改圆角的人
 * 要先在一屏里读完深色模式。</p>
 */
public final class AppearanceActivity extends Activity implements App.Themed {
    private static final int TAB_APP = 0, TAB_SCHEDULE = 1;
    private int tab = TAB_APP;
    /** Live values while a slider is being dragged; the file only hears about it on release. */
    private int draftRadius = Theme.DEFAULT_RADIUS, draftOpacity = Theme.DEFAULT_OPACITY;

    private int dp(int n) { return AppUi.dp(this, n); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        draftRadius = Theme.radius();
        draftOpacity = Theme.opacity();
        render();
    }

    @Override public void onThemeChanged() { render(); }

    // ---- scaffolding -----------------------------------------------------------------------

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(4), 1f);
        return view;
    }

    private TextView bold(String value, int size) {
        TextView view = text(value, size, AppUi.INK);
        view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void gap(LinearLayout parent, int size) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size)));
    }

    private void add(LinearLayout parent, View view) {
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private void heading(LinearLayout parent, String label) {
        TextView view = bold(label, 13);
        view.setTextColor(AppUi.MUTED);
        view.setPadding(dp(4), dp(22), 0, dp(8));
        parent.addView(view);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(AppUi.outlined(this, AppUi.SURFACE, 16, AppUi.LINE));
        return card;
    }

    private void caption(LinearLayout parent, String value) {
        TextView view = text(value, 12, AppUi.MUTED);
        view.setPadding(dp(2), dp(6), dp(2), 0);
        parent.addView(view);
    }

    private void render() {
        final int side = AppUi.side(this, 20);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(side, dp(4), side, dp(28));
        column.setBackgroundColor(AppUi.BG);
        if (Build.VERSION.SDK_INT >= 30) column.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(side + bars.left, dp(4) + bars.top, side + bars.right, dp(28) + bars.bottom);
            return insets;
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(column);
        setContentView(scroll);
        column.requestApplyInsets();

        TextView back = text("‹ 返回账号", 15, AppUi.ACCENT);
        back.setPadding(0, dp(12), 0, dp(14));
        back.setMinHeight(dp(44));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> finish());
        add(column, back);

        add(column, bold("外观与主题", 26));
        gap(column, 6);
        add(column, text("只影响这台手机上的显示，不联网，也不跟着账号走。", 13, AppUi.MUTED));
        gap(column, 18);

        add(column, segmented(new String[]{"应用主题", "课表外观"}, tab, value -> {
            if (tab == value) return;
            tab = value; draftRadius = Theme.radius(); draftOpacity = Theme.opacity(); render();
        }));
        if (tab == TAB_APP) appTheme(column); else scheduleLook(column);
    }

    /** A row of equal cells; the selected one sits on a raised surface instead of a filled pill. */
    private View segmented(String[] labels, int selected, IntConsumer pick) {
        LinearLayout row = new LinearLayout(this);
        row.setBackground(AppUi.shape(this, AppUi.SOFT, 12));
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int index = 0; index < labels.length; index++) {
            final int value = index;
            boolean on = index == selected;
            TextView cell = new TextView(this);
            cell.setText(labels[index]);
            cell.setTextSize(14);
            cell.setGravity(Gravity.CENTER);
            cell.setMinHeight(dp(40));
            cell.setTextColor(on ? AppUi.INK : AppUi.MUTED);
            cell.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            cell.setBackground(on ? AppUi.shape(this, AppUi.SURFACE, 9) : null);
            cell.setSelected(on);
            cell.setContentDescription(on ? labels[index] + "，已选中" : labels[index]);
            cell.setOnClickListener(v -> pick.accept(value));
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return row;
    }

    private Switch tick(boolean value, Consumer<Boolean> change) {
        Switch toggle = new Switch(this);
        toggle.setChecked(value);
        toggle.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{AppUi.ACCENT, 0xffb9c1cd}));
        toggle.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{(0x66 << 24) | (AppUi.ACCENT & 0xffffff), 0x35808a99}));
        toggle.setOnCheckedChangeListener((button, checked) -> change.accept(checked));
        return toggle;
    }

    /** Title on the left, the reason underneath, the switch on the right — one row per setting. */
    private void toggleRow(LinearLayout parent, String title, String subtitle, boolean value, Consumer<Boolean> change) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(bold(title, 15));
        if (subtitle != null) {
            TextView detail = text(subtitle, 12, AppUi.MUTED);
            detail.setPadding(0, dp(2), dp(10), 0);
            labels.addView(detail);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(tick(value, checked -> { change.accept(checked); render(); }),
            new LinearLayout.LayoutParams(-2, -2));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    // ---- 应用主题 --------------------------------------------------------------------------

    private void appTheme(LinearLayout column) {
        heading(column, "明暗模式");
        LinearLayout mode = card();
        add(mode, segmented(new String[]{"自动", "浅色", "深色"}, Theme.mode(), value -> {
            Theme.setMode(this, value); render();
        }));
        String now = Theme.mode() == Theme.AUTO
            ? "跟随系统 · 现在是" + (Theme.dark() ? "深色" : "浅色")
            : (Theme.mode() == Theme.DARK ? "始终深色" : "始终浅色");
        caption(mode, now);
        add(column, mode);

        heading(column, "主题色彩");
        LinearLayout accent = card();
        add(accent, segmented(new String[]{"默认", "自定义"}, Theme.accentChoice() == 0 ? 0 : 1, value -> {
            if (value == 0) Theme.setAccent(this, 0); else pickAccent();
            render();
        }));
        caption(accent, Theme.accentChoice() == 0 ? "品牌蓝 #275DAD" : "自定义 " + hex(Theme.accentChoice()));
        gap(accent, 8);
        LinearLayout swatches = new LinearLayout(this);
        for (int index = 0; index < 12; index++) {
            final int color = ColorDialog.SOLIDS[index];
            TextView dot = new TextView(this);
            boolean on = Theme.accentChoice() == color;
            android.graphics.drawable.GradientDrawable shape = AppUi.shape(this, color, 10);
            if (on) shape.setStroke(dp(3), AppUi.INK);
            dot.setBackground(shape);
            dot.setContentDescription("主题色 " + hex(color));
            dot.setOnClickListener(v -> { Theme.setAccent(this, color); render(); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            swatches.addView(dot, params);
        }
        add(accent, swatches);
        add(column, accent);

        heading(column, "底栏样式");
        LinearLayout bar = card();
        toggleRow(bar, "悬浮底栏样式", "底栏浮在内容上方，左右与底部留出空隙", Theme.floatingBar(), value -> Theme.setFloatingBar(this, value));
        add(column, bar);

        heading(column, "交互");
        LinearLayout motion = card();
        toggleRow(motion, "返回手势动画", "打开与返回页面时左右滑入滑出", Theme.slide(), value -> Theme.setSlide(this, value));
        add(column, motion);

        gap(column, 20);
        caption(column, "关闭返回手势动画后，页面之间的切换会变成直接出现，旧一些的机型上会更跟手。");
    }

    private void pickAccent() {
        ColorDialog.show(this, "主题色", Theme.accentChoice(), ColorDialog.SOLIDS, color -> {
            Theme.setAccent(this, color); render();
        });
    }

    // ---- 课表外观 --------------------------------------------------------------------------

    private void scheduleLook(LinearLayout column) {
        gap(column, 16);
        BoardPreview preview = new BoardPreview();
        LinearLayout frame = card();
        frame.setPadding(dp(12), dp(12), dp(12), dp(12));
        frame.addView(preview, new LinearLayout.LayoutParams(-1, dp(148)));
        add(column, frame);
        caption(column, "预览跟着下面的设置走，改完直接看这一块就行。");

        heading(column, "课程配色");
        LinearLayout palette = card();
        add(palette, segmented(new String[]{"默认", "自定义"}, Theme.palette(), value -> {
            if (value == Theme.BUILT_IN) Theme.resetCustomFills(this);
            else Theme.setPalette(this, Theme.PICKED);
            render();
        }));
        caption(palette, Theme.palette() == Theme.PICKED ? "点一格换一个颜色，长按「恢复默认配色」全部还原。" : "内置 18 色，按课程名分配，两门课不会撞色。");
        if (Theme.palette() == Theme.PICKED) {
            gap(palette, 10);
            LinearLayout slots = new LinearLayout(this);
            int columns = AppUi.compact(this) ? 6 : 7;
            for (int slot = 0; slot < io.qzxy.lite.core.TimetablePalette.SIZE; slot++) {
                if (slot % columns == 0) {
                    slots = new LinearLayout(this);
                    add(palette, slots);
                }
                final int index = slot;
                TextView cell = new TextView(this);
                int fill = Theme.fill(slot);
                cell.setBackground(AppUi.shape(this, fill, 9));
                cell.setContentDescription("第 " + (slot + 1) + " 种课程颜色 " + hex(fill));
                cell.setOnClickListener(v -> ColorDialog.show(this, "第 " + (index + 1) + " 种课程颜色", Theme.fill(index), ColorDialog.TINTS, color -> {
                    Theme.setCustomFill(this, index, color); render();
                }));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1);
                params.setMargins(dp(3), dp(3), dp(3), dp(3));
                slots.addView(cell, params);
            }
            // Fill the last row with invisible cells: an incomplete row must not stretch its four
            // swatches into a different size from the two rows above it.
            int missing = (columns - io.qzxy.lite.core.TimetablePalette.SIZE % columns) % columns;
            for (int index = 0; index < missing; index++) {
                LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(0, dp(36), 1);
                spacer.setMargins(dp(3), dp(3), dp(3), dp(3));
                slots.addView(new View(this), spacer);
            }
            TextView reset = text("恢复默认配色", 13, AppUi.ACCENT);
            reset.setPadding(dp(2), dp(12), 0, dp(2));
            reset.setOnClickListener(v -> { Theme.resetCustomFills(this); render(); });
            add(palette, reset);
        }
        add(column, palette);

        heading(column, "课程文字颜色");
        LinearLayout ink = card();
        add(ink, segmented(new String[]{"默认", "自定义"}, Theme.inkChoice() == 0 ? 0 : 1, value -> {
            if (value == 0) Theme.setInk(this, 0); else pickInk();
            render();
        }));
        caption(ink, Theme.inkChoice() == 0 ? "每门课用自己的深色，配色改了也不会看不清。" : "所有课程共用一个 " + hex(Theme.inkChoice()));
        add(column, ink);

        heading(column, "圆角");
        LinearLayout corners = card();
        slider(corners, "圆角", "", draftRadius, Theme.MIN_RADIUS, Theme.MAX_RADIUS,
            value -> { draftRadius = value; preview.invalidate(); },
            value -> Theme.setRadius(this, value));
        add(column, corners);

        heading(column, "格子不透明度");
        LinearLayout opacity = card();
        slider(opacity, "不透明度", "%", draftOpacity, 0, 100,
            value -> { draftOpacity = value; preview.invalidate(); },
            value -> Theme.setOpacity(this, value));
        caption(opacity, "调低以后自带的课表背景图会更透出来。");
        add(column, opacity);

        heading(column, "显示网格线");
        LinearLayout lines = card();
        toggleRow(lines, "显示网格线", "关掉后只剩课程块，表格靠留白分隔", Theme.grid(), value -> Theme.setGrid(this, value));
        add(column, lines);

        gap(column, 20);
        caption(column, "课表外观只作用于课表页与桌面组件之外的表格，不会改动课程本身的数据。");
    }

    private void pickInk() {
        ColorDialog.show(this, "课程文字颜色", Theme.inkChoice(), ColorDialog.SOLIDS, color -> {
            Theme.setInk(this, color); render();
        });
    }

    /**
     * @param live     the value while the thumb is still down, so the preview keeps up
     * @param persist  written once, on release: a drag is one decision, not forty
     */
    private void slider(LinearLayout parent, String prefix, String suffix, int value, int min, int max,
                        IntConsumer live, IntConsumer persist) {
        final TextView title = bold(prefix + " " + value + suffix, 15);
        add(parent, title);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setProgressTintList(ColorStateList.valueOf(AppUi.ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(AppUi.ACCENT));
        bar.setPadding(0, dp(10), 0, dp(6));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int progress, boolean fromUser) {
                int current = min + progress;
                title.setText(prefix + " " + current + suffix);
                if (fromUser) live.accept(current);
            }
            @Override public void onStartTrackingTouch(SeekBar seek) { }
            @Override public void onStopTrackingTouch(SeekBar seek) {
                persist.accept(min + seek.getProgress());
                render();
            }
        });
        add(parent, bar);
    }

    private static String hex(int color) {
        return String.format("#%06X", color & 0xffffff);
    }

    /** A three-by-two piece of timetable, drawn with the settings currently in the page. */
    private final class BoardPreview extends View {
        private final Paint paint = new Paint(3);
        private final RectF rect = new RectF();
        private final String[] days = {"周一", "周二", "周三"};
        private final String[] names = {"高数", "大学物理", "英语", "线性代数", "体育", "电路"};
        private final String[] rooms = {"A101", "B204", "C305", "A110", "操场", "D402"};

        BoardPreview() { super(AppearanceActivity.this); }

        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight(), head = dp(24), pad = dp(6);
            float column = (width - 2 * pad) / 3f, row = (height - head - pad) / 2f;
            rect.set(0, 0, width, height);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(AppUi.SOFT);
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(10));
            paint.setColor(AppUi.MUTED);
            for (int day = 0; day < 3; day++) canvas.drawText(days[day], pad + column * (day + .5f), dp(15), paint);

            // 网格线 is a preference rather than a fixed decoration: the lines are what makes a table
            // read as a table, and they are also what the reader is looking at when they turn them off.
            if (Theme.grid()) {
                paint.setColor(AppUi.LINE);
                paint.setStrokeWidth(Math.max(1, dp(.6f)));
                for (int day = 0; day <= 3; day++) {
                    float x = pad + column * day;
                    canvas.drawLine(x, head - dp(8), x, height - pad, paint);
                }
                canvas.drawLine(pad, head - dp(6), width - pad, head - dp(6), paint);
            }

            paint.setTypeface(Typeface.DEFAULT);
            for (int slot = 0; slot < 6; slot++) {
                int day = slot % 3, line = slot / 3;
                float left = pad + column * day + dp(3), top = head + row * line + dp(3);
                rect.set(left, top, left + column - dp(6), top + row - dp(6));
                // The preview shows the opaque fill on its own backdrop, so a lowered opacity reads as
                // "this cell is transparent" instead of being cancelled out by the card behind it.
                paint.setColor(withDraft(Theme.fill(slot)));
                float radius = draftRadius;
                canvas.drawRoundRect(rect, radius, radius, paint);
                paint.setTextSize(dp(11));
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setColor(Theme.inkFor(slot));
                canvas.drawText(names[slot], rect.centerX(), rect.centerY() - dp(1), paint);
                paint.setTextSize(dp(9));
                paint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(rooms[slot], rect.centerX(), rect.centerY() + dp(11), paint);
            }
        }

        private int withDraft(int color) {
            if (draftOpacity >= 100) return color;
            return ((draftOpacity * 255 / 100) << 24) | (color & 0xffffff);
        }
    }
}

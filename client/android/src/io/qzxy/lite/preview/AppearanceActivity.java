package io.qzxy.lite.preview;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
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
public final class AppearanceActivity extends Page implements App.Themed {
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
        // 窗口自己也要换色。这一页只重画内容，系统栏与窗口背景不会跟着重来——点「深色」以后
        // 内容下面那块仍然是上一套主题的浅色，看上去就像页面从半截开始漏了白。
        Theme.bars(this);
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
        // 内容比一屏短时，剩下的那一截是 ScrollView 自己的背景。撑满视口并且自己上色，
        // 才不会在页面末尾露出一条窗口底色。
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(AppUi.BG);
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
        // 轨道要自己上色：平台的轨道默认是半透明的，染成一个带 alpha 的颜色只会更淡，看起来
        // 像一颗悬着的蓝色圆点。这里两档都给不透明的实色，打开与关闭一眼分得清。
        toggle.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
            new int[]{AppUi.ACCENT, Theme.mix(AppUi.MUTED, AppUi.SURFACE, .45f)}));
        toggle.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
            new int[]{Theme.mix(AppUi.ACCENT, AppUi.SURFACE, .25f), 0xffc3cad6}));
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
        int chosen = Theme.accentChoice();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(accentCard("品牌蓝", Theme.BRAND, false, chosen == 0), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(spacer(dp(12)), new LinearLayout.LayoutParams(dp(12), 1));
        row.addView(accentCard("自定义", chosen == 0 ? Theme.BRAND : chosen, true, chosen != 0), new LinearLayout.LayoutParams(0, -2, 1));
        add(column, row);
        caption(column, chosen == 0 ? "品牌蓝 #275DAD" : "自定义 " + hex(chosen) + "，长按任一卡片恢复品牌蓝。");

        heading(column, "交互");
        LinearLayout motion = card();
        toggleRow(motion, "返回手势动画", "系统支持时跟手缩成卡片", Theme.slide(), value -> {
            Theme.setSlide(this, value);
            // 当场换掉返回方式，不用退出这一页再看效果。
            refreshBackMode();
        });
        add(column, motion);
        gap(column, 18);
        caption(column, Build.VERSION.SDK_INT >= 34
            ? "打开后往回滑时整页跟着手指缩成一张圆角卡片，上一页在下面，松手才返回，中途放弃会弹回去；关掉以后是上一版那段轻微的缩小淡出。"
            : "这台设备的系统不给逐帧手势进度，两种开关都是轻微的缩小淡出；要跟手缩卡需要 Android 14 及以上。");
    }

    private View spacer(int size) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, 1));
        return view;
    }

    /**
     * 主题色的一张卡片：三个深浅不同的色块先说清「选它会长什么样」，名字和记号在下面。
     *
     * <p>只画一个圆点不够——主题色在按钮、选中项、开关上是三种深浅，读者要看的正是这三档，
     * 而不是那个原色。</p>
     */
    private View accentCard(String title, int color, boolean custom, boolean selected) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(12));
        box.setBackground(selected
            ? AppUi.outlined(this, tint(color, .88f), 16, AppUi.ACCENT)
            : AppUi.outlined(this, AppUi.SURFACE, 16, AppUi.LINE));

        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        float[] amounts = { .18f, .45f, .72f };
        for (float amount : amounts) {
            View swatch = new View(this);
            swatch.setBackground(AppUi.shape(this, tint(color, amount), 8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(26), dp(28));
            params.rightMargin = dp(6);
            swatches.addView(swatch, params);
        }
        add(box, swatches);
        gap(box, 12);

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(bold(title, 14), new LinearLayout.LayoutParams(0, -2, 1));
        TextView mark = new TextView(this);
        // 选中的打勾；「自定义」没选中时给一个加号，说明点进去能自己调；「品牌蓝」没选中就空着。
        if (selected) mark.setBackground(AppUi.icon(this, "check", AppUi.ACCENT, 18));
        else if (custom) mark.setBackground(AppUi.icon(this, "plus", AppUi.MUTED, 18));
        line.addView(mark, new LinearLayout.LayoutParams(dp(18), dp(18)));
        box.addView(line);

        box.setOnClickListener(v -> {
            if (custom) pickAccent(); else { Theme.setAccent(this, 0); render(); }
        });
        box.setOnLongClickListener(v -> { Theme.setAccent(this, 0); render(); return true; });
        box.setContentDescription(title + (selected ? "，已选中" : ""));
        return box;
    }

    /** Mixes a colour towards the page surface, which is what a "container" is in this app. */
    private int tint(int color, float amount) {
        return Theme.mix(color, AppUi.SURFACE, amount);
    }

    private void pickAccent() {
        ColorDialog.show(this, "选择颜色", Theme.accentChoice() == 0 ? Theme.BRAND : Theme.accentChoice(), color -> {
            Theme.setAccent(this, color); render();
        });
    }

    // ---- 课表外观 --------------------------------------------------------------------------

    private void scheduleLook(LinearLayout column) {
        heading(column, "预览");
        MiniBoard preview = new MiniBoard();
        LinearLayout frame = new LinearLayout(this);
        frame.setPadding(dp(10), dp(10), dp(10), dp(10));
        frame.setBackground(AppUi.outlined(this, AppUi.BG, 18, AppUi.LINE));
        frame.addView(preview, new LinearLayout.LayoutParams(-1, dp(208)));
        add(column, frame);
        caption(column, "这一块就是课表本身，只是去掉了节次时间：改圆角、不透明度、配色，它当场跟着变。");

        heading(column, "课程配色");
        LinearLayout palette = card();
        add(palette, segmented(new String[]{"默认", "自定义"}, Theme.palette(), value -> {
            if (value == Theme.BUILT_IN) Theme.resetCustomFills(this);
            else Theme.setPalette(this, Theme.PICKED);
            render();
        }));
        caption(palette, Theme.palette() == Theme.PICKED ? "点一格改一个颜色，恢复默认会把整组还原。" : "内置 18 色，按课程名分配，两门课不会撞色。");
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
                cell.setOnClickListener(v -> ColorDialog.show(this, "第 " + (index + 1) + " 种课程颜色", Theme.fill(index), color -> {
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
        caption(ink, Theme.inkChoice() == 0 ? "每门课用自己的深色，配色改了也不会看不清。" : "所有课程共用一个 " + hex(Theme.inkChoice()) + "。");
        gap(ink, 10);
        LinearLayout sample = new LinearLayout(this);
        for (int index = 0; index < 3; index++) {
            TextView chip = new TextView(this);
            chip.setText(index == 0 ? "高等数学" : index == 1 ? "大学英语" : "程序设计");
            chip.setTextSize(12);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setTextColor(Theme.inkFor(index));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(9), 0, dp(9));
            chip.setBackground(AppUi.shape(this, Theme.fill(index), Math.max(4, Theme.radius())));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(2), 0, dp(2), 0);
            sample.addView(chip, params);
        }
        add(ink, sample);
        add(column, ink);

        heading(column, "圆角");
        LinearLayout corners = card();
        slider(corners, "圆角", "dp", draftRadius, Theme.MIN_RADIUS, Theme.MAX_RADIUS,
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
        toggleRow(lines, "显示网格线", "默认关闭：只剩课程块，表格靠留白分隔", Theme.grid(), value -> Theme.setGrid(this, value));
        add(column, lines);

        gap(column, 20);
        caption(column, "课表外观只作用于课表本身，不会改动课程的数据。");
    }

    private void pickInk() {
        ColorDialog.show(this, "选择颜色", Theme.inkChoice() == 0 ? 0xff1f2937 : Theme.inkChoice(), color -> {
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

    /**
     * 一块缩小的课表，照真实课表的样子画：左边节次，上面星期，右边跨节次的课程块。
     *
     * <p>先前的预览是两行三列六个色块，只能证明「颜色变了」，证明不了「课表会变成什么样」——
     * 圆角在 30dp 的方块上几乎看不出来，不透明度也被卡片底色吃掉。这里按真正的格子高度画，
     * 圆角就是课表上的那个圆角。</p>
     */
    private final class MiniBoard extends View {
        private final android.text.TextPaint paint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final String[] days = { "周一", "周二", "周三", "周四", "周五" };
        private final String[] dates = { "13", "14", "15", "16", "17" };
        /** day, first period, how many periods it covers, palette slot. */
        private final int[][] blocks = {
            { 0, 0, 2, 0 }, { 1, 1, 2, 1 }, { 2, 0, 3, 2 }, { 3, 1, 2, 3 }, { 4, 0, 2, 4 }
        };
        private final String[] names = { "高等数学", "大学英语", "程序设计", "大学物理", "理论力学" };
        private final String[] rooms = { "A101", "B204", "C102", "D402", "A204" };
        private final int periods = 4;

        MiniBoard() { super(AppearanceActivity.this); }

        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight(), pad = dp(8), head = dp(30), gutter = dp(26);
            float column = (width - gutter - 2 * pad) / days.length;
            float row = (height - head - 2 * pad) / periods;

            rect.set(0, 0, width, height);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(AppUi.SURFACE);
            canvas.drawRoundRect(rect, dp(14), dp(14), paint);

            paint.setTextAlign(Paint.Align.CENTER);
            for (int day = 0; day < days.length; day++) {
                float center = pad + gutter + column * (day + .5f);
                paint.setColor(AppUi.MUTED);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(dp(9));
                canvas.drawText(days[day], center, pad + dp(11), paint);
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(dp(8));
                canvas.drawText(dates[day], center, pad + dp(21), paint);
            }

            // 网格线 is a preference and off by default: the preview has to show the same bare board
            // the reader gets, or turning the switch on looks like it did nothing.
            if (Theme.grid()) {
                paint.setColor(AppUi.LINE);
                paint.setStrokeWidth(Math.max(1, dp(.7f)));
                float top = pad + head - dp(6);
                for (int day = 0; day <= days.length; day++) {
                    float x = pad + gutter + column * day;
                    canvas.drawLine(x, top, x, height - pad, paint);
                }
                for (int line = 0; line <= periods; line++) {
                    float y = pad + head + row * line;
                    canvas.drawLine(pad + gutter, y, width - pad, y, paint);
                }
            }

            paint.setTextSize(dp(9));
            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(AppUi.MUTED);
            for (int period = 0; period < periods; period++) {
                float center = pad + head + row * (period + .5f) + dp(3);
                canvas.drawText(String.valueOf(period + 1), pad + gutter / 2f, center, paint);
            }

            for (int index = 0; index < blocks.length; index++) {
                int day = blocks[index][0], first = blocks[index][1], span = blocks[index][2], slot = blocks[index][3];
                float left = pad + gutter + column * day + dp(2);
                float top = pad + head + row * first + dp(2);
                rect.set(left, top, left + column - dp(4), top + row * span - dp(4));

                paint.setColor(withDraft(Theme.fill(slot)));
                canvas.drawRoundRect(rect, draftRadius, draftRadius, paint);

                int ink = Theme.inkFor(slot);
                float room = rect.width() - dp(4);
                paint.setColor(ink);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(dp(10));
                canvas.drawText(TextUtils.ellipsize(names[index], paint, room, TextUtils.TruncateAt.END).toString(),
                    rect.centerX(), rect.centerY() - dp(1), paint);
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(dp(8));
                paint.setAlpha(190);
                canvas.drawText(rooms[index], rect.centerX(), rect.centerY() + dp(11), paint);
                paint.setAlpha(255);
            }
        }

        private int withDraft(int color) {
            if (draftOpacity >= 100) return color;
            return ((draftOpacity * 255 / 100) << 24) | (color & 0xffffff);
        }
    }
}

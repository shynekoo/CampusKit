package io.qzxy.lite.preview;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 取色：一排色块加一个色值输入框。
 *
 * <p>色块用来做选择，输入框用来做「我就要这个色」——只给色块，读者想调的东西就永远调不出来；
 * 只给输入框，每次改色都得先知道色值。两者放在一个弹窗里，选完立刻能看到预览。</p>
 */
final class ColorDialog {
    interface Result { void accept(int color); }

    /** Pastel fills, for 课程配色. The last four are the neutral greys a timetable also needs. */
    static final int[] TINTS = {
        0xffdcefe9, 0xffe8e1f5, 0xfff7e5ce, 0xffdce9fa, 0xfff5dfe8, 0xffe9edcf,
        0xffddefdb, 0xffe0e2f8, 0xfff8dfd3, 0xffd8eef3, 0xfff0dff1, 0xffe3ead8,
        0xffd9eaf0, 0xfff6edcf, 0xffecdeda, 0xfff7e0dc, 0xffe2e8ef, 0xffd4efef,
        0xfff1f3f5, 0xffe6e9ed, 0xffd8dde3, 0xffcfd6de, 0xffffe3e0, 0xffe4f3e6
    };

    /** Saturated colours, for 主题色 and 课程文字颜色: text and accents need weight, not tint. */
    static final int[] SOLIDS = {
        0xff275dad, 0xff0f766e, 0xff15803d, 0xff4d7c0f, 0xffb45309, 0xffc2410c,
        0xffb91c1c, 0xffbe185d, 0xff7e22ce, 0xff6d28d9, 0xff4338ca, 0xff0369a1,
        0xff0891b2, 0xff65a30d, 0xffca8a04, 0xffea580c, 0xffdc2626, 0xffdb2777,
        0xff9333ea, 0xff4f46e5, 0xff0e7490, 0xff475569, 0xff334155, 0xff111827
    };

    private ColorDialog() { }

    static void show(Activity activity, String title, int current, int[] presets, Result result) {
        int side = AppUi.dp(activity, 20);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(side, AppUi.dp(activity, 4), side, 0);

        final int[] choice = {current};
        final TextView value = new TextView(activity);
        value.setTextSize(13);
        value.setTextColor(AppUi.MUTED);
        value.setPadding(0, AppUi.dp(activity, 8), 0, AppUi.dp(activity, 4));

        final EditText hex = new EditText(activity);
        hex.setSingleLine(true);
        hex.setTextSize(16);
        hex.setTextColor(AppUi.INK);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setHint("#275DAD");
        hex.setHintTextColor(AppUi.MUTED);

        final TextView marks[] = new TextView[presets.length];
        final int size = AppUi.dp(activity, 38);
        LinearLayout row = null;
        for (int index = 0; index < presets.length; index++) {
            if (index % 6 == 0) {
                row = new LinearLayout(activity);
                body.addView(row);
            }
            final int color = presets[index];
            final TextView swatch = new TextView(activity);
            swatch.setContentDescription("颜色 " + String.format("#%06X", color & 0xffffff));
            swatch.setOnClickListener(v -> { choice[0] = color; hex.setText(String.format("#%06X", color & 0xffffff)); paint(activity, marks, presets, choice[0]); value.setText("已选 " + String.format("#%06X", color & 0xffffff)); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(AppUi.dp(activity, 3), AppUi.dp(activity, 3), AppUi.dp(activity, 3), AppUi.dp(activity, 3));
            row.addView(swatch, params);
            marks[index] = swatch;
        }
        paint(activity, marks, presets, current);
        body.addView(value);
        body.addView(hex);
        value.setText(current == 0 ? "还没有自定义颜色" : "当前 " + String.format("#%06X", current & 0xffffff));
        if (current != 0) hex.setText(String.format("#%06X", current & 0xffffff));

        android.app.AlertDialog dialog = Theme.dialog(activity)
            .setTitle(title)
            .setView(body)
            .setPositiveButton("确定", (d, w) -> result.accept(parse(hex.getText().toString(), choice[0])))
            .setNegativeButton("取消", null)
            .create();
        dialog.show();
        // The dialog's own title is drawn by the platform; this page's type is set on purpose, so the
        // body keeps its spacing rather than sitting tight under the title rule.
        TextView heading = dialog.findViewById(activity.getResources().getIdentifier("alertTitle", "id", "android"));
        if (heading != null) heading.setTypeface(null, Typeface.BOLD);
    }

    private static void paint(Activity activity, TextView[] marks, int[] presets, int selected) {
        for (int index = 0; index < marks.length; index++) {
            TextView swatch = marks[index];
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(selected == presets[index] ? AppUi.SURFACE : presets[index]);
            shape.setCornerRadius(AppUi.dp(activity, 11));
            shape.setStroke(AppUi.dp(activity, selected == presets[index] ? 3 : 1), selected == presets[index] ? AppUi.ACCENT : AppUi.LINE);
            swatch.setBackground(shape);
        }
    }

    /** Accepts "#rrggbb" or "rrggbb"; anything else keeps the colour already chosen. */
    static int parse(String text, int fallback) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}")) return fallback;
        return 0xff000000 | (int) Long.parseLong(value, 16);
    }
}

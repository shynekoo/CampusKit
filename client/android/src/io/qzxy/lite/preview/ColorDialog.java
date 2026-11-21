// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 取色：色相条 + 明度/饱和度方块 + 六位色值。
 *
 * <p>只给一排预设色块，读者想调的东西就永远调不出来——课表上十几门课，想要的颜色总在第 19 种；
 * 只给输入框，每次改色都得先知道色值。所以：拖两个地方调颜色，下面的色值跟着变，想精确输入
 * 也行，最后按「应用」才生效。</p>
 */
final class ColorDialog {
    interface Result { void accept(int color); }

    private ColorDialog() { }

    static void show(Activity activity, String title, int current, Result done) {
        final int start = current == 0 ? Theme.BRAND : current;
        final float[] hsv = new float[3];
        Color.colorToHSV(start, hsv);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(AppUi.dp(activity, 18), AppUi.dp(activity, 2), AppUi.dp(activity, 18), 0);

        final Board board = new Board(activity, hsv[0], hsv[1], hsv[2]);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(-1, AppUi.dp(activity, 184));
        body.addView(board, boardParams);

        final Hue hue = new Hue(activity, hsv[0]);
        LinearLayout.LayoutParams hueParams = new LinearLayout.LayoutParams(-1, AppUi.dp(activity, 22));
        hueParams.topMargin = AppUi.dp(activity, 14);
        body.addView(hue, hueParams);

        // 色值一行：左边一个实心圆做「就是这一色」的凭证，右边是 # 加六位十六进制。
        LinearLayout field = new LinearLayout(activity);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(AppUi.dp(activity, 12), 0, AppUi.dp(activity, 10), 0);
        field.setBackground(AppUi.shape(activity, AppUi.SOFT, 12));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, AppUi.dp(activity, 46));
        fieldParams.topMargin = AppUi.dp(activity, 14);

        final View dot = new View(activity);
        field.addView(dot, new LinearLayout.LayoutParams(AppUi.dp(activity, 22), AppUi.dp(activity, 22)));
        ((LinearLayout.LayoutParams) dot.getLayoutParams()).rightMargin = AppUi.dp(activity, 10);

        TextView hash = new TextView(activity);
        hash.setText("#");
        hash.setTextSize(16);
        hash.setTextColor(AppUi.MUTED);
        hash.setTypeface(Typeface.MONOSPACE);
        field.addView(hash);

        final EditText hex = new EditText(activity);
        hex.setSingleLine(true);
        hex.setTextSize(16);
        hex.setTextColor(AppUi.INK);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setBackground(null);
        hex.setPadding(AppUi.dp(activity, 4), 0, 0, 0);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(6) });
        field.addView(hex, new LinearLayout.LayoutParams(0, -1, 1));
        body.addView(field, fieldParams);

        final boolean[] typing = { false };
        final int[] chosen = { start };

        Runnable syncField = () -> {
            typing[0] = true;
            hex.setText(String.format("%06X", Color.HSVToColor(new float[]{board.hue, board.saturation, board.value}) & 0xffffff));
            hex.setSelection(hex.getText().length());
            typing[0] = false;
        };
        syncField.run();

        board.setOnChange(() -> {
            chosen[0] = Color.HSVToColor(new float[]{board.hue, board.saturation, board.value});
            hue.setHue(board.hue, false);
            dot.setBackground(dot(activity, chosen[0]));
            if (!typing[0]) syncField.run();
        });
        hue.setOnChange(value -> {
            board.setHue(value);
            chosen[0] = Color.HSVToColor(new float[]{board.hue, board.saturation, board.value});
            dot.setBackground(dot(activity, chosen[0]));
            if (!typing[0]) syncField.run();
        });

        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable text) {
                if (typing[0]) return;
                String value = text.toString().trim();
                if (!value.matches("[0-9a-fA-F]{6}")) return;
                int color = 0xff000000 | (int) Long.parseLong(value, 16);
                float[] next = new float[3];
                Color.colorToHSV(color, next);
                board.setColor(next[0], next[1], next[2], false);
                hue.setHue(next[0], false);
                chosen[0] = color;
                dot.setBackground(dot(activity, color));
            }
        });

        dot.setBackground(dot(activity, chosen[0]));

        android.app.AlertDialog dialog = Theme.dialog(activity)
            .setTitle(title)
            .setView(body)
            .setPositiveButton("应用", (d, w) -> done.accept(chosen[0]))
            .setNegativeButton("取消", null)
            .create();
        dialog.show();
        TextView heading = dialog.findViewById(activity.getResources().getIdentifier("alertTitle", "id", "android"));
        if (heading != null) heading.setTypeface(null, Typeface.BOLD);
    }

    /** 明度/饱和度方块：横轴饱和度，纵轴亮度，底下的色相由调色条决定。 */
    private static final class Board extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /**
         * 两块渐变各用一支画笔。同一支画笔在同一帧里换两次着色器，硬件渲染会按第一次的程序
         * 把两次绘制画成一样——本机实测：滑一下色板，整块就淡成一片白。
         */
        private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        float hue, saturation, value;
        private Runnable listener;

        Board(Context context, float hue, float saturation, float value) {
            super(context);
            this.hue = hue; this.saturation = saturation; this.value = value;
        }

        void setOnChange(Runnable change) { listener = change; }

        void setHue(float next) { hue = next; invalidate(); }

        void setColor(float hue, float saturation, float value, boolean notify) {
            this.hue = hue; this.saturation = saturation; this.value = value;
            invalidate();
            if (notify && listener != null) listener.run();
        }

        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight(), radius = getResources().getDisplayMetrics().density * 12;
            // 圆角用 drawRoundRect 自己给，不靠 clipPath：硬件画布上被裁剪过的着色器会按另一套
            // 坐标取样，滑过一下整块就变成一片发白——本机实测过的那个毛病。
            rect.set(0, 0, width, height);

            wash.setShader(new LinearGradient(0, 0, width, 0, 0xffffffff,
                Color.HSVToColor(new float[]{hue, 1f, 1f}), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, wash);

            shade.setShader(new LinearGradient(0, 0, 0, height, 0x00000000, 0xff000000, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, radius, radius, shade);

            float cx = saturation * width, cy = (1f - value) * height, ring = getResources().getDisplayMetrics().density * 8;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(getResources().getDisplayMetrics().density * 2.4f);
            paint.setColor(0xffffffff);
            canvas.drawCircle(cx, cy, ring, paint);
            paint.setStrokeWidth(getResources().getDisplayMetrics().density);
            paint.setColor(0x40000000);
            canvas.drawCircle(cx, cy, ring + getResources().getDisplayMetrics().density * 1.6f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float width = Math.max(1, getWidth()), height = Math.max(1, getHeight());
                saturation = Math.max(0f, Math.min(1f, event.getX() / width));
                value = 1f - Math.max(0f, Math.min(1f, event.getY() / height));
                invalidate();
                if (listener != null) listener.run();
                return true;
            }
            return true;
        }
    }

    /** 色相条：七个定点摊开整圈色相，拖到哪就是哪一度。 */
    private static final class Hue extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int[] spectrum = { 0xffff0000, 0xffffff00, 0xff00ff00, 0xff00ffff,
            0xff0000ff, 0xffff00ff, 0xffff0000 };
        float hue;
        private java.util.function.Consumer<Float> listener;

        Hue(Context context, float hue) { super(context); this.hue = hue; }

        void setOnChange(java.util.function.Consumer<Float> change) { listener = change; }

        void setHue(float next, boolean notify) {
            hue = next;
            invalidate();
            if (notify && listener != null) listener.accept(next);
        }

        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight(), radius = height / 2f;
            paint.setShader(new LinearGradient(0, 0, width, 0, spectrum, null, Shader.TileMode.CLAMP));
            rect.set(0, 0, width, height);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);

            float cx = Math.max(radius, Math.min(width - radius, hue / 360f * width));
            paint.setColor(0xffffffff);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, height / 2f, height / 2f - getResources().getDisplayMetrics().density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(getResources().getDisplayMetrics().density * 2);
            paint.setColor(Color.HSVToColor(new float[]{hue, 1f, 1f}));
            canvas.drawCircle(cx, height / 2f, height / 2f - getResources().getDisplayMetrics().density * 2.5f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float width = Math.max(1, getWidth());
                hue = Math.max(0f, Math.min(1f, event.getX() / width)) * 360f;
                invalidate();
                if (listener != null) listener.accept(hue);
                return true;
            }
            return true;
        }
    }

    /** The one glyph this dialog needs: a filled circle, drawn in a colour, with a hairline rim. */
    static GradientDrawable dot(Activity activity, int color) {
        GradientDrawable shape = AppUi.shape(activity, color, 11);
        shape.setStroke(Math.max(1, AppUi.dp(activity, 1)), 0x33000000);
        return shape;
    }
}

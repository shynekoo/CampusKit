package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;

/**
 * 外观与主题：读者选的那几项存在本机，换算成颜色后写进 {@link AppUi}。
 *
 * <p>这个应用画的每一个像素都是从 {@code AppUi} 取色的，所以换主题不需要第二套布局：
 * 在页面画出来之前把那几个静态字段换成另一套值就够了。深色不是把浅色反相，而是另一组
 * 手挑的灰阶——正文仍然是正文的对比度，卡片仍然是卡片。</p>
 *
 * <p>课表外观单独一组：它只影响课表怎么画（配色、圆角、不透明度、网格线），不影响这一页
 * 之外的任何界面。两者放在同一个偏好文件里，因为它们回答的是同一个问题「它看起来怎么样」。</p>
 */
final class Theme {
    /** 偏好文件。与课表、账号分开：清账号不该顺手把外观也清回默认。 */
    private static final String STORE = "appearance";

    static final int AUTO = 0, LIGHT = 1, DARK = 2;
    /** 课程配色 / 文字颜色：0 用内置，1 用读者挑的。 */
    static final int BUILT_IN = 0, PICKED = 1;

    /** 品牌蓝，也是主题色「默认」的那一档。 */
    static final int BRAND = 0xff275dad;
    static final int DEFAULT_RADIUS = 8, DEFAULT_OPACITY = 100;
    static final int MIN_RADIUS = 0, MAX_RADIUS = 20;

    private static final int LIGHT_BG = 0xfff6f7f9, LIGHT_SURFACE = 0xffffffff, LIGHT_INK = 0xff202735,
        LIGHT_MUTED = 0xff596575, LIGHT_LINE = 0xffe3e7ed, LIGHT_SOFT = 0xffedf2f9;
    private static final int DARK_BG = 0xff101419, DARK_SURFACE = 0xff1a1f27, DARK_INK = 0xffe9eef6,
        DARK_MUTED = 0xff98a4b5, DARK_LINE = 0xff2b333e, DARK_SOFT = 0xff232b35;

    private static final String KEY_FILLS = "customFills";
    /** One entry per palette slot, so a half-edited palette still reads back as what was saved. */
    private static final int SLOTS = io.qzxy.lite.core.TimetablePalette.SIZE;

    private static int mode = AUTO, accent = 0, palette = BUILT_IN, ink = 0,
        radius = DEFAULT_RADIUS, opacity = DEFAULT_OPACITY;
    private static boolean floatingBar, slide = true, grid = true, dark;
    private static int[] fills = new int[SLOTS];
    /** Bumped on every save: a page that rendered under an older look knows to draw again. */
    private static int revision;

    private Theme() { }

    /** Reads the preferences and publishes the colours. Called before any view is built. */
    static void load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        mode = prefs.getInt("mode", AUTO);
        accent = prefs.getInt("accent", 0);
        floatingBar = prefs.getBoolean("floatingBar", false);
        slide = prefs.getBoolean("slide", true);
        palette = prefs.getInt("palette", BUILT_IN);
        ink = prefs.getInt("ink", 0);
        radius = clamp(prefs.getInt("radius", DEFAULT_RADIUS), MIN_RADIUS, MAX_RADIUS);
        opacity = clamp(prefs.getInt("opacity", DEFAULT_OPACITY), 0, 100);
        grid = prefs.getBoolean("grid", true);
        parseFills(prefs.getString(KEY_FILLS, ""));
        dark = mode == DARK || (mode == AUTO && night(context));
        publish();
    }

    private static boolean night(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int clamp(int value, int low, int high) {
        return value < low ? low : (value > high ? high : value);
    }

    /**
     * The one place that decides what every screen looks like. Accent is lifted towards white in the
     * dark theme: the brand blue was picked to sit on paper, and at 4.6:1 on 0xff101419 it would be
     * the one element nobody could read.
     */
    private static void publish() {
        AppUi.BG = dark ? DARK_BG : LIGHT_BG;
        AppUi.SURFACE = dark ? DARK_SURFACE : LIGHT_SURFACE;
        AppUi.INK = dark ? DARK_INK : LIGHT_INK;
        AppUi.MUTED = dark ? DARK_MUTED : LIGHT_MUTED;
        AppUi.LINE = dark ? DARK_LINE : LIGHT_LINE;
        AppUi.SOFT = dark ? DARK_SOFT : LIGHT_SOFT;
        AppUi.ACCENT = dark ? mix(accent == 0 ? BRAND : accent, 0xffffffff, .46f) : (accent == 0 ? BRAND : accent);
        AppUi.ON_ACCENT = readableOn(AppUi.ACCENT);
    }

    private static int mix(int from, int to, float amount) {
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * amount);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * amount);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * amount);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static void parseFills(String stored) {
        fills = new int[SLOTS];
        if (stored.isEmpty()) return;
        String[] parts = stored.split(",");
        for (int i = 0; i < SLOTS && i < parts.length; i++) {
            try { fills[i] = (int) Long.parseLong(parts[i].trim(), 16); } catch (NumberFormatException ignored) { fills[i] = 0; }
        }
    }

    private static String encodeFills() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) {
            if (i > 0) out.append(',');
            if (fills[i] != 0) out.append(Long.toHexString(fills[i] & 0xffffffffL));
        }
        return out.toString();
    }

    // ---- reading ---------------------------------------------------------------------------

    static boolean dark() { return dark; }
    static int mode() { return mode; }
    /** The chosen accent, or 0 when the reader left it at 默认. */
    static int accentChoice() { return accent; }
    static boolean floatingBar() { return floatingBar; }
    static boolean slide() { return slide; }
    static int palette() { return palette; }
    static int inkChoice() { return ink; }
    static int radius() { return radius; }
    static int opacity() { return opacity; }
    static boolean grid() { return grid; }
    static int revision() { return revision; }

    /** Fill for a 0-based palette slot, already carrying the reader's opacity. */
    static int cellFill(int slot) {
        return withOpacity(fill(slot));
    }

    /** The palette's opaque fill; used by the settings preview, which draws over its own backdrop. */
    static int fill(int slot) {
        int custom = customFill(slot);
        if (custom != 0) return custom;
        return io.qzxy.lite.core.TimetablePalette.fill(slot + 1);
    }

    static int customFill(int slot) {
        if (palette != PICKED || slot < 0 || slot >= SLOTS) return 0;
        return fills[slot];
    }

    /**
     * Text colour for a block. An explicit choice wins; otherwise a picked fill gets black or white
     * by luminance, because the reader may well have chosen something the built-in ink table was
     * never measured against.
     */
    static int inkFor(int slot) {
        if (ink != 0) return ink;
        int custom = customFill(slot);
        if (custom != 0) return readableOn(custom);
        return io.qzxy.lite.core.TimetablePalette.ink(slot + 1);
    }

    private static int readableOn(int fill) {
        double luminance = .299 * ((fill >> 16) & 255) + .587 * ((fill >> 8) & 255) + .114 * (fill & 255);
        return luminance > 150 ? 0xff1f2937 : 0xfff7fafc;
    }

    private static int withOpacity(int color) {
        if (opacity >= 100) return color;
        return ((opacity * 255 / 100) << 24) | (color & 0xffffff);
    }

    // ---- writing ---------------------------------------------------------------------------

    static void setMode(Context context, int value) { save(context, p -> p.putInt("mode", value)); }
    static void setAccent(Context context, int color) { save(context, p -> p.putInt("accent", color)); }
    static void setFloatingBar(Context context, boolean value) { save(context, p -> p.putBoolean("floatingBar", value)); }
    static void setSlide(Context context, boolean value) { save(context, p -> p.putBoolean("slide", value)); }
    static void setPalette(Context context, int value) { save(context, p -> p.putInt("palette", value)); }
    static void setInk(Context context, int color) { save(context, p -> p.putInt("ink", color)); }
    static void setRadius(Context context, int value) { save(context, p -> p.putInt("radius", clamp(value, MIN_RADIUS, MAX_RADIUS))); }
    static void setOpacity(Context context, int value) { save(context, p -> p.putInt("opacity", clamp(value, 0, 100))); }
    static void setGrid(Context context, boolean value) { save(context, p -> p.putBoolean("grid", value)); }

    /** One slot of the custom palette; also switches the timetable onto it. */
    static void setCustomFill(Context context, int slot, int color) {
        fills[slot] = color;
        save(context, p -> p.putString(KEY_FILLS, encodeFills()).putInt("palette", PICKED));
    }

    /** Back to the built-in colours: clears the slots as well, so 自定义 starts from a clean sheet. */
    static void resetCustomFills(Context context) {
        fills = new int[SLOTS];
        save(context, p -> p.putString(KEY_FILLS, "").putInt("palette", BUILT_IN));
    }

    private interface Edit { void apply(SharedPreferences.Editor editor); }

    private static void save(Context context, Edit edit) {
        SharedPreferences.Editor editor = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit();
        edit.apply(editor);
        editor.apply();
        revision++;
        load(context);
    }

    // ---- shared chrome ---------------------------------------------------------------------

    /**
     * Status and navigation bars belong to the same page as everything else, so they get the page's
     * colour and the icons flip with it. Without this a dark theme keeps two white strips at the top
     * and bottom of every screen.
     */
    static void decorate(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(AppUi.BG);
        window.setNavigationBarColor(AppUi.BG);
        // The window background is what shows for the frame before the content is laid out; left at
        // the light value it is a white flash on every page of a dark theme.
        window.setBackgroundDrawable(new ColorDrawable(AppUi.BG));
        // 返回手势动画: a page that slides in from the right and slides back out reads as one movement
        // with the system's back gesture. It is a preference because on a slow phone the extra frame
        // is worse than no animation at all.
        window.setWindowAnimations(slide ? R.style.SlidePage : 0);
    }

    private static void systemBarIcons(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    /** Applies the bar colour and icon tint; call again after the window is attached. */
    static void bars(Activity activity) {
        decorate(activity);
        systemBarIcons(activity);
    }

    /**
     * A dialog is its own window with its own theme, so a dark page would otherwise raise a white
     * sheet with light text on it. Wrapping the context is the only way to pick the dialog theme at
     * runtime — the activity theme is fixed before onCreate returns.
     */
    static AlertDialog.Builder dialog(Context context) {
        return new AlertDialog.Builder(new ContextThemeWrapper(context, dark ? R.style.AppDialogDark : R.style.AppDialog)) {
            /**
             * The style's own {@code windowBackground} loses to the one the platform's alert theme
             * installs, so the surface is painted here instead — otherwise a dark page offers a
             * #424242 sheet, which is neither this app's colour nor the one the reader just chose.
             */
            @Override public AlertDialog create() {
                AlertDialog dialog = super.create();
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(
                        dark ? R.drawable.dialog_background_dark : R.drawable.dialog_background);
                }
                return dialog;
            }
        };
    }
}

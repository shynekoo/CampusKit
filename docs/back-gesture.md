# 返回手势动画：相关代码一览（v2.5.6.14）

实现思路：**不自绘侧滑，也不把 `page_exit.xml` 当成跟手动画**。返回分三种方式，统一由 `Page.refreshBackMode()` 选出来：

| 方式 | 什么时候用 | 谁来画 |
| --- | --- | --- |
| 系统预测式 | 开关开 + Android 15(API 35) 起 + 本页返回一定关页 | 系统：整页缩成圆角卡片、上一页在下面、跟手、可取消 |
| 本地缩放 | 开关开 + 系统画不出来（14 及以下）或本页返回不关页 | 自己：`page_exit` 缩小淡出 |
| 直接切换 | 开关关 | 谁都不画，页面直切 |

`page_exit.xml` 是「播完就结束」的定时动画，**没有跟手能力**；它只是系统画不出来时的兜底，不要指望它等于 MyCHU 的效果。

涉及文件：

| 文件 | 作用 |
| --- | --- |
| `client/android/AndroidManifest.xml` | `enableOnBackInvokedCallback="true"`，系统动画的总开关 |
| `src/.../Page.java` | 所有页面基类：选方式、设窗口过渡、注册/不注册返回回调 |
| `res/anim/page_enter.xml` / `page_exit.xml` | 非手势场景的缩放过渡（旧系统） |
| `src/.../Theme.java` | 记忆开关 `slide`（默认 true） |
| `src/.../AppearanceActivity.java` | 「外观与主题 → 交互 → 返回手势动画」开关，改完当场生效 |
| `TimetableActivity` | `backMayStay()` 按 tab 状态作答 |
| `TimetableWebActivity` / `SchoolSiteActivity` / `EduPortalActivity` / `EduQueryActivity` | `backMayStay()` 按网页历史作答，`onPageFinished` 里刷新 |

---

## 1. AndroidManifest.xml

```xml
<uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36" />

<application
    android:name=".App"
    android:enableOnBackInvokedCallback="true"
    android:label="科信小助手"
    android:theme="@style/AppTheme"
    android:usesCleartextTraffic="false" ... >
```

targetSdk 34 起系统默认就开，这里显式写上，免得被别的工具改回去。

---

## 2. Page.java（全文）

`client/android/src/io/qzxy/lite/preview/Page.java`

```java
package io.qzxy.lite.preview;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * 每个页面的共同外壳：进出动画，以及「返回」该由谁来画。
 *
 * <p>返回一共三种方式，都从 {@link #refreshBackMode()} 出去，页面自己不各写一套：</p>
 *
 * <ul>
 *   <li><b>系统预测式</b>（开关开、Android 15 起、且本页返回就一定关页）：应用一条回调都不注册，
 *       系统才有机会画「整页缩成圆角卡片 + 上一页在下面 + 跟手 + 可取消」。参考项目 my_chu 走的
 *       就是这一套。</li>
 *   <li><b>本地缩放</b>（开关开、但系统画不出来）：注册普通回调自己接管，用 {@code page_exit}
 *       播一段确定的缩小淡出。系统版本、厂商主题都不影响它的样子。</li>
 *   <li><b>直接切换</b>（开关关）：注册普通回调，进出都不做动画。</li>
 * </ul>
 *
 * <p>这个类以前在 {@code onCreate} 里直接 {@code overrideActivityTransition(CLOSE, …)}。一旦设了
 * CLOSE，手势返回播放的就是这条过渡，系统的跨页面动画被顶掉——滑到一半什么都看不见，看着像
 * 「没反应」。现在只有确实不要系统动画时才设它，并集中在一处。</p>
 */
public class Page extends Activity {
    /** 交给系统画跨页面预测式返回，应用不注册回调。 */
    private static final int BACK_SYSTEM = 0;
    /** 自己播一段缩放，不依赖系统版本。 */
    private static final int BACK_LOCAL = 1;
    /** 不做动画，直接切换。 */
    private static final int BACK_OFF = 2;

    /**
     * 只在「系统不画」的两种方式下注册，用来抢在系统前面接管返回。
     *
     * <p>声明成 {@code Object} 是故意的：minSdk 24，直接写 {@code OnBackInvokedCallback} 类型
     * 会让低版本设备在加载这个类时去找一个不存在的接口。</p>
     */
    private Object backCallback;

    /** 上一次落实的方式；-1 = 还没配过。每次 {@code onPause} 都清回 -1，回来必然重配。 */
    private int backMode = -1;

    // ---- lifecycle -------------------------------------------------------------------------

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // 进出动画不在这里写死：统一由 refreshBackMode() 决定，避免两处各写一套互相顶掉。
        if (Diag.ON) Diag.step("返回手势", "SDK=" + Build.VERSION.SDK_INT
            + " 动画=" + (Theme.slide() ? "开" : "关") + " 类型=" + (backMayStay() ? "页内返回" : "关闭页面"));
    }

    @Override protected void onResume() {
        super.onResume();
        refreshBackMode();
    }

    @Override protected void onPause() {
        unregisterBackCallback();
        backMode = -1;
        super.onPause();
    }

    // ---- leaving ---------------------------------------------------------------------------

    @Override public void startActivity(Intent intent) {
        super.startActivity(intent);
        if (Build.VERSION.SDK_INT < 34 && Theme.slide()) overridePendingTransition(R.anim.page_enter, 0);
    }

    @Override public void startActivity(Intent intent, Bundle options) {
        super.startActivity(intent, options);
        if (Build.VERSION.SDK_INT < 34 && Theme.slide()) overridePendingTransition(R.anim.page_enter, 0);
    }

    @Override public void startActivityForResult(Intent intent, int request) {
        super.startActivityForResult(intent, request);
        if (Build.VERSION.SDK_INT < 34 && Theme.slide()) overridePendingTransition(R.anim.page_enter, 0);
    }

    /**
     * 应用里每个「返回」按钮都调 {@code finish()}，所以这里也是关闭动画唯一要说的地方；34 起
     * 走窗口过渡（见 {@link #applyTransition(int)}），这里只照顾旧系统。
     */
    @Override public void finish() {
        super.finish();
        // 官方要求 overridePendingTransition 跟在 finish() 之后，否则可能被忽略。
        if (Build.VERSION.SDK_INT < 34) {
            overridePendingTransition(0, Theme.slide() ? R.anim.page_exit : 0);
        }
    }

    // ---- choosing how 返回 looks -----------------------------------------------------------

    /**
     * True for pages whose 返回 might not leave the page at all — a web view going one page back,
     * a tab bar returning to 今日. The system assumes back closes the activity, so it would shrink
     * the whole page before handing the press over; on these pages that reads as a bounce.
     *
     * <p>子类按当前状态作答（网页还有历史吗、还在别的 tab 上吗），状态变了再调一次
     * {@link #refreshBackMode()}，返回方式会跟着换。</p>
     */
    protected boolean backMayStay() { return false; }

    /**
     * 按「开关 + 系统版本 + 本页返回时会不会留在原地」选出方式并当场落实。开关刚被改过时也调
     * 这里，所以不必退出页面再看效果。
     */
    protected final void refreshBackMode() {
        int mode = pickBackMode();
        if (mode == backMode) return;
        applyTransition(mode);
        applyCallback(mode);
        backMode = mode;
        if (Diag.ON) Diag.step("返回手势", "方式=" + (mode == BACK_SYSTEM ? "系统预测式"
            : mode == BACK_LOCAL ? "本地缩放" : "直接切换"));
    }

    private int pickBackMode() {
        if (!Theme.slide()) return BACK_OFF;
        // 跨 Activity 的预测式返回从 Android 15（API 35）起才是默认效果；更早的系统上各家长得
        // 都不一样，赌它能画出来就是「滑了没反应」，不如自己播一段确定的缩放。
        return Build.VERSION.SDK_INT >= 35 && !backMayStay() ? BACK_SYSTEM : BACK_LOCAL;
    }

    private void applyTransition(int mode) {
        if (Build.VERSION.SDK_INT < 34) return;
        if (mode == BACK_SYSTEM) {
            // 清掉自己设过的关闭动画，系统才会用默认的跨页面预测式动画。
            clearOverrideActivityTransition(OVERRIDE_TRANSITION_CLOSE);
        } else if (mode == BACK_LOCAL) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.page_exit);
        } else {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        }
    }

    private void applyCallback(int mode) {
        unregisterBackCallback();
        if (Build.VERSION.SDK_INT < 33 || mode == BACK_SYSTEM) return;
        // 只有「系统不画」的两种方式才需要抢在系统前面：注册普通回调，系统就不再缩卡片，
        // 页面按 onBackPressed() 的说法走，该关的按 page_exit 关。
        android.window.OnBackInvokedCallback plain = () -> {
            if (Diag.ON) Diag.step("返回手势", "回调命中");
            onBackPressed();
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, plain);
        backCallback = plain;
    }

    private void unregisterBackCallback() {
        if (backCallback == null) return;
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                (android.window.OnBackInvokedCallback) backCallback);
        }
        backCallback = null;
    }
}
```

### 几个容易踩的点

1. **不要同时留着 CLOSE 覆盖和「交给系统」**。`overrideActivityTransition(CLOSE, …)` 一旦设过，手势返回播的就是它；不清掉，系统就不画自己的跨页动画。
2. **`clearOverrideActivityTransition` 只在 34+ 存在**，所以整段用 `SDK_INT >= 34` 兜住。
3. **回调只在「系统不画」时注册**。注册了就轮到我们处理，系统的缩卡预览不会出现。
4. **`backMayStay()` 变成动态的**：网页还有历史、还在别的 tab 上时返回不会关页，这时必须先接管，否则系统先缩卡片、处理器只是后退一页，卡片再弹回来。

---

## 3. 过渡动画资源

`client/android/res/anim/page_enter.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 打开一个页面：和参考项目同一个观感——新页从略小的地方放大到位，而不是从右侧推入。
     缩放的另一个好处是四边始终能看见下面那一页，和手势返回里缩小的卡片是同一个方向。 -->
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:interpolator="@android:anim/decelerate_interpolator">
    <scale android:fromXScale="0.92" android:toXScale="1"
        android:fromYScale="0.92" android:toYScale="1"
        android:pivotX="50%" android:pivotY="50%" android:duration="220" />
    <alpha android:fromAlpha="0.6" android:toAlpha="1.0" android:duration="180" />
</set>
```

`client/android/res/anim/page_exit.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 关闭一个页面（点「返回」按钮、或系统接管返回）：缩小并淡出，下面的页面始终看得见，
     和手势返回里那张缩小的卡片是同一个方向。 -->
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:interpolator="@android:anim/accelerate_interpolator">
    <scale android:fromXScale="1" android:toXScale="0.92"
        android:fromYScale="1" android:toYScale="0.92"
        android:pivotX="50%" android:pivotY="50%" android:duration="200" />
    <alpha android:fromAlpha="1.0" android:toAlpha="0.5" android:duration="200" />
</set>
```

---

## 4. Theme.java 里的开关存储

```java
private static boolean slide = true, grid = true, dark;

// load()
slide = prefs.getBoolean("slide", true);

// 读取
static boolean slide() { return slide; }

// 写入（内部还会 revision++ 并 load()，所以 setSlide 之后 slide() 立刻就是新值）
static void setSlide(Context context, boolean value) { save(context, p -> p.putBoolean("slide", value)); }
```

---

## 5. 外观页上的那一行开关

`client/android/src/io/qzxy/lite/preview/AppearanceActivity.java`

```java
heading(column, "交互");
LinearLayout motion = card();
toggleRow(motion, "返回手势动画", "系统支持时跟手缩成卡片", Theme.slide(), value -> {
    Theme.setSlide(this, value);
    // 当场换掉返回方式，不用退出这一页再看效果。
    refreshBackMode();
});
add(column, motion);
gap(column, 18);
caption(column, Build.VERSION.SDK_INT >= 35
    ? "打开后往回滑时整页跟着手指缩成一张圆角卡片，上一页在下面，松手才返回，中途放弃会弹回去；关掉以后页面之间直接切换。"
    : "这台设备的系统画不出跟手缩小，打开后返回会播一段缩小淡出，比直接切换柔和；关掉则是直接切换。");
```

`refreshBackMode()` 是 `Page` 里的 `protected final`，`AppearanceActivity extends Page`，所以直接调。它内部记着上一次的方式，没变化时什么也不做，不会反复注册回调。

---

## 6. 覆盖 `backMayStay()` 的页面

```java
// TimetableActivity：返回先收内部状态：校区页 → 校区列表 → 今日
@Override protected boolean backMayStay() { return tab != 0 || campusView == 1; }

@Override public void onBackPressed() {
    if (tab == 2 && campusView == 1) { campusView = 0; render(); return; }
    if (tab != 0) { tab = 0; campusView = 0; render(); return; }
    super.onBackPressed();
}

// 四个网页页：网页还有上一页时返回只是后退，窗口不会关
@Override protected boolean backMayStay() { return web != null && web.canGoBack(); }

// 网页历史变了要重新选一次方式，否则「能不能缩卡片」的判断会过期
public void onPageFinished(WebView view, String url) {
    refreshBackMode();
    ...
}
```

`TimetableActivity.render()` 开头也有一句 `refreshBackMode()`：tab / campusView 刚变过，返回方式要跟着变；`SchoolSiteActivity` 的 `doUpdateVisitedHistory` 里同样有一句。

---

## 7. 还没做到的（要真跟手得改架构）

- 现在项目是**一个页面一个 Activity**，返回动画的观感由系统（或系统版本）决定；MyCHU 是**单 Flutter 路由栈**，前后两页都在自己的渲染树里，所以能稳定做到「当前页缩小、上一页在下面、进度跟手」。
- 想要 Android 14 以下也跟手、且不依赖厂商主题，得改成**一个 Activity + 页面栈**（Fragment 或自绘 View 栈），用 `OnBackAnimationCallback`（API 34+）或自绘手势驱动进度，把上一页的位图垫在下面。
- 目前的分级是明确的：15+ 交给系统，14 及以下播一段确定的缩放，开关关掉直接切换。三种都不会再出现「滑了没反应还看不出在干嘛」。

## 8. 验证情况（v2.5.6.14，android-34 模拟器）

- 应用内「返回账号」、系统返回键都能正常关页，无崩溃。
- 今日首页按返回退出应用；课表/账号 tab 按返回先回今日。
- 网页页按返回：有历史时后退一页，历史用完再按才关窗口。
- 「返回手势动画」开关切换后当场生效（开↔关都能立刻返回），偏好持久化。
- **手势本身的缩卡动画 adb 注入不了**（`input swipe` 不会被 SystemUI 的边缘手势识别，系统设置里也一样），只能真机确认；且 15+ 才有系统动画，34 上走的是本地缩放。

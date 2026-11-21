// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies 外观与主题 before any page is built, and keeps the pages that are already open in step
 * with it.
 *
 * <p>The colours live in static fields on {@link AppUi}, so the earliest possible moment to set them
 * is {@code Application.onCreate}. Changing a setting later must not require every page to be written
 * with a theme observer: the pages that know how to repaint themselves say so by implementing
 * {@link Themed}, and everything else is rebuilt by the framework. That is why the account page, the
 * 更多 page and a web view somebody is reading all come back in the new colours without a single
 * line of theme code in them.</p>
 */
public final class App extends Application {
    /** A page that can redraw itself in place; everything else is recreated instead. */
    interface Themed {
        void onThemeChanged();
    }

    /** The look each activity was created under, so a resumed page knows it is out of date. */
    private final Map<Activity, Integer> drawn = new WeakHashMap<>();

    /**
     * 前台页面的计数。0 → 1 就是「回到前台」，1 → 0 是「退到后台」。用它而不是每个页面的
     * {@code onResume}：一次进前台只触发一次，页面之间来回切不会重复问统计服务。
     */
    private int started;

    @Override public void onCreate() {
        super.onCreate();
        Theme.load(this);
        // 越早装崩溃兜底越好：登录、课表这些页面崩之前，进程可能还没打开过任何一页。
        // 这里不写任何记录（运行期默认关），只是把文件目录和未捕获异常处理准备好。
        Diag.install(this);
        registerActivityLifecycleCallbacks(new Lifecycle());
    }

    private static int signature() {
        return Theme.revision() * 7 + (Theme.dark() ? 3 : 0);
    }

    private final class Lifecycle implements ActivityLifecycleCallbacks {
        @Override public void onActivityCreated(Activity activity, Bundle saved) {
            // Re-read on every create: the system's night setting can flip while the app is in the
            // background, and a recreated page must come up in the colours it will be seen in.
            Theme.load(activity);
            Theme.bars(activity);
            drawn.put(activity, signature());
        }

        @Override public void onActivityResumed(Activity activity) {
            // A resumed page whose look has changed since it was drawn is one the reader would
            // otherwise still see in the old theme.
            Integer was = drawn.get(activity);
            if (was != null && was == signature()) return;
            drawn.put(activity, signature());
            Theme.bars(activity);
            if (activity instanceof Themed) ((Themed) activity).onThemeChanged();
            else activity.recreate();
        }

        @Override public void onActivityStarted(Activity activity) {
            // 只有「从后台回来」这一次算进入前台。没有同意统计的用户在这里只是一次偏好读取，
            // 不会生成标识、也不会发请求。
            if (started++ > 0) return;
            AnalyticsReporter.onForeground(getApplicationContext());
        }

        @Override public void onActivityPaused(Activity activity) { }

        @Override public void onActivityStopped(Activity activity) {
            if (started > 0) started--;
        }

        @Override public void onActivitySaveInstanceState(Activity activity, Bundle saved) { }

        @Override public void onActivityDestroyed(Activity activity) {
            drawn.remove(activity);
        }
    }
}

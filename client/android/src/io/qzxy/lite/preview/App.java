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

    @Override public void onCreate() {
        super.onCreate();
        Theme.load(this);
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

        @Override public void onActivityStarted(Activity activity) { }

        @Override public void onActivityPaused(Activity activity) { }

        @Override public void onActivityStopped(Activity activity) { }

        @Override public void onActivitySaveInstanceState(Activity activity, Bundle saved) { }

        @Override public void onActivityDestroyed(Activity activity) {
            drawn.remove(activity);
        }
    }
}

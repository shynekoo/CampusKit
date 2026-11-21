// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.app.Activity;
import android.webkit.WebView;
import android.widget.Button;

/**
 * Web history first, the window second.
 *
 * <p>Inside an embedded school site the top bar's back button used to call {@code finish()}
 * directly, so a reader a few links deep into the site lost the whole window instead of walking
 * back one page. Every embedded browser here now goes back through the WebView's own history and
 * only closes when there is nothing left to return to.</p>
 *
 * <p>The caption follows the same rule, so the outcome of a tap is visible beforehand: 返回 while a
 * previous page exists, 关闭 when the tap will leave the site. The system back key uses the same
 * path, which keeps both ways of going back consistent.</p>
 */
final class WebBack {
    static final String PREVIOUS = "‹ 返回";
    static final String CLOSE = "✕ 关闭";

    private WebBack() {}

    /** Returns true when the page moved back, false when the window was closed instead. */
    static boolean go(Activity activity, WebView web) {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        activity.finish();
        return false;
    }

    /** Keeps the caption honest; safe to call before the WebView exists. */
    static void caption(Button button, WebView web) {
        if (button == null) return;
        button.setText(web != null && web.canGoBack() ? PREVIOUS : CLOSE);
    }
}

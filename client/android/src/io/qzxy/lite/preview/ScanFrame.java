// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Dims everything outside the square a code has to sit in and marks that square's corners.
 *
 * <p>A bare preview gives no hint about where to aim; the brackets do, and the dimmed surround keeps
 * them readable on a bright wall. Nothing here animates, so it costs one draw per layout change.
 */
final class ScanFrame extends View {
    private final Paint scrim = new Paint();
    private final Paint bracket = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF window = new RectF();
    private float arm;

    ScanFrame(Context context) {
        super(context);
        scrim.setColor(0x73000000);
        bracket.setStyle(Paint.Style.STROKE);
        bracket.setStrokeWidth(dp(3));
        bracket.setStrokeCap(Paint.Cap.ROUND);
        bracket.setColor(0xffffffff);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        // Slightly above centre, because the hint sits under the square and thumbs sit at the bottom.
        float side = Math.min(width * 0.74f, height * 0.62f);
        side = Math.max(Math.min(side, dp(320)), Math.min(dp(150), width * 0.9f));
        float left = (width - side) / 2f;
        float top = height * 0.44f - side / 2f;
        top = Math.max(dp(16), Math.min(top, height - side - dp(64)));
        window.set(left, top, left + side, top + side);
        arm = Math.max(dp(22), side * 0.16f);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), window.top, scrim);
        canvas.drawRect(0, window.bottom, getWidth(), getHeight(), scrim);
        canvas.drawRect(0, window.top, window.left, window.bottom, scrim);
        canvas.drawRect(window.right, window.top, getWidth(), window.bottom, scrim);

        float l = window.left, t = window.top, r = window.right, b = window.bottom;
        canvas.drawLine(l, t + arm, l, t, bracket);
        canvas.drawLine(l, t, l + arm, t, bracket);
        canvas.drawLine(r - arm, t, r, t, bracket);
        canvas.drawLine(r, t, r, t + arm, bracket);
        canvas.drawLine(l, b - arm, l, b, bracket);
        canvas.drawLine(l, b, l + arm, b, bracket);
        canvas.drawLine(r - arm, b, r, b, bracket);
        canvas.drawLine(r, b, r, b - arm, bracket);
    }
}

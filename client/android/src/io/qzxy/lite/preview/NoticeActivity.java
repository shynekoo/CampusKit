// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.os.Bundle;

/**
 * 使用公告的完整页面：正文来自 {@link LegalText#NOTICE}，与首次启动的 {@link NoticeDialog}
 * 共用同一份文字，所以弹窗里读到的和这里读到的一字不差。
 *
 * <p>整篇是打包进 APK 的静态文字，不联网、不用 WebView，断网也能读完。入口有两处：
 * 「更多」页的独立条目，以及「数据与隐私」页末尾的卡片。</p>
 */
public final class NoticeActivity extends Page {

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LegalView.Screen screen = LegalView.screen(this, "使用公告", "个人开发的第三方客户端");
        for (String[] section : LegalText.NOTICE) LegalView.section(this, screen.content, section);
        setContentView(screen.root);
        screen.root.requestApplyInsets();
    }
}

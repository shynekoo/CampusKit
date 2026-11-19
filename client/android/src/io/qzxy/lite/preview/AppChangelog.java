package io.qzxy.lite.preview;

/**
 * 应用内更新日志的唯一数据源。
 *
 * <p>只收**已经正式发出去**的版本：每条都能对上一份 {@code delivery/release-<版本>.md}
 * 或 {@code CHANGELOG.md} 里的发布记录，日期、版本代码照抄，不推测、不补写。
 * {@code CHANGELOG.md} 里的「未发布」段落属于开发记录，不能出现在这里——
 * {@code scripts/test_ui_contract.py} 会核对本表与 {@code delivery/update/update.json}
 * 的版本代码，发版忘了加日志会直接失败。</p>
 *
 * <p>排版在 {@link ChangelogActivity}，这里只放数据。第二阶段要做「升级后弹出本次更新」时，
 * 直接按 {@link Entry#code} 取区间即可（{@code lastSeen < code <= 当前}），不必改这棵树。</p>
 */
final class AppChangelog {

    /** 一条变化的类别；只影响图标与配色，不影响文字。 */
    enum Kind {
        /** 新功能。 */
        ADDED,
        /** 体验优化：更快、更顺手、文案更清楚。 */
        IMPROVED,
        /** 修好了一个用户能看见的问题。 */
        FIXED,
        /** 其他：说明、记录、内部调整。 */
        OTHER
    }

    /** 一条面向用户的变化。文字写给同学看，不写类名、接口名和业务码。 */
    static final class Item {
        final Kind kind;
        final String text;

        Item(Kind kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    /**
     * 一个正式版本。
     *
     * <p>{@code released} 目前恒为 true：只登记发出去的版本。留着这个字段是为了把
     * 「只出包未上线」的版本（如 2.5.3）与正式版本区分开——真需要登记时置 false，
     * 页面按 {@link #released()} 取数据，不会把它当成正式更新展示。</p>
     */
    static final class Entry {
        final String version;
        final int code;
        /** 发布日期，{@code yyyy-MM-dd}，取自发布说明。 */
        final String date;
        final Item[] items;
        /** 这一版仍存在的已知问题；没有则为空数组。 */
        final String[] known;
        final boolean released;

        Entry(String version, int code, String date, String[] known, Item... items) {
            this(version, code, date, known, true, items);
        }

        Entry(String version, int code, String date, String[] known, boolean released, Item... items) {
            this.version = version;
            this.code = code;
            this.date = date;
            this.known = known == null ? new String[0] : known;
            this.released = released;
            this.items = items;
        }
    }

    /**
     * 新到旧。新增版本**只在最前面追加**，日期与版本代码从发布说明里抄。
     *
     * <p>未收录：2.0–2.3.9（仓库里只有安装包与发布说明，没有留下带日期的发布记录）、
     * 2.5.3（只出包、未上线）、2.5.4（并入 2.5.5）、2.5.6.x 的开发迭代（如 2.5.6.1）。
     * 收录口径写在这里，免得下次有人以为漏了。</p>
     */
    static final Entry[] ENTRIES = {
        new Entry("2.5.8", 208, "2026-11-19", null,
            new Item(Kind.IMPROVED, "底栏和页面切换动画重做：切换更顺，切走再回来还停在原来的位置"),
            new Item(Kind.ADDED, "新增学业情况查询：每个课组都能点开，看到课程、成绩和学分"),
            new Item(Kind.ADDED, "新增趣智校园使用码：没带手机扫不了码时，输入 8 位码也能开启设备"),
            new Item(Kind.ADDED, "新增应用内更新日志：更多 → 更新日志，每次改了什么都能回看"),
            new Item(Kind.IMPROVED, "「回本周」改成整屏滑过去，松手那一刻就是那一周，不再闪一下"),
            new Item(Kind.FIXED, "修好部分课组「右边写着有学分、点开却说没有课程记录」的问题"),
            new Item(Kind.ADDED, "新增诊断日志：遇到问题时打开记录、导出给开发者；默认关闭，只存在本机"),
            new Item(Kind.IMPROVED, "使用公告与隐私说明按实际行为重写，并去掉一个从未用过的权限")),
        new Entry("2.5.7", 197, "2026-11-18", null,
            new Item(Kind.ADDED, "新增深色主题：版面和浅色一样，晚上看课表不再刺眼"),
            new Item(Kind.ADDED, "新增返回手势动画：从屏幕边缘滑回时，页面跟着手指缩小"),
            new Item(Kind.ADDED, "应用与课表的主题色都能自己调"),
            new Item(Kind.FIXED, "课名字号与行高按屏幕自动适配，窄屏不再挤成两字一行")),
        new Entry("2.5.6", 177, "2026-11-17", null,
            new Item(Kind.FIXED, "修好慧生活无法使用：上一版设备页的「进入独立用水」入口丢了，现在已恢复，支付宝免密登录照旧"),
            new Item(Kind.FIXED, "调课那类多段周次的课（如「2-6周(双),11-15周」）以前导入会被跳过，现在能正常进课表")),
        new Entry("2.5.5", 176, "2026-11-17", null,
            new Item(Kind.FIXED, "修好「清除登录状态」点了不管用的问题"),
            new Item(Kind.IMPROVED, "登录做了优化：没登录会自动跳到登录页，登完自动回到原来的页面"),
            new Item(Kind.ADDED, "校园页新增「教务系统」入口；「数据与隐私」挪进「更多」")),
        new Entry("2.5.0", 170, "2026-11-16", null,
            new Item(Kind.IMPROVED, "开启用水固定走支付宝免密，不再弹出支付方式让你挑"),
            new Item(Kind.FIXED, "设备没提供支付宝免密时会提前说明并列出可用方式，不会盲发请求"),
            new Item(Kind.IMPROVED, "「业务码 -3」的提示改成「设备可能刚被他人占用或免密代扣未生效」，不再引导换支付方式")),
        new Entry("2.3.10", 169, "2026-11-16", null,
            new Item(Kind.FIXED, "慧生活设备控制的请求头和参数对齐官方客户端，开启与结束用水更稳"),
            new Item(Kind.IMPROVED, "支付方式默认支付宝免密；商家钱包余额为 0 时排到后面"),
            new Item(Kind.OTHER, "这一版是对着同类开源客户端 WaterWidget 的实测结果逐项修正的")),
    };

    private AppChangelog() { }

    /** 只取正式发布的版本。 */
    static Entry[] released() {
        int count = 0;
        for (Entry entry : ENTRIES) if (entry.released) count++;
        Entry[] out = new Entry[count];
        int at = 0;
        for (Entry entry : ENTRIES) if (entry.released) out[at++] = entry;
        return out;
    }

    /** 当前安装版本对应的条目；安装的是未发布构建时返回 null，页面据此不显示「当前版本」。 */
    static Entry current(String versionName) {
        if (versionName == null) return null;
        for (Entry entry : ENTRIES) if (entry.released && entry.version.equals(versionName.trim())) return entry;
        return null;
    }
}

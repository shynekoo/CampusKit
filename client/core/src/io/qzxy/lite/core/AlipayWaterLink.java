// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/** Original sj.b.i/h/e handoff. Returned URI contains credentials: never log or persist it. */
public final class AlipayWaterLink {
    private AlipayWaterLink() { }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    public static String bathroom(String serial, AccountSession session, String accountId,
                                  String projectId, String telephone, String migrated) {
        return device(4,serial,session,accountId,projectId,telephone,migrated);
    }
    /** Original sj.r case 7 -> sj.b.l -> moduleId 5; bathroom retains moduleId 1. */
    public static String device(int type, String serial, AccountSession session, String accountId,
                                  String projectId, String telephone, String migrated) {
        if(type!=4&&type!=7)throw new IllegalArgumentException("暂不支持此设备跳转");
        if (serial == null || !serial.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("设备编号无效，请重新扫码");
        if (session == null || projectId == null || !projectId.matches("[1-9][0-9]{0,19}")
            || !projectId.equals(session.projectId()) || accountId == null
            || !accountId.matches("[1-9][0-9]{0,19}") || telephone == null
            || !telephone.matches("1[0-9]{10}") || migrated == null || !migrated.matches("[0-9]{1,9}"))
            throw new IllegalArgumentException("账号或校园跳转信息不完整，请重新登录后再试");
        if (!session.loginCode().matches("[A-Za-z0-9_.=+/-]{1,4096}"))
            throw new IllegalArgumentException("登录凭证格式不支持跳转，请重新登录");
        String query = "moduleId="+(type==7?5:1)+"&accountId=" + accountId + "&userId=" + session.userId()
            + "&token=" + session.loginCode() + "&telPhone=" + telephone
            + "&projectId=" + projectId + "&isMigrated=" + migrated + "&snCode=" + serial;
        return "alipays://platformapi/startapp?appId=2018090661238647"
            + "&page=pages/alipayJumpPage/alipayJumpPage&query=" + encode(query);
    }
}

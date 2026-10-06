package io.qzxy.lite.preview;

import io.qzxy.lite.core.AccountSession;
import org.json.JSONObject;
import java.net.URLDecoder;

/** Runs the actual API adapter with synthetic responses, without INTERNET permission. */
final class AlipayHandoffTests {
    static int run() throws Exception {
        final int[] calls = {0};
        final JSONObject user = new JSONObject("{\"userId\":42,\"telephone\":\"13800000000\",\"userAccount\":{\"accountId\":7,\"projectId\":9}}");
        final JSONObject project = new JSONObject("{\"projectId\":9,\"migrateDTO\":{\"isMigrated\":1}}");
        AccountSession login = new AccountSession("42", "SYNTHETIC_TOKEN", "9");
        AccountProfile account = AccountProfile.parse(user, "42");
        DeviceLookup device = DeviceDetails.lookup(new JSONObject("{\"projectId\":9,\"deviceId\":8,\"bigTypeId\":4,\"isUse\":1,\"snCode\":\"SYNTHETIC233\"}"), "9", account);
        HttpForm.testTransport = (path, form, post) -> {
            if (post) throw new AssertionError("Handoff must only query");
            calls[0]++;
            JSONObject data;
            if (path.equals("user/info")) data = user;
            else if (path.equals("project/info/triple")) {
                if (!form.contains("accountId=7") || !form.contains("telPhone=13800000000"))
                    throw new AssertionError("Missing original request context");
                data = project;
            } else throw new AssertionError("Unexpected endpoint");
            return new JSONObject().put("errorCode", 0).put("data", data);
        };
        try {
            String link = SmsAccountApi.alipayWaterLink(login, device);
            String query = URLDecoder.decode(link.substring(link.indexOf("&query=") + 7), "UTF-8");
            if (!query.equals("moduleId=1&accountId=7&userId=42&token=SYNTHETIC_TOKEN&telPhone=13800000000&projectId=9&isMigrated=1&snCode=SYNTHETIC233"))
                throw new AssertionError("Original handoff contract mismatch");
            if (calls[0] != 2) throw new AssertionError("Unexpected calls");
            JSONObject dryerData=new JSONObject("{\"projectId\":9,\"deviceId\":8,\"bigTypeId\":7,\"isUse\":1,\"snCode\":\"SYNTHETIC202\",\"devTypeStatus\":4,\"isOnline\":1}");
            DeviceLookup dryer=DeviceDetails.lookup(dryerData,"9",account);
            if(dryer.ready||!dryer.alipayReady())throw new AssertionError("Dryer must only enable handoff");
            String dryerLink=SmsAccountApi.alipayWaterLink(login,dryer);
            String dryerQuery=URLDecoder.decode(dryerLink.substring(dryerLink.indexOf("&query=")+7),"UTF-8");
            if(!dryerQuery.equals(query.replace("moduleId=1","moduleId=5").replace("SYNTHETIC233","SYNTHETIC202")))throw new AssertionError("Dryer module contract");
            for(int unsupported:new int[]{5,6,8,17}){dryerData.put("bigTypeId",unsupported);reject(()->SmsAccountApi.alipayWaterLink(login,DeviceDetails.lookup(dryerData,"9",account)));}
            dryerData.put("bigTypeId",7).put("isUse",0);reject(()->SmsAccountApi.alipayWaterLink(login,DeviceDetails.lookup(dryerData,"9",account)));
            dryerData.put("isUse",1).put("devTypeStatus",1);reject(()->SmsAccountApi.alipayWaterLink(login,DeviceDetails.lookup(dryerData,"9",account)));
            dryerData.put("devTypeStatus",5).put("isOnline",0);reject(()->SmsAccountApi.alipayWaterLink(login,DeviceDetails.lookup(dryerData,"9",account)));
            dryerData.put("isOnline",1);if(!DeviceDetails.lookup(dryerData,"9",account).alipayReady())throw new AssertionError("Online mixed dryer");
            dryerData.put("projectId",10);reject(()->SmsAccountApi.alipayWaterLink(login,DeviceDetails.lookup(dryerData,"9",account)));
            if (!SmsAccountApi.migrationFlag(new JSONObject()).equals("1")) throw new AssertionError("Missing DTO default");
            if (!SmsAccountApi.migrationFlag(new JSONObject("{\"migrateDTO\":null}")).equals("1")) throw new AssertionError("Null DTO default");
            if (!SmsAccountApi.migrationFlag(new JSONObject("{\"migrateDTO\":{}}")).equals("0")) throw new AssertionError("Primitive default");
            if (!SmsAccountApi.migrationFlag(new JSONObject("{\"isMigrated\":1,\"migrateDTO\":{\"isMigrated\":0}}")).equals("0")) throw new AssertionError("Nested field precedence");
            project.put("projectId", 10);
            reject(() -> SmsAccountApi.alipayWaterLink(login, device));
            project.put("projectId", 9);
            user.getJSONObject("userAccount").put("accountId", 99);
            int previous = calls[0];
            reject(() -> SmsAccountApi.alipayWaterLink(login, device));
            if (calls[0] != previous + 1) throw new AssertionError("Must stop on changed account");
            reject(() -> SmsAccountApi.migrationFlag(new JSONObject("{\"migrateDTO\":\"bad\"}")));
            reject(() -> SmsAccountApi.migrationFlag(new JSONObject("{\"migrateDTO\":{\"isMigrated\":\"bad\"}}")));
            return 22;
        } finally { HttpForm.testTransport = null; }
    }
    interface Operation { void run() throws Exception; }
    private static void reject(Operation action) throws Exception {
        try { action.run(); } catch (java.io.IOException | org.json.JSONException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid handoff accepted");
    }
}

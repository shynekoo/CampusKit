package io.qzxy.lite.core;

/** Narrow original numeric-code/NFC contracts; reject unrelated service and payment codes. */
public final class Life798Qr {
    private Life798Qr() { }
    public static String parse(String raw) {
        if(raw == null || raw.length()>1024)throw new IllegalArgumentException("二维码无效");
        String value=raw.trim();
        if(value.matches("[0-9]{1,64}"))return value;
        try {
            java.net.URI uri=new java.net.URI(value);
            if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))
                || uri.getUserInfo()!=null || uri.getPort()!=-1 || uri.getFragment()!=null
                || !java.util.Arrays.asList("i.ilife798.com","i.hnkzy.com","act.hnkzy.com","act2.hnkzy.com").contains(uri.getHost())
                )throw new IllegalArgumentException();
            if(uri.getRawQuery()==null && uri.getPath().matches("/q/[0-9]+/[A-Za-z0-9-]{1,64}"))
                return uri.getPath().substring(uri.getPath().lastIndexOf('/')+1);
            if(!"/nfc.html".equals(uri.getPath()))throw new IllegalArgumentException();
            String query=uri.getRawQuery();
            if(query!=null && query.matches("q=[A-Za-z0-9_-]{1,64}"))return query.substring(2);
        } catch(Exception invalid) { }
        throw new IllegalArgumentException("暂不支持此慧生活二维码格式，请使用原版扫码");
    }
}

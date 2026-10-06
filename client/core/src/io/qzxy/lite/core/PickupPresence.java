package io.qzxy.lite.core;

/** Only positive pickup evidence promotes the card; unknown/loading remains below courses. */
public final class PickupPresence {
    private PickupPresence() { }
    public static boolean hasPickup(String text) {
        if(text == null) return false;
        return java.util.regex.Pattern.compile("(?:取件码|提货码|取货码)[：:\\s]*[A-Za-z0-9]+(?:[-－][A-Za-z0-9]+)+|[1-9][0-9]*\\s*(?:个|件)?\\s*(?:待取件|待取包裹)|(?:待取件|待取包裹)[：:\\s]*[1-9][0-9]*").matcher(text).find();
    }
}

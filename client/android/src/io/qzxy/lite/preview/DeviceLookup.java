package io.qzxy.lite.preview;

import io.qzxy.lite.core.DeviceProfile;

/**
 * Result of one read-only device lookup. It carries everything a later order request needs, so the
 * account profile is not re-fetched and the serial number stays out of the UI layer's reach.
 */
final class DeviceLookup {
    final DeviceProfile profile;
    final AccountProfile account;
    /** Pre-rendered, display-safe description; contains no serial number. */
    final String text;
    /** True only when the profile passed every gate for a future water command. */
    final boolean ready;

    /** Separate from the bathroom-only direct-control gate. */
    boolean alipayReady() {
        if(profile.bathroom())return ready;
        return profile.dsbTypeId()==7&&profile.usable()&&profile.hasSerial()
            &&account!=null&&profile.inProject(account.projectId)
            &&(profile.transportHint()==4||profile.transportHint()==5&&profile.online()==1);
    }

    DeviceLookup(DeviceProfile profile, AccountProfile account, String text, boolean ready) {
        this.profile = profile; this.account = account; this.text = text; this.ready = ready;
    }
}

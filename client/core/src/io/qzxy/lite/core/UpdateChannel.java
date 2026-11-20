package io.qzxy.lite.core;

/**
 * Decides which of the two published manifests — release or preview — is worth offering.
 *
 * <p>Both files are public, so a candidate here is only ever "the manifest that arrived"; every
 * field in it has already been checked by {@link UpdateManifest}, and the download is verified
 * again before install. What this class fixes is the choice:</p>
 *
 * <ul>
 *   <li>the preview list is consulted only when the reader turned the switch on;</li>
 *   <li>the winner is the one with the larger {@code versionCode}, never the nicer-looking name;</li>
 *   <li>a candidate must be strictly newer than what is installed, so a reader who is running a
 *       preview and turns the switch off is left alone instead of being pushed down to the older
 *       release — the installer would refuse it anyway, after a download;</li>
 *   <li>a list that cannot be fetched is simply absent: the other channel still answers;</li>
 *   <li>a preview entry that carries a release name is a mis-published file and is ignored, so a
 *       preview never gets announced as a release (or the other way round).</li>
 * </ul>
 */
public final class UpdateChannel {

    private UpdateChannel() { }

    /**
     * @param installedCode the running build's {@code versionCode}
     * @param betaEnabled   the reader's saved choice
     * @param release       manifest from the release list, or null when it could not be read
     * @param preview       manifest from the preview list, or null when it could not be read
     * @return the manifest to offer, or null when there is nothing newer to offer
     */
    public static UpdateManifest pick(int installedCode, boolean betaEnabled,
                                      UpdateManifest release, UpdateManifest preview) {
        UpdateManifest best = newer(release, installedCode) ? release : null;
        if (betaEnabled && preview != null && preview.beta && newer(preview, installedCode)) {
            // Equal codes mean the same build appeared in both lists; the release file wins, so the
            // dialog never labels a stable build as a preview.
            if (best == null || preview.versionCode > best.versionCode) best = preview;
        }
        return best;
    }

    private static boolean newer(UpdateManifest manifest, int installedCode) {
        return manifest != null && manifest.versionCode > installedCode;
    }
}

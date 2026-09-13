package app.template.patches.redditsync.removesyncultra.fingerprints

import app.morphe.patcher.fingerprint

/**
 * Matches Lpa/w;->C3(Landroid/os/Bundle;Ljava/lang/String;)V ("PreferencesCommentsFragment"
 * per its source-file annotation, v23.06.30-13:39) — the fragment behind the "Comments"
 * settings screen (res/xml/cat_comments.xml).
 *
 * Confirmed by hand via apktool: unlike every other Preferences*Fragment touched so far in
 * this project, this method has no preference-click-wiring setup call at all — it only
 * inflates the XML (t3) and calls super. Used by removeSyncUltraSetupPatch to add one.
 */
val preferencesCommentsFragmentFingerprint = fingerprint {
    returns("V")
    parameters("Landroid/os/Bundle;", "Ljava/lang/String;")
    custom { method, classDef -> classDef.type == "Lpa/w;" && method.name == "C3" }
}

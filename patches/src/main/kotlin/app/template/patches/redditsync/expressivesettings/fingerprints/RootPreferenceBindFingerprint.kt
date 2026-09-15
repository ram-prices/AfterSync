package app.template.patches.redditsync.expressivesettings.fingerprints

import app.morphe.patcher.fingerprint

/**
 * Matches Lcom/laurencedawson/reddit_sync/ui/preferences/custom/RootPreference;->S(Landroidx/preference/h;)V
 * — RootPreference's bind method (Preference.onBindViewHolder, R8-renamed), which sets up
 * each root-settings-list row's title/summary/icon/click-highlight every time it's bound to
 * a RecyclerView item view. Used to reach both this method (to rewrite its icon-tint logic)
 * and RootPreference's classDef (to add a new palette-color helper to the same class).
 */
val rootPreferenceBindFingerprint = fingerprint {
    returns("V")
    parameters("Landroidx/preference/h;")
    custom { method, classDef ->
        classDef.type == "Lcom/laurencedawson/reddit_sync/ui/preferences/custom/RootPreference;" &&
            method.name == "S"
    }
}

package app.template.patches.redditsync.dynamiccolor.fingerprints

import app.morphe.patcher.fingerprint

/**
 * Matches Lcom/laurencedawson/reddit_sync/ui/activities/BaseActivity;->onCreate(Landroid/os/Bundle;)V
 * — the root onCreate() every real screen in the app eventually calls up into via its own
 * super.onCreate() chain (BaseActivity -> BaseMaterialActivity -> BaseMaterialDragActivity ->
 * most real activities, confirmed by hand via apktool), making it the one place a
 * per-Activity setup call actually reaches the whole app.
 */
val baseActivityOnCreateFingerprint = fingerprint {
    returns("V")
    parameters("Landroid/os/Bundle;")
    custom { method, classDef ->
        classDef.type == "Lcom/laurencedawson/reddit_sync/ui/activities/BaseActivity;" &&
            method.name == "onCreate"
    }
}

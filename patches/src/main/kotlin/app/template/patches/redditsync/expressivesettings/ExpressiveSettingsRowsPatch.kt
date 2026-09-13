package app.template.patches.redditsync.expressivesettings

import app.morphe.patcher.patch.resourcePatch

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Third step of the ongoing Settings restyle: gives every settings row its own
 * rounded, inset card background instead of an edge-to-edge strip, matching the
 * agreed-on mockup.
 *
 * Confirmed by hand via apktool: the two shared row layouts already patched for icon
 * containers (res/layout/preference_material.xml, used by the generic SyncPreference
 * class everywhere, and res/layout/preference_root.xml, used by the main Settings
 * menu's top-level RootPreference entries) are each a single root LinearLayout whose
 * only current "background" is ?android:selectableItemBackground (the tap ripple, not
 * a fill). Both get a real fill — a new drawable using ?attr/colorSurfaceContainer, a
 * genuine Material 3 tonal role confirmed present in this app's compiled resources —
 * plus margins so each row reads as its own object with a visible gap to its
 * neighbors, while keeping the original ripple by moving it to android:foreground
 * (supported on any View since API 23, this app's minSdk) so tap feedback still shows
 * on top of the new background instead of being replaced by it.
 *
 * Confirmed no central "divider between rows" mechanism exists for ordinary
 * preferences (searched styles.xml for PreferenceGroup/dividerVisibility attributes:
 * no matches) — the only divider mechanism in this app is the explicit
 * DividerPreference element used sparingly as its own row, which margin-separated
 * cards don't conflict with.
 */
val expressiveSettingsRowsPatch = resourcePatch(
    name = "Expressive Settings row cards",
    description = "Gives every settings row its own rounded, inset card background, " +
        "matching a Material 3 Expressive-inspired restyle.",
    default = true,
) {
    dependsOn(expressiveSettingsIconsPatch)

    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        get("res/drawable/aftersync_row_container.xml").writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
                    <corners android:radius="18dp" />
                    <solid android:color="?attr/colorSurfaceContainer" />
                </shape>
            """.trimIndent(),
        )

        document("res/layout/preference_material.xml").use { document ->
            val row = document.documentElement
            row.setAttribute("android:background", "@drawable/aftersync_row_container")
            row.setAttribute("android:foreground", "?android:selectableItemBackground")
            row.setAttribute("android:layout_marginStart", "12.0dp")
            row.setAttribute("android:layout_marginEnd", "12.0dp")
            row.setAttribute("android:layout_marginTop", "3.0dp")
            row.setAttribute("android:layout_marginBottom", "3.0dp")
        }

        document("res/layout/preference_root.xml").use { document ->
            val row = document.documentElement
            row.setAttribute("android:background", "@drawable/aftersync_row_container")
            row.setAttribute("android:foreground", "?android:selectableItemBackground")
            row.setAttribute("android:layout_marginStart", "12.0dp")
            row.setAttribute("android:layout_marginEnd", "12.0dp")
            row.setAttribute("android:layout_marginTop", "3.0dp")
            row.setAttribute("android:layout_marginBottom", "3.0dp")
        }
    }
}

package app.template.patches.redditsync.dynamiccolor.fingerprints

import app.morphe.patcher.fingerprint

/**
 * Matches Lcom/google/android/material/color/DynamicColors;->c()Z ("isDynamicColorAvailable",
 * R8-renamed) — used only to reach that class's classDef so DynamicColorPatch.kt can add a
 * new helper method to it.
 */
val dynamicColorsAvailableFingerprint = fingerprint {
    returns("Z")
    parameters()
    custom { method, classDef ->
        classDef.type == "Lcom/google/android/material/color/DynamicColors;" && method.name == "c"
    }
}

package app.template.patches.redditsync.cleanuprootmenu

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.redditsync.removerestorepurchases.fingerprints.preferencesNewRootFragmentFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Confirmed via a real device crash log: removing "Developer options" in
 * cleanupRootMenuPatch triggered the exact same crash pattern already fixed once for
 * "Restore purchases" (see removeRestorePurchasesSetupPatch.kt) — PreferencesNewRootFragment
 * (Lpa/u0;->C3) has a SEPARATE, earlier force-visibility block for "Developer options"
 * that was missed when checking cleanupRootMenuPatch's safety: it force-shows the
 * preference for a hardcoded ~9-username allowlist of testers, with no null check.
 * That block is real, active code (unlike "Legacy settings", confirmed dead there),
 * so deleting the preference without also removing this lookup crashes Settings on
 * every launch.
 *
 * This patch removes those 43 instructions (anchored on the unique
 * "const p1, 0x7f0a0009" load of @integer/DEVELOPER_OPTIONS) outright — the same
 * technique already used for @integer/PURCHASES in the same method. Everything else
 * in C3() (the WEB_PREFERENCES/MESSAGING force-show blocks and the conditional ULTRA
 * one) is untouched.
 *
 * Depends on cleanupRootMenuPatch (the XML deletion) so selecting either one in Morphe
 * Manager applies both together.
 */
val cleanupRootMenuSetupPatch = bytecodePatch(
    name = "Clean up root settings menu (fix crash)",
    description = "Removes the dead \"Developer options\" visibility-check code left " +
        "behind after removing it from Sync for Reddit's settings menu.",
    default = true,
) {
    dependsOn(cleanupRootMenuPatch)

    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        val method = preferencesNewRootFragmentFingerprint.method
        val implementation = method.implementation!!

        val devOptionsIndex = implementation.instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.CONST &&
                (instruction as? NarrowLiteralInstruction)?.narrowLiteral == 0x7f0a0009
        }

        if (devOptionsIndex == -1) {
            error(
                "Could not find the \"Developer options\" force-visibility setup code in " +
                    "the root preferences fragment. This build's method structure may " +
                    "differ from what was inspected — re-check with apktool.",
            )
        }

        // 43 instructions: load the @integer/DEVELOPER_OPTIONS id, resolve it, convert
        // to a key string, look up the preference, build the 9-username tester
        // allowlist array, compare the current username against it, and call
        // setVisible(...) on the (now null) result.
        repeat(43) { implementation.removeInstruction(devOptionsIndex) }
    }
}

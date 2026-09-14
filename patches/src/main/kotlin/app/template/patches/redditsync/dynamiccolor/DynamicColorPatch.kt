package app.template.patches.redditsync.dynamiccolor

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.redditsync.dynamiccolor.fingerprints.baseActivityOnCreateFingerprint
import app.template.patches.redditsync.dynamiccolor.fingerprints.dynamicColorsAvailableFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Per explicit follow-up request during the "expressive redesign" experiment: rather than
 * hand-picking a fixed accent color/palette, use Android's own Material You dynamic color
 * (the system reads the user's wallpaper and generates a personalized color scheme; apps
 * opt in to have that scheme applied to their Material theme colors automatically).
 *
 * This was never wired up in Sync itself, but confirmed by hand via apktool that the
 * Material Components library it already bundles (`com.google.android.material:material`,
 * the same dependency providing every M3 style this project has touched elsewhere) still
 * contains the real `DynamicColors`/`DynamicColorsOptions`/`ThemeUtils` classes needed to
 * apply it — just never called from anywhere in the app.
 *
 * The "obvious" one-line API — `DynamicColors.applyToActivitiesIfAvailable(Application)`,
 * which registers an ActivityLifecycleCallbacks that themes every activity automatically —
 * was itself stripped by R8 as dead code, along with `DynamicColorsOptions.Builder.build()`
 * and `DynamicColorsOptions`'s own constructor (also unused, also stripped). Reconstructing
 * either of those from scratch would mean rebuilding a multi-field object or a whole new
 * ActivityLifecycleCallbacks implementation. Neither was necessary: `DynamicColors` itself
 * still has both of the two primitives the stripped convenience methods were built from —
 * `c()Z` (checks device/OS eligibility — Android 12+) and the private `b(Context)I` (looks
 * up the correct light/dark theme-overlay resource via the `dynamicColorThemeOverlay`
 * theme attribute, which stock Material3 themes already declare — confirmed present and
 * correctly wired in this app's own styles.xml, so no resource changes were needed either)
 * — plus `ThemeUtils.a(Context, int)V` (applies that theme overlay to both the activity's
 * theme and its already-created decor view, if any). Chaining exactly those three calls is
 * exactly what the stripped `applyToActivityIfAvailable` did internally for the plain
 * "no custom options" case.
 *
 * The new method is added directly to `DynamicColors` itself (not a separate helper class)
 * specifically so it can call the private `b(Context)I` as a same-class call — the normal
 * Java/dex private-access rule (only the declaring class can call a private member) is
 * satisfied automatically this way, with no need to touch `b`'s own access flags. Calling
 * the package-private `ThemeUtils.a(...)` needs no such care since `DynamicColors` already
 * lives in the same `com.google.android.material.color` package.
 *
 * The call site is a single straight-line `invoke-static` inserted as the very first
 * instruction of `BaseActivity.onCreate(Bundle)V`, before that method's own existing
 * `super.onCreate()` call — required ordering, since AppCompatActivity's own onCreate is
 * what actually creates the window/theme this needs to already be applied for. BaseActivity
 * is the common ancestor essentially every real screen's own onCreate() eventually calls up
 * into (BaseActivity -> BaseMaterialActivity -> BaseMaterialDragActivity -> most real
 * activities, confirmed by hand), so this one insertion point covers the whole app for free
 * — the same "find the one shared root" approach already used by centerPostMediaPatch and
 * fixCommentImageSizingPatch elsewhere in this project.
 */
val dynamicColorPatch = bytecodePatch(
    name = "Material You dynamic color (experimental)",
    description = "Experimental: applies Android's wallpaper-based Material You color " +
        "scheme to Sync's own Material theme colors, on devices that support it (Android " +
        "12+), instead of a fixed accent color.",
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        val dynamicColorsClass = dynamicColorsAvailableFingerprint.classDef

        // 2 registers = 1 local + 1 param (the Context to theme).
        val applyIfAvailableDefinition = ImmutableMethod(
            "Lcom/google/android/material/color/DynamicColors;",
            "applyIfAvailable",
            listOf(ImmutableMethodParameter("Landroid/content/Context;", emptySet(), "context")),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(2, emptyList(), emptyList(), emptyList()),
        )
        val applyIfAvailable = MutableMethod(applyIfAvailableDefinition)
        applyIfAvailable.addInstructions(
            """
                invoke-static {}, Lcom/google/android/material/color/DynamicColors;->c()Z
                move-result v0
                if-eqz v0, :done

                invoke-static {p0}, Lcom/google/android/material/color/DynamicColors;->b(Landroid/content/Context;)I
                move-result v0
                if-eqz v0, :done

                invoke-static {p0, v0}, Lcom/google/android/material/color/ThemeUtils;->a(Landroid/content/Context;I)V

                :done
                return-void
            """,
        )
        dynamicColorsClass.directMethods.add(applyIfAvailable)

        val onCreateMethod = baseActivityOnCreateFingerprint.method
        onCreateMethod.addInstructions(
            0,
            "invoke-static {p0}, Lcom/google/android/material/color/DynamicColors;->applyIfAvailable(Landroid/content/Context;)V",
        )
    }
}

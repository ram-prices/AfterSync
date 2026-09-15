package app.template.patches.redditsync.expressivesettings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.redditsync.expressivesettings.fingerprints.rootPreferenceBindFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Part of the "expressive redesign" experiment. Per explicit request, gives each row on
 * Sync's own root Settings screen (res/xml/cat_root.xml, rendered via
 * ui.preferences.custom.RootPreference — confirmed by hand via apktool this is Sync's own
 * genuine settings screen, not one injected by another patch bundle) a distinct colored
 * circular icon badge, matching the reference design's look (a different accent color per
 * row rather than one shared color).
 *
 * Confirmed via RootPreference's own bind method (`S`, Preference.onBindViewHolder,
 * R8-renamed) that the icon's color is NOT a static resource at all: it's forced to a
 * single flat gray/black at runtime via `setImageTintList`, using a value from a shared
 * "default icon/text color" helper (`Lj9/g;->a`) — the same helper used for the row's own
 * title color a few lines earlier. No per-row static resource edit could have changed this;
 * only rewriting that runtime call does.
 *
 * Rather than add a genuinely new custom XML attribute for per-row color (which would mean
 * editing the compiled styleable array `RootPreference` already reads via
 * `obtainStyledAttributes` — risky surgery on a shared attribute table whose existing
 * indices several other reads in the same constructor already depend on), this derives
 * each row's color from `h0` — the existing `preference_ref` integer field every row
 * already has one of (each row routes to a different settings sub-screen via this value),
 * already unique per row and already parsed in the constructor. A new static helper,
 * `paletteColor(I)I`, added to RootPreference itself, maps it through a fixed 8-color
 * palette via `h0 % 8`, entirely within its own fresh method (its own register file, no
 * branch spliced into the existing bind method — the same safe pattern used throughout
 * this project after two past VerifyError crashes from doing that directly).
 *
 * The bind method's existing icon-tint block (get context -> shared gray/black color ->
 * ColorStateList -> setImageTintList on the icon ImageView, `l0`) is replaced in place —
 * same instruction count class, no new branch — with: the icon itself tinted a fixed
 * opaque white (matching the reference design's white glyphs on colored circles), and the
 * icon's existing FrameLayout wrapper (`k0`, already present — it wraps the icon area, no
 * layout change needed) given a freshly-built oval `GradientDrawable` background tinted
 * with the new per-row palette color. Building the oval via `GradientDrawable` at runtime
 * (`setShape`/`setColor`) avoids needing a new drawable resource file at all — no
 * resource-ID-from-smali problem to solve, unlike an XML-defined drawable would need.
 */
val expressiveSettingsPatch = bytecodePatch(
    name = "Expressive settings icons (experimental)",
    description = "Experimental: gives each row on Sync's root Settings screen a distinct " +
        "colored circular icon badge (derived from its existing navigation-target id) " +
        "instead of a flat gray icon, replacing a runtime color override that no static " +
        "resource edit could reach.",
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        val rootPreferenceClass = rootPreferenceBindFingerprint.classDef
        val bindMethod = rootPreferenceBindFingerprint.method

        // Fresh helper: h0 (the existing preference_ref field) -> one of 8 accent colors.
        // 4 registers = 3 locals (v0-v2) + 1 param (the preferenceRef int).
        val paletteColorImpl = ImmutableMethodImplementation(4, emptyList(), emptyList(), emptyList())
        val paletteColorDefinition = ImmutableMethod(
            "Lcom/laurencedawson/reddit_sync/ui/preferences/custom/RootPreference;",
            "paletteColor",
            listOf(ImmutableMethodParameter("I", emptySet(), "preferenceRef")),
            "I",
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            paletteColorImpl,
        )
        val paletteColor = MutableMethod(paletteColorDefinition)
        paletteColor.addInstructions(
            """
                const/16 v0, 0x8
                rem-int/2addr p0, v0
                if-gez p0, :non_negative
                add-int/2addr p0, v0

                :non_negative
                new-array v0, v0, [I
                const v1, 0xff5c6bc0
                const/4 v2, 0x0
                aput v1, v0, v2
                const v1, 0xff546e7a
                const/4 v2, 0x1
                aput v1, v0, v2
                const v1, 0xff9575cd
                const/4 v2, 0x2
                aput v1, v0, v2
                const v1, 0xff7e57c2
                const/4 v2, 0x3
                aput v1, v0, v2
                const v1, 0xffe53935
                const/4 v2, 0x4
                aput v1, v0, v2
                const v1, 0xff7986cb
                const/4 v2, 0x5
                aput v1, v0, v2
                const v1, 0xff26a69a
                const/4 v2, 0x6
                aput v1, v0, v2
                const v1, 0xffef6c00
                const/4 v2, 0x7
                aput v1, v0, v2
                aget v0, v0, p0
                return v0
            """,
        )
        rootPreferenceClass.directMethods.add(paletteColor)

        // Anchor: the one setImageTintList call in this method (the icon's existing tint
        // logic) — unique, confirmed by hand via apktool. The block being replaced is the
        // 8 instructions ending here: get context, call the shared gray/black color
        // helper, wrap it, and apply it — starting 7 instructions earlier.
        val setImageTintListIndex = bindMethod.implementation!!.instructions.indexOfFirst { instruction ->
            (instruction as? ReferenceInstruction)?.reference?.let { ref ->
                ref is MethodReference &&
                    ref.definingClass == "Landroid/widget/ImageView;" &&
                    ref.name == "setImageTintList"
            } == true
        }
        if (setImageTintListIndex == -1) {
            error(
                "Could not find the icon's setImageTintList(...) call in " +
                    "RootPreference.S(...). This build's method structure may differ from " +
                    "what was inspected — re-check with apktool.",
            )
        }

        val blockStart = setImageTintListIndex - 7
        bindMethod.removeInstructions(blockStart, 8)
        bindMethod.addInstructions(
            blockStart,
            """
                iget-object v1, p0, Lcom/laurencedawson/reddit_sync/ui/preferences/custom/RootPreference;->l0:Landroid/widget/ImageView;
                const v0, -0x1
                invoke-static {v0}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;
                move-result-object v0
                invoke-virtual {v1, v0}, Landroid/widget/ImageView;->setImageTintList(Landroid/content/res/ColorStateList;)V

                iget v0, p0, Lcom/laurencedawson/reddit_sync/ui/preferences/custom/RootPreference;->h0:I
                invoke-static {v0}, Lcom/laurencedawson/reddit_sync/ui/preferences/custom/RootPreference;->paletteColor(I)I
                move-result v0

                new-instance v1, Landroid/graphics/drawable/GradientDrawable;
                invoke-direct {v1}, Landroid/graphics/drawable/GradientDrawable;-><init>()V
                sget-object v2, Landroid/graphics/drawable/GradientDrawable${'$'}Shape;->OVAL:Landroid/graphics/drawable/GradientDrawable${'$'}Shape;
                invoke-virtual {v1, v2}, Landroid/graphics/drawable/GradientDrawable;->setShape(Landroid/graphics/drawable/GradientDrawable${'$'}Shape;)V
                invoke-virtual {v1, v0}, Landroid/graphics/drawable/GradientDrawable;->setColor(I)V

                iget-object v0, p0, Lcom/laurencedawson/reddit_sync/ui/preferences/custom/RootPreference;->k0:Landroid/widget/FrameLayout;
                invoke-virtual {v0, v1}, Landroid/view/View;->setBackground(Landroid/graphics/drawable/Drawable;)V
            """,
        )
    }
}

package app.template.patches.redditsync.fixcommentimagesizing

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.redditsync.fixcommentimagesizing.fingerprints.emoteImageSpanDrawFingerprint
import app.template.patches.redditsync.fixcommentimagesizing.fingerprints.syncHtmlToSpannedImageTagFingerprint
import app.template.patches.redditsync.fixpreviewimages.fingerprints.syncHtmlToSpannedConverterFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Per explicit request: inline images/GIFs in comments always render stretched to a
 * square, at the full width of the comment. Confirmed by hand via apktool this is two
 * separate, compounding issues in EmoteImageSpan (Lnb/c;, "EmoteImageSpan.java"), the
 * ReplacementSpan used to draw every inline comment image (subreddit emotes,
 * preview.redd.it images, Giphy embeds):
 *
 * 1. Lnb/c;->draw(...) unconditionally calls `Drawable.setBounds(0, 0, s, t)` — where s/t
 *    are the span's fixed box width/height — for both the static-bitmap and animated-GIF
 *    cases, stretching the actual loaded image to exactly fill that box regardless of its
 *    real aspect ratio. This patch adds a new static helper, Lnb/c;->w4
 *    (Landroid/graphics/drawable/Drawable;II)V, that reads the drawable's real
 *    getIntrinsicWidth()/getIntrinsicHeight(), scales it down to fit within the s×t box
 *    while preserving its aspect ratio, and centers it (letterboxed) within that box —
 *    then replaces both of draw()'s setBounds(...) calls that size the actual *loaded*
 *    image (the static-bitmap branch and the animated-GIF branch) with a call to this
 *    helper instead. The third setBounds(...) call in this method, which sizes a generic
 *    loading-placeholder icon (RedditApplication.P) shown before the real image loads, is
 *    deliberately left untouched — a fixed-size placeholder icon doesn't need aspect-ratio
 *    awareness the way a real photo does.
 *
 *    This does NOT make every inline image's reserved box genuinely aspect-correct — the
 *    span's box (s×t) itself is unaffected, only what happens *inside* that box when
 *    drawing. Making the box itself aspect-correct (rather than just not-stretched) would
 *    mean recovering the image's real dimensions before it's ever sized (e.g. an async
 *    re-layout after Glide's callback fires, or a network probe), which is real new
 *    architecture with its own failure modes — deliberately out of scope here. Letterboxing
 *    at draw time already eliminates the actual visual distortion (the dominant part of the
 *    complaint) without touching that riskier territory.
 *
 * 2. Lnc/d;->y(...) (the &lt;img&gt; HTML-tag handler, used for subreddit "emote" images
 *    embedded directly in a comment body) builds every such image with a fixed 42dp-square
 *    box — small and, combined with #1's stretching, especially noticeable. Bumped to
 *    120dp, matching the reference box size this same class's Giphy-embed code
 *    (Lnc/d;->y, the giphy%7C branch) already uses elsewhere for consistency.
 *
 * 3. Lnc/d;->e(...) (SyncHtmlToSpannedConverter's main method, shared with
 *    fixPreviewImagesPatch.kt) sizes preview.redd.it comment images to the FULL available
 *    column width (`Lnc/a;->e`, confirmed by hand to be the rendering container's pixel
 *    width) — by far the biggest images in a comment, exactly matching the "too big,
 *    sticker-sized would be nicer" follow-up request once #1 above stopped them being
 *    stretched. Fixed by clamping that width to 120dp (same reference size as #2) via
 *    `Math.min(availableWidth, 120dp)` right after it's read, before any of the existing
 *    (unmodified) proportional-height math runs on it — a value it already knows how to
 *    handle correctly for any width, having previously only ever seen the full column
 *    width. Anchored on the specific `iget p1, p1, Lnc/a;->e:I` instruction (destination
 *    and source register both `p1`, unique among several other reads of the same field
 *    elsewhere in this method for unrelated purposes) rather than a fixed offset from any
 *    string constant, since fixPreviewImagesPatch.kt removes the nearest useful string
 *    anchor ("&height") in this same region — this way the two patches' edits stay
 *    independent of whichever one the patcher happens to run first. Uses the temporarily-free
 *    v0 register (immediately overwritten by the original, unmodified next instruction
 *    either way) rather than a new local, avoiding this method's own prior crash history
 *    with labeled-branch insertions (see fixPreviewImagesPatch.kt) by using
 *    `Math.min(II)I` instead of a branch.
 */
val fixCommentImageSizingPatch = bytecodePatch(
    name = "Fix inline comment image sizing",
    description = "Stops inline images and GIFs in comments/posts from being stretched to " +
        "a square, and gives subreddit emote images a more generous box size.",
    default = true,
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        // 1. Letterbox the loaded drawable within its box instead of stretching it.
        val emoteImageSpanClass = emoteImageSpanDrawFingerprint.classDef
        val drawMethod = emoteImageSpanDrawFingerprint.method
        val drawImpl = drawMethod.implementation!!

        val setBoundsIndices = drawImpl.instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                    it.definingClass == "Landroid/graphics/drawable/Drawable;" && it.name == "setBounds"
                } == true
        }.map { it.index }

        if (setBoundsIndices.size != 3) {
            error(
                "Expected 3 Drawable.setBounds(...) calls in EmoteImageSpan.draw() " +
                    "(placeholder icon, static bitmap, animated GIF), found " +
                    "${setBoundsIndices.size}. This build's method structure may differ " +
                    "from what was inspected — re-check with apktool.",
            )
        }

        val helperImpl = ImmutableMethodImplementation(11, emptyList(), emptyList(), emptyList())
        val helperDefinition = ImmutableMethod(
            "Lnb/c;",
            "w4",
            listOf(
                ImmutableMethodParameter("Landroid/graphics/drawable/Drawable;", emptySet(), "drawable"),
                ImmutableMethodParameter("I", emptySet(), "boxWidth"),
                ImmutableMethodParameter("I", emptySet(), "boxHeight"),
            ),
            "V",
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            helperImpl,
        )
        val aspectFitBounds = MutableMethod(helperDefinition)
        aspectFitBounds.addInstructions(
            """
                invoke-virtual {p0}, Landroid/graphics/drawable/Drawable;->getIntrinsicWidth()I
                move-result v0
                invoke-virtual {p0}, Landroid/graphics/drawable/Drawable;->getIntrinsicHeight()I
                move-result v1
                if-lez v0, :fallback
                if-lez v1, :fallback

                int-to-float v2, p1
                int-to-float v3, v0
                div-float/2addr v2, v3

                int-to-float v3, p2
                int-to-float v4, v1
                div-float/2addr v3, v4

                cmpg-float v4, v2, v3
                if-ltz v4, :use_scale
                move v2, v3
                :use_scale

                int-to-float v3, v0
                mul-float/2addr v3, v2
                float-to-int v3, v3

                int-to-float v4, v1
                mul-float/2addr v4, v2
                float-to-int v4, v4

                sub-int v5, p1, v3
                div-int/lit8 v5, v5, 0x2

                sub-int v6, p2, v4
                div-int/lit8 v6, v6, 0x2

                add-int v7, v5, v3
                add-int v3, v6, v4

                invoke-virtual {p0, v5, v6, v7, v3}, Landroid/graphics/drawable/Drawable;->setBounds(IIII)V
                return-void

                :fallback
                const/4 v0, 0x0
                invoke-virtual {p0, v0, v0, p1, p2}, Landroid/graphics/drawable/Drawable;->setBounds(IIII)V
                return-void
            """,
        )
        emoteImageSpanClass.directMethods.add(aspectFitBounds)

        // Replace the 2nd (static bitmap) and 3rd (animated GIF) setBounds(...) calls —
        // in descending index order so replacing one doesn't shift the other's index.
        // Both call sites use the identical register pattern: {drawable, 0, 0, s, t}.
        listOf(setBoundsIndices[2], setBoundsIndices[1]).forEach { index ->
            drawMethod.replaceInstruction(index, "invoke-static {p2, p4, p5}, Lnb/c;->w4(Landroid/graphics/drawable/Drawable;II)V")
        }

        // 2. Bump the fixed emote-image box from 42dp to 120dp.
        val imageTagMethod = syncHtmlToSpannedImageTagFingerprint.method
        val imageTagImpl = imageTagMethod.implementation!!

        val boxSizeIndices = imageTagImpl.instructions.withIndex().filter { (_, instruction) ->
            (instruction as? NarrowLiteralInstruction)?.narrowLiteral == 0x2a
        }.map { it.index }

        if (boxSizeIndices.size != 1) {
            error(
                "Expected exactly 1 occurrence of the 42dp emote-image box size constant " +
                    "in SyncHtmlToSpannedConverter's <img> tag handler, found " +
                    "${boxSizeIndices.size}. This build's method structure may differ from " +
                    "what was inspected — re-check with apktool.",
            )
        }
        imageTagMethod.replaceInstruction(boxSizeIndices.single(), "const/16 v2, 0x78")

        // 3. Cap preview.redd.it comment images to a sticker-sized 120dp box instead of
        // the full available column width.
        val htmlMethod = syncHtmlToSpannedConverterFingerprint.method
        val htmlImpl = htmlMethod.implementation!!

        val availableWidthIndex = htmlImpl.instructions.indexOfFirst { instruction ->
            (instruction as? Instruction22c)?.let {
                it.registerA == it.registerB &&
                    (it.reference as? FieldReference)?.let { field ->
                        field.definingClass == "Lnc/a;" && field.name == "e"
                    } == true
            } == true
        }

        if (availableWidthIndex == -1) {
            error(
                "Could not find the self-referential \"iget p1, p1, Lnc/a;->e:I\" " +
                    "available-width read in SyncHtmlToSpannedConverter's preview.redd.it " +
                    "handling. This build's method structure may differ from what was " +
                    "inspected — re-check with apktool.",
            )
        }

        htmlMethod.addInstructions(
            availableWidthIndex + 1,
            """
                const/16 v0, 0x78
                invoke-static {v0}, Lt7/f0;->c(I)I
                move-result v0
                invoke-static {p1, v0}, Ljava/lang/Math;->min(II)I
                move-result p1
            """,
        )
    }
}

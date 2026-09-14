package app.template.patches.redditsync.fixcommentimagesizing

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
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
 *    (Landroid/graphics/drawable/Drawable;IILandroid/graphics/Canvas;)V, that reads the
 *    drawable's real getIntrinsicWidth()/getIntrinsicHeight(), scales it up to COVER the
 *    s×t box while preserving its aspect ratio (center-crop, not letterbox — the scale
 *    factor picks the larger of the two axis ratios instead of the smaller), clips the
 *    canvas to exactly the s×t box, then centers the now-oversized-on-one-axis drawable
 *    within it — then replaces both of draw()'s setBounds(...) calls that size the actual
 *    *loaded* image (the static-bitmap branch and the animated-GIF branch) with a call to
 *    this helper instead. The third setBounds(...) call in this method, which sizes a
 *    generic loading-placeholder icon (RedditApplication.P) shown before the real image
 *    loads, is deliberately left untouched — a fixed-size placeholder icon doesn't need
 *    aspect-ratio awareness the way a real photo does.
 *
 *    The clip is safe to leave unrestored inside the helper: draw() already wraps every
 *    setBounds+draw() call pair in its own canvas.save()/canvas.translate()/canvas.restore()
 *    (confirmed by hand via apktool — register p1 holds the Canvas throughout draw() and is
 *    never reassigned), and that restore() already runs immediately after the drawable's
 *    own draw(Canvas) call — so the outer save/restore this method already had scopes the
 *    clip to exactly one drawable's draw call with no new save/restore pair needed.
 *
 *    Originally (v1.12.1-era) this helper fit the image INSIDE the box (letterboxed),
 *    leaving empty "pillarbox" margins on non-matching-aspect-ratio media — the box itself
 *    (s×t) was never touched, so a landscape GIF in a square-ish box still showed visible
 *    empty space to either side. Per explicit follow-up request ("is it possible to
 *    somehow not have those margins?"), this crops instead: the image fully covers the box
 *    with no visible margin, at the cost of trimming its longer axis to fit. This still does
 *    NOT make the box itself aspect-correct — recovering the image's real dimensions before
 *    it's ever sized (e.g. an async re-layout after Glide's callback fires, or a network
 *    probe) is real new architecture with its own failure modes, deliberately out of scope
 *    here — but center-cropping inside a fixed box is the standard, low-risk way to
 *    eliminate visible margins without that.
 *
 * 2. Lnc/d;->y(...) (the &lt;img&gt; HTML-tag handler, used for subreddit "emote" images
 *    embedded directly in a comment body) builds every such image with a fixed 42dp-square
 *    box — small and, combined with #1's stretching, especially noticeable. Bumped to
 *    160dp. Left square (rather than widened like #4 below) deliberately: this sits deep
 *    inside a large, mostly-unexamined 8-local method, and safely widening it would need a
 *    new scratch register whose liveness across the rest of that method isn't fully
 *    understood — not worth the risk for what's a rarer code path (literal &lt;img&gt; tags
 *    in comment HTML) than the Giphy and preview.redd.it paths below.
 *
 * 3. Lnc/d;->e(...) (SyncHtmlToSpannedConverter's main method, shared with
 *    fixPreviewImagesPatch.kt) sizes preview.redd.it comment images to the FULL available
 *    column width (`Lnc/a;->e`, confirmed by hand to be the rendering container's pixel
 *    width) — by far the biggest images in a comment, exactly matching the "too big,
 *    sticker-sized would be nicer" follow-up request once #1 above stopped them being
 *    stretched. Fixed by clamping that width to 160dp via
 *    `Math.min(availableWidth, 160dp)` right after it's read, before any of the existing
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
 *
 * 4. Lnc/d;->a(Loc/c;Ljava/lang/String;)V is SyncHtmlToSpannedConverter's fallback for a
 *    `[gif](giphy|id)` embed when Reddit's `media_metadata` for it has no usable size data
 *    (confirmed by hand from a real example: Reddit's own JSON reports
 *    `"media_metadata": {"giphy|<id>": {"status": "invalid"}}` — no "s" size object at
 *    all — for a Giphy asset that Giphy itself still serves fine at
 *    https://media.giphy.com/media/<id>/giphy.mp4, i.e. Reddit's classic native
 *    comment-Giphy integration has bit-rotted server-side, not something this patch can
 *    fix at the source). The original fallback builds a static, non-animated
 *    100px-wide thumbnail wrapped in a 56dp icon-and-text "link chip"
 *    (Lnb/d;/InlineImageSpan) — never an actual inline GIF. Replaces that with a real
 *    animated embed (Lnb/c;/EmoteImageSpan, the same span class every other inline image
 *    in this file uses) at a fixed 200dp × 112dp (~16:9) box — wide rather than square,
 *    per explicit follow-up request, since most reaction GIFs/videos are landscape-shaped
 *    and a square box left visible empty margins around them even once letterboxing (#1)
 *    stopped the image itself from being stretched — pointed at the higher-res
 *    media1.giphy.com/.../200.gif URL the WITH-dimensions branch of this same method
 *    already uses. Since the real dimensions are still unknown, the box can't be made
 *    genuinely aspect-correct the way the with-dimensions case is. This is a full method
 *    body replacement built fresh via ImmutableMethod/ImmutableMethodImplementation with
 *    an explicit 6-register count (4 locals + 2 parameter registers, one more scratch
 *    register than the original/previous version needed for the two-int Lnb/c;
 *    constructor) — see the register-count pitfall documented in
 *    removeSyncUltraSetupPatch.kt for why this can't just be bumped in place.
 */
val fixCommentImageSizingPatch = bytecodePatch(
    name = "Fix inline comment image sizing",
    description = "Stops inline images and GIFs in comments/posts from being stretched to " +
        "a square, caps their size, and gives Giphy embeds a real animated fallback " +
        "instead of a static link chip when Reddit's own size metadata for them is missing.",
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

        // 12 registers = 8 locals (v0-v7, unchanged from the fit-inside version) + 4
        // params (drawable, boxWidth, boxHeight, and the new trailing canvas param) — one
        // more than before since only a param was added, no new scratch register.
        val helperImpl = ImmutableMethodImplementation(12, emptyList(), emptyList(), emptyList())
        val helperDefinition = ImmutableMethod(
            "Lnb/c;",
            "w4",
            listOf(
                ImmutableMethodParameter("Landroid/graphics/drawable/Drawable;", emptySet(), "drawable"),
                ImmutableMethodParameter("I", emptySet(), "boxWidth"),
                ImmutableMethodParameter("I", emptySet(), "boxHeight"),
                ImmutableMethodParameter("Landroid/graphics/Canvas;", emptySet(), "canvas"),
            ),
            "V",
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            helperImpl,
        )
        val aspectFillBounds = MutableMethod(helperDefinition)
        aspectFillBounds.addInstructions(
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
                if-gtz v4, :use_scale
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

                const/4 v0, 0x0
                invoke-virtual {p3, v0, v0, p1, p2}, Landroid/graphics/Canvas;->clipRect(IIII)Z

                invoke-virtual {p0, v5, v6, v7, v3}, Landroid/graphics/drawable/Drawable;->setBounds(IIII)V
                return-void

                :fallback
                const/4 v0, 0x0
                invoke-virtual {p0, v0, v0, p1, p2}, Landroid/graphics/drawable/Drawable;->setBounds(IIII)V
                return-void
            """,
        )
        emoteImageSpanClass.directMethods.add(aspectFillBounds)

        // Replace the 2nd (static bitmap) and 3rd (animated GIF) setBounds(...) calls —
        // in descending index order so replacing one doesn't shift the other's index.
        // Both call sites use the identical register pattern: {drawable, 0, 0, s, t}, with
        // the Canvas sitting unchanged in p1 throughout draw() (confirmed by hand via
        // apktool), so it can be passed straight through as the helper's trailing arg.
        listOf(setBoundsIndices[2], setBoundsIndices[1]).forEach { index ->
            drawMethod.replaceInstruction(
                index,
                "invoke-static {p2, p4, p5, p1}, Lnb/c;->w4(Landroid/graphics/drawable/Drawable;IILandroid/graphics/Canvas;)V",
            )
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
        imageTagMethod.replaceInstruction(boxSizeIndices.single(), "const/16 v2, 0xa0")

        // 3. Cap preview.redd.it comment images to a sticker-sized 160dp box instead of
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
                const/16 v0, 0xa0
                invoke-static {v0}, Lt7/f0;->c(I)I
                move-result v0
                invoke-static {p1, v0}, Ljava/lang/Math;->min(II)I
                move-result p1
            """,
        )

        // 4. Give Giphy embeds a real animated fallback when Reddit's own metadata for
        // them is missing/invalid, instead of a tiny static-thumbnail link chip. Uses a
        // wide (200dp × 112dp, ~16:9) box instead of a square one, per explicit request —
        // most reaction images/GIFs/videos are landscape-shaped, so this avoids the empty
        // pillar-box margins a square box leaves around them (confirmed by hand: letterboxing
        // from #1 above only avoids stretching, it doesn't stop the box itself from being
        // visibly bigger than a landscape image actually needs). Rebuilt via
        // ImmutableMethod/ImmutableMethodImplementation with an explicit 6-register count
        // (4 locals + 2 parameter registers) rather than reusing the previous version's
        // 5-register layout, since the two-int Lnb/c; constructor needs one more scratch
        // register than the single-int one did.
        val htmlConverterClass = syncHtmlToSpannedConverterFingerprint.classDef
        val oldGiphyNoDimsFallback = htmlConverterClass.directMethods
            .firstOrNull { it.name == "a" && it.parameterTypes.size == 2 }
            ?: error(
                "Could not find SyncHtmlToSpannedConverter's no-dimensions Giphy fallback " +
                    "method (Lnc/d;->a). This build's method structure may differ from " +
                    "what was inspected — re-check with apktool.",
            )
        htmlConverterClass.directMethods.remove(oldGiphyNoDimsFallback)

        val giphyNoDimsFallbackDefinition = ImmutableMethod(
            "Lnc/d;",
            "a",
            listOf(
                ImmutableMethodParameter("Loc/c;", emptySet(), "converter"),
                ImmutableMethodParameter("Ljava/lang/String;", emptySet(), "giphyId"),
            ),
            "V",
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(6, emptyList(), emptyList(), emptyList()),
        )
        val giphyNoDimsFallback = MutableMethod(giphyNoDimsFallbackDefinition)
        giphyNoDimsFallback.addInstructions(
            """
                new-instance v0, Ljava/lang/StringBuilder;
                invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                const-string v1, "https://media1.giphy.com/media/"
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                const-string v1, "/200.gif"
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object p1

                const/16 v0, 0xc8
                invoke-static {v0}, Lt7/f0;->c(I)I
                move-result v0

                const/16 v1, 0x70
                invoke-static {v1}, Lt7/f0;->c(I)I
                move-result v1

                const/4 v2, 0x2
                new-array v2, v2, [Ljava/lang/Object;

                new-instance v3, Lnb/c;
                invoke-direct {v3, p1, v0, v1}, Lnb/c;-><init>(Ljava/lang/String;II)V
                const/4 v0, 0x0
                aput-object v3, v2, v0

                new-instance v3, Lmb/d;
                invoke-direct {v3, p1}, Lmb/d;-><init>(Ljava/lang/String;)V
                const/4 v0, 0x1
                aput-object v3, v2, v0

                const-string v0, "￼"
                invoke-virtual {p0, v0, v2}, Loc/c;->c(Ljava/lang/String;[Ljava/lang/Object;)V

                const-string v0, "\n"
                invoke-virtual {p0, v0}, Loc/c;->b(Ljava/lang/CharSequence;)V

                return-void
            """,
        )
        htmlConverterClass.directMethods.add(giphyNoDimsFallback)
    }
}

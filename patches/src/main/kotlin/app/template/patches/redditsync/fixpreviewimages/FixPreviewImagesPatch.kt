package app.template.patches.redditsync.fixpreviewimages

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.redditsync.fixpreviewimages.fingerprints.syncHtmlToSpannedConverterFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * When a user embeds an image directly in a comment/post body, Reddit hosts it at a URL
 * like `https://preview.redd.it/<id>.png?width=1062&format=png&auto=webp&s=<hash>` — with
 * a `width` query parameter but, confirmed from real examples, never a `height` one.
 *
 * Confirmed by hand from real smali (via apktool): SyncHtmlToSpannedConverter's URL
 * classifier only treats a `preview.redd.it` URL as an inline-embeddable image if it
 * contains all three of "preview.redd.it", "width", AND "height" as substrings. Since
 * real preview.redd.it comment-image URLs never contain "height", every one of them
 * fails this gate and falls through to the generic "unrecognized link" path — which is
 * what renders the raw URL as plain text with a footnote marker, and (since nothing
 * downstream ever recognizes it as the image it actually is) can also end up pulling in
 * an unrelated website-preview card for the bare URL instead of the image itself.
 *
 * This patch removes the "height" substring requirement so width-only preview.redd.it
 * URLs still qualify. An earlier version of this patch (published as v1.4.0, since
 * reverted in v1.4.1) additionally tried to keep parsing a real "height" query parameter
 * when present, falling back only when it was absent, using an inserted conditional
 * branch — but mixing a low-level instruction removal after a higher-level labeled-branch
 * insertion left a stale jump target, and ART's verifier rejected the entire class
 * outright (VerifyError, confirmed via a real on-device crash log), crashing the app
 * almost immediately since this class renders the very first screen's post previews.
 *
 * This version avoids that failure mode entirely by never inserting a branch: it
 * unconditionally reuses the already-parsed `width` value as the height too (a 1:1
 * aspect-ratio guess), which matches every real-world preview.redd.it comment-image URL
 * (none of which carry a real height value anyway) and needs only a single straight-line
 * instruction in place of the old height lookup — no labels, no jumps, nothing for a
 * later edit to invalidate.
 *
 * A second, separate hardcoded assumption in the same block also needed fixing (found
 * via a real device log showing Sync's own "failed to parse this content" fallback,
 * confirming a caught runtime exception rather than a verifier-level failure this time):
 * right after the height fix above, the method computes
 * `url.substring(0, url.indexOf("&height"))` to build the final display URL by
 * truncating everything from "&height" onward. Real preview.redd.it URLs *embedded in a
 * comment* never contain "&height" (confirmed by hand from several real examples), so
 * `indexOf` returns -1 and `substring(0, -1)` throws a StringIndexOutOfBoundsException.
 *
 * A first attempt at this (v1.5.1) fixed the crash by searching for the first "&"
 * generally instead of "&height" specifically — that stopped the exception, but
 * confirmed on-device that it silently broke image loading itself: real preview.redd.it
 * URLs put the required signature ("&...&s=<hash>") after width, so truncating at the
 * first "&" strips it, and Reddit's CDN needs that signature to actually serve the
 * image (an empty reserved space where the image should be, no error, is exactly what a
 * failed/unauthorized image load looks like).
 *
 * A second attempt (v1.13.0-era, alongside fixCommentImageSizingPatch/
 * fixPostBodyImagesPatch) removed the truncation entirely instead of relocating it, on
 * the assumption that real preview.redd.it URLs never contain "&height" at all — true
 * for every comment example checked, but confirmed FALSE for post-body-embedded images
 * by a real device log + curl test: a post's embedded image URL can genuinely carry a
 * trailing "&height=<n>" (e.g. "...&s=<hash>&height=2160"), and leaving that trailing
 * param in place made Reddit's CDN 403 the request — while the *same* image loaded fine
 * once this patch was disabled (restoring the original truncate-at-"&height" behavior,
 * which is a no-op for comments but strips the trailing param for posts that have it).
 *
 * This version keeps the untouched-URL behavior for the case it was actually needed for
 * (comments, where "&height" is genuinely absent) while restoring the truncation for the
 * case that turned out to still need it (posts, where "&height" is genuinely present and
 * apparently must be stripped for Reddit's CDN to accept the request). The signature
 * ("&s=<hash>") always precedes "&height" in every real URL seen so far, so truncating
 * there never touches it — the same reasoning the v1.5.1 first-"&" attempt got wrong.
 *
 * A first attempt at THIS fix (also v1.19.0, reverted within the same version bump before
 * release) spliced an `if-ltz` branch directly into this existing giant method at the
 * "&height" call site — and immediately reproduced the exact failure class this method's
 * own history already warned about (see the v1.4.0 note above): a real device crash log
 * showed `VerifyError: ... type Conflict unexpected as arg to if-eqz/if-nez` in this exact
 * method, at an offset consistent with that inserted branch. Splicing a NEW branch into
 * this method's existing, heavily hand-optimized register/type layout is fragile even
 * when the branch itself looks correct in isolation — the surrounding method's already-
 * inferred register types don't necessarily tolerate a new control-flow join point.
 *
 * This version avoids that entirely by moving the conditional logic into a brand-new,
 * independent static helper method (`truncateAtHeightParam`, added to this same class)
 * instead of splicing anything into the existing method. A freshly-built method defines
 * its own register types from scratch, so a branch inside IT carries none of the
 * surrounding giant method's fragility — this is the same reasoning already validated by
 * every from-scratch helper elsewhere in this project (e.g. EmoteImageSpan's `w4` in
 * fixCommentImageSizingPatch.kt, which also branches internally with no issue). The call
 * site inside the giant method shrinks to a single `invoke-static` + `move-result-object`
 * pair touching only the one register (the URL string) already known safe to reassign —
 * no new registers, no new branches, nothing for the existing method's type inference to
 * trip over.
 *
 * All edits are anchored on the "preview.redd.it" string constant, which appears
 * exactly once in this method (confirmed by hand) — everything else this ~4500-line
 * method does (every other URL type it recognizes, the video/gif/imgur/gallery handling,
 * the website-preview-card logic for genuinely unrecognized links) is untouched.
 */
val fixPreviewImagesPatch = bytecodePatch(
    name = "Fix preview.redd.it comment/post images",
    description = "Fixes comment and post images hosted on preview.redd.it (Reddit's " +
        "current inline-image hosting) showing as a raw link instead of embedding " +
        "properly in Sync for Reddit.",
    default = true,
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        val method = syncHtmlToSpannedConverterFingerprint.method
        val implementation = method.implementation!!

        val base = implementation.instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.CONST_STRING &&
                ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                    ?.string == "preview.redd.it"
        }

        if (base == -1) {
            error(
                "Could not find the \"preview.redd.it\" inline-image detection code in " +
                    "SyncHtmlToSpannedConverter. This build's method structure may differ " +
                    "from what was inspected — re-check with apktool.",
            )
        }

        // Remove the "height".contains() gate first (instructions 11-13 relative to the
        // "preview.redd.it" const-string, 0-indexed offsets base+10/+11/+12). The
        // "height" string constant itself (instruction 10, offset base+9) is left in
        // place — it becomes unused dead code, which is harmless, rather than risk
        // touching anything else.
        repeat(3) { implementation.removeInstruction(base + 10) }

        // The height lookup+parse (originally instructions 22-25, offsets base+21
        // through base+24) has now shifted down by 3 to base+18 through base+21.
        // Replace all 4 with one branch-free instruction: reuse the already-parsed
        // width value (register v1) as the height too, instead of ever reading a
        // "height" query parameter that real preview.redd.it URLs don't send.
        repeat(4) { implementation.removeInstruction(base + 18) }
        method.addInstructions(base + 18, "move p2, v1")

        // Searched fresh (rather than computed as a further fixed offset from "base")
        // since it comes after the edits above and this string constant is unique in
        // the method on its own, so there's no ambiguity risk in finding it this way.
        val ampHeightIndex = implementation.instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.CONST_STRING &&
                ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                    ?.string == "&height"
        }

        if (ampHeightIndex == -1) {
            error(
                "Could not find the \"&height\" URL-truncation code in " +
                    "SyncHtmlToSpannedConverter. This build's method structure may differ " +
                    "from what was inspected — re-check with apktool.",
            )
        }

        // New, fully independent static helper: does the same guarded truncation, but
        // with its own fresh register allocation so the branch inside it can't conflict
        // with the giant existing method's own inferred register types.
        val truncateHelperImpl = ImmutableMethodImplementation(3, emptyList(), emptyList(), emptyList())
        val truncateHelperDefinition = ImmutableMethod(
            "Lnc/d;",
            "truncateAtHeightParam",
            listOf(ImmutableMethodParameter("Ljava/lang/String;", emptySet(), "url")),
            "Ljava/lang/String;",
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            truncateHelperImpl,
        )
        val truncateHelper = MutableMethod(truncateHelperDefinition)
        truncateHelper.addInstructions(
            """
                const-string v0, "&height"
                invoke-virtual {p0, v0}, Ljava/lang/String;->indexOf(Ljava/lang/String;)I
                move-result v0
                if-ltz v0, :no_height_param
                const/4 v1, 0x0
                invoke-virtual {p0, v1, v0}, Ljava/lang/String;->substring(II)Ljava/lang/String;
                move-result-object p0
                :no_height_param
                return-object p0
            """,
        )
        syncHtmlToSpannedConverterFingerprint.classDef.directMethods.add(truncateHelper)

        // Replace the old unconditional 5-instruction truncation block with a call to
        // the helper above — touches only v2 (the URL string, already known safe to
        // reassign here), nothing else in this method changes.
        repeat(5) { implementation.removeInstruction(ampHeightIndex) }
        method.addInstructions(
            ampHeightIndex,
            """
                invoke-static {v2}, Lnc/d;->truncateAtHeightParam(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v2
            """,
        )
    }
}

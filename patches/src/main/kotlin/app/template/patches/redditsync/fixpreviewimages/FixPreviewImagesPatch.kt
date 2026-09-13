package app.template.patches.redditsync.fixpreviewimages

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.redditsync.fixpreviewimages.fingerprints.syncHtmlToSpannedConverterFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

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
 * URLs still qualify. Doing that alone isn't safe on its own, though: right after the
 * gate, the method unconditionally does
 * `Integer.parseInt(uri.getQueryParameter("height"))` — which throws a
 * NumberFormatException on the null string every real-world URL would now produce,
 * crashing comment rendering instead of just failing to embed. So this patch also makes
 * that parse null-safe, falling back to reusing the already-parsed `width` value (a
 * reasonable 1:1 aspect-ratio guess) whenever "height" genuinely isn't present — matching
 * what the code already does for the (apparently rarer, or non-existent in current
 * Reddit URLs) case where a height *is* given.
 *
 * Both edits are anchored on the "preview.redd.it" string constant, which appears
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
    compatibleWith("com.laurencedawson.reddit_sync"("23.06.30-13:39"))

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

        // Instructions 24-25 relative to the "preview.redd.it" const-string (0-indexed
        // offsets base+23/+24): the unconditional `Integer.parseInt(getQueryParameter(
        // "height"))` + its result. Replaced first (a higher index) so the gate removal
        // below doesn't shift it.
        repeat(2) { implementation.removeInstruction(base + 23) }
        method.addInstructions(
            base + 23,
            """
                if-eqz p2, :height_missing
                invoke-static {p2}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I
                move-result p2
                goto :height_resolved
                :height_missing
                move p2, v1
                :height_resolved
            """,
        )

        // Instructions 11-13 relative to the "preview.redd.it" const-string (0-indexed
        // offsets base+10/+11/+12): the "height".contains() check and its branch. The
        // "height" string constant itself (instruction 10, offset base+9) is kept since
        // it's still needed for the getQueryParameter("height") call above.
        repeat(3) { implementation.removeInstruction(base + 10) }
    }
}

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
    }
}

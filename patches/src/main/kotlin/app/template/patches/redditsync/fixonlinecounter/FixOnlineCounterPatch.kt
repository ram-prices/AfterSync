package app.template.patches.redditsync.fixonlinecounter

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.redditsync.fixonlinecounter.fingerprints.subredditInfoParseResponseFingerprint
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
 * Fixes the "number of users online" count on a subreddit's info screen always showing 0.
 *
 * Confirmed by hand via apktool: OAuthSubredditInfoRequest (Ly8/y0;) parses this value with
 * `jsonObject.optInt("accounts_active")` — the field name Reddit's API originally used for
 * this count. Confirmed via web research that Reddit has since deprecated that field in
 * favor of `active_user_count`; `accounts_active` is presumably still present in current
 * API responses for backward compatibility but no longer populated with a real value —
 * `optInt` silently returns its default (0) for a key that's missing OR merely present with
 * a null/non-int value, which is indistinguishable from "genuinely zero users online" from
 * this call alone, matching the always-0 symptom exactly.
 *
 * Fixed by reading `active_user_count` first, falling back to the original `accounts_active`
 * only if that key is genuinely absent (a sentinel default of -1, distinguishable from a
 * real "0 users online" value some other subreddit might legitimately have) — the same
 * "try the new field, fall back to the old one" shape as this project's other Reddit-API
 * field-drift fixes (see project memory). The fallback branch lives entirely inside a new,
 * from-scratch static helper (its own fresh register file) rather than spliced into
 * OAuthSubredditInfoRequest's own giant parseNetworkResponse method — this project has hit
 * real VerifyError crashes doing that directly to other large existing methods, so new
 * branching logic always goes in a dedicated helper instead. The call site swaps only the
 * existing 3-instruction sequence (push the field-name string, call optInt, capture the
 * result) for an equivalent 2-instruction call into the helper — no new branch, no new
 * register, in the existing method itself.
 */
val fixOnlineCounterPatch = bytecodePatch(
    name = "Fix online user counter",
    description = "Fixes a subreddit's \"number of users online\" count always showing 0, " +
        "by reading Reddit's current active_user_count field instead of the deprecated " +
        "accounts_active field the app was still relying on.",
    default = true,
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        val subredditInfoClass = subredditInfoParseResponseFingerprint.classDef
        val parseMethod = subredditInfoParseResponseFingerprint.method
        val parseImpl = parseMethod.implementation!!

        val accountsActiveIndex = parseImpl.instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.CONST_STRING &&
                ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                    ?.string == "accounts_active"
        }
        if (accountsActiveIndex == -1) {
            error(
                "Could not find the \"accounts_active\" field read in " +
                    "OAuthSubredditInfoRequest.parseNetworkResponse(...). This build's " +
                    "method structure may differ from what was inspected — re-check with " +
                    "apktool.",
            )
        }

        // New, fully independent static helper: does the same guarded fallback, but with
        // its own fresh register allocation so the branch inside it can't conflict with the
        // giant existing method's own inferred register types — same reasoning already
        // validated by every other from-scratch helper in this project.
        val resolveImpl = ImmutableMethodImplementation(3, emptyList(), emptyList(), emptyList())
        val resolveDefinition = ImmutableMethod(
            "Ly8/y0;",
            "resolveActiveUserCount",
            listOf(ImmutableMethodParameter("Lorg/json/JSONObject;", emptySet(), "subredditJson")),
            "I",
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            resolveImpl,
        )
        val resolveActiveUserCount = MutableMethod(resolveDefinition)
        resolveActiveUserCount.addInstructions(
            """
                const-string v0, "active_user_count"
                const/4 v1, -0x1
                invoke-virtual {p0, v0, v1}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;I)I
                move-result v0
                if-ltz v0, :fall_back
                return v0

                :fall_back
                const-string v0, "accounts_active"
                invoke-virtual {p0, v0}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;)I
                move-result v0
                return v0
            """,
        )
        subredditInfoClass.directMethods.add(resolveActiveUserCount)

        // Replace the 3 instructions that read "accounts_active" directly (push the field
        // name, call optInt, capture the result) with a call to the helper above — touches
        // only the same register the original 3 instructions already wrote to, nothing
        // else in this method changes.
        parseMethod.removeInstructions(accountsActiveIndex, 3)
        parseMethod.addInstructions(
            accountsActiveIndex,
            """
                invoke-static {v0}, Ly8/y0;->resolveActiveUserCount(Lorg/json/JSONObject;)I
                move-result v0
            """,
        )
    }
}

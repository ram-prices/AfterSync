package app.template.patches.redditsync.removerestorepurchases

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Confirmed by hand from real resources (via apktool): there are two separate
 * purchase-restore surfaces.
 *
 * 1. The root settings screen's "Restore purchases" entry (res/xml/cat_root.xml),
 *    which opens res/xml/cat_purchases.xml (a lone PurchasesPreference). Its click
 *    handler clears the "remove_ads" flag and reopens a Play Billing / Firebase
 *    verification dialog for the ad-removal purchase. The same PurchasesPreference
 *    is also used by res/xml/cat_onboarding_purchases.xml during first-run
 *    onboarding.
 * 2. The Sync Ultra screen (res/xml/cat_ultra.xml) has its own "Restore subscription"
 *    button (UltraRestorePreference) and a dev-only "Reset subscription locally
 *    (debugging)" button (UltraResetPreference), independent of (1) and specific to
 *    Ultra.
 *
 * Since unlockUltraPatch and removeAdsPatch already force Ultra unlocked and ads off
 * unconditionally at their respective gate functions — ignoring the SharedPreferences
 * state these restore/reset buttons read and write entirely — none of this has any
 * effect on the app's behavior anymore either way. Removing all of these navigation
 * entries makes every class they would have used permanently unreachable — nothing
 * ever constructs or calls into them again, which is the practical equivalent of
 * deleting that code. This patcher's API has no way to excise classes from the
 * compiled app outright (confirmed: no such method exists on BytecodePatchContext or
 * anywhere else in the patcher), so removing the entry points and the now-orphaned
 * resource files is as thorough a cleanup as is possible here.
 */
val removeRestorePurchasesPatch = resourcePatch(
    name = "Remove Restore purchases",
    description = "Removes the \"Restore purchases\" entry from Sync for Reddit's settings, " +
        "plus the Sync Ultra screen's \"Restore subscription\" and dev-only \"Reset " +
        "subscription locally\" buttons, now that Ultra and ad removal are unlocked " +
        "unconditionally and don't depend on this state.",
    default = true,
) {
    compatibleWith("com.laurencedawson.reddit_sync"("23.06.30-13:39"))

    execute {
        document("res/xml/cat_root.xml").use { document ->
            val root = document.documentElement
            val entries = root.getElementsByTagName("*")

            var restoreEntry: Element? = null
            for (i in 0 until entries.length) {
                val element = entries.item(i) as? Element ?: continue
                val localTag = element.localName ?: element.tagName.substringAfterLast('.')
                if (!localTag.endsWith("RootPreference")) continue

                val title = element.attributes?.let { attrs ->
                    (0 until attrs.length)
                        .map { attrs.item(it) }
                        .firstOrNull { (it.localName ?: it.nodeName.substringAfterLast(':')) == "title" }
                        ?.nodeValue
                }

                if (title == "Restore purchases") {
                    restoreEntry = element
                    break
                }
            }

            val entry = restoreEntry ?: error(
                "Could not find the \"Restore purchases\" entry in res/xml/cat_root.xml. The " +
                    "structure may differ from what was inspected, or another patch already " +
                    "removed it.",
            )

            entry.parentNode?.removeChild(entry)
        }

        document("res/xml/cat_ultra.xml").use { document ->
            val root = document.documentElement
            val entries = root.getElementsByTagName("*")

            val toRemove = mutableListOf<Element>()
            for (i in 0 until entries.length) {
                val element = entries.item(i) as? Element ?: continue
                val localTag = element.localName ?: element.tagName.substringAfterLast('.')
                if (localTag == "UltraRestorePreference" || localTag == "UltraResetPreference") {
                    toRemove += element
                }
            }

            if (toRemove.size != 2) {
                error(
                    "Expected to find exactly one UltraRestorePreference and one " +
                        "UltraResetPreference in res/xml/cat_ultra.xml, found " +
                        "${toRemove.size} matching elements. The structure may differ from " +
                        "what was inspected.",
                )
            }

            toRemove.forEach { it.parentNode?.removeChild(it) }
        }

        delete("res/xml/cat_purchases.xml")
        delete("res/xml/cat_onboarding_purchases.xml")
        delete("res/layout/preference_ultra_restore.xml")
        delete("res/layout/preference_ultra_reset.xml")
    }
}

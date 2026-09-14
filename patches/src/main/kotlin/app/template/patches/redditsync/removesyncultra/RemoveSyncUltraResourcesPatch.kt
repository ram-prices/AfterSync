package app.template.patches.redditsync.removesyncultra

import app.morphe.patcher.patch.resourcePatch
import app.template.patches.redditsync.moveultrasetting.moveUltraSettingPatch
import app.template.patches.redditsync.removeultracloudbackup.removeUltraCloudBackupResourcesPatch
import org.w3c.dom.Element

private data class RelocatedPreference(
    val tag: String,
    val icon: String?,
    val title: String,
    val key: String,
    val summary: String?,
)

private fun attr(element: Element, name: String): String? = element.attributes?.let { attrs ->
    (0 until attrs.length).map { attrs.item(it) }
        .firstOrNull { (it.localName ?: it.nodeName.substringAfterLast(':')) == name }
        ?.nodeValue
}

private fun localTagOf(element: Element) = element.localName ?: element.tagName.substringAfterLast('.')

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Per explicit request: relocates "Translate text" and "Restore removed comments" into the
 * existing "View tweaks" category, and "Paint users" and "Tag users" into the existing
 * "Highlighting" category, both already present on the "Comments" settings screen
 * (res/xml/cat_comment_view_customization.xml, under the root menu's "Appearance" category)
 * — then removes everything else remaining on the Sync Ultra screen (res/xml/cat_ultra.xml),
 * including the "Select text from images" and "Support the dev!" perks and the four
 * already-vestigial "ultra_disabled_*" rows, and the "Sync Ultra" entry point itself from the
 * root settings menu (res/xml/cat_root.xml).
 *
 * IMPORTANT: this is NOT res/xml/cat_comments.xml (PreferencesCommentsFragment, Lpa/w;) —
 * that backs the separately-named "Comment options" entry under "Content", a confusingly
 * similar but different screen. An earlier version of this patch targeted that screen by
 * mistake; this one targets cat_comment_view_customization.xml (PreferencesCommentViewCustomizationFragment,
 * Lpa/v;), confirmed by hand via apktool to be the "Comments" entry under "Appearance" and to
 * already have "View tweaks"/"Highlighting" categories of its own with real preferences in
 * them (e.g. "Highlight OP", "Show emotes pictures") — exactly matching what was asked for.
 *
 * Confirmed by hand via apktool: none of these four preferences' click behavior is specific
 * to PreferencesUltraFragment (Lpa/l1;) — see removeSyncUltraSetupPatch.kt, which relocates
 * their wiring to PreferencesCommentViewCustomizationFragment (Lpa/v;) and strips the
 * now-pointless setup code from Lpa/l1;->s4()V.
 *
 * With the "Sync Ultra" entry point gone, cat_ultra.xml and PreferencesUltraFragment become
 * unreachable from the UI — same "can't excise a class outright, orphaning is the practical
 * equivalent" reasoning already used for cat_purchases.xml in removeRestorePurchasesPatch.kt.
 * The leftover UltraSignupPreference/UltraManagePreference cards, the "Terms and conditions"
 * and "Privacy" categories, and the fragment class itself are deliberately left alone since
 * they're harmless dead weight once unreachable, and touching them buys nothing.
 *
 * Depends on moveUltraSettingPatch so it always runs after "Sync Ultra" has been moved into
 * the "Content" category — otherwise, if this patch ran first, it would find and delete the
 * entry from wherever it originally sat, and moveUltraSettingPatch's own search for it would
 * then fail.
 *
 * Also depends on removeUltraCloudBackupResourcesPatch, which independently removes
 * "ultra_cloud" from the same "Cloud services" category (res/xml/cat_ultra.xml,
 * key="ultra_cloud_section") that this patch empties out and deletes once ultra_paint/
 * ultra_tag are relocated out of it. Confirmed the hard way on a real device: without this
 * dependency, the patcher can run this patch first, which deletes the whole category —
 * "ultra_cloud" included as collateral, since this patch never touches that key itself —
 * before removeUltraCloudBackupResourcesPatch gets a chance to find and remove it on its
 * own, crashing the whole patch run with "Could not find \"ultra_cloud\" in cat_ultra.xml."
 */
val removeSyncUltraResourcesPatch = resourcePatch(
    name = "Remove Sync Ultra screen (resources)",
    description = "Relocates \"Translate text\"/\"Restore removed comments\" into the " +
        "existing \"View tweaks\" category and \"Paint users\"/\"Tag users\" into the " +
        "existing \"Highlighting\" category on the Comments settings screen, then removes " +
        "everything else on the Sync Ultra screen, including its entry point in the root " +
        "settings menu.",
    default = true,
) {
    dependsOn(moveUltraSettingPatch)
    dependsOn(removeUltraCloudBackupResourcesPatch)

    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        document("res/xml/cat_root.xml").use { document ->
            val entries = document.documentElement.getElementsByTagName("*")

            var ultraEntry: Element? = null
            for (i in 0 until entries.length) {
                val element = entries.item(i) as? Element ?: continue
                if (!localTagOf(element).endsWith("RootPreference")) continue
                if (attr(element, "title") == "Sync Ultra") {
                    ultraEntry = element
                    break
                }
            }

            val entry = ultraEntry ?: error(
                "Could not find the \"Sync Ultra\" entry in res/xml/cat_root.xml. The " +
                    "structure may differ from what was inspected.",
            )
            entry.parentNode?.removeChild(entry)
        }

        val relocated = mutableMapOf<String, RelocatedPreference>()

        document("res/xml/cat_ultra.xml").use { document ->
            val root = document.documentElement
            val entries = root.getElementsByTagName("*")

            val relocatedKeys = setOf("ultra_paint", "ultra_tag", "ultra_translate", "ultra_removed")
            val deletedKeys = setOf(
                "ultra_select_text", "ultra_support",
                "ultra_disabled_cloud", "ultra_disabled_paint", "ultra_disabled_tag",
                "ultra_disabled_website_previews",
            )

            var cloudSection: Element? = null
            var perks: Element? = null
            val toDelete = mutableListOf<Element>()
            val toRelocate = mutableListOf<Element>()

            for (i in 0 until entries.length) {
                val element = entries.item(i) as? Element ?: continue
                val localTag = localTagOf(element)
                val key = attr(element, "key")

                when {
                    localTag == "PreferenceCategory" && key == "ultra_cloud_section" -> cloudSection = element
                    localTag == "PreferenceCategory" && key == "ultra_perks" -> perks = element
                    key in relocatedKeys -> toRelocate += element
                    key in deletedKeys -> toDelete += element
                }
            }

            if (toRelocate.size != relocatedKeys.size) {
                error(
                    "Expected to find ${relocatedKeys.size} preferences to relocate " +
                        "(ultra_paint/ultra_tag/ultra_translate/ultra_removed) in " +
                        "res/xml/cat_ultra.xml, found ${toRelocate.size}.",
                )
            }
            if (toDelete.size != deletedKeys.size) {
                error(
                    "Expected to find ${deletedKeys.size} removable Perks entries in " +
                        "res/xml/cat_ultra.xml, found ${toDelete.size}.",
                )
            }

            toRelocate.forEach { element ->
                val key = attr(element, "key")!!
                relocated[key] = RelocatedPreference(
                    tag = element.tagName,
                    icon = attr(element, "icon"),
                    title = attr(element, "title")!!,
                    key = key,
                    summary = attr(element, "summary"),
                )
                element.parentNode?.removeChild(element)
            }
            toDelete.forEach { it.parentNode?.removeChild(it) }

            val cloud = cloudSection ?: error("Could not find the \"Cloud services\" category (ultra_cloud_section) in res/xml/cat_ultra.xml.")
            cloud.parentNode?.removeChild(cloud)

            val perksCategory = perks ?: error("Could not find the \"Perks\" category (ultra_perks) in res/xml/cat_ultra.xml.")
            perksCategory.parentNode?.removeChild(perksCategory)
        }

        document("res/xml/cat_comment_view_customization.xml").use { document ->
            val entries = document.documentElement.getElementsByTagName("*")

            fun findCategoryByHeaderTitle(title: String): Element {
                for (i in 0 until entries.length) {
                    val element = entries.item(i) as? Element ?: continue
                    if (!localTagOf(element).endsWith("CategoryHeaderPreference")) continue
                    if (attr(element, "categoryTitle") == title) {
                        return element.parentNode as? Element
                            ?: error("The \"$title\" category header has no parent element.")
                    }
                }
                error(
                    "Could not find the \"$title\" category in " +
                        "res/xml/cat_comment_view_customization.xml. The structure may " +
                        "differ from what was inspected.",
                )
            }

            fun newElement(pref: RelocatedPreference): Element {
                val element = document.createElement(pref.tag)
                pref.icon?.let { element.setAttribute("android:icon", it) }
                element.setAttribute("android:title", pref.title)
                element.setAttribute("android:key", pref.key)
                pref.summary?.let { element.setAttribute("android:summary", it) }
                return element
            }

            fun relocatedOrError(key: String) = relocated[key]
                ?: error("Preference \"$key\" was not captured from res/xml/cat_ultra.xml before it was removed.")

            val viewTweaks = findCategoryByHeaderTitle("View tweaks")
            viewTweaks.appendChild(newElement(relocatedOrError("ultra_translate")))
            viewTweaks.appendChild(newElement(relocatedOrError("ultra_removed")))

            val highlighting = findCategoryByHeaderTitle("Highlighting")
            highlighting.appendChild(newElement(relocatedOrError("ultra_paint")))
            highlighting.appendChild(newElement(relocatedOrError("ultra_tag")))
        }
    }
}

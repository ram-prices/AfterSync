package app.template.patches.redditsync.moveultrasetting

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Moves the "Sync Ultra" entry in res/xml/cat_root.xml from the "New" category (at
 * the top of Settings, alongside "Developer options" and "Legacy settings") to be the
 * first entry in the "Content" category, per explicit request.
 *
 * This only changes the RootPreference element's position in the XML tree, not any of
 * its attributes (title, summary, icon, app:preference_ref) — its identity and the
 * implicit key derived from @integer/ULTRA are unaffected, so the force-visibility
 * setup code in PreferencesNewRootFragment (which looks it up by that key) keeps
 * working exactly as before regardless of where it sits in the file.
 */
val moveUltraSettingPatch = resourcePatch(
    name = "Move Sync Ultra setting",
    description = "Moves the \"Sync Ultra\" entry in Sync for Reddit's settings from the " +
        "\"New\" category to the top of the \"Content\" category.",
    default = true,
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        document("res/xml/cat_root.xml").use { document ->
            val root = document.documentElement
            val entries = root.getElementsByTagName("*")

            var ultraEntry: Element? = null
            var contentHeader: Element? = null

            for (i in 0 until entries.length) {
                val element = entries.item(i) as? Element ?: continue
                val localTag = element.localName ?: element.tagName.substringAfterLast('.')

                if (localTag.endsWith("RootPreference")) {
                    val title = element.attributes?.let { attrs ->
                        (0 until attrs.length)
                            .map { attrs.item(it) }
                            .firstOrNull { (it.localName ?: it.nodeName.substringAfterLast(':')) == "title" }
                            ?.nodeValue
                    }
                    if (title == "Sync Ultra") ultraEntry = element
                } else if (localTag.endsWith("CategoryHeaderPreference")) {
                    val categoryTitle = element.attributes?.let { attrs ->
                        (0 until attrs.length)
                            .map { attrs.item(it) }
                            .firstOrNull {
                                (it.localName ?: it.nodeName.substringAfterLast(':')) == "categoryTitle"
                            }
                            ?.nodeValue
                    }
                    if (categoryTitle == "Content") contentHeader = element
                }
            }

            val ultra = ultraEntry ?: error(
                "Could not find the \"Sync Ultra\" entry in res/xml/cat_root.xml. The " +
                    "structure may differ from what was inspected.",
            )
            val header = contentHeader ?: error(
                "Could not find the \"Content\" category header in res/xml/cat_root.xml. " +
                    "The structure may differ from what was inspected.",
            )

            val contentCategory = header.parentNode
                ?: error("The \"Content\" category header has no parent element.")

            ultra.parentNode?.removeChild(ultra)
            // Insert right after the category header, i.e. as the first entry in Content.
            contentCategory.insertBefore(ultra, header.nextSibling)
        }
    }
}

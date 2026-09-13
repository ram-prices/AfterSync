package app.template.patches.redditsync.expressivesettings

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Second step of the ongoing Settings restyle, per explicit feedback that the search
 * bar looks too big. Confirmed by hand via apktool: its size comes from two places —
 * res/layout/preference_search.xml (SearchPreference's own layout) sets a 24dp top
 * margin above the whole box, and res/layout/view_monet_search_box.xml (the shared
 * layout MonetSearchBoxView inflates for its actual content) pads the row itself by
 * 12dp top/bottom in addition to a 24dp icon. Reduced both — the outer margin to 12dp
 * and the inner padding to 8dp — for a more compact, pill-like search field, leaving
 * the icon size and horizontal padding alone.
 */
val expressiveSettingsSearchBarPatch = resourcePatch(
    name = "Expressive Settings search bar",
    description = "Makes Sync for Reddit's settings search bar more compact, matching " +
        "a Material 3 Expressive-inspired restyle.",
    default = true,
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        document("res/layout/preference_search.xml").use { document ->
            val root = document.documentElement
            root.setAttribute("android:layout_marginTop", "12.0dp")
        }

        document("res/layout/view_monet_search_box.xml").use { document ->
            val entries = document.documentElement.getElementsByTagName("*")
            var wrapper: Element? = null

            for (i in 0 until entries.length) {
                val element = entries.item(i) as? Element ?: continue
                val id = element.attributes?.let { attrs ->
                    (0 until attrs.length).map { attrs.item(it) }
                        .firstOrNull { (it.localName ?: it.nodeName.substringAfterLast(':')) == "id" }
                        ?.nodeValue
                }
                if (id == "@id/search_wrapper") { wrapper = element; break }
            }

            val row = wrapper ?: error("Could not find \"search_wrapper\" in view_monet_search_box.xml.")
            row.setAttribute("android:paddingTop", "8.0dp")
            row.setAttribute("android:paddingBottom", "8.0dp")
        }
    }
}

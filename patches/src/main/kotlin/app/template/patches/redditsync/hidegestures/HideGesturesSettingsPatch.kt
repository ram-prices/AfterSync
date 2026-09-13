package app.template.patches.redditsync.hidegestures

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * The General settings screen is built from res/xml/cat_general.xml, which
 * contains a <PreferenceCategory> whose header has categoryTitle="Gestures",
 * holding three child preferences:
 *   - key="swipe_hide"  ("Swipe to return")
 *   - key="swipe_dim"   ("Dim behind activity")
 *   - key="swipe_sens"  ("Swipe to return sensitivity")
 *
 * IMPORTANT: this patch does NOT delete the category/preferences from the XML.
 * An earlier version did, and it crashed the settings screen — the fragment's
 * onStart() code unconditionally calls a method on the "swipe_sens" preference
 * it looks up by key (confirmed via a real device crash log: a
 * NullPointerException calling ListPreference's getValue() equivalent),
 * without checking whether that lookup returned null. Deleting the preference
 * makes the lookup return null and crashes the app.
 *
 * Instead, this patch sets app:isPreferenceVisible="false" on the
 * PreferenceCategory itself. AndroidX's Preference framework excludes
 * invisible groups (and everything nested inside them) from the rendered
 * settings list, while still keeping the underlying Preference objects
 * registered in the hierarchy — so findPreference("swipe_sens") etc. still
 * resolve to real objects, and the crash-prone code path never breaks. The
 * net effect for the user is identical: the Gestures section is gone from
 * the Settings screen.
 */
val hideGesturesSettingsPatch = resourcePatch(
    name = "Hide Gestures settings",
    description = "Hides the \"Gestures\" section (Swipe to return, Dim behind activity, " +
        "Swipe to return sensitivity) from Sync for Reddit's General settings screen.",
) {
    compatibleWith("com.laurencedawson.reddit_sync"("23.06.30-13:39"))

    execute {
        document("res/xml/cat_general.xml").use { document ->
            val root = document.documentElement
            val appNs = "http://schemas.android.com/apk/res-auto"

            // Make sure the "app" namespace prefix is actually declared on the
            // root element before we use it below — otherwise the attribute
            // we add won't resolve correctly when this gets recompiled.
            val xmlnsNs = "http://www.w3.org/2000/xmlns/"
            if (!root.getAttributeNS(xmlnsNs, "app").let { it.isNotEmpty() }) {
                root.setAttributeNS(xmlnsNs, "xmlns:app", appNs)
            }

            val categoryNodes = root.getElementsByTagName("PreferenceCategory")
            val categoriesToHide = mutableListOf<Element>()
            val seenTitles = mutableListOf<String>()

            for (i in 0 until categoryNodes.length) {
                val category = categoryNodes.item(i) as? Element ?: continue

                // Match the header child by its *local* tag name (ignoring any
                // namespace prefix), since custom preference elements can be
                // referenced with or without one depending on how the XML was
                // originally authored/compiled.
                var header: Element? = null
                val children = category.childNodes
                for (j in 0 until children.length) {
                    val node = children.item(j) as? Element ?: continue
                    val localTag = node.localName ?: node.tagName.substringAfterLast(':')
                    if (localTag == "CategoryHeaderPreference" ||
                        node.tagName.endsWith("CategoryHeaderPreference")
                    ) {
                        header = node
                        break
                    }
                }
                val header2 = header ?: continue

                // Read the categoryTitle attribute regardless of namespace
                // prefix — getAttribute("categoryTitle") silently returns ""
                // instead of matching if the attribute is actually serialized
                // with a prefix like app:categoryTitle.
                val title = header2.attributes?.let { attrs ->
                    (0 until attrs.length)
                        .map { attrs.item(it) }
                        .firstOrNull {
                            (it.localName ?: it.nodeName.substringAfterLast(':')) == "categoryTitle"
                        }
                        ?.nodeValue
                }

                if (title != null) seenTitles += title
                if (title == "Gestures") {
                    categoriesToHide += category
                }
            }

            if (categoriesToHide.isEmpty()) {
                error(
                    "Could not find the Gestures preference category in " +
                        "res/xml/cat_general.xml. Category titles actually found: " +
                        seenTitles.joinToString(", ").ifEmpty { "(none)" } +
                        ". Another patch in this bundle may have already modified this " +
                        "file, or the structure may differ from what was inspected.",
                )
            }

            categoriesToHide.forEach { category ->
                category.setAttributeNS(appNs, "app:isPreferenceVisible", "false")
            }
        }
    }
}

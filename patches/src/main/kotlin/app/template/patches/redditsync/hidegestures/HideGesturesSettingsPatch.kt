package app.template.patches.redditsync.hidegestures

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Confirmed by decompiling the actual APK: the app's General settings screen is
 * built from a static resource file, res/xml/cat_general.xml, which contains a
 * <PreferenceCategory> whose header has categoryTitle="Gestures", holding three
 * child preferences:
 *   - key="swipe_hide"  ("Swipe to return")
 *   - key="swipe_dim"   ("Dim behind activity")
 *   - key="swipe_sens"  ("Swipe to return sensitivity")
 *
 * This patch removes that entire <PreferenceCategory> node (header + all three
 * preferences) from the XML, so the section and everything in it disappears from
 * the Settings screen. Because this edits a resource file rather than bytecode,
 * it's far less likely to break on Sync app updates than a bytecode fingerprint
 * would be — it only needs to be revisited if a future Sync build renames or
 * restructures this XML file.
 */
val hideGesturesSettingsPatch = resourcePatch(
    name = "Hide Gestures settings",
    description = "Removes the \"Gestures\" section (Swipe to return, Dim behind activity, " +
        "Swipe to return sensitivity) from Sync for Reddit's General settings screen.",
) {
    compatibleWith("com.laurencedawson.reddit_sync"("23.06.30-13:39"))

    execute {
        // NOTE ON API: this assumes Morphe's resource-patch DSL exposes a
        // `document(path)` helper that parses the given resource XML into a
        // standard org.w3c.dom.Document and writes it back to the APK when the
        // `use { }` block ends — this is the pattern used throughout ReVanced /
        // Morphe's own XML-editing patches.
        document("res/xml/cat_general.xml").use { document ->
            val root = document.documentElement

            val categoryNodes = root.getElementsByTagName("PreferenceCategory")
            val categoriesToRemove = mutableListOf<Element>()
            val seenTitles = mutableListOf<String>()

            for (i in 0 until categoryNodes.length) {
                val category = categoryNodes.item(i) as? Element ?: continue

                // Match the header child by its *local* tag name (ignoring any
                // namespace prefix), since custom preference elements can be
                // referenced with or without one depending on how the XML was
                // originally authored/compiled.
                val headerNodes = category.childNodes
                var header: Element? = null
                for (j in 0 until headerNodes.length) {
                    val node = headerNodes.item(j) as? Element ?: continue
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
                // with a prefix like app:categoryTitle, which is a common
                // source of false "not found" results.
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
                    categoriesToRemove += category
                }
            }

            if (categoriesToRemove.isEmpty()) {
                error(
                    "Could not find the Gestures preference category in " +
                        "res/xml/cat_general.xml. Category titles actually found: " +
                        seenTitles.joinToString(", ").ifEmpty { "(none)" } +
                        ". Another patch in this bundle may have already modified this " +
                        "file, or the structure may differ from what was inspected.",
                )
            }

            categoriesToRemove.forEach { category ->
                category.parentNode?.removeChild(category)
            }
        }
    }
}

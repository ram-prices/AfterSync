package app.template.patches.redditsync.expressivesettings

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Third step of the ongoing Settings restyle: gives every settings row its own
 * rounded, inset card background instead of an edge-to-edge strip, matching the
 * agreed-on mockup.
 *
 * A first attempt at this (v1.11.0) set the new background directly on each row's
 * root element, which had no visible effect — confirmed on-device. Root cause: AndroidX's
 * Preference.onBindViewHolder() explicitly manages that root view's background itself,
 * resetting it to ?android:selectableItemBackground (the tap ripple) every time the
 * preference binds, for any selectable preference. This is standard library behavior,
 * not app-specific code, and it silently overwrites whatever a layout XML declares
 * there — which is exactly why the earlier attempt had no effect, while the icon
 * container (a nested child view the library never touches) worked fine.
 *
 * This version doesn't fight the library for control of the root background. Instead,
 * it wraps each row's existing content in a new inner container that carries the new
 * rounded fill (?attr/colorSurfaceContainer, a real Material 3 tonal role confirmed
 * present in this app's compiled resources), while the original root element keeps its
 * original ?android:selectableItemBackground untouched and only gains the new margins
 * that create the gap between cards. Confirmed no central "divider between rows"
 * mechanism exists for ordinary preferences that this could conflict with.
 */
val expressiveSettingsRowsPatch = resourcePatch(
    name = "Expressive Settings row cards",
    description = "Gives every settings row its own rounded, inset card background, " +
        "matching a Material 3 Expressive-inspired restyle.",
    default = true,
) {
    dependsOn(expressiveSettingsIconsPatch)

    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        get("res/drawable/aftersync_row_container.xml").writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
                    <corners android:radius="18dp" />
                    <solid android:color="?attr/colorSurfaceContainer" />
                </shape>
            """.trimIndent(),
        )

        // Wraps a row's existing children in a new inner LinearLayout carrying the
        // rounded fill, moving every attribute off the root except background/size —
        // those stay so the library's own ripple management keeps working unmodified.
        fun wrapRowContent(document: Document, keepOnRoot: Set<String>) {
            val root = document.documentElement

            val moved = mutableListOf<Pair<String, String>>()
            val attrs = root.attributes
            var i = attrs.length - 1
            while (i >= 0) {
                val attr = attrs.item(i)
                val localName = attr.localName ?: attr.nodeName.substringAfterLast(':')
                if (localName !in keepOnRoot) {
                    moved += attr.nodeName to attr.nodeValue
                    root.removeAttributeNode(attr as org.w3c.dom.Attr)
                }
                i--
            }

            val children = mutableListOf<Element>()
            val childNodes = root.childNodes
            for (j in 0 until childNodes.length) {
                (childNodes.item(j) as? Element)?.let { children += it }
            }

            val wrapper = document.createElement("LinearLayout")
            moved.forEach { (name, value) -> wrapper.setAttribute(name, value) }
            wrapper.setAttribute("android:background", "@drawable/aftersync_row_container")

            children.forEach { wrapper.appendChild(it) }
            root.appendChild(wrapper)

            root.setAttribute("android:layout_marginStart", "12.0dp")
            root.setAttribute("android:layout_marginEnd", "12.0dp")
            root.setAttribute("android:layout_marginTop", "3.0dp")
            root.setAttribute("android:layout_marginBottom", "3.0dp")
        }

        document("res/layout/preference_material.xml").use { document ->
            wrapRowContent(document, keepOnRoot = setOf("background", "layout_width", "layout_height"))
        }

        document("res/layout/preference_root.xml").use { document ->
            wrapRowContent(document, keepOnRoot = setOf("background", "layout_width", "layout_height"))
        }
    }
}

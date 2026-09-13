package app.template.patches.redditsync.expressivesettings

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * First step of an ongoing, iterative Material 3 Expressive-inspired restyle of the
 * Settings screens, per explicit request. Gives every settings row's icon a rounded,
 * tonal "icon container" background — the single most visually distinctive cue from
 * the agreed-on mockup — without touching color logic at all, since the app's real
 * accent color is set programmatically at runtime by its own "Theme management"
 * customizer (confirmed by hand: the base theme's colorPrimary/colorSecondary are
 * literal placeholder greys, not the colors actually shown in the app), so a static
 * XML recolor would very likely just get overwritten. The container uses
 * ?attr/colorPrimaryContainer, a real Material 3 color role, so it should track
 * whatever accent the user has actually chosen rather than clashing with it — this is
 * the one part of this patch I can't fully verify without a real device test.
 *
 * Confirmed by hand via apktool: nearly every ordinary settings row uses the generic
 * SyncPreference class, which sets no layout of its own — it relies on the shared
 * res/layout/image_frame.xml (included into res/layout/preference_material.xml, the
 * stock AndroidX Preference row layout this app's near-empty preference theme falls
 * back to). The one exception is RootPreference (only the top-level entries on the
 * main Settings menu), which already has a dedicated 40dp "image_wrapper" frame in
 * res/layout/preference_root.xml — that one only needs a background attribute added,
 * since the sizing frame already exists.
 *
 * This edit touches two resources shared across virtually every settings screen, not
 * one screen at a time — the tradeoff for that leverage is a wider (though purely
 * visual, non-crash-risk) blast radius if something looks off, which is exactly why
 * this is being done as a small first increment to check on a real device before
 * going further (e.g. rounding the row backgrounds themselves, or the root menu's
 * icon tint color).
 */
val expressiveSettingsIconsPatch = resourcePatch(
    name = "Expressive Settings icon containers",
    description = "Gives Sync for Reddit's settings-row icons a rounded, tonal " +
        "container background, matching a Material 3 Expressive-inspired restyle.",
    default = true,
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        // New drawable: a rounded-square, tonal container for every settings icon.
        get("res/drawable/aftersync_icon_container.xml").writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
                    <corners android:radius="11dp" />
                    <solid android:color="?attr/colorPrimaryContainer" />
                </shape>
            """.trimIndent(),
        )

        // RootPreference (the main Settings menu's top-level entries) already has a
        // fixed-size icon frame — it just needs the new background.
        document("res/layout/preference_root.xml").use { document ->
            val entries = document.documentElement.getElementsByTagName("*")
            var imageWrapper: Element? = null

            for (i in 0 until entries.length) {
                val element = entries.item(i) as? Element ?: continue
                val id = element.attributes?.let { attrs ->
                    (0 until attrs.length).map { attrs.item(it) }
                        .firstOrNull { (it.localName ?: it.nodeName.substringAfterLast(':')) == "id" }
                        ?.nodeValue
                }
                if (id == "@id/image_wrapper") { imageWrapper = element; break }
            }

            val wrapper = imageWrapper
                ?: error("Could not find \"image_wrapper\" in preference_root.xml.")
            wrapper.setAttribute("android:background", "@drawable/aftersync_icon_container")
        }

        // Every other settings row (the generic SyncPreference class) gets its icon
        // from this shared, included layout, which has no fixed-size frame yet —
        // wrap the icon in one, shrinking it slightly to leave room for padding.
        document("res/layout/image_frame.xml").use { document ->
            val entries = document.documentElement.getElementsByTagName("*")
            val icon = (0 until entries.length)
                .mapNotNull { entries.item(it) as? Element }
                .firstOrNull {
                    (it.localName ?: it.tagName.substringAfterLast('.')).endsWith("PreferenceImageView")
                } ?: error("Could not find PreferenceImageView in image_frame.xml.")

            icon.setAttribute("android:layout_width", "20.0dp")
            icon.setAttribute("android:layout_height", "20.0dp")
            icon.setAttribute("android:layout_gravity", "center")
            icon.setAttribute("app:maxHeight", "20.0dp")
            icon.setAttribute("app:maxWidth", "20.0dp")

            val container = document.createElement("FrameLayout")
            container.setAttribute("android:layout_width", "36.0dp")
            container.setAttribute("android:layout_height", "36.0dp")
            container.setAttribute("android:layout_gravity", "center")
            container.setAttribute("android:background", "@drawable/aftersync_icon_container")

            val parent = icon.parentNode
                ?: error("PreferenceImageView has no parent in image_frame.xml.")
            parent.replaceChild(container, icon)
            container.appendChild(icon)
        }
    }
}

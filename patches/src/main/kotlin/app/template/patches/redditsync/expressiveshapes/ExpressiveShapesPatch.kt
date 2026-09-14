package app.template.patches.redditsync.expressiveshapes

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Experimental step toward a broader "more expressive" visual pass (per explicit request,
 * loosely inspired by Material 3 Expressive's bigger/rounder/roomier language — not a
 * literal port of any specific app's implementation; see project chat history for the
 * scoping investigation that led here). Deliberately does not touch color at all — Sync
 * already colors itself dynamically per subreddit (confirmed the app's own real theme sets
 * colorPrimary/colorSecondary to a flat #777 placeholder, meaning the actual color comes
 * from somewhere else at runtime, per subreddit) — a fixed palette here would fight that,
 * not complement it, per explicit follow-up request.
 *
 * An early version of this patch widened the central `ShapeAppearance.M3.Sys.Shape.Corner.*`
 * style tokens, reasoning every M3 component references them indirectly. Confirmed on a
 * real device (by pulling the installed APK back off and decompiling it) to have NO visible
 * effect anywhere: tracing three concrete, really-rendered widgets (a bottom sheet, a
 * MaterialAlertDialog, CardView) showed every one resolves its corner radius through a
 * completely separate resource — app-specific custom styles or plain `dimens.xml` values —
 * never through that token chain. It's present in the compiled resources (presumably
 * vestigial Material Components library scaffolding) but nothing in Sync's own UI reads it.
 *
 * This version targets only resources confirmed (or, for the ones added in this broader
 * pass, strongly evidenced by their app-specific, feature-referencing names rather than a
 * generic library prefix like `mtrl_`/`m3_`/`abc_`) to actually drive Sync's own rendering:
 *
 * Shape (res/values/dimens.xml unless noted):
 * - `cardview_default_radius`/`mtrl_card_corner_radius` — the legacy androidx CardView
 *   radius and its Material Components successor; both exist because this app was never
 *   fully migrated off the older CardView-based UI.
 * - `mtrl_btn_corner_radius` — MaterialButton corners.
 * - `m3_chip_corner_size` — chip corners (e.g. the "News"/"Rumour" post-flair tags).
 * - `m3_navigation_drawer_layout_corner_size` — the nav drawer's leading-edge corners.
 * - `mtrl_snackbar_background_corner_radius` — snackbar corners.
 * - `mtrl_textinput_box_corner_radius_medium` — any MDC-backed text input box.
 * - `ShapeAppearanceBottomSheetDialog_Rounded` (res/values/styles.xml) — Sync's own custom
 *   style for its rounded modal bottom sheets (confirmed via the real "Post options" sheet).
 *   Only the two top corners are rounded on purpose — it's a bottom sheet, the bottom
 *   corners stay square, flush with the screen edge.
 *
 * Spacing (res/values/dimens.xml, all app-specific names — not stock Android Studio
 * template dimens despite `activity_horizontal_margin`/`activity_vertical_margin` looking
 * like ones; every value here is a real, feature-specific dimen actually named after what
 * it spaces):
 * - `posts_fragment_list_full_padding`/`posts_fragment_list_half_padding` — the post feed's
 *   own list padding.
 * - `comment_row_spacer` — vertical gap between comment rows.
 * - `static_padding_regular` — a generic app-wide padding constant reused in several places.
 *
 * Two earlier, more cautious rounds (16dp then 24dp on the smaller shape-only set) were
 * each confirmed working via the real-device + decompile process but read as too
 * incremental to actually feel like a redesign. This pass goes further in both scope (adds
 * the drawer/snackbar/text-input shapes and the whole spacing set) and degree, per explicit
 * follow-up request to make significant changes in one go rather than many small ones.
 *
 * Deliberately still leaves alone: `m3_alert_dialog_corner_size` (28dp, already generously
 * rounded), AppCompat/framework compat widgets like `abc_control_corner_material` (shared
 * with system-provided widgets outside Sync's own UI), and calendar/tooltip/progress-
 * indicator corner dimens (low visual impact, rarely-seen surfaces).
 */
val expressiveShapesPatch = resourcePatch(
    name = "Expressive shapes and spacing (experimental)",
    description = "Experimental: widens the corner radius of cards, buttons, chips, the " +
        "nav drawer, snackbars, text inputs, and Sync's own custom bottom sheets, and " +
        "gives the post feed and comment list more generous spacing — loosely inspired by " +
        "Material 3 Expressive. Leaves color untouched; Sync already colors itself " +
        "dynamically per subreddit.",
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        document("res/values/dimens.xml").use { document ->
            // name -> (original, widened)
            val widenedDimens = mapOf(
                // Shape
                "cardview_default_radius" to ("2.0dp" to "24.0dp"),
                "mtrl_card_corner_radius" to ("4.0dp" to "24.0dp"),
                "mtrl_btn_corner_radius" to ("4.0dp" to "24.0dp"),
                "m3_chip_corner_size" to ("8.0dp" to "24.0dp"),
                "m3_navigation_drawer_layout_corner_size" to ("16.0dp" to "28.0dp"),
                "mtrl_snackbar_background_corner_radius" to ("4.0dp" to "16.0dp"),
                "mtrl_textinput_box_corner_radius_medium" to ("4.0dp" to "16.0dp"),
                // Spacing
                "posts_fragment_list_full_padding" to ("16.0dp" to "24.0dp"),
                "posts_fragment_list_half_padding" to ("8.0dp" to "16.0dp"),
                "comment_row_spacer" to ("4.0dp" to "12.0dp"),
                "static_padding_regular" to ("10.0dp" to "16.0dp"),
            )

            val dimens = document.documentElement.getElementsByTagName("dimen")
            val found = mutableSetOf<String>()

            for (i in 0 until dimens.length) {
                val dimen = dimens.item(i) as? Element ?: continue
                val name = dimen.getAttribute("name")
                val target = widenedDimens[name] ?: continue
                val (expectedOriginal, widened) = target

                if (dimen.textContent != expectedOriginal) {
                    error(
                        "Expected dimen \"$name\" to be \"$expectedOriginal\", found " +
                            "\"${dimen.textContent}\". This build's resource structure may " +
                            "differ from what was inspected — re-check with apktool.",
                    )
                }
                dimen.textContent = widened
                found += name
            }

            if (found != widenedDimens.keys) {
                error(
                    "Expected to widen ${widenedDimens.keys}, only found $found in " +
                        "dimens.xml. This build's resource structure may differ from what " +
                        "was inspected — re-check with apktool.",
                )
            }
        }

        document("res/values/styles.xml").use { document ->
            val styles = document.documentElement.getElementsByTagName("style")
            var bottomSheetStyle: Element? = null
            for (i in 0 until styles.length) {
                val style = styles.item(i) as? Element ?: continue
                if (style.getAttribute("name") == "ShapeAppearanceBottomSheetDialog_Rounded") {
                    bottomSheetStyle = style
                    break
                }
            }
            val style = bottomSheetStyle ?: error(
                "Could not find \"ShapeAppearanceBottomSheetDialog_Rounded\" in styles.xml. " +
                    "This build's resource structure may differ from what was inspected — " +
                    "re-check with apktool.",
            )

            val items = style.getElementsByTagName("item")
            val topCornerItemNames = setOf("cornerSizeTopLeft", "cornerSizeTopRight")
            val widened = mutableSetOf<String>()
            for (i in 0 until items.length) {
                val item = items.item(i) as? Element ?: continue
                val itemName = item.getAttribute("name")
                if (itemName !in topCornerItemNames) continue

                if (item.textContent != "12.0dp") {
                    error(
                        "Expected \"ShapeAppearanceBottomSheetDialog_Rounded\"'s \"$itemName\" " +
                            "to be \"12.0dp\", found \"${item.textContent}\". This build's " +
                            "resource structure may differ from what was inspected — " +
                            "re-check with apktool.",
                    )
                }
                item.textContent = "32.0dp"
                widened += itemName
            }

            if (widened != topCornerItemNames) {
                error(
                    "Expected to widen $topCornerItemNames, only found $widened in " +
                        "\"ShapeAppearanceBottomSheetDialog_Rounded\". This build's resource " +
                        "structure may differ from what was inspected — re-check with apktool.",
                )
            }
        }
    }
}

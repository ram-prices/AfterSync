package app.template.patches.redditsync.expressiveshapes

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Experimental step toward a broader "more expressive" visual pass (per explicit request,
 * loosely inspired by Material 3 Expressive's bigger/rounder shape language — not a
 * literal port of any specific app's implementation; see project chat history for the
 * scoping investigation that led here).
 *
 * A first version of this patch widened the central `ShapeAppearance.M3.Sys.Shape.Corner.*`
 * style tokens in res/values/styles.xml, reasoning that every M3 component references them
 * indirectly. That version built and applied fine but was confirmed on a real device to
 * have NO visible effect anywhere in the app. Root-caused by pulling the actual installed
 * (patched) APK back off the device and decompiling it: the token edit really was present
 * in the installed build, but tracing three concrete, really-rendered widgets (the "Post
 * options" bottom sheet, a MaterialAlertDialog, and CardView) showed every one of them
 * resolves its corner radius through a COMPLETELY SEPARATE, independent resource —
 * app-specific custom styles or plain `dimens.xml` values — never through the
 * `Sys.Shape.Corner.*` token chain at all. That token layer is present in the compiled
 * resources (presumably vestigial Material Components library scaffolding) but nothing
 * in Sync's actual UI reads it.
 *
 * This version targets what was actually confirmed to matter instead:
 * - `ShapeAppearanceBottomSheetDialog_Rounded` (res/values/styles.xml) — Sync's own custom
 *   style for its rounded modal bottom sheets (confirmed via the real "Post options" sheet
 *   opened from a post's overflow menu on a real device). Only the two top corners are
 *   rounded (it's a bottom sheet — the bottom corners are meant to stay square, flush with
 *   the screen edge), so only those two items are touched.
 * - `cardview_default_radius`/`mtrl_card_corner_radius` (res/values/dimens.xml) — the
 *   legacy androidx CardView radius and its Material Components successor. Both exist
 *   because this app was never fully migrated off the older CardView-based UI; touching
 *   both covers whichever one any given card-like surface actually uses.
 * - `mtrl_btn_corner_radius` (res/values/dimens.xml) — MaterialButton's corner radius.
 * - `m3_chip_corner_size` (res/values/dimens.xml) — chip corner radius (e.g. the small
 *   colored post-flair tags like "News"/"Rumour" on the frontpage).
 *
 * Deliberately leaves `m3_alert_dialog_corner_size` (28dp) alone — already generously
 * rounded, no need to push it further — and leaves every OTHER dimens.xml corner value
 * (tooltips, snackbars, text input boxes, the navigation drawer, AppCompat/framework
 * compat widgets like `abc_control_corner_material`) untouched, since those weren't
 * confirmed to affect anything visible in Sync's own screens and widening them
 * indiscriminately risks affecting unrelated system-provided widgets.
 */
val expressiveShapesPatch = resourcePatch(
    name = "Expressive shapes (experimental)",
    description = "Experimental: widens the corner radius of cards, buttons, chips, and " +
        "Sync's own custom bottom sheets to a bigger, rounder scale, loosely inspired by " +
        "Material 3 Expressive.",
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        document("res/values/dimens.xml").use { document ->
            // name -> (original, widened)
            val widenedDimens = mapOf(
                "cardview_default_radius" to ("2.0dp" to "16.0dp"),
                "mtrl_card_corner_radius" to ("4.0dp" to "16.0dp"),
                "mtrl_btn_corner_radius" to ("4.0dp" to "16.0dp"),
                "m3_chip_corner_size" to ("8.0dp" to "16.0dp"),
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
                item.textContent = "24.0dp"
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

package app.template.patches.redditsync.expressiveshapes

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Experimental first step toward a broader "more expressive" visual pass (per explicit
 * request, loosely inspired by Material 3 Expressive's bigger/rounder shape language —
 * not a literal port of any specific app's implementation, since none of the actual
 * references found while scoping this turned out to have real Expressive motion/shape
 * code to copy; see project chat history for that investigation).
 *
 * Sync for Reddit already ships the full Material Components shape-token system in
 * res/values/styles.xml — confirmed by hand via apktool: every M3-style button, card,
 * dialog, and sheet references one of a handful of central
 * `ShapeAppearance.M3.Sys.Shape.Corner.*` styles (via component-shape aliases like
 * `ShapeAppearance.M3.Comp.FilledButton.Container.Shape`), each just a `cornerSize` value.
 * Bumping those few central values is a pure resource edit — no bytecode, no risk of the
 * verifier/register issues this project has otherwise hit — and cascades to every
 * component using the M3 shape system at once.
 *
 * Deliberately leaves `Corner.None` (0dp, used for full-bleed containers like the bottom
 * app bar) and `Corner.Full` (50%, used for pills/switches/circular indicators) untouched
 * — both are semantically "no corner radius" and "fully round" respectively, not points on
 * the size scale this patch is widening.
 *
 * This is a first, deliberately small/reversible experiment (see the
 * experiment/m3-expressive-redesign branch) meant to be built and checked on a real
 * device before deciding whether/how far to take the rest of a visual redesign — resource
 * XML edits like this one are still never applied by CI (see project memory), so whether
 * this actually reads as "more expressive" across real screens, versus just being a
 * no-op because most of the app's own custom views don't reference these tokens at all,
 * can only be confirmed by looking at the app itself.
 */
val expressiveShapesPatch = resourcePatch(
    name = "Expressive shapes (experimental)",
    description = "Experimental: widens Sync's central Material shape-corner tokens " +
        "(Small/Medium/Large/ExtraLarge) to a bigger, rounder scale, loosely inspired by " +
        "Material 3 Expressive. Cascades to every component using the M3 shape system.",
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        document("res/values/styles.xml").use { document ->
            // dp value -> (original, widened)
            val widenedCornerSizes = mapOf(
                "ShapeAppearance.M3.Sys.Shape.Corner.Small" to ("8.0dp" to "12.0dp"),
                "ShapeAppearance.M3.Sys.Shape.Corner.Medium" to ("12.0dp" to "20.0dp"),
                "ShapeAppearance.M3.Sys.Shape.Corner.Large" to ("16.0dp" to "28.0dp"),
                "ShapeAppearance.M3.Sys.Shape.Corner.ExtraLarge" to ("28.0dp" to "36.0dp"),
            )

            val styles = document.documentElement.getElementsByTagName("style")
            val found = mutableSetOf<String>()

            for (i in 0 until styles.length) {
                val style = styles.item(i) as? Element ?: continue
                val name = style.getAttribute("name")
                val target = widenedCornerSizes[name] ?: continue
                val (expectedOriginal, widened) = target

                val items = style.getElementsByTagName("item")
                var cornerSizeItem: Element? = null
                for (j in 0 until items.length) {
                    val item = items.item(j) as? Element ?: continue
                    if (item.getAttribute("name") == "cornerSize") {
                        cornerSizeItem = item
                        break
                    }
                }
                val item = cornerSizeItem ?: error(
                    "Could not find the cornerSize item inside \"$name\" in styles.xml. " +
                        "This build's resource structure may differ from what was " +
                        "inspected — re-check with apktool.",
                )

                if (item.textContent != expectedOriginal) {
                    error(
                        "Expected \"$name\"'s cornerSize to be \"$expectedOriginal\", found " +
                            "\"${item.textContent}\". This build's resource structure may " +
                            "differ from what was inspected — re-check with apktool.",
                    )
                }
                item.textContent = widened
                found += name
            }

            if (found != widenedCornerSizes.keys) {
                error(
                    "Expected to widen ${widenedCornerSizes.keys}, only found $found in " +
                        "styles.xml. This build's resource structure may differ from what " +
                        "was inspected — re-check with apktool.",
                )
            }
        }
    }
}

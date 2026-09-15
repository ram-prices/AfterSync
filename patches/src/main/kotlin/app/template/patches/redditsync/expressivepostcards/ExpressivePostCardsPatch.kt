package app.template.patches.redditsync.expressivepostcards

import app.morphe.patcher.patch.resourcePatch

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Part of the "expressive redesign" experiment (see expressiveShapesPatch.kt and project
 * chat history for the scoping work behind this). Confirmed by hand via apktool that the
 * post feed's own row layout (res/layout/holder_simple.xml, inflated by
 * ui.viewholders.posts.simple.SimpleHolder) has NO background/card treatment at all in the
 * stock app — its root (`SimpleLinearLayout`) is a plain flat container with only padding,
 * separated from neighboring posts by a thin RecyclerView divider. This is confirmed to be
 * why every prior shape/spacing patch in this experiment, despite each one being verified
 * (via pulling the installed APK back off a real device and decompiling it) to correctly
 * apply its resource edits, still read as "no visible difference" while just scrolling the
 * main feed — none of those edits touch anything the feed's own rows actually use. This is
 * the first patch in this experiment that changes the main feed's own visual structure
 * rather than an existing-but-unused-here shape token.
 *
 * Adds an actual rounded card background to each post row plus margin between rows, so
 * feed posts read as distinct floating cards rather than a flat divided list — the single
 * biggest, most-visible piece of the "expressive" look, since the post feed is what's on
 * screen the overwhelming majority of the time in this app.
 *
 * The new drawable is written directly into the resource workspace via `get(path, copy =
 * true)` rather than edited from an existing file, since no existing drawable in this app
 * is both a plain single-layer rounded rect AND uses a theme-correct (light/dark aware)
 * surface color — this project has not added a brand-new resource file before, so this is
 * unverified until tested on a real device, same as every other new technique tried during
 * this experiment.
 */
val expressivePostCardsPatch = resourcePatch(
    name = "Expressive post cards (experimental)",
    description = "Experimental: gives each post in the feed an actual rounded card " +
        "background and spacing between posts, instead of a flat divided list — the post " +
        "feed's own row layout has no card treatment at all in the stock app.",
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        val cardBackgroundFile = get("res/drawable/expressive_post_card_background.xml", true)
        cardBackgroundFile.parentFile?.mkdirs()
        cardBackgroundFile.writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
                    <solid android:color="?attr/colorSurface" />
                    <corners android:radius="24dp" />
                </shape>
            """.trimIndent(),
        )

        document("res/layout/holder_simple.xml").use { document ->
            val root = document.documentElement
            val expectedTag = "com.laurencedawson.reddit_sync.ui.views.posts.simple.SimpleLinearLayout"
            if (root.tagName != expectedTag) {
                error(
                    "Expected holder_simple.xml's root element to be \"$expectedTag\", " +
                        "found \"${root.tagName}\". This build's resource structure may " +
                        "differ from what was inspected — re-check with apktool.",
                )
            }

            root.setAttribute("android:background", "@drawable/expressive_post_card_background")
            root.setAttribute("android:elevation", "4dp")
            root.setAttribute("android:layout_marginLeft", "12dp")
            root.setAttribute("android:layout_marginRight", "12dp")
            root.setAttribute("android:layout_marginTop", "8dp")
            root.setAttribute("android:layout_marginBottom", "8dp")
        }
    }
}

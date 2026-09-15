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
 * separated from neighboring posts by a thin RecyclerView divider.
 *
 * A first version of this patch set `android:background`/`android:elevation`/margins
 * directly on the existing `SimpleLinearLayout` root, pointing its background at a new,
 * from-scratch rounded-rect drawable (written via `get(path, copy = true)` — the first
 * time this project added a brand-new resource file rather than editing one). Confirmed on
 * a real device that this correctly created visible cards with real gaps between posts —
 * but zoomed-in inspection of the actual corner pixels showed it rendering with hard,
 * perfectly square corners despite the drawable resource itself (re-verified by pulling the
 * installed APK and decompiling it) correctly containing `<corners android:radius="24dp"/>`.
 * `SimpleLinearLayout` has no custom draw/background overrides of its own (confirmed via
 * its smali — it's a plain LinearLayoutCompat subclass with only swipe-related methods),
 * and neither SimpleHolder nor its superclass sets a background programmatically, so the
 * exact cause of the plain `android:background` approach silently ignoring its own
 * drawable's corner radius was never pinned down.
 *
 * This version sidesteps the mystery entirely by wrapping the existing row in a real
 * `androidx.cardview.widget.CardView` instead of styling the row itself — CardView is a
 * long-established, purpose-built component for exactly this (its `cardCornerRadius`
 * governs actual content clipping, not just a background drawable's own paint shape), and
 * this app already bundles it (confirmed: `cardview_default_radius` and `Base.CardView` are
 * real, present resources, already touched by expressiveShapesPatch.kt). Confirmed safe to
 * wrap: `SimpleHolder` resolves all of its view references — including `root` itself,
 * typed as `SimpleLinearLayout` — via Butterknife `@BindView`-generated `findViewById`
 * calls, never by assuming the inflated item view IS a particular type, so introducing a
 * new CardView ancestor around the (unchanged, still `id="root"`) SimpleLinearLayout does
 * not affect how the holder finds any of its bound views.
 *
 * Also reduces the gap between cards from the first version (12dp/8dp) to a tighter 8dp/4dp
 * per explicit follow-up request — the first version's spacing read as too loose once cards
 * were actually visible.
 */
val expressivePostCardsPatch = resourcePatch(
    name = "Expressive post cards (experimental)",
    description = "Experimental: wraps each post in the feed in a real rounded CardView " +
        "with spacing between posts, instead of a flat divided list — the post feed's own " +
        "row layout has no card treatment at all in the stock app.",
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        document("res/layout/holder_simple.xml").use { document ->
            val oldRoot = document.documentElement
            val expectedTag = "com.laurencedawson.reddit_sync.ui.views.posts.simple.SimpleLinearLayout"
            if (oldRoot.tagName != expectedTag) {
                error(
                    "Expected holder_simple.xml's root element to be \"$expectedTag\", " +
                        "found \"${oldRoot.tagName}\". This build's resource structure may " +
                        "differ from what was inspected — re-check with apktool.",
                )
            }

            // The namespace declarations live on the document root; they need to move to
            // the new outermost element (the CardView) rather than stay on the now-nested
            // SimpleLinearLayout, so app:card... attributes on the CardView resolve.
            val androidNs = oldRoot.getAttribute("xmlns:android")
            val appNs = oldRoot.getAttribute("xmlns:app")
            oldRoot.removeAttribute("xmlns:android")
            oldRoot.removeAttribute("xmlns:app")

            val cardView = document.createElement("androidx.cardview.widget.CardView")
            cardView.setAttribute("xmlns:android", androidNs)
            cardView.setAttribute("xmlns:app", appNs)
            cardView.setAttribute("android:layout_width", "match_parent")
            cardView.setAttribute("android:layout_height", "wrap_content")
            cardView.setAttribute("android:layout_marginLeft", "8dp")
            cardView.setAttribute("android:layout_marginRight", "8dp")
            cardView.setAttribute("android:layout_marginTop", "4dp")
            cardView.setAttribute("android:layout_marginBottom", "4dp")
            cardView.setAttribute("app:cardCornerRadius", "24dp")
            cardView.setAttribute("app:cardElevation", "4dp")
            cardView.setAttribute("app:cardBackgroundColor", "?attr/colorSurface")
            cardView.setAttribute("app:cardPreventCornerOverlap", "true")
            cardView.setAttribute("app:cardUseCompatPadding", "false")

            document.replaceChild(cardView, oldRoot)
            cardView.appendChild(oldRoot)
        }
    }
}

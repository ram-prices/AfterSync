package app.template.patches.redditsync.centerpostmedia

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.redditsync.centerpostmedia.fingerprints.htmlTextViewRenderFingerprint
import app.template.patches.redditsync.centerpostmedia.fingerprints.optionsSetPostFingerprint
import app.template.patches.redditsync.centerpostmedia.fingerprints.spannableBuilderAddSpanFingerprint
import app.template.patches.redditsync.fixpostbodyimages.fingerprints.postCommentHolderBindFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * Target app: Sync for Reddit (com.laurencedawson.reddit_sync), v23.06.30-13:39.
 *
 * Per explicit request: media embedded in a post's own body should always be centered
 * horizontally; media in comments should stay left-aligned as it already is.
 *
 * The HTML→Spanned pipeline (SyncHtmlToSpannedConverter, patched extensively by
 * fixCommentImageSizingPatch/fixPreviewImagesPatch/fixCommentImageDimensionsPatch) is shared
 * between posts and comments and has no notion of "which screen is this for" threaded through
 * its internals — deliberately not touched here, both because it's already proven fragile to
 * new branches (see feedback in fixPreviewImagesPatch.kt's history) and because it doesn't
 * need to be: every image/GIF/video span this pipeline creates gets queued (not yet applied)
 * on a Loc/c; ("SpannableTextViewStringBuilder") builder object via Loc/c;->s(...), and that
 * queue is only actually applied — via Loc/c;->d(), called from Loc/b;->F() — AFTER the whole
 * HTML parse finishes. That gap, entirely outside the parser, is where this patch adds
 * centering: once parsing is done, walk the already-built queue, find every entry whose span
 * is one of the app's media span types, and queue an additional AlignmentSpan.Standard(CENTER)
 * over that same [start, end) range.
 *
 * The one thing needed to make this post-only: a new boolean field on Lnc/a; ("Options.java",
 * the render-options object both PostCommentHolder and CommentsHtmlTextView build before
 * rendering), set true only by PostCommentHolder (posts) and left at its default false
 * everywhere else (comments) — mirroring the existing d(I)/e(Lxa/d;) builder-style setters
 * already on that class.
 *
 * Implementation notes on where each piece lives, and why:
 * - The centering LOGIC (a loop + several instanceof checks + a conditional queue-add) is a
 *   brand-new STATIC method added to Loc/c; itself (`maybeCenterMediaSpans(Loc/c;Lnc/a;)V`,
 *   taking the builder as an explicit first param rather than as an instance method's
 *   implicit "this"), built from scratch via ImmutableMethod/ImmutableMethodImplementation
 *   rather than spliced into any existing method — per the hard lesson from
 *   fixPreviewImagesPatch.kt's two verifier crashes this same project: a new branch/loop is
 *   only safe inside a method with its own fresh register types, never spliced into
 *   pre-optimized existing code. It's added to Loc/c; specifically (not some unrelated
 *   class) so it can freely `iget`/`iput` Loc/c$a;'s fields exactly like Loc/c;'s own
 *   existing methods already do — legal only because it's compiled as part of the same
 *   class. Static, like every other from-scratch helper in this project, was not just a
 *   style choice: a first attempt made it a plain public instance method and added it to
 *   `directMethods` — but dex's "direct" method category is only for static/private/
 *   constructor methods, and a non-static, non-private, non-constructor method belongs in
 *   `virtualMethods` instead. Getting this wrong doesn't just break one class — it corrupts
 *   the dex file badly enough that ART refuses to load ANY class from it, confirmed via a
 *   real device crash log: `Direct/virtual method ... not in expected list`, with the whole
 *   app failing to even instantiate its own Application subclass. Making both new methods
 *   static (taking their "receiver" as an explicit first parameter instead) sidesteps the
 *   direct/virtual distinction entirely, the same way every prior helper in this project
 *   already does.
 * - The call site inside HtmlTextView.H(Lnc/a;Ljava/lang/String;)V (an existing method) is
 *   two straight-line instructions with zero new branches: read the new boolean field, then
 *   unconditionally call the helper — inserted between the existing parse call and the
 *   existing finalize call.
 * - The call site inside PostCommentHolder.h(Lxa/d;I)V that flips the new field to true is
 *   anchored on Lnc/a;'s constructor call (unique in that method), a separate, independent
 *   anchor from fixPostBodyImagesPatch's own edit to this same method — both patches only add
 *   instructions (never remove any here), so they compose safely in either order without a
 *   dependsOn.
 */
val centerPostMediaPatch = bytecodePatch(
    name = "Center media in posts",
    description = "Centers images, GIFs, and videos embedded in a post's own body " +
        "horizontally. Media in comments stays left-aligned.",
    default = true,
) {
    compatibleWith("com.laurencedawson.reddit_sync"("v23.06.30-13:39"))

    execute {
        // 1. Add the new "is this render for a post?" field + builder-style setter to Lnc/a;.
        val optionsClass = optionsSetPostFingerprint.classDef
        optionsClass.instanceFields.add(
            MutableField(
                ImmutableField(
                    "Lnc/a;",
                    "isPost",
                    "Z",
                    AccessFlags.PUBLIC.value,
                    null,
                    emptySet(),
                    null,
                ),
            ),
        )

        // Static (matching every other from-scratch helper in this project), taking the
        // Lnc/a; receiver as an explicit first param rather than as an instance method's
        // implicit "this". This isn't just style: a plain public INSTANCE method belongs in
        // dexlib2's virtualMethods, not directMethods (dex's "direct" category is only for
        // static/private/constructor methods) — adding one to directMethods (as first
        // attempted here) corrupts the dex file badly enough that ART refuses to load ANY
        // class from it at all ("Direct/virtual method ... not in expected list"), confirmed
        // via a real device crash log showing the whole app failing to even instantiate its
        // Application class. Static sidesteps the direct/virtual distinction entirely.
        //
        // TEMPORARY diagnostic: log whether setPost is even reached and whether the field
        // write sticks when read back immediately — maybeCenterMediaSpans always sees
        // isPost=false on-device, so this narrows down whether the bug is in the write side
        // (this method / its call site in PostCommentHolder.h()) or something stranger.
        //
        // 5 registers = 3 locals (v0-v2, only needed for the log building) + 2 params.
        val setIsPostDefinition = ImmutableMethod(
            "Lnc/a;",
            "setPost",
            listOf(
                ImmutableMethodParameter("Lnc/a;", emptySet(), "options"),
                ImmutableMethodParameter("Z", emptySet(), "isPost"),
            ),
            "Lnc/a;",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(5, emptyList(), emptyList(), emptyList()),
        )
        val setIsPost = MutableMethod(setIsPostDefinition)
        setIsPost.addInstructions(
            """
                iput-boolean p1, p0, Lnc/a;->isPost:Z

                new-instance v0, Ljava/lang/StringBuilder;
                invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                const-string v1, "CenterPostMedia: setPost called with isPost="
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
                const-string v1, ", read back as="
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                iget-boolean v2, p0, Lnc/a;->isPost:Z
                invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
                invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v0
                const-string v1, "CenterPostMedia"
                invoke-static {v1, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

                return-object p0
            """,
        )
        optionsClass.directMethods.add(setIsPost)

        // 2. Add the centering-logic helper to Loc/c;, reusing its existing span-queuing
        // method (s(Ljava/lang/Object;III)V) and its existing span queue (field "b").
        val builderClass = spannableBuilderAddSpanFingerprint.classDef

        // Static, for the same dex direct/virtual reason as setPost above — takes the
        // Loc/c; builder as an explicit first param instead of as an instance method's
        // implicit "this".
        //
        // TEMPORARY diagnostic build: centering wasn't visibly taking effect on-device with
        // no crash and no logged exception, so this adds a few log lines via this app's own
        // existing "SPANS"-style logger (Lwc/i;->e(String), already used throughout
        // Loc/c;/Loc/b; for exactly this kind of span tracing) to find out, with real
        // evidence, which of "never called" / "isPost never true" / "queue empty" /
        // "no span matched" is actually happening — instead of guessing further. Remove
        // once the real cause is found.
        //
        // 13 registers = 11 locals (v0-v10) + 2 params (the builder, the options object) —
        // takes the whole Lnc/a; object (rather than a plain boolean) specifically so the
        // call site inside HtmlTextView.H() (which only declares .locals 1) never needs a
        // second scratch register: it just forwards its own untouched Lnc/a; parameter
        // unchanged.
        val centerHelperImpl = ImmutableMethodImplementation(13, emptyList(), emptyList(), emptyList())
        val centerHelperDefinition = ImmutableMethod(
            "Loc/c;",
            "maybeCenterMediaSpans",
            listOf(
                ImmutableMethodParameter("Loc/c;", emptySet(), "builder"),
                ImmutableMethodParameter("Lnc/a;", emptySet(), "options"),
            ),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            centerHelperImpl,
        )
        val centerHelper = MutableMethod(centerHelperDefinition)
        centerHelper.addInstructions(
            """
                iget-boolean v0, p1, Lnc/a;->isPost:Z

                new-instance v9, Ljava/lang/StringBuilder;
                invoke-direct {v9}, Ljava/lang/StringBuilder;-><init>()V
                const-string v10, "CenterPostMedia: isPost="
                invoke-virtual {v9, v10}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v9, v0}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
                invoke-virtual {v9}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v9
                const-string v10, "CenterPostMedia"
                invoke-static {v10, v9}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

                if-eqz v0, :done

                iget-object v0, p0, Loc/c;->b:Ljava/util/ArrayList;
                invoke-virtual {v0}, Ljava/util/ArrayList;->size()I
                move-result v1

                new-instance v9, Ljava/lang/StringBuilder;
                invoke-direct {v9}, Ljava/lang/StringBuilder;-><init>()V
                const-string v10, "CenterPostMedia: queueSize="
                invoke-virtual {v9, v10}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v9, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
                invoke-virtual {v9}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v9
                const-string v10, "CenterPostMedia"
                invoke-static {v10, v9}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

                const/4 v2, 0x0

                :loop
                if-ge v2, v1, :done

                invoke-virtual {v0, v2}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;
                move-result-object v3
                check-cast v3, Loc/c${'$'}a;

                iget-object v4, v3, Loc/c${'$'}a;->d:Ljava/lang/Object;

                new-instance v9, Ljava/lang/StringBuilder;
                invoke-direct {v9}, Ljava/lang/StringBuilder;-><init>()V
                const-string v10, "CenterPostMedia: queue entry class="
                invoke-virtual {v9, v10}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v4}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
                move-result-object v10
                invoke-virtual {v9, v10}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;
                invoke-virtual {v9}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v9
                const-string v10, "CenterPostMedia"
                invoke-static {v10, v9}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

                instance-of v5, v4, Lnb/b;
                if-nez v5, :is_media
                instance-of v5, v4, Lnb/c;
                if-nez v5, :is_media
                instance-of v5, v4, Lnb/d;
                if-nez v5, :is_media
                instance-of v5, v4, Lnb/f;
                if-nez v5, :is_media
                instance-of v5, v4, Lnb/g;
                if-nez v5, :is_media
                instance-of v5, v4, Lnb/h;
                if-eqz v5, :next

                :is_media
                const-string v9, "CenterPostMedia"
                const-string v10, "CenterPostMedia: MATCHED, queuing AlignmentSpan"
                invoke-static {v9, v10}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
                new-instance v5, Landroid/text/style/AlignmentSpan${'$'}Standard;
                sget-object v6, Landroid/text/Layout${'$'}Alignment;->ALIGN_CENTER:Landroid/text/Layout${'$'}Alignment;
                invoke-direct {v5, v6}, Landroid/text/style/AlignmentSpan${'$'}Standard;-><init>(Landroid/text/Layout${'$'}Alignment;)V

                iget v6, v3, Loc/c${'$'}a;->a:I
                iget v7, v3, Loc/c${'$'}a;->b:I
                iget v8, v3, Loc/c${'$'}a;->c:I

                invoke-virtual {p0, v5, v6, v7, v8}, Loc/c;->s(Ljava/lang/Object;III)V

                :next
                add-int/lit8 v2, v2, 0x1
                goto :loop

                :done
                return-void
            """,
        )
        builderClass.directMethods.add(centerHelper)

        // 3. Wire it into HtmlTextView.H(...): read the new field, call the helper, all
        // between the existing parse call and the existing finalize call — no new branches
        // added to this existing method at all.
        val renderMethod = htmlTextViewRenderFingerprint.method
        val renderImplementation = renderMethod.implementation!!

        val parseCallIndex = renderImplementation.instructions.indexOfFirst { instruction ->
            (instruction as? ReferenceInstruction)?.reference.let {
                (it as? MethodReference)?.let { ref ->
                    ref.definingClass == "Lnc/c;" && ref.name == "a"
                } == true
            }
        }

        if (parseCallIndex == -1) {
            error(
                "Could not find the HTML-parse call in HtmlTextView.H(). This build's " +
                    "method structure may differ from what was inspected — re-check with " +
                    "apktool.",
            )
        }

        // v0 already holds the Loc/c; builder at this point (moved there right before this
        // call); p1 is this method's own Lnc/a; parameter, untouched up to here. This method
        // declares only .locals 1 (v0), so the call forwards p1 as-is rather than needing any
        // extra scratch register.
        renderMethod.addInstructions(
            parseCallIndex + 1,
            "invoke-static {v0, p1}, Loc/c;->maybeCenterMediaSpans(Loc/c;Lnc/a;)V",
        )

        // TEMPORARY diagnostic: setPost is never logging at all (not just returning
        // isPost=false) — meaning either it's never being called from
        // PostCommentHolder.h(), or that whole method isn't reached the way expected for
        // this exact render. A zero-arg-friendly helper (takes only an int checkpoint ID,
        // so it needs no registers from the tightly-constrained 2-local h() beyond one
        // already free at each call site) placed at two points — the very top of h(), and
        // right after the setPost call — will show which one is actually failing.
        val logCheckpointDefinition = ImmutableMethod(
            "Lnc/a;",
            "logCenterCheckpoint",
            listOf(ImmutableMethodParameter("I", emptySet(), "checkpoint")),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(3, emptyList(), emptyList(), emptyList()),
        )
        val logCheckpoint = MutableMethod(logCheckpointDefinition)
        logCheckpoint.addInstructions(
            """
                new-instance v0, Ljava/lang/StringBuilder;
                invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                const-string v1, "CenterPostMedia: checkpoint "
                invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
                invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v0
                const-string v1, "CenterPostMedia"
                invoke-static {v1, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
                return-void
            """,
        )
        optionsClass.directMethods.add(logCheckpoint)

        // 4. Flip the new field to true only for posts, anchored on Lnc/a;'s no-arg
        // constructor call (unique in this method) — independent of wherever
        // fixPostBodyImagesPatch's own edit to this same method ends up.
        val bindMethod = postCommentHolderBindFingerprint.method
        val bindImplementation = bindMethod.implementation!!

        // Checkpoint 1: is h() reached at all for this render? Inserted before the
        // original first instruction — v0 is safe scratch here since nothing has run yet
        // and the original code overwrites it immediately anyway.
        bindMethod.addInstructions(
            0,
            """
                const/4 v0, 0x1
                invoke-static {v0}, Lnc/a;->logCenterCheckpoint(I)V
            """,
        )

        val optionsInitIndex = bindImplementation.instructions.indexOfFirst { instruction ->
            (instruction as? ReferenceInstruction)?.reference.let {
                (it as? MethodReference)?.let { ref ->
                    ref.definingClass == "Lnc/a;" && ref.name == "<init>" && ref.parameterTypes.isEmpty()
                } == true
            }
        }

        if (optionsInitIndex == -1) {
            error(
                "Could not find the \"new Lnc/a;()\" render-options construction in " +
                    "PostCommentHolder.h(). This build's method structure may differ from " +
                    "what was inspected — re-check with apktool.",
            )
        }

        bindMethod.addInstructions(
            optionsInitIndex + 1,
            """
                const/4 v1, 0x1
                invoke-static {v0, v1}, Lnc/a;->setPost(Lnc/a;Z)Lnc/a;
                const/4 v1, 0x2
                invoke-static {v1}, Lnc/a;->logCenterCheckpoint(I)V
            """,
        )
    }
}

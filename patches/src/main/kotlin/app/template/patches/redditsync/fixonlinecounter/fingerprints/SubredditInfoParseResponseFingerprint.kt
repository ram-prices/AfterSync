package app.template.patches.redditsync.fixonlinecounter.fingerprints

import app.morphe.patcher.fingerprint

/**
 * Matches Ly8/y0;->parseNetworkResponse(Lcom/android/volley/NetworkResponse;)Lcom/android/volley/Response;
 * ("OAuthSubredditInfoRequest.java") — parses a subreddit's /about response, including the
 * "number of users online" count this project's fix targets.
 */
val subredditInfoParseResponseFingerprint = fingerprint {
    returns("Lcom/android/volley/Response;")
    parameters("Lcom/android/volley/NetworkResponse;")
    custom { method, classDef ->
        classDef.type == "Ly8/y0;" && method.name == "parseNetworkResponse"
    }
}

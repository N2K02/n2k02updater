package dev.n2k02.updater

import java.io.IOException

/** Published metadata remains authoritative when reachable, including a null result. */
internal object UpdateSourceFallback {
    fun resolve(
        published: (() -> UpdateRelease?)?,
        github: () -> UpdateRelease?,
    ): UpdateRelease? {
        if (published == null) return github()
        return try {
            published()
        } catch (error: IOException) {
            val candidate = github()
            if (candidate != null && !candidate.sha256.orEmpty().matches(Regex("[a-fA-F0-9]{64}"))) {
                throw IOException("GitHub fallback release has no valid SHA-256 checksum", error)
            }
            candidate
        }
    }
}

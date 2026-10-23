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
            github()
        }
    }
}

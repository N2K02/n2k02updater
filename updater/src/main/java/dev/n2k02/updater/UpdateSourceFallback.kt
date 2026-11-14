package dev.n2k02.updater

/**
 * Public GitHub Releases are authoritative for stable and experimental channels.
 * Retain the legacy [published] argument for source compatibility with existing
 * host apps; it is intentionally not called, so stale website manifests cannot
 * override an app's releases or require ANDROID_DISTRIBUTION_TOKEN.
 */
internal object UpdateSourceFallback {
    fun resolve(
        @Suppress("UNUSED_PARAMETER") published: (() -> UpdateRelease?)?,
        github: () -> UpdateRelease?,
    ): UpdateRelease? = github()
}

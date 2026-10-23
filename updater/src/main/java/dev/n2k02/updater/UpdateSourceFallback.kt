package dev.n2k02.updater

/**
 * Published manifests are authoritative whenever a host configures one.
 *
 * A GitHub release tag is human-facing metadata and cannot safely represent an
 * Android APK's manifest versionCode. The legacy GitHub source is therefore
 * used only by hosts that have not configured a published manifest source.
 */
internal object UpdateSourceFallback {
    fun resolve(
        published: (() -> UpdateRelease?)?,
        github: () -> UpdateRelease?,
    ): UpdateRelease? = if (published != null) published() else github()
}

package dev.n2k02.updater

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

/**
 * Public GitHub Releases adapter. No GitHub token is embedded in consuming apps.
 * Both stable releases and experimental prereleases are read directly without a token.
 */
class GitHubReleaseSource(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) {
    fun latest(config: UpdateConfig): UpdateRelease? {
        if (config.releaseTrack != null) {
            // Fail closed: never use /latest or legacy unscoped releases for a tracked APK.
            val releases = fetchReleases(config.repository, latest = false) ?: return null
            return parseTrackedReleases(releases, config)
        }
        val stable = fetchReleases(config.repository, latest = true)
            ?.let { parseRelease(it, config.installedVersionCode, repository = config.repository) }
        if (!config.experimentalEnabled) return stable
        val experimental = fetchReleases(config.repository, latest = false)
            ?.let { parseExperimentalReleases(it, config.installedVersionCode) }
        return listOfNotNull(stable, experimental).maxByOrNull { it.versionCode }
    }

    private fun fetchReleases(repository: String, latest: Boolean): String? {
        // Fetch the first page only: it contains recent published experimental builds.
        // GitHub's /latest endpoint excludes prereleases by design.
        val url = URL("https://api.github.com/repos/$repository/releases" + if (latest) "/latest" else "?per_page=100")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "N2K02Updater-Android")
            val status = connection.responseCode
            if (status == 404) return null
            if (status != 200) throw IOException("GitHub release request failed (HTTP $status)")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val sha256Pattern = Regex("sha256:([a-fA-F0-9]{64})")

        /** The stable tag encodes the version. Experimental tags encode PR and run. */
        fun belongsToTrack(tag: String, assetName: String, track: String): Boolean {
            val architecture = Regex.escape(track)
            val stable = Regex("^$architecture-(?:sdcleaner-)?v([0-9]+[.][0-9]+)$", RegexOption.IGNORE_CASE)
            val experimental = Regex("^experimental-$architecture-pr-[0-9]+-run-[0-9]+$", RegexOption.IGNORE_CASE)
            val version = stable.matchEntire(tag)?.groupValues?.get(1)
            val suffix = when {
                version != null -> Regex.escape(version)
                experimental.matches(tag) -> "[0-9]+[.][0-9]+-experimental[.][0-9]+"
                else -> return false
            }
            return Regex("^[A-Za-z0-9_][A-Za-z0-9_-]*-$architecture-$suffix[.]apk$", RegexOption.IGNORE_CASE)
                .matches(assetName)
        }

        private fun isCanonicalAssetUrl(url: String, repository: String, tag: String, name: String): Boolean {
            val prefix = "https://github.com/$repository/releases/download/"
            return url.startsWith(prefix, ignoreCase = true) && url.substring(prefix.length) == "$tag/$name"
        }

        fun parseTrackedReleases(json: String, config: UpdateConfig): UpdateRelease? {
            val track = requireNotNull(config.releaseTrack)
            val releases = runCatching { JSONArray(json) }.getOrNull() ?: return null
            return (0 until releases.length()).mapNotNull { index ->
                val release = releases.optJSONObject(index) ?: return@mapNotNull null
                if (release.opt("draft") != false) return@mapNotNull null
                val tag = release.optString("tag_name")
                val experimental = release.opt("prerelease") as? Boolean ?: return@mapNotNull null
                if (experimental != tag.startsWith("experimental-", ignoreCase = true) ||
                    experimental && !config.experimentalEnabled
                ) return@mapNotNull null
                val code = trackedVersionCode(release.optString("body")) ?: return@mapNotNull null
                if (code <= config.installedVersionCode) return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .singleOrNull {
                        val name = it.optString("name")
                        name.startsWith(config.repository.substringAfter('/') + "-", ignoreCase = true) &&
                            belongsToTrack(tag, name, track) &&
                            isCanonicalAssetUrl(it.optString("browser_download_url"), config.repository, tag, name)
                    } ?: return@mapNotNull null
                val sha = sha256Pattern.matchEntire(apk.optString("digest"))?.groupValues?.get(1)?.lowercase()
                    ?: return@mapNotNull null
                UpdateRelease(
                    tag = tag,
                    versionCode = code,
                    notes = release.optString("body").lineSequence()
                        .map { it.trim().removePrefix("- ").removePrefix("* ") }
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .take(12).toList(),
                    apkUrl = apk.optString("browser_download_url"),
                    apkSize = apk.optLong("size").takeIf { it > 0 },
                    sha256 = sha,
                    experimental = experimental,
                    publishedAtMillis = ReleaseDateParser.parse(release.optString("published_at")),
                )
            }.maxByOrNull { it.versionCode }
        }

        private fun trackedVersionCode(body: String): Long? =
            Regex("""(?im)^[ \t]*Version code:[ \t]*(\d+)[ \t]*$""")
                .findAll(body).singleOrNull()?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }

        fun releaseVersionCode(body: String): Long? =
            Regex("""(?im)^\s*Version code:\s*(\d+)\s*$""")
                .find(body)?.groupValues?.get(1)?.toLongOrNull()

        /** Existing experimental build tags encode versionCode as 100000 + run number. */
        fun experimentalVersionCode(tag: String): Long? {
            val run = Regex("""^experimental-pr-\d+-run-(\d+)$""", RegexOption.IGNORE_CASE)
                .matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull() ?: return null
            return if (run > 0 && run <= Long.MAX_VALUE - 100_000L) 100_000L + run else null
        }

        /** GitHub prereleases carry their Android versionCode in the release notes. */
        fun parseExperimentalReleases(json: String, installedVersionCode: Long): UpdateRelease? {
            val releases = JSONArray(json)
            return (0 until releases.length()).mapNotNull { index ->
                val release = releases.optJSONObject(index) ?: return@mapNotNull null
                if (release.optBoolean("draft") || !release.optBoolean("prerelease")) return@mapNotNull null
                val code = releaseVersionCode(release.optString("body"))
                    ?: experimentalVersionCode(release.optString("tag_name"))
                    ?: return@mapNotNull null
                if (code <= installedVersionCode) return@mapNotNull null
                val tag = release.optString("tag_name").trim()
                if (tag.isBlank()) return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull {
                        it.optString("name").endsWith(".apk", ignoreCase = true) &&
                            it.optString("browser_download_url")
                                .startsWith("https://github.com/")
                    } ?: return@mapNotNull null
                val digest = apk.optString("digest")
                val sha = digest.removePrefix("sha256:").lowercase()
                    .takeIf { digest.startsWith("sha256:") && it.matches(Regex("[a-f0-9]{64}")) }
                UpdateRelease(
                    tag = tag,
                    versionCode = code,
                    notes = release.optString("body").lineSequence()
                        .map { it.trim().removePrefix("- ").removePrefix("* ") }
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .take(12).toList(),
                    apkUrl = apk.optString("browser_download_url"),
                    apkSize = apk.optLong("size").takeIf { it > 0 },
                    sha256 = sha,
                    experimental = true,
                    publishedAtMillis = ReleaseDateParser.parse(release.optString("published_at")),
                )
            }.maxByOrNull { it.versionCode }
        }
        /**
         * Parse release metadata. A tag is not assumed to equal Android versionCode:
         * callers may provide an app-specific mapping when their version scheme differs.
         */
        fun parseRelease(
            json: String,
            installedVersionCode: Long,
            versionCodeFromTag: (String) -> Long? = ::defaultVersionCode,
            repository: String? = null,
        ): UpdateRelease? {
            val root = JSONObject(json)
            if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
            val tag = root.optString("tag_name").trim()
            if (tag.isBlank()) return null
            val assets = root.optJSONArray("assets") ?: return null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val downloadUrl = asset.optString("browser_download_url")
                if (!name.endsWith(".apk", ignoreCase = true) ||
                    !downloadUrl.startsWith("https://github.com/")) continue
                val digest = asset.optString("digest")
                val code = releaseVersionCode(root.optString("body"))
                    ?: HistoricalStableVersions.find(repository, tag, digest)
                    ?: versionCodeFromTag(tag) ?: continue
                if (code <= installedVersionCode) continue
                val sha = digest.removePrefix("sha256:").lowercase()
                    .takeIf { digest.startsWith("sha256:") && it.matches(Regex("[a-f0-9]{64}")) }
                return UpdateRelease(
                    tag = tag,
                    versionCode = code,
                    notes = root.optString("body").lineSequence()
                        .map { it.trim().removePrefix("- ").removePrefix("* ") }
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .take(12).toList(),
                    apkUrl = downloadUrl,
                    apkSize = asset.optLong("size").takeIf { it > 0 },
                    sha256 = sha,
                    experimental = false,
                    publishedAtMillis = ReleaseDateParser.parse(root.optString("published_at")),
                )
            }
            return null
        }

        /** Default mapping for v1.8 -> 18, matching the original mRadio convention. */
        fun defaultVersionCode(tag: String): Long? {
            val match = Regex("^(?:sdcleaner-)?v?(\\d+)\\.(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(tag)
                ?: return null
            val major = match.groupValues[1].toLongOrNull() ?: return null
            val minor = match.groupValues[2].toLongOrNull() ?: return null
            if (minor > 9 || major > Long.MAX_VALUE / 10) return null
            return major * 10 + minor
        }
    }
}

package dev.n2k02.updater

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Public GitHub Releases adapter. No GitHub token is embedded in consuming apps.
 * Experimental GitHub Actions artifacts require a separate authenticated/public manifest adapter.
 */
class GitHubReleaseSource(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) {
    fun latest(config: UpdateConfig): UpdateRelease? {
        val url = URL("https://api.github.com/repos/${config.repository}/releases/latest")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "N2K02Updater-Android")
            val status = connection.responseCode
            if (status == 404) return null
            if (status != 200) throw IOException("GitHub release request failed (HTTP $status)")
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            return parseRelease(json, config.installedVersionCode)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /**
         * Parse release metadata. A tag is not assumed to equal Android versionCode:
         * callers may provide an app-specific mapping when their version scheme differs.
         */
        fun parseRelease(
            json: String,
            installedVersionCode: Long,
            versionCodeFromTag: (String) -> Long? = ::defaultVersionCode,
        ): UpdateRelease? {
            val root = JSONObject(json)
            if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
            val tag = root.optString("tag_name").trim()
            if (tag.isBlank()) return null
            val code = versionCodeFromTag(tag) ?: return null
            if (code <= installedVersionCode) return null
            val assets = root.optJSONArray("assets") ?: return null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val downloadUrl = asset.optString("browser_download_url")
                if (!name.endsWith(".apk", ignoreCase = true) ||
                    !downloadUrl.startsWith("https://github.com/")) continue
                val digest = asset.optString("digest")
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
                )
            }
            return null
        }

        /** Default mapping for v1.8 -> 18, matching the original mRadio convention. */
        fun defaultVersionCode(tag: String): Long? {
            val match = Regex("^v?(\\d+)\\.(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(tag)
                ?: return null
            val major = match.groupValues[1].toLongOrNull() ?: return null
            val minor = match.groupValues[2].toLongOrNull() ?: return null
            if (minor > 9 || major > Long.MAX_VALUE / 10) return null
            return major * 10 + minor
        }
    }
}

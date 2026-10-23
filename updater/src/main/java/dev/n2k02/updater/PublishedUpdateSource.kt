package dev.n2k02.updater

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Reads signed-release metadata published as public JSON by each app's CI.
 * Unlike GitHub Actions artifact APIs, this needs no secret in the Android APK.
 * The APK's SHA-256 and signing certificate are verified by VerifiedApkDownloader.
 */
class PublishedUpdateSource(private val manifestBaseUrl: String) {
    init { require(manifestBaseUrl.startsWith("https://")) }

    fun latest(config: UpdateConfig): UpdateRelease? {
        val channel = if (config.experimentalEnabled) "experimental" else "stable"
        val connection = openSecureManifest("${manifestBaseUrl.trimEnd('/')}/$channel.json")
        try {
            // openSecureManifest configures headers and follows HTTPS-only redirects.
            // Accessing responseCode there already connects; setters here would throw.
            val status = connection.responseCode
            if (status == 404) return null
            if (status != 200) throw IOException("Update manifest unavailable (HTTP $status)")
            return parse(connection.inputStream.bufferedReader().use { it.readText() },
                config.installedVersionCode, config.experimentalEnabled)
        } finally { connection.disconnect() }
    }

    private fun openSecureManifest(source: String): HttpURLConnection {
        var target = URL(source)
        repeat(6) {
            if (target.protocol != "https") throw IOException("Insecure manifest redirect rejected")
            val connection = target.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "N2K02Updater-Android")
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IOException("Manifest redirect has no destination")
                val next = URL(target, location)
                connection.disconnect()
                target = next
            } else {
                return connection
            }
        }
        throw IOException("Too many manifest redirects")
    }

    companion object {
        fun parse(json: String, installedVersionCode: Long, experimental: Boolean): UpdateRelease? {
            val data = JSONObject(json)
            val code = data.optLong("versionCode")
            if (code <= installedVersionCode) return null
            val url = data.optString("apkUrl")
            val digest = data.optString("sha256").lowercase()
            if (!url.startsWith("https://") || !digest.matches(Regex("[a-f0-9]{64}"))) {
                throw IOException("Invalid update manifest: APK URL or SHA-256")
            }
            val notes = data.optJSONArray("notes")
            return UpdateRelease(
                tag = data.getString("tag"),
                versionCode = code,
                notes = notes?.let { array ->
                    (0 until array.length()).map { array.optString(it) }.take(12)
                } ?: emptyList(),
                apkUrl = url,
                apkSize = data.optLong("apkSize").takeIf { it > 0 },
                sha256 = digest,
                experimental = experimental,
            )
        }
    }
}

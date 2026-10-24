package dev.n2k02.updater

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads into app-private cache and validates APK identity before it is exposed to an installer.
 * This does not grant install permission or start an installation.
 */
class VerifiedApkDownloader(private val context: Context) {
    /**
     * Resume interrupted downloads only when the server honors HTTP Range.
     * Partial bytes are scoped to the release version AND expected SHA-256 and
     * are never installable until the complete file is rehashed and verified.
     */
    suspend fun download(
        release: UpdateRelease,
        config: UpdateConfig,
        onProgress: (Float?) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        require(release.apkUrl.startsWith("https://")) { "Only HTTPS downloads are supported" }
        val expectedHash = release.sha256
            ?: throw IOException("No SHA-256 digest supplied for this release")
        require(expectedHash.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid release checksum" }
        val directory = File(context.cacheDir, "n2k02-updates").apply { mkdirs() }
        if (!directory.isDirectory) throw IOException("Cannot create update cache")
        val maxBytes = 1024L * 1024L * 1024L
        val partial = File(directory, "partial-${release.versionCode}-${expectedHash.take(16).lowercase()}.part")
        if (partial.length() >= maxBytes || (release.apkSize != null && partial.length() > release.apkSize)) {
            partial.delete()
        }

        var offset = partial.takeIf { it.isFile }?.length() ?: 0L
        var connection = openSecureConnection(release.apkUrl, offset.takeIf { it > 0 })
        try {
            if (offset > 0L &&
                !DownloadResumePolicy.canAppend(
                    offset, connection.responseCode, connection.getHeaderField("Content-Range")
                )) {
                // 200, 416, or a malformed range: restart safely instead of
                // appending duplicate or mismatched data to an existing file.
                connection.disconnect()
                if (!partial.delete()) throw IOException("Cannot reset partial APK")
                offset = 0L
                connection = openSecureConnection(release.apkUrl, null)
            }
            val status = connection.responseCode
            if ((offset > 0 && status != HttpURLConnection.HTTP_PARTIAL) ||
                (offset == 0L && status != HttpURLConnection.HTTP_OK)) {
                throw IOException("APK download failed (HTTP $status)")
            }
            val reportedTotal = if (offset > 0L) {
                connection.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            } else {
                connection.contentLengthLong.takeIf { it > 0 }
            }
            val total = release.apkSize ?: reportedTotal
            if (total != null && total > maxBytes) throw IOException("APK exceeds download limit")
            val digest = MessageDigest.getInstance("SHA-256")
            if (offset > 0L) {
                partial.inputStream().use { prior ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = prior.read(bytes)
                        if (n < 0) break
                        digest.update(bytes, 0, n)
                    }
                }
            }
            var received = offset
            onProgress(total?.takeIf { it > 0 }?.let {
                (received.toFloat() / it).coerceIn(0f, 1f)
            })
            connection.inputStream.use { input ->
                java.io.FileOutputStream(partial, offset > 0L).use { output ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(bytes)
                        if (n < 0) break
                        output.write(bytes, 0, n)
                        digest.update(bytes, 0, n)
                        received += n
                        if (received > maxBytes) throw IOException("APK exceeds download limit")
                        onProgress(total?.takeIf { it > 0 }?.let {
                            (received.toFloat() / it).coerceIn(0f, 1f)
                        })
                    }
                }
            }
            // A truncated response can be resumed later, but must never install.
            if (release.apkSize != null && received != release.apkSize) {
                throw IOException("APK size mismatch; retry to resume download")
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                partial.delete()
                throw IOException("APK checksum mismatch")
            }
            try {
                verifyPackage(partial, config)
            } catch (error: Exception) {
                partial.delete()
                throw error
            }
            val destination = File(directory, "verified-${release.versionCode}-${expectedHash.take(16).lowercase()}.apk")
            if (destination.exists() && !destination.delete()) throw IOException("Cannot replace cached APK")
            if (!partial.renameTo(destination)) throw IOException("Cannot store verified APK")
            destination
        } finally {
            connection.disconnect()
        }
    }

    private fun openSecureConnection(source: String, rangeStart: Long?): HttpURLConnection {
        var target = URL(source)
        repeat(6) {
            if (target.protocol != "https") throw IOException("Insecure APK redirect rejected")
            val connection = target.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            if (rangeStart != null) connection.setRequestProperty("Range", "bytes=$rangeStart-")
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IOException("APK redirect has no destination")
                val next = URL(target, location)
                connection.disconnect()
                target = next
            } else {
                return connection
            }
        }
        throw IOException("Too many APK redirects")
    }

    private fun verifyPackage(file: File, config: UpdateConfig) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        } ?: throw IOException("Downloaded file is not a valid APK")
        if (archive.packageName != config.packageName) throw IOException("APK package name mismatch")
        val version = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode
            else archive.versionCode.toLong()
        if (version <= config.installedVersionCode) throw IOException("APK is not newer than installed version")
        val installed = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(config.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(config.packageName, flags)
        }
        @Suppress("DEPRECATION")
        val archiveSigners = if (Build.VERSION.SDK_INT >= 28) {
            archive.signingInfo?.let { info ->
                (if (info.hasMultipleSigners()) info.apkContentsSigners
                else info.signingCertificateHistory)?.map { it.toCharsString() }?.toSet()
            }
        } else archive.signatures?.map { it.toCharsString() }?.toSet()
        @Suppress("DEPRECATION")
        val trustedSigners = if (Build.VERSION.SDK_INT >= 28) {
            installed.signingInfo?.let { info ->
                (if (info.hasMultipleSigners()) info.apkContentsSigners
                else info.signingCertificateHistory)?.map { it.toCharsString() }?.toSet()
            }
        } else installed.signatures?.map { it.toCharsString() }?.toSet()
        // Shared signing history is a compatibility preflight; Android remains the
        // authority on whether a lineage is valid for the installed package.
        if (archiveSigners.isNullOrEmpty() || trustedSigners.isNullOrEmpty() ||
            archiveSigners.intersect(trustedSigners).isEmpty()) {
            throw IOException("APK signing certificate does not match installed app")
        }
    }
}

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
    suspend fun download(
        release: UpdateRelease,
        config: UpdateConfig,
        onProgress: (Float?) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        require(release.apkUrl.startsWith("https://")) { "Only HTTPS downloads are supported" }
        val directory = File(context.cacheDir, "n2k02-updates").apply { mkdirs() }
        if (!directory.isDirectory) throw IOException("Cannot create update cache")
        val temp = File.createTempFile("update-", ".part", directory)
        try {
            val connection = openSecureConnection(release.apkUrl)
            try {
                // openSecureConnection already configured and opened this connection.
                // Do not mutate its settings after reading responseCode.
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("APK download failed (HTTP ${connection.responseCode})")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                val maxBytes = 1024L * 1024L * 1024L
                if (total != null && total > maxBytes) throw IOException("APK exceeds download limit")
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            received += count
                            if (received > maxBytes) throw IOException("APK exceeds download limit")
                            onProgress(total?.takeIf { it > 0 }?.let {
                                (received.toFloat() / it).coerceIn(0f, 1f)
                            })
                        }
                    }
                }
                if (release.apkSize != null && release.apkSize > 0 && received != release.apkSize) {
                    throw IOException("APK size mismatch")
                }
                val expectedHash = release.sha256
                    ?: throw IOException("No SHA-256 digest supplied for this release")
                if (!expectedHash.matches(Regex("[a-fA-F0-9]{64}"))) {
                    throw IOException("Invalid release checksum")
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    throw IOException("APK checksum mismatch")
                }
                verifyPackage(temp, config)
                val destination = File(directory, "verified-${release.versionCode}.apk")
                if (destination.exists() && !destination.delete()) throw IOException("Cannot replace cached APK")
                if (!temp.renameTo(destination)) throw IOException("Cannot store verified APK")
                destination
            } finally {
                connection.disconnect()
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun openSecureConnection(source: String): HttpURLConnection {
        var target = URL(source)
        repeat(6) {
            if (target.protocol != "https") throw IOException("Insecure APK redirect rejected")
            val connection = target.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
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

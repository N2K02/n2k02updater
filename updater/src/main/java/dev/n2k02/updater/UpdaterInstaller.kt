package dev.n2k02.updater

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Opens Android's package installer after a verified APK has been downloaded.
 * The host is responsible for its own install guard (e.g. active recording).
 */
class UpdaterInstaller {
    fun canRequestInstall(activity: Activity): Boolean =
        Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 26) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
        }
    }

    suspend fun install(
        activity: Activity,
        verifiedApk: File,
        guard: UpdateInstallGuard,
    ): Boolean {
        if (!guard.mayInstall()) return false
        if (!verifiedApk.isFile || verifiedApk.extension.lowercase() != "apk") {
            throw IOException("Verified APK is missing")
        }
        if (!canRequestInstall(activity)) return false
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.n2k02updater.files",
            verifiedApk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(intent)
        return true
    }
}

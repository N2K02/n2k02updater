package dev.n2k02.updater

/** Host app metadata. Package identity is validated before installation. */
data class UpdateConfig(
    val repository: String,
    val packageName: String,
    val installedVersionCode: Long,
    val experimentalEnabled: Boolean = false,
) {
    init {
        require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repository)) {
            "Repository must be owner/name"
        }
        require(packageName.isNotBlank())
        require(installedVersionCode >= 0)
    }
}

data class UpdateRelease(
    val tag: String,
    val versionCode: Long,
    val notes: List<String>,
    val apkUrl: String,
    val apkSize: Long?,
    val sha256: String?,
    val experimental: Boolean,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data class Available(val release: UpdateRelease) : UpdateStatus
    data class Downloading(val release: UpdateRelease, val progress: Float?) : UpdateStatus
    data class NeedsInstallPermission(val release: UpdateRelease) : UpdateStatus
    data class Ready(val release: UpdateRelease) : UpdateStatus
    data class Failed(val message: String, val diagnostics: String? = null) : UpdateStatus
}

/**
 * Host-controlled guard, e.g. BCam must stop recording before opening the installer.
 * Return false to prevent installation; never bypass an app's active-operation safety.
 */
fun interface UpdateInstallGuard {
    suspend fun mayInstall(): Boolean
}

package dev.n2k02.updater

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Per-application update coordinator. The host should retain one instance while
 * the app process runs; do not recreate it on every Activity configuration change.
 * Only the host can start an installation, and host install guards are mandatory.
 */
class UpdaterController(
    context: Context,
    private val config: UpdateConfig,
    private val releaseSource: GitHubReleaseSource = GitHubReleaseSource(),
    private val publishedSource: PublishedUpdateSource? = null,
    private val installer: UpdaterInstaller = UpdaterInstaller(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val offerPreferences: SharedPreferences? = null,
) {
    private val appContext = context.applicationContext
    private val downloader = VerifiedApkDownloader(appContext)
    private val offerCache = UpdateOfferCache(offerPreferences, config)
    private val mutableStatus = MutableStateFlow<UpdateStatus>(
        offerCache.restore()?.let { UpdateStatus.Available(it) } ?: UpdateStatus.Idle
    )
    val status: StateFlow<UpdateStatus> = mutableStatus

    private val mutableLastCheckedAt = MutableStateFlow(offerPreferences?.getLong("lastCheck", 0L) ?: 0L)
    /** Time of most recent attempted network check (successful or failed). */
    val lastCheckedAt: StateFlow<Long> = mutableLastCheckedAt

    private val mutableLastSuccessfulCheckAt = MutableStateFlow(
        offerPreferences?.getLong("lastSuccessfulCheck", 0L) ?: 0L
    )
    /** Time of most recent successful check, separate from failed attempts. */
    val lastSuccessfulCheckAt: StateFlow<Long> = mutableLastSuccessfulCheckAt

    private val mutableLastCheckFailed = MutableStateFlow(
        offerPreferences?.getBoolean("lastCheckFailed", false) ?: false
    )
    val lastCheckFailed: StateFlow<Boolean> = mutableLastCheckFailed

    private var operation: Job? = null
    private var verifiedFile: File? = null
    private var installerOpened = false

    /** Throttled automatic refresh that preserves pending downloads and install state. */
    fun refreshIfDue() = check(manual = false)

    /** Invoke from Activity.onResume without gating on Idle or Current. */
    fun onHostResume(activity: Activity, installGuard: UpdateInstallGuard) {
        when (val current = status.value) {
            is UpdateStatus.NeedsInstallPermission -> {
                mutableStatus.value = InstallReturnPolicy.afterPermission(
                    current.release, installer.canRequestInstall(activity)
                )
            }
            is UpdateStatus.Ready -> {
                if (installerOpened) {
                    installerOpened = false
                    val next = InstallReturnPolicy.afterInstaller(
                        current.release, isVersionInstalled(current.release.versionCode)
                    )
                    if (next == UpdateStatus.Current) {
                        offerCache.clear()
                        verifiedFile = null
                    }
                    mutableStatus.value = next
                }
            }
            is UpdateStatus.Downloading -> Unit
            is UpdateStatus.Failed -> {
                if (current.release == null) refreshIfDue()
            }
            else -> refreshIfDue()
        }
    }

    @Suppress("DEPRECATION")
    private fun isVersionInstalled(versionCode: Long): Boolean = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val installed = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
            else info.versionCode.toLong()
        installed >= versionCode
    }.getOrDefault(false)

    fun check(manual: Boolean = true) {
        // Never cancel a verified install or an active download to run a check.
        when (mutableStatus.value) {
            is UpdateStatus.Downloading, is UpdateStatus.Ready,
            is UpdateStatus.NeedsInstallPermission -> return
            else -> Unit
        }
        if (!manual && operation?.isActive == true) return
        if (!manual && offerPreferences != null && !UpdateOfferPolicy.isDue(
                System.currentTimeMillis(),
                offerPreferences.getLong("lastCheck", 0L),
                UpdateOfferPolicy.retryInterval(mutableLastCheckFailed.value),
            )) return

        operation?.cancel()
        operation = scope.launch {
            val previous = mutableStatus.value
            val previousOffer = when (previous) {
                is UpdateStatus.Available -> previous.release
                is UpdateStatus.Failed -> previous.release
                else -> null
            }
            if (previousOffer == null) mutableStatus.value = UpdateStatus.Checking
            try {
                val release = withContext(Dispatchers.IO) {
                    UpdateSourceFallback.resolve(
                        published = publishedSource?.let { source -> { source.latest(config) } },
                        github = { releaseSource.latest(config) },
                    )
                }
                val offered = release?.takeIf {
                    UpdateOfferPolicy.shouldOffer(
                        it, offerPreferences?.getString("skipped", null), manual
                    )
                }
                if (offered == null) offerCache.clear() else offerCache.save(offered)
                val time = System.currentTimeMillis()
                offerPreferences?.edit()
                    ?.putLong("lastCheck", time)
                    ?.putLong("lastSuccessfulCheck", time)
                    ?.putBoolean("lastCheckFailed", false)
                    ?.apply()
                mutableLastCheckedAt.value = time
                mutableLastSuccessfulCheckAt.value = time
                mutableLastCheckFailed.value = false
                mutableStatus.value = when {
                    offered != null -> UpdateStatus.Available(offered)
                    manual -> UpdateStatus.Current
                    else -> UpdateStatus.Idle
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                val time = System.currentTimeMillis()
                offerPreferences?.edit()?.putLong("lastCheck", time)
                    ?.putBoolean("lastCheckFailed", true)?.apply()
                mutableLastCheckedAt.value = time
                mutableLastCheckFailed.value = true
                // On a temporary network error, never erase an existing known release.
                mutableStatus.value = when {
                    previousOffer != null -> UpdateStatus.Available(previousOffer)
                    else -> UpdateOfferPolicy.checkFailure(manual, error.message)
                }
            }
        }
    }

    /** Explicitly ignore a release for automatic checks. Manual checks still show it. */
    fun skip(release: UpdateRelease) {
        offerPreferences?.edit()?.putString("skipped", release.tag)?.apply()
        offerCache.clear()
        operation?.cancel()
        operation = null
        mutableStatus.value = UpdateStatus.Idle
    }

    fun download(release: UpdateRelease) {
        if (release.versionCode <= config.installedVersionCode) return
        operation?.cancel()
        verifiedFile = null
        installerOpened = false
        operation = scope.launch {
            mutableStatus.value = UpdateStatus.Downloading(release, null)
            try {
                verifiedFile = downloader.download(release, config) { progress ->
                    mutableStatus.value = UpdateStatus.Downloading(release, progress)
                }
                mutableStatus.value = UpdateStatus.Ready(release)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                mutableStatus.value = UpdateStatus.Failed(
                    "Download or verification failed", error.message, release,
                    UpdateRetryAction.DOWNLOAD,
                )
            }
        }
    }

    fun install(activity: Activity, guard: UpdateInstallGuard) {
        val release = when (val current = mutableStatus.value) {
            is UpdateStatus.Ready -> current.release
            is UpdateStatus.NeedsInstallPermission -> current.release
            is UpdateStatus.Failed -> current.release
                ?.takeIf { current.retryAction == UpdateRetryAction.INSTALL }
            else -> null
        } ?: return

        val file = verifiedFile
        if (file == null || !file.isFile) {
            mutableStatus.value = UpdateStatus.Failed(
                "Verified APK is no longer available", "Download the update again.",
                release, UpdateRetryAction.DOWNLOAD,
            )
            return
        }
        operation?.cancel()
        operation = scope.launch {
            try {
                if (!installer.canRequestInstall(activity)) {
                    mutableStatus.value = UpdateStatus.NeedsInstallPermission(release)
                    return@launch
                }
                if (installer.install(activity, file, guard)) {
                    installerOpened = true
                    mutableStatus.value = UpdateStatus.Ready(
                        release, "Android installer opened. Return here if installation is cancelled."
                    )
                } else {
                    mutableStatus.value = UpdateStatus.Ready(
                        release, "Installation paused by this app. Stop recording, casting, or playback, then retry."
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                mutableStatus.value = UpdateStatus.Failed(
                    "Could not open Android installer", error.message, release,
                    UpdateRetryAction.INSTALL,
                )
            }
        }
    }

    fun openInstallPermission(activity: Activity) = installer.requestInstallPermission(activity)

    fun cancel() {
        operation?.cancel()
        operation = null
        mutableStatus.value = when (val current = mutableStatus.value) {
            is UpdateStatus.Downloading -> UpdateStatus.Available(current.release)
            else -> UpdateStatus.Idle
        }
    }

    /** Keep a pending update visible; the user must explicitly skip it to hide it. */
    fun dismiss() {
        if (mutableStatus.value is UpdateStatus.Downloading) return
        mutableStatus.value = when (val current = mutableStatus.value) {
            is UpdateStatus.Failed -> current.release?.let { UpdateStatus.Available(it) } ?: UpdateStatus.Idle
            else -> current
        }
    }
}

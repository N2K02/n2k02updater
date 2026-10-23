package dev.n2k02.updater

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
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
 * Per-app update coordinator. No automatic installs; the host explicitly calls install().
 * An app should retain this instance for its settings screen lifecycle.
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
    private val downloader = VerifiedApkDownloader(context.applicationContext)
    private val mutableStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = mutableStatus
    private var operation: Job? = null
    private var verifiedFile: File? = null

    fun check(manual: Boolean = true) {
        if (!manual && offerPreferences != null && !UpdateOfferPolicy.isDue(
                System.currentTimeMillis(), offerPreferences.getLong("lastCheck", 0L)
            )) return
        operation?.cancel()
        operation = scope.launch {
            mutableStatus.value = UpdateStatus.Checking
            try {
                val release = withContext(Dispatchers.IO) {
                    if (publishedSource != null) publishedSource.latest(config)
                    else releaseSource.latest(config)
                }
                offerPreferences?.edit()?.putLong("lastCheck", System.currentTimeMillis())?.apply()
                mutableStatus.value = when {
                    release == null -> if (manual) UpdateStatus.Current else UpdateStatus.Idle
                    !UpdateOfferPolicy.shouldOffer(release, offerPreferences?.getString("skipped", null), manual) -> UpdateStatus.Idle
                    else -> UpdateStatus.Available(release)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                mutableStatus.value = UpdateStatus.Failed("Could not check for updates", error.message)
            }
        }
    }

    /** Dismiss this release for automatic checks; manual checks may still offer it. */
    fun skip(release: UpdateRelease) {
        offerPreferences?.edit()?.putString("skipped", release.tag)?.apply()
        dismiss()
    }

    fun download(release: UpdateRelease) {
        operation?.cancel()
        verifiedFile = null
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
                mutableStatus.value = UpdateStatus.Failed("Could not verify update", error.message)
            }
        }
    }

    fun install(activity: Activity, guard: UpdateInstallGuard) {
        val state = when (val current = mutableStatus.value) {
            is UpdateStatus.Ready -> current.release
            is UpdateStatus.NeedsInstallPermission -> current.release
            else -> return
        }
        val file = verifiedFile ?: return
        operation?.cancel()
        operation = scope.launch {
            try {
                if (!installer.canRequestInstall(activity)) {
                    mutableStatus.value = UpdateStatus.NeedsInstallPermission(state)
                    return@launch
                }
                if (!installer.install(activity, file, guard)) {
                    mutableStatus.value = UpdateStatus.Ready(state)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                mutableStatus.value = UpdateStatus.Failed("Could not open Android installer", error.message)
            }
        }
    }

    fun openInstallPermission(activity: Activity) = installer.requestInstallPermission(activity)

    fun cancel() {
        operation?.cancel()
        operation = null
        mutableStatus.value = UpdateStatus.Idle
    }

    fun dismiss() {
        if (mutableStatus.value !is UpdateStatus.Downloading) mutableStatus.value = UpdateStatus.Idle
    }
}

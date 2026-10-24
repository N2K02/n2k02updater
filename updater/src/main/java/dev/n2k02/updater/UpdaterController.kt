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
    private val offerCache = UpdateOfferCache(offerPreferences, config)
    // Restore the last known offer immediately, even when the automatic check is
    // throttled. Never restore a Ready state without re-verifying the APK.
    private val mutableStatus = MutableStateFlow<UpdateStatus>(
        offerCache.restore()?.let { UpdateStatus.Available(it) } ?: UpdateStatus.Idle
    )
    val status: StateFlow<UpdateStatus> = mutableStatus
    private val mutableLastCheckedAt = MutableStateFlow(offerPreferences?.getLong("lastCheck", 0L) ?: 0L)
    /** Last completed update-check attempt in epoch milliseconds, or 0 if unknown. */
    val lastCheckedAt: StateFlow<Long> = mutableLastCheckedAt
    private var operation: Job? = null
    private var verifiedFile: File? = null

    /**
     * Safe to invoke every time the app enters the foreground, regardless of
     * whether the current state is Idle, Current, or Available. A pending offer
     * stays visible while a background refresh checks for a newer release.
     */
    fun refreshIfDue() = check(manual = false)

    fun check(manual: Boolean = true) {
        // A background refresh must not cancel an APK download or an installation.
        when (mutableStatus.value) {
            is UpdateStatus.Downloading, is UpdateStatus.Ready,
            is UpdateStatus.NeedsInstallPermission -> return
            else -> Unit
        }
        if (!manual && operation?.isActive == true) return
        if (!manual && offerPreferences != null && !UpdateOfferPolicy.isDue(
                System.currentTimeMillis(), offerPreferences.getLong("lastCheck", 0L)
            )) return
        operation?.cancel()
        operation = scope.launch {
            val previousOffer = mutableStatus.value as? UpdateStatus.Available
            // Avoid hiding a known update (and its badge) during revalidation.
            if (previousOffer == null) mutableStatus.value = UpdateStatus.Checking
            try {
                val release = withContext(Dispatchers.IO) {
                    UpdateSourceFallback.resolve(
                        published = publishedSource?.let { source -> { source.latest(config) } },
                        github = { releaseSource.latest(config) },
                    )
                }
                val offered = release?.takeIf {
                    UpdateOfferPolicy.shouldOffer(it, offerPreferences?.getString("skipped", null), manual)
                }
                if (offered == null) offerCache.clear() else offerCache.save(offered)
                val checkedAt = System.currentTimeMillis()
                offerPreferences?.edit()?.putLong("lastCheck", checkedAt)?.apply()
                mutableLastCheckedAt.value = checkedAt
                mutableStatus.value = when {
                    offered != null -> UpdateStatus.Available(offered)
                    manual -> UpdateStatus.Current
                    else -> UpdateStatus.Idle
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                // A temporary offline or HTTP failure must not erase an already
                // validated offer. Background failures remain silent and throttled.
                val checkedAt = System.currentTimeMillis()
                offerPreferences?.edit()?.putLong("lastCheck", checkedAt)?.apply()
                mutableLastCheckedAt.value = checkedAt
                mutableStatus.value = previousOffer
                    ?: UpdateOfferPolicy.checkFailure(manual, error.message)
            }
        }
    }

    /** Dismiss this release for automatic checks; manual checks may still offer it. */
    fun skip(release: UpdateRelease) {
        offerPreferences?.edit()?.putString("skipped", release.tag)?.apply()
        offerCache.clear()
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
        // Cancelling a download does not mean the new release disappeared.
        // Return to the available offer so the badge and download action remain.
        mutableStatus.value = when (val previous = mutableStatus.value) {
            is UpdateStatus.Downloading -> UpdateStatus.Available(previous.release)
            else -> UpdateStatus.Idle
        }
    }

    fun dismiss() {
        if (mutableStatus.value !is UpdateStatus.Downloading) mutableStatus.value = UpdateStatus.Idle
    }
}

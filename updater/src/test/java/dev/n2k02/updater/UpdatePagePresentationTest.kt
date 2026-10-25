package dev.n2k02.updater

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePagePresentationTest {
    private val offer = UpdateRelease(
        tag = "v4.0", versionCode = 400L, notes = listOf("Stability"),
        apkUrl = "https://example.org/app.apk", apkSize = 1024L,
        sha256 = "a".repeat(64), experimental = false,
    )

    @Test fun eachLifecycleStateMapsToOneStableAnimationPhase() {
        val expectations = listOf(
            UpdateStatus.Idle to UpdatePagePhase.IDLE,
            UpdateStatus.Checking to UpdatePagePhase.CHECKING,
            UpdateStatus.Current to UpdatePagePhase.CURRENT,
            UpdateStatus.Available(offer) to UpdatePagePhase.AVAILABLE,
            UpdateStatus.Downloading(offer, null) to UpdatePagePhase.DOWNLOADING,
            UpdateStatus.Downloading(offer, 0.25f) to UpdatePagePhase.DOWNLOADING,
            UpdateStatus.Ready(offer) to UpdatePagePhase.READY,
            UpdateStatus.NeedsInstallPermission(offer) to UpdatePagePhase.PERMISSION,
            UpdateStatus.Failed("offline") to UpdatePagePhase.FAILED,
        )
        expectations.forEach { (state, phase) -> assertEquals(phase, state.pagePhase()) }
    }

    @Test fun primaryActionReflectsActualAllowedRecovery() {
        assertEquals("Check again", UpdateStatus.Current.primaryActionLabel())
        assertEquals("Download update", UpdateStatus.Available(offer).primaryActionLabel())
        assertEquals("Install update", UpdateStatus.Ready(offer).primaryActionLabel())
        assertEquals("Allow installation", UpdateStatus.NeedsInstallPermission(offer).primaryActionLabel())
        assertEquals("Retry download", UpdateStatus.Failed("error", release = offer,
            retryAction = UpdateRetryAction.DOWNLOAD).primaryActionLabel())
        assertEquals("Retry installation", UpdateStatus.Failed("error", release = offer,
            retryAction = UpdateRetryAction.INSTALL).primaryActionLabel())
        assertEquals("Check again", UpdateStatus.Failed("error",
            retryAction = UpdateRetryAction.DOWNLOAD).primaryActionLabel())
    }
}

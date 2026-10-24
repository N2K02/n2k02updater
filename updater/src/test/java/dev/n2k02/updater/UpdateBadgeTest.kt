package dev.n2k02.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBadgeTest {
    private fun release(experimental: Boolean = false) = UpdateRelease(
        tag = if (experimental) "experimental-12" else "v1.2",
        versionCode = 12L,
        notes = emptyList(),
        apkUrl = "https://example.org/app.apk",
        apkSize = null,
        sha256 = null,
        experimental = experimental,
    )

    @Test
    fun pendingUpdateShowsBadgeDuringDownloadAndInstallation() {
        for (experimental in listOf(false, true)) {
            val update = release(experimental)
            assertTrue(UpdateStatus.Available(update).hasPendingUpdate())
            assertTrue(UpdateStatus.Downloading(update, null).hasPendingUpdate())
            assertTrue(UpdateStatus.Downloading(update, 0.5f).hasPendingUpdate())
            assertTrue(UpdateStatus.Ready(update).hasPendingUpdate())
            assertTrue(UpdateStatus.NeedsInstallPermission(update).hasPendingUpdate())
        }
    }

    @Test fun failedDownloadsKeepBadgeUntilRecovered() {
        val release = release()
        assertTrue(UpdateStatus.Failed(
            "network disconnected", null, release, UpdateRetryAction.DOWNLOAD
        ).hasPendingUpdate())
    }

    @Test
    fun idleCheckingCurrentAndFailureHaveNoPendingBadge() {
        assertFalse(UpdateStatus.Idle.hasPendingUpdate())
        assertFalse(UpdateStatus.Checking.hasPendingUpdate())
        assertFalse(UpdateStatus.Current.hasPendingUpdate())
        assertFalse(UpdateStatus.Failed("Network unavailable").hasPendingUpdate())
    }
}

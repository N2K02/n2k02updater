package dev.n2k02.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRecoveryTest {
    private val release = UpdateRelease(
        tag = "v2.4", versionCode = 24, notes = listOf("Fix"), 
        apkUrl = "https://example.org/update.apk", apkSize = 42L,
        sha256 = "a".repeat(64), experimental = false,
    )

    @Test fun failedDownloadAndInstallationKeepPendingBadgeAndRetryContext() {
        val download = UpdateStatus.Failed("network offline", "timeout", release, UpdateRetryAction.DOWNLOAD)
        val install = UpdateStatus.Failed("installer unavailable", "error", release, UpdateRetryAction.INSTALL)
        assertTrue(download.hasPendingUpdate())
        assertTrue(install.hasPendingUpdate())
        assertEquals(release, download.release)
        assertEquals(UpdateRetryAction.DOWNLOAD, download.retryAction)
        assertEquals(UpdateRetryAction.INSTALL, install.retryAction)
    }

    @Test fun failedCheckNeverClaimsUpdateIsAvailable() {
        val failed = UpdateStatus.Failed("offline", "network")
        assertFalse(failed.hasPendingUpdate())
        assertEquals(UpdateRetryAction.CHECK, failed.retryAction)
    }

    @Test fun readyStateCanCarryPermissionAndInstallerMessages() {
        val ready = UpdateStatus.Ready(release, "Stop recording before installing")
        assertTrue(ready.hasPendingUpdate())
        assertEquals(release, ready.release)
        assertEquals("Stop recording before installing", ready.notice)
    }

    @Test fun retryIsFasterButStillThrottled() {
        val t = 1000000L
        assertEquals(12L * 60 * 60 * 1000, UpdateOfferPolicy.retryInterval(false))
        assertEquals(15L * 60 * 1000, UpdateOfferPolicy.retryInterval(true))
        assertFalse(UpdateOfferPolicy.isDue(
            t + 14L * 60 * 1000, t, UpdateOfferPolicy.retryInterval(true)
        ))
        assertTrue(UpdateOfferPolicy.isDue(
            t + 15L * 60 * 1000, t, UpdateOfferPolicy.retryInterval(true)
        ))
    }
}

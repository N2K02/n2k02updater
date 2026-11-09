package dev.n2k02.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveUpdateLayoutTest {
    private val release = UpdateRelease(
        tag = "v2.5",
        versionCode = 100138,
        notes = listOf("First fix", "Second fix", "Third fix", "Fourth fix"),
        apkUrl = "https://example.com/release.apk",
        apkSize = 17_510_400,
        sha256 = "a".repeat(64),
        experimental = false,
    )

    @Test fun allUpdaterScenariosHaveCorrectPhaseAndAction() {
        val scenarios = listOf(
            UpdateStatus.Idle to (UpdatePagePhase.IDLE to "Check for updates"),
            UpdateStatus.Checking to (UpdatePagePhase.CHECKING to "Checking…"),
            UpdateStatus.Current to (UpdatePagePhase.CURRENT to "Check again"),
            UpdateStatus.Available(release) to (UpdatePagePhase.AVAILABLE to "Download update"),
            UpdateStatus.Downloading(release, .63f) to
                (UpdatePagePhase.DOWNLOADING to "Downloading…"),
            UpdateStatus.Ready(release) to (UpdatePagePhase.READY to "Install update"),
            UpdateStatus.NeedsInstallPermission(release) to
                (UpdatePagePhase.PERMISSION to "Allow installation"),
            UpdateStatus.Failed("HTTP 504", release = release,
                retryAction = UpdateRetryAction.DOWNLOAD) to
                (UpdatePagePhase.FAILED to "Retry download"),
            UpdateStatus.Failed("Check failed", retryAction = UpdateRetryAction.CHECK) to
                (UpdatePagePhase.FAILED to "Check again"),
            UpdateStatus.Failed("Install failed", release = release,
                retryAction = UpdateRetryAction.INSTALL) to
                (UpdatePagePhase.FAILED to "Retry installation"),
        )
        scenarios.forEach { (status, expected) ->
            assertEquals(expected.first, status.pagePhase())
            assertEquals(expected.second, status.primaryActionLabel())
        }
    }

    @Test fun structuredDiagnosticsAreReadableAndPreserveFields() {
        val formatted = formatUpdaterDiagnostics(
            """{"stage":"download","code":504,"message":"Gateway Timeout","retryable":true}"""
        )
        assertTrue(formatted.contains("\n"))
        assertTrue(formatted.contains("\"stage\""))
        assertTrue(formatted.contains("504"))
        assertTrue(formatted.contains("Gateway Timeout"))
    }

    @Test fun plainTextErrorsAreNeverLost() {
        val raw = "APK download failed (HTTP 504)"
        assertEquals(raw, formatUpdaterDiagnostics(raw))
    }
}

package dev.n2k02.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallReturnPolicyTest {
    private val release = UpdateRelease(
        "v3", 30L, emptyList(), "https://example.org/app.apk",
        123L, "a".repeat(64), experimental = false,
    )

    @Test fun grantingPermissionRequiresExplicitInstallTap() {
        val next = InstallReturnPolicy.afterPermission(release, true)
        assertTrue(next is UpdateStatus.Ready)
        assertEquals(release, (next as UpdateStatus.Ready).release)
    }

    @Test fun refusingPermissionDoesNotLaunchInstaller() {
        assertEquals(UpdateStatus.NeedsInstallPermission(release),
            InstallReturnPolicy.afterPermission(release, false))
    }

    @Test fun cancelledInstallerReturnsToReadyWithoutLosingRelease() {
        val next = InstallReturnPolicy.afterInstaller(release, false)
        assertTrue(next is UpdateStatus.Ready)
        assertTrue(next.hasPendingUpdate())
    }

    @Test fun installedVersionClearsPendingUpdate() {
        assertEquals(UpdateStatus.Current, InstallReturnPolicy.afterInstaller(release, true))
    }
}

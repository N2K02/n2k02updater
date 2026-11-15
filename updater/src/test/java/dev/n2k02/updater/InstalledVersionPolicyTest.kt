package dev.n2k02.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledVersionPolicyTest {
    @Test fun liveInstalledCodeOverridesStaleStartupConfiguration() {
        assertEquals(100500L, InstalledVersionPolicy.effectiveCode(100200L, 100500L))
        assertEquals(100500L, InstalledVersionPolicy.effectiveCode(100500L, 100100L))
    }
    @Test fun missingPackageMetadataFallsBackWithoutInventingVersion() {
        assertEquals(123L, InstalledVersionPolicy.effectiveCode(123L, null))
        assertEquals(123L, InstalledVersionPolicy.effectiveCode(123L, -1L))
    }
    @Test fun stableCanReplaceExperimentalOnlyWithNewerCode() {
        assertTrue(InstalledVersionPolicy.canInstall(100452, 100453))
        assertFalse(InstalledVersionPolicy.canInstall(100452, 100452))
        assertFalse(InstalledVersionPolicy.canInstall(100452, 100451))
    }
}

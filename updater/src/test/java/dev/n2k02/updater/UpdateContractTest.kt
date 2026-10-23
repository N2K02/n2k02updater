package dev.n2k02.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateContractTest {
    @Test fun mRadioVersionConvention() {
        assertEquals(18L, GitHubReleaseSource.defaultVersionCode("v1.8"))
        assertEquals(20L, GitHubReleaseSource.defaultVersionCode("2.0"))
    }

    @Test fun rejectsAmbiguousVersionTags() {
        assertNull(GitHubReleaseSource.defaultVersionCode("v1.10"))
        assertNull(GitHubReleaseSource.defaultVersionCode("v1.8-beta"))
        assertNull(GitHubReleaseSource.defaultVersionCode("not-a-version"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidRepository() {
        UpdateConfig("invalid", "com.example.app", 1)
    }
}

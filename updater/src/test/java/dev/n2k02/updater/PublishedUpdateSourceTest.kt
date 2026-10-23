package dev.n2k02.updater

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishedUpdateSourceTest {
    private val digest = "a".repeat(64)

    private fun manifest(
        versionCode: Int = 24,
        apkUrl: String = "https://example.org/update.apk",
        sha256: String = digest,
    ) = """{
        "tag":"v2.4",
        "versionCode":$versionCode,
        "apkUrl":"$apkUrl",
        "sha256":"$sha256",
        "apkSize":1234,
        "notes":["Important fix"]
    }""".trimIndent()

    @Test fun readsNewerStableRelease() {
        val release = PublishedUpdateSource.parse(manifest(), 23L, false)
        requireNotNull(release)
        assertEquals("v2.4", release.tag)
        assertEquals(24L, release.versionCode)
        assertEquals(1234L, release.apkSize)
        assertEquals(listOf("Important fix"), release.notes)
        assertTrue(!release.experimental)
    }

    @Test fun preservesExperimentalChannel() {
        val release = PublishedUpdateSource.parse(manifest(), 1L, true)
        assertTrue(requireNotNull(release).experimental)
    }

    @Test fun ignoresCurrentAndOlderVersions() {
        assertNull(PublishedUpdateSource.parse(manifest(), 24L, false))
        assertNull(PublishedUpdateSource.parse(manifest(), 25L, false))
    }

    @Test(expected = IOException::class)
    fun rejectsInsecureApkUrl() {
        PublishedUpdateSource.parse(manifest(apkUrl = "http://example.org/update.apk"), 1L, false)
    }

    @Test(expected = IOException::class)
    fun rejectsInvalidChecksum() {
        PublishedUpdateSource.parse(manifest(sha256 = "bad"), 1L, false)
    }
}

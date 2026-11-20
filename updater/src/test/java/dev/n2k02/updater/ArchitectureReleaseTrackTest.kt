package dev.n2k02.updater

import org.junit.Assert.*
import org.junit.Test

class ArchitectureReleaseTrackTest {
    private fun release(tag: String, apk: String, code: Long, experimental: Boolean): String =
        """{"tag_name":"$tag","draft":false,"prerelease":$experimental,"body":"Version code: $code","published_at":"2026-11-18T19:00:00Z","assets":[{"name":"$apk","browser_download_url":"https://github.com/N2K02/SEQ/releases/download/$tag/$apk","size":1024,"digest":"sha256:${"a".repeat(64)}"}]}"""

    private val all = "[" + listOf(
        release("v0.7", "SEQ-0.7.apk", 110, false),
        release("adb-v0.8", "SEQ-ADB-0.8.apk", 120, false),
        release("shizuku-v0.8", "SEQ-SHIZUKU-0.8.apk", 130, false),
        release("experimental-adb-pr-7-run-400", "SEQ-ADB-0.9-experimental.1.apk", 140, true),
        release("experimental-shizuku-pr-6-run-410", "SEQ-SHIZUKU-0.9-experimental.1.apk", 150, true),
        release("experimental-adb-pr-7-run-420", "SEQ-SHIZUKU-0.9-experimental.1.apk", 160, true),
        release("experimental-shizuku-pr-6-run-430", "SEQ-ADB-0.9-experimental.1.apk", 170, true),
    ).joinToString(",") + "]"

    @Test fun adbStableCannotSeeShizukuStableOrLegacy() {
        val candidate = GitHubReleaseSource.parseTrackedReleases(all, UpdateConfig("N2K02/SEQ", "com.n2k02.seq", 0, releaseTrack = "adb"))
        assertEquals("adb-v0.8", candidate?.tag)
        assertFalse(candidate!!.experimental)
    }

    @Test fun shizukuStableCannotSeeAdbStableOrLegacy() {
        val candidate = GitHubReleaseSource.parseTrackedReleases(all, UpdateConfig("N2K02/SEQ", "com.n2k02.seq", 0, releaseTrack = "shizuku"))
        assertEquals("shizuku-v0.8", candidate?.tag)
    }

    @Test fun adbExperimentalOnlySeesAdbReleases() {
        val candidate = GitHubReleaseSource.parseTrackedReleases(all, UpdateConfig("N2K02/SEQ", "com.n2k02.seq", 0, true, "adb"))
        assertEquals("experimental-adb-pr-7-run-400", candidate?.tag)
    }

    @Test fun shizukuExperimentalOnlySeesShizukuReleases() {
        val candidate = GitHubReleaseSource.parseTrackedReleases(all, UpdateConfig("N2K02/SEQ", "com.n2k02.seq", 0, true, "shizuku"))
        assertEquals("experimental-shizuku-pr-6-run-410", candidate?.tag)
    }

    @Test fun noCrossTrackOffersAfterAlreadyInstalled() {
        assertNull(GitHubReleaseSource.parseTrackedReleases(all, UpdateConfig("N2K02/SEQ", "com.n2k02.seq", 145, true, "adb")))
        assertNull(GitHubReleaseSource.parseTrackedReleases(all, UpdateConfig("N2K02/SEQ", "com.n2k02.seq", 155, true, "shizuku")))
    }

    @Test fun legacyAppsContinueToAcceptLegacyReleases() {
        assertEquals(18L, GitHubReleaseSource.defaultVersionCode("v1.8"))
        assertEquals(null, UpdateConfig("N2K02/SEQ", "com.n2k02.seq", 1).releaseTrack)
    }

    @Test fun tagAndAssetMustAgree() {
        assertFalse(GitHubReleaseSource.belongsToTrack("adb-v1.0", "SEQ-SHIZUKU-1.0.apk", "adb"))
        assertFalse(GitHubReleaseSource.belongsToTrack("v1.0", "SEQ-ADB-1.0.apk", "adb"))
        assertTrue(GitHubReleaseSource.belongsToTrack("adb-sdcleaner-v2.4", "SDCleaner-ADB-2.4.apk", "adb"))
    }

    @Test fun invalidMetadataIsIgnoredBeforeChoosingHighestVersion() {
        val valid = release("adb-v1.0", "SEQ-ADB-1.0.apk", 200, false)
        val higher = release("adb-v1.1", "SEQ-ADB-1.1.apk", 201, false)
        val config = UpdateConfig("N2K02/SEQ", "com.n2k02.seq", 199, true, "adb")
        val malformed = listOf(
            higher.replace(".apk", ".apk.apk"),
            higher.replace("SEQ-ADB", "Other-ADB"),
            higher.replace("1.1.apk", "1.2.apk"),
            higher.replace("sha256:", "sha512:"),
            higher.replace("N2K02/SEQ", "someone/SEQ"),
            higher.replace("Version code: 201", "Version code: 201\\nVersion code: 202"),
            higher.replace("\"prerelease\":false", "\"prerelease\":true"),
        )
        malformed.forEach {
            assertEquals("adb-v1.0", GitHubReleaseSource.parseTrackedReleases("[$valid,$it]", config)?.tag)
        }
        assertNull(GitHubReleaseSource.parseTrackedReleases("invalid JSON", config))
        assertNull(GitHubReleaseSource.parseTrackedReleases("[$valid]", config.copy(installedVersionCode = 200)))
    }

    @Test fun signerPreflightAllowsForwardRotationAndRequiresAllParallelSigners() {
        assertTrue(signingCertificatesMatch(listOf("a"), listOf("b"), listOf("a", "b")))
        assertFalse(signingCertificatesMatch(listOf("b"), listOf("a"), listOf("a")))
        assertFalse(signingCertificatesMatch(listOf("a"), listOf("b"), listOf("a", "c")))
        assertTrue(signingCertificatesMatch(listOf("a", "b"), listOf("b", "a"), emptyList()))
        assertFalse(signingCertificatesMatch(listOf("a", "b"), listOf("a", "c"), emptyList()))
        assertFalse(signingCertificatesMatch(emptyList(), emptyList(), emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownTrackIsRejected() { UpdateConfig("N2K02/SEQ", "com.n2k02.seq", 1, releaseTrack = "none") }
}

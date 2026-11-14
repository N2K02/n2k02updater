package dev.n2k02.updater

import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseChannelsTest {
    private fun release(tag: String, code: Long, prerelease: Boolean, apk: Boolean = true): String =
        """{"tag_name":"$tag","draft":false,"prerelease":$prerelease,
            "body":"Version code: $code\\n### What's new\\n- Test update",
            "published_at":"2026-11-15T00:00:00Z",
            "assets":[${if (apk) """{"name":"test.apk","browser_download_url":"https://github.com/N2K02/SEQ/releases/download/$tag/test.apk","size":100,"digest":"sha256:${"a".repeat(64)}"}""" else ""}]}"""

    @Test fun experimentalChoosesLatestAndroidVersionNotListOrder() {
        val json = "[${release("experimental-old", 1001, true)},${release("experimental-new", 1003, true)}]"
        val result = GitHubReleaseSource.parseExperimentalReleases(json, 999)
        assertEquals(1003L, result?.versionCode)
        assertEquals(true, result?.experimental)
    }

    @Test fun missingExperimentalDoesNotInventUpdate() {
        val json = "[${release("v1.0", 10, false)}]"
        assertNull(GitHubReleaseSource.parseExperimentalReleases(json, 1))
    }

    @Test fun rejectsDraftAndNoApk() {
        val json = "[${release("experimental", 1002, true, apk = false)}]"
        assertNull(GitHubReleaseSource.parseExperimentalReleases(json, 1))
    }

    @Test fun stableUsesAndroidVersionCodeFromReleaseNotes() {
        val result = GitHubReleaseSource.parseRelease(release("v0.5", 100163, false), 100000)
        assertEquals(100163L, result?.versionCode)
        assertEquals(false, result?.experimental)
    }

    @Test fun ignoresOlderExperimental() {
        assertNull(GitHubReleaseSource.parseExperimentalReleases("[${release("experimental", 1002, true)}]", 1002))
    }
}

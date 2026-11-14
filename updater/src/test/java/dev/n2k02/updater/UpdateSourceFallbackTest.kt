package dev.n2k02.updater

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class UpdateSourceFallbackTest {
    private val stable = UpdateRelease("v2.0", 20L, emptyList(),
        "https://github.com/N2K02/SEQ/releases/download/v2.0/app.apk", 100L,
        "a".repeat(64), false)

    @Test fun githubIsAuthoritativeEvenWhenLegacyManifestExists() {
        var manifestCalls = 0
        val actual = UpdateSourceFallback.resolve(
            published = { manifestCalls++; stable.copy(versionCode = 19L) },
            github = { stable },
        )
        assertEquals(stable, actual)
        assertEquals(0, manifestCalls)
    }

    @Test fun noGithubExperimentalDoesNotFallBackToStaleManifest() {
        var manifestCalls = 0
        val actual = UpdateSourceFallback.resolve(
            published = { manifestCalls++; stable },
            github = { null },
        )
        assertNull(actual)
        assertEquals(0, manifestCalls)
    }

    @Test fun githubFailureIsNotHiddenByLegacyManifest() {
        try {
            UpdateSourceFallback.resolve(
                published = { stable },
                github = { throw IOException("GitHub unavailable") },
            )
            fail("Expected GitHub network failure")
        } catch (_: IOException) {
            // Retry remains the controller's responsibility.
        }
    }

    @Test fun noPublishedSourceUsesGithub() {
        assertEquals(stable, UpdateSourceFallback.resolve(null, { stable }))
    }
}

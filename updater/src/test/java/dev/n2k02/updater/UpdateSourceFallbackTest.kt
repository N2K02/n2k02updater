package dev.n2k02.updater

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateSourceFallbackTest {
    private val stable = UpdateRelease("v2.0", 20L, emptyList(),
        "https://example.org/app.apk", 100L, "a".repeat(64), false)

    @Test fun reachableManifestIsAuthoritative() {
        var calls = 0
        val actual = UpdateSourceFallback.resolve(
            published = { stable },
            github = { calls++; null },
        )
        assertEquals(stable, actual)
        assertEquals(0, calls)
    }

    @Test fun noUpdateFromManifestDoesNotUseGithub() {
        var calls = 0
        val actual = UpdateSourceFallback.resolve(
            published = { null },
            github = { calls++; stable },
        )
        assertNull(actual)
        assertEquals(0, calls)
    }

    @Test fun ioFailureFallsBackToGithub() {
        val actual = UpdateSourceFallback.resolve(
            published = { throw IOException("offline") },
            github = { stable },
        )
        assertEquals(stable, actual)
    }

    @Test fun noPublishedSourceUsesGithub() {
        assertEquals(stable, UpdateSourceFallback.resolve(null, { stable }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun programmingErrorsAreNotSilentlyHidden() {
        UpdateSourceFallback.resolve(
            published = { throw IllegalArgumentException("invalid state") },
            github = { stable },
        )
    }

    @Test(expected = IOException::class)
    fun githubFailureRemainsVisible() {
        UpdateSourceFallback.resolve(
            published = { throw IOException("manifest offline") },
            github = { throw IOException("GitHub offline") },
        )
    }
}

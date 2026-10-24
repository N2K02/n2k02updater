package dev.n2k02.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateOfferPolicyTest {
    private val release = UpdateRelease(
        tag = "v2.0", versionCode = 20L, notes = emptyList(),
        apkUrl = "https://example.com/app.apk", apkSize = 100L,
        sha256 = "a".repeat(64), experimental = false
    )

    @Test fun automaticCheckRespectsTwelveHourInterval() {
        val start = 1_000_000L
        assertTrue(UpdateOfferPolicy.isDue(start, 0L))
        assertFalse(UpdateOfferPolicy.isDue(start + 1000L, start))
        assertTrue(UpdateOfferPolicy.isDue(start + UpdateOfferPolicy.DEFAULT_INTERVAL_MS, start))
    }

    @Test fun clockRollbackPermitsCheck() {
        assertTrue(UpdateOfferPolicy.isDue(100L, 1000L))
    }

    @Test fun automaticSkipDoesNotHideManualOffers() {
        assertFalse(UpdateOfferPolicy.shouldOffer(release, "v2.0", manual = false))
        assertTrue(UpdateOfferPolicy.shouldOffer(release, "v2.0", manual = true))
        assertTrue(UpdateOfferPolicy.shouldOffer(release, "v1.9", manual = false))
    }
    @Test fun skipAppliesOnlyToSelectedReleaseChannel() {
        val skippedStable = UpdateOfferPolicy.skippedKey(release)
        assertFalse(UpdateOfferPolicy.shouldOffer(release, skippedStable, false))
        assertTrue(UpdateOfferPolicy.shouldOffer(release.copy(experimental = true), skippedStable, false))
        assertFalse(UpdateOfferPolicy.shouldOffer(release, "v2.0", false)) // legacy preference
    }

    @Test fun automaticCheckFailureIsSilent() {
        assertEquals(UpdateStatus.Idle, UpdateOfferPolicy.checkFailure(false, "offline"))
    }

    @Test fun manualCheckFailureShowsDiagnostics() {
        assertEquals(
            UpdateStatus.Failed("Could not check for updates", "offline"),
            UpdateOfferPolicy.checkFailure(true, "offline"),
        )
    }

}

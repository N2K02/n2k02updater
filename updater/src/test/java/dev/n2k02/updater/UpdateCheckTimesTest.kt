package dev.n2k02.updater

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckTimesTest {
    @Test fun manualRefreshImmediatelyChangesLastCheckedDate() {
        val old = UpdateCheckTimes(attemptedAt = 100L, succeededAt = 100L)
        val checking = old.begin(now = 250L)
        assertEquals(250L, checking.attemptedAt)
        assertEquals(100L, checking.succeededAt)
    }

    @Test fun successfulRefreshUpdatesBothTimestamps() {
        val updated = UpdateCheckTimes(100L, 100L).begin(250L).succeeded(300L)
        assertEquals(300L, updated.attemptedAt)
        assertEquals(300L, updated.succeededAt)
    }

    @Test fun offlineRetryDoesNotPretendItSucceeded() {
        val updated = UpdateCheckTimes(100L, 100L).begin(250L).failed(310L)
        assertEquals(310L, updated.attemptedAt)
        assertEquals(100L, updated.succeededAt)
    }
}

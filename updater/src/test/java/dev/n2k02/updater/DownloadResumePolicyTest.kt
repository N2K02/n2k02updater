package dev.n2k02.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResumePolicyTest {
    @Test fun acceptsValidRangeWhenContinuingInterruptedDownload() {
        assertTrue(DownloadResumePolicy.canAppend(4096L, 206, "bytes 4096-8191/16384"))
    }

    @Test fun resetsWhenServerIgnoresRangeOrReportsError() {
        assertFalse(DownloadResumePolicy.canAppend(4096L, 200, null))
        assertFalse(DownloadResumePolicy.canAppend(4096L, 416, "bytes */16384"))
        assertFalse(DownloadResumePolicy.canAppend(0L, 206, "bytes 0-10/20"))
    }

    @Test fun gatewayFailuresPreservePartialApk() {
        for (status in listOf(408, 429, 500, 502, 503, 504)) {
            assertFalse(DownloadResumePolicy.shouldResetPartial(status, null, 4096L))
        }
    }

    @Test fun ignoredRangesAndMalformedPartialResponsesRestartCleanly() {
        assertTrue(DownloadResumePolicy.shouldResetPartial(200, null, 4096L))
        assertTrue(DownloadResumePolicy.shouldResetPartial(416, "bytes */16384", 4096L))
        assertTrue(DownloadResumePolicy.shouldResetPartial(206, "bad range", 4096L))
        assertFalse(DownloadResumePolicy.shouldResetPartial(206, "bytes 4096-8191/16384", 4096L))
    }

    @Test fun rejectsMismatchedOrTruncatedContentRanges() {
        assertFalse(DownloadResumePolicy.canAppend(4096L, 206, "bytes 0-8191/16384"))
        assertFalse(DownloadResumePolicy.canAppend(4096L, 206, "bytes 4096-1000/16384"))
        assertFalse(DownloadResumePolicy.canAppend(4096L, 206, "bytes 4096-16384/16384"))
        assertFalse(DownloadResumePolicy.canAppend(4096L, 206, "bytes 4096-8191/*"))
        assertFalse(DownloadResumePolicy.canAppend(4096L, 206, "garbled"))
        assertFalse(DownloadResumePolicy.canAppend(4096L, 206, null))
        assertFalse(DownloadResumePolicy.canAppend(4096L, 206, "bytes 4096-8191/1099511627776"))
    }
}

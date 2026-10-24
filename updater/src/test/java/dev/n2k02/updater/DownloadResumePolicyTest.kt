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

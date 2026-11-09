package dev.n2k02.updater

import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRetryPolicyTest {
    @Test fun retriesTransientGatewayAndRequestTimeoutErrors() {
        for (status in listOf(408, 502, 503, 504)) {
            assertTrue(DownloadRetryPolicy.shouldRetry(IOException("APK download failed (HTTP $status)"), 0))
            assertTrue(DownloadRetryPolicy.shouldRetry(IOException("APK download failed (HTTP $status)"), 2))
            assertFalse(DownloadRetryPolicy.shouldRetry(IOException("APK download failed (HTTP $status)"), 3))
        }
    }

    @Test fun neverRetriesRateLimitsOrPermanentClientErrors() {
        for (status in listOf(400, 401, 403, 404, 416, 422, 429, 500)) {
            assertFalse(DownloadRetryPolicy.shouldRetry(IOException("APK download failed (HTTP $status)"), 0))
        }
        assertEquals("HTTP 429 (access or rate limit)",
            DownloadRetryPolicy.reason(IOException("APK download failed (HTTP 429)")))
    }

    @Test fun retriesSocketTimeoutAndPrematureEofButNotSecurityErrors() {
        assertTrue(DownloadRetryPolicy.shouldRetry(SocketTimeoutException("Read timed out"), 0))
        assertTrue(DownloadRetryPolicy.shouldRetry(EOFException("Unexpected end"), 1))
        assertFalse(DownloadRetryPolicy.shouldRetry(SocketTimeoutException("Timed out"), 3))
        for (error in listOf("APK checksum mismatch", "APK package name mismatch",
            "APK signing certificate does not match installed app",
            "Not enough free storage for this update", "Invalid release checksum")) {
            assertFalse(DownloadRetryPolicy.shouldRetry(IOException(error), 0))
        }
    }

    @Test fun delaysAreBoundedAndNeverNegative() {
        assertEquals(750L, DownloadRetryPolicy.backoffMillis(0))
        assertEquals(1500L, DownloadRetryPolicy.backoffMillis(1))
        assertEquals(3000L, DownloadRetryPolicy.backoffMillis(2))
        assertEquals(6000L, DownloadRetryPolicy.backoffMillis(3))
        assertEquals(750L, DownloadRetryPolicy.backoffMillis(-1))
        assertEquals(6000L, DownloadRetryPolicy.backoffMillis(Int.MAX_VALUE))
    }

    @Test fun parsingDoesNotConfuseHttpCodesWithOtherNumbers() {
        assertEquals(null, DownloadRetryPolicy.status(IOException("file size 504 bytes")))
        assertEquals(504, DownloadRetryPolicy.status(IOException("APK download failed (HTTP 504)")))
        assertFalse(DownloadRetryPolicy.shouldRetry(IOException("HTTP 504: invalid APK checksum"), 3))
    }
}

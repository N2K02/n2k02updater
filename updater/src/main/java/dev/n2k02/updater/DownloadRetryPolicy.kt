package dev.n2k02.updater

import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Bounded retries only for transient transport failures. HTTP 403/429
 * (including rate limits), invalid APKs, and verification failures are not retried.
 * The caller reopens the same checksum-scoped partial APK on every attempt.
 */
internal object DownloadRetryPolicy {
    const val maxRetries = 3
    private val transientStatuses = setOf(408, 502, 503, 504)
    private val statusPattern = Regex("""HTTP (\\d{3})""")

    fun status(error: IOException): Int? =
        statusPattern.find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()

    fun reason(error: IOException): String = when (val code = status(error)) {
        403, 429 -> "HTTP $code (access or rate limit)"
        null -> error.javaClass.simpleName
        else -> "HTTP $code"
    }

    fun shouldRetry(error: IOException, retriesAlreadyUsed: Int): Boolean {
        if (retriesAlreadyUsed >= maxRetries) return false
        val code = status(error)
        return if (code != null) code in transientStatuses
        else error is SocketTimeoutException || error is EOFException
    }

    fun backoffMillis(retriesAlreadyUsed: Int): Long =
        (750L shl retriesAlreadyUsed.coerceIn(0, maxRetries)).coerceAtMost(6000L)
}

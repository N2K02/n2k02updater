package dev.n2k02.updater

/** Validate HTTP Range responses before appending to an untrusted partial APK. */
internal object DownloadResumePolicy {
    private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

    /** A gateway error is retried without destroying valid partial bytes. */
    fun shouldResetPartial(statusCode: Int, contentRange: String?, offset: Long): Boolean =
        statusCode == 200 || statusCode == 416 ||
            (statusCode == 206 && !canAppend(offset, statusCode, contentRange))

    fun canAppend(offset: Long, statusCode: Int, contentRange: String?): Boolean {
        if (offset <= 0 || statusCode != 206) return false
        val parts = CONTENT_RANGE.matchEntire(contentRange.orEmpty()) ?: return false
        val start = parts.groupValues[1].toLongOrNull() ?: return false
        val end = parts.groupValues[2].toLongOrNull() ?: return false
        val total = parts.groupValues[3].toLongOrNull() ?: return false
        return start == offset && end >= start && total > end && total <= 1024L * 1024L * 1024L
    }
}

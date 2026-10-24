package dev.n2k02.updater

import java.time.Instant

/** Release timestamps are optional; missing/malformed dates are never invented. */
internal object ReleaseDateParser {
    fun parse(value: String?): Long? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty() || raw == "null") return null
        return runCatching {
            if (raw.all { it.isDigit() }) {
                when (raw.length) {
                    10 -> Math.multiplyExact(raw.toLong(), 1000L)
                    13 -> raw.toLong()
                    else -> null
                }
            } else {
                Instant.parse(raw).toEpochMilli()
            }
        }.getOrNull()?.takeIf { it > 0L }
    }
}

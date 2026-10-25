package dev.n2k02.updater

/** Keeps the latest check attempt distinct from the last successful check. */
internal data class UpdateCheckTimes(val attemptedAt: Long, val succeededAt: Long) {
    fun begin(now: Long): UpdateCheckTimes = copy(attemptedAt = now)
    fun succeeded(now: Long): UpdateCheckTimes = copy(attemptedAt = now, succeededAt = now)
    fun failed(now: Long): UpdateCheckTimes = copy(attemptedAt = now)
}

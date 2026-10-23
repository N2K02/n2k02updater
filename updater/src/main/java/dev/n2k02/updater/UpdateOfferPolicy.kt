package dev.n2k02.updater

/**
 * Pure policy for optional automatic checks and skipped releases.
 * Manual checks always bypass the interval and skipped-version preference.
 */
object UpdateOfferPolicy {
    const val DEFAULT_INTERVAL_MS = 12L * 60 * 60 * 1000

    fun isDue(nowMillis: Long, lastCheckMillis: Long, intervalMillis: Long = DEFAULT_INTERVAL_MS): Boolean {
        require(intervalMillis > 0)
        return lastCheckMillis <= 0L || nowMillis < lastCheckMillis ||
            nowMillis - lastCheckMillis >= intervalMillis
    }

    fun shouldOffer(release: UpdateRelease, skippedTag: String?, manual: Boolean): Boolean =
        manual || skippedTag != release.tag
    /** Background checks fail quietly; explicit user checks report the error. */
    fun checkFailure(manual: Boolean, diagnostics: String?): UpdateStatus =
        if (manual) UpdateStatus.Failed("Could not check for updates", diagnostics)
        else UpdateStatus.Idle
}

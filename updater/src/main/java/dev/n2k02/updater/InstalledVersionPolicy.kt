package dev.n2k02.updater

/** Android's currently installed package is authoritative; host configuration is fallback only. */
internal object InstalledVersionPolicy {
    fun effectiveCode(configured: Long, live: Long?): Long =
        live?.takeIf { it >= 0L } ?: configured

    fun canInstall(installedCode: Long, availableCode: Long): Boolean =
        availableCode > installedCode
}

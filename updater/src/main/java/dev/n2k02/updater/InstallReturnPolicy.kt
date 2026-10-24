package dev.n2k02.updater

/** Deterministic permission / installer-return decisions, shared across apps. */
internal object InstallReturnPolicy {
    fun afterPermission(release: UpdateRelease, granted: Boolean): UpdateStatus =
        if (granted) UpdateStatus.Ready(release, "Permission granted. Tap Install update to continue.")
        else UpdateStatus.NeedsInstallPermission(release)

    fun afterInstaller(release: UpdateRelease, installedNewVersion: Boolean): UpdateStatus =
        if (installedNewVersion) UpdateStatus.Current
        else UpdateStatus.Ready(release, "Installation was not completed. You can try again.")
}

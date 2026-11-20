package dev.n2k02.updater

// Android verifies the proof-of-rotation capabilities during installation.
internal fun signingCertificatesMatch(
    installedSigners: List<String>,
    archiveSigners: List<String>,
    archiveHistory: List<String>,
): Boolean {
    if (installedSigners.isEmpty() || archiveSigners.isEmpty()) return false
    if (installedSigners.toSet() == archiveSigners.toSet()) return true
    return installedSigners.size == 1 && archiveSigners.size == 1 &&
        archiveHistory.lastOrNull() == archiveSigners.single() &&
        installedSigners.single() in archiveHistory
}

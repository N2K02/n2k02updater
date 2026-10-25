package dev.n2k02.updater

/**
 * Stable phases for transitions: download progress and incidental error text must
 * not restart the entire Expressive animation on each StateFlow update.
 */
enum class UpdatePagePhase {
    IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, PERMISSION, FAILED
}

fun UpdateStatus.pagePhase(): UpdatePagePhase = when (this) {
    UpdateStatus.Idle -> UpdatePagePhase.IDLE
    UpdateStatus.Checking -> UpdatePagePhase.CHECKING
    UpdateStatus.Current -> UpdatePagePhase.CURRENT
    is UpdateStatus.Available -> UpdatePagePhase.AVAILABLE
    is UpdateStatus.Downloading -> UpdatePagePhase.DOWNLOADING
    is UpdateStatus.Ready -> UpdatePagePhase.READY
    is UpdateStatus.NeedsInstallPermission -> UpdatePagePhase.PERMISSION
    is UpdateStatus.Failed -> UpdatePagePhase.FAILED
}

/** Keep one primary action, and never imply that we install in the background. */
fun UpdateStatus.primaryActionLabel(): String = when (this) {
    UpdateStatus.Idle -> "Check for updates"
    UpdateStatus.Checking -> "Checking…"
    UpdateStatus.Current -> "Check again"
    is UpdateStatus.Available -> "Download update"
    is UpdateStatus.Downloading -> "Downloading…"
    is UpdateStatus.Ready -> "Install update"
    is UpdateStatus.NeedsInstallPermission -> "Allow installation"
    is UpdateStatus.Failed -> when (retryAction) {
        UpdateRetryAction.DOWNLOAD -> if (release != null) "Retry download" else "Check again"
        UpdateRetryAction.INSTALL -> if (release != null) "Retry installation" else "Check again"
        UpdateRetryAction.CHECK -> "Check again"
    }
}

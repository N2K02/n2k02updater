package dev.n2k02.updater

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * A pending update remains visible while downloading, verified, or awaiting Android's
 * install permission. Stable and experimental releases use the same indicator.
 */
fun UpdateStatus.hasPendingUpdate(): Boolean = when (this) {
    is UpdateStatus.Available,
    is UpdateStatus.Downloading,
    is UpdateStatus.Ready,
    is UpdateStatus.NeedsInstallPermission -> true
    is UpdateStatus.Failed -> release != null
    else -> false
}

/** Reusable "New" indicator for update cards and Settings list-item trailing content. */
@Composable
fun N2K02UpdateBadge(status: UpdateStatus, modifier: Modifier = Modifier) {
    if (status.hasPendingUpdate()) {
        Badge(modifier = modifier) { Text("New") }
    }
}

/**
 * Wrap a menu or navigation icon with the same pending-update badge.
 * The host retains control of the icon, click behavior, and overall navigation layout.
 */
@Composable
fun N2K02UpdateBadgedIcon(
    status: UpdateStatus,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val accessibleModifier = if (status.hasPendingUpdate()) {
        modifier.semantics { stateDescription = "New update available" }
    } else {
        modifier
    }
    BadgedBox(
        modifier = accessibleModifier,
        badge = { N2K02UpdateBadge(status) },
    ) {
        icon()
    }
}

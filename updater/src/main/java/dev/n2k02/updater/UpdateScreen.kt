@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.n2k02.updater

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Feed
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class InstalledAppInfo(
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lastUpdatedAt: Long,
)

@Suppress("DEPRECATION")
private fun Context.installedAppInfo(): InstalledAppInfo {
    val info = packageManager.getPackageInfo(packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    return InstalledAppInfo(
        name = packageManager.getApplicationLabel(applicationInfo).toString(),
        versionName = info.versionName ?: "Unknown",
        versionCode = code,
        lastUpdatedAt = info.lastUpdateTime,
    )
}

private fun displayVersion(version: String): String =
    if (version.startsWith("v", ignoreCase = true)) version else "v$version"

private fun displayDate(timeMillis: Long): String =
    if (timeMillis <= 0L) "Not available" else
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timeMillis))

private fun displaySize(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1) String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    else "${bytes / 1024} KB"
}

private fun Context.isMeteredConnection(): Boolean = runCatching {
    getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
}.getOrDefault(false)

/**
 * One complete, vertically scrollable Material 3 Expressive update page.
 * The host owns the navigation bar and its back action. If a host already
 * supplies "About & updates", leave showHeader=false (the default).
 */
@Composable
fun N2K02UpdatePage(
    controller: UpdaterController,
    installGuard: UpdateInstallGuard,
    modifier: Modifier = Modifier,
    experimentalEnabled: Boolean? = null,
    onExperimentalEnabledChange: ((Boolean) -> Unit)? = null,
    showHeader: Boolean = false,
) {
    UpdatePageContent(
        controller = controller,
        installGuard = installGuard,
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        experimentalEnabled = experimentalEnabled,
        onExperimentalEnabledChange = onExperimentalEnabledChange,
        showHeader = showHeader,
    )
}

/** For host pages that already own their vertical scroll (no nested scroll). */
@Composable
fun N2K02UpdateScreen(
    controller: UpdaterController,
    installGuard: UpdateInstallGuard,
    modifier: Modifier = Modifier,
    experimentalEnabled: Boolean? = null,
    onExperimentalEnabledChange: ((Boolean) -> Unit)? = null,
) {
    UpdatePageContent(
        controller = controller,
        installGuard = installGuard,
        modifier = modifier,
        experimentalEnabled = experimentalEnabled,
        onExperimentalEnabledChange = onExperimentalEnabledChange,
    )
}

@Composable
private fun UpdatePageContent(
    controller: UpdaterController,
    installGuard: UpdateInstallGuard,
    modifier: Modifier = Modifier,
    experimentalEnabled: Boolean? = null,
    onExperimentalEnabledChange: ((Boolean) -> Unit)? = null,
    showHeader: Boolean = false,
) {
    val status by controller.status.collectAsState()
    val lastCheckedAt by controller.lastCheckedAt.collectAsState()
    val lastCheckFailed by controller.lastCheckFailed.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val appInfo = remember(context.packageName) {
        runCatching { context.installedAppInfo() }.getOrNull()
    }
    var confirmLargeDownload by remember { mutableStateOf<UpdateRelease?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    fun downloadOrConfirm(candidate: UpdateRelease) {
        if ((candidate.apkSize ?: 0L) >= 20L * 1024 * 1024 && context.isMeteredConnection()) {
            confirmLargeDownload = candidate
        } else {
            controller.download(candidate)
        }
    }
    // Recheck on entering the page regardless of whether last state was Current.
    LaunchedEffect(controller) { controller.refreshIfDue() }

    val release = when (val current = status) {
        is UpdateStatus.Available -> current.release
        is UpdateStatus.Downloading -> current.release
        is UpdateStatus.Ready -> current.release
        is UpdateStatus.NeedsInstallPermission -> current.release
        is UpdateStatus.Failed -> current.release
        else -> null
    }

    confirmLargeDownload?.let { candidate ->
        AlertDialog(
            onDismissRequest = { confirmLargeDownload = null },
            title = { Text("Download over mobile data?") },
            text = { Text("This update is ${displaySize(candidate.apkSize ?: 0L)}. Mobile data charges may apply.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLargeDownload = null
                    controller.download(candidate)
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { confirmLargeDownload = null }) { Text("Cancel") } },
        )
    }

    Column(
        // Leave a reachable scroll tail below the primary action and expandable details.
        // The host Scaffold must still apply its actual bottom-bar innerPadding.
        modifier = modifier.fillMaxWidth().padding(
            start = 16.dp, end = 16.dp, top = 14.dp, bottom = 48.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (showHeader) {
            N2K02UpdateTitle(modifier = Modifier.padding(bottom = 8.dp))
        }

        // The launcher icon belongs only in the About & updates page title;
        // do not repeat it in the installed-version summary.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    appInfo?.name ?: context.packageName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    appInfo?.let { "${displayVersion(it.versionName)} (build ${it.versionCode})" }
                        ?: "Version unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    "Installed",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MetaDate(
                title = "Last updated",
                date = appInfo?.lastUpdatedAt ?: 0L,
                modifier = Modifier.weight(1f),
            )
            MetaDate(
                title = "Last checked",
                date = lastCheckedAt,
                modifier = Modifier.weight(1f),
            )
        }
        if (lastCheckFailed) {
            Text(
                "Could not check for updates. Try again when you are connected.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (experimentalEnabled != null && onExperimentalEnabledChange != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.FactCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Experimental updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Include signed preview builds. Test versions may have bugs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = experimentalEnabled, onCheckedChange = onExperimentalEnabledChange)
            }
        }
        HorizontalDivider()

        StatusPanel(status = status, installed = appInfo?.versionName)

        // One prominent primary action. The status panel owns context and
        // progress; we do not repeat status in a second nested action card.
        when (val current = status) {
            is UpdateStatus.Downloading -> {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = controller::cancel,
                ) { Text("Cancel download") }
            }
            else -> {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    enabled = current !is UpdateStatus.Checking &&
                        (activity != null || current !is UpdateStatus.Ready &&
                            current !is UpdateStatus.NeedsInstallPermission &&
                            !(current is UpdateStatus.Failed && current.retryAction == UpdateRetryAction.INSTALL)),
                    onClick = {
                        when (current) {
                            UpdateStatus.Idle, UpdateStatus.Current, is UpdateStatus.Failed -> {
                                when (current) {
                                    is UpdateStatus.Failed -> when (current.retryAction) {
                                        UpdateRetryAction.DOWNLOAD -> current.release?.let(::downloadOrConfirm)
                                            ?: controller.check(manual = true)
                                        UpdateRetryAction.INSTALL -> activity?.let { controller.install(it, installGuard) }
                                        UpdateRetryAction.CHECK -> controller.check(manual = true)
                                    }
                                    else -> controller.check(manual = true)
                                }
                            }
                            is UpdateStatus.Available -> downloadOrConfirm(current.release)
                            is UpdateStatus.Ready -> activity?.let { controller.install(it, installGuard) }
                            is UpdateStatus.NeedsInstallPermission -> activity?.let(controller::openInstallPermission)
                            else -> Unit
                        }
                    },
                ) {
                    Icon(
                        if (current is UpdateStatus.Available) Icons.Rounded.Download
                        else if (current is UpdateStatus.Ready) Icons.Rounded.SystemUpdate
                        else Icons.Rounded.Refresh,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(current.primaryActionLabel())
                }
            }
        }

        AnimatedVisibility(
            visible = status is UpdateStatus.Available ||
                status is UpdateStatus.NeedsInstallPermission ||
                (status is UpdateStatus.Failed && (status as UpdateStatus.Failed).diagnostics != null),
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                when (val current = status) {
                    is UpdateStatus.Available -> TextButton(onClick = { controller.skip(current.release) }) {
                        Text("Skip this version")
                    }
                    is UpdateStatus.NeedsInstallPermission -> TextButton(
                        onClick = { activity?.let { controller.install(it, installGuard) } },
                        enabled = activity != null,
                    ) { Text("Try installation again") }
                    is UpdateStatus.Failed -> {
                        if (!current.diagnostics.isNullOrBlank()) {
                            TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                                Text(if (showDiagnostics) "Hide error details" else "Show error details")
                            }
                            AnimatedVisibility(visible = showDiagnostics) {
                                Text(current.diagnostics.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

        // Show only information that changes the update decision. Installed
        // version and dates are already visible at the top of this page.
        if (release != null) {
            release.apkSize?.takeIf { it > 0L }?.let { bytes ->
                Text(
                    "${if (release.experimental) "Experimental" else "Stable"} update · ${displaySize(bytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (release.notes.isNotEmpty()) {
                var showNotes by remember(release.tag) { mutableStateOf(false) }
                TextButton(onClick = { showNotes = !showNotes }) {
                    Text(if (showNotes) "Hide what's new" else "What's new in ${release.tag}")
                }
                AnimatedVisibility(
                    visible = showNotes,
                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Text(
                        release.notes.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaDate(title: String, date: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(displayDate(date), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusPanel(status: UpdateStatus, installed: String?) {
    val background by animateColorAsState(
        targetValue = when (status.pagePhase()) {
            UpdatePagePhase.CURRENT -> MaterialTheme.colorScheme.tertiaryContainer
            UpdatePagePhase.AVAILABLE, UpdatePagePhase.READY -> MaterialTheme.colorScheme.primaryContainer
            UpdatePagePhase.FAILED, UpdatePagePhase.PERMISSION -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = 270),
        label = "Update panel background",
    )
    val foreground = when (status.pagePhase()) {
        UpdatePagePhase.CURRENT -> MaterialTheme.colorScheme.onTertiaryContainer
        UpdatePagePhase.AVAILABLE, UpdatePagePhase.READY -> MaterialTheme.colorScheme.onPrimaryContainer
        UpdatePagePhase.FAILED, UpdatePagePhase.PERMISSION -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = background,
        contentColor = foreground,
        modifier = Modifier.fillMaxWidth().animateContentSize(
            animationSpec = spring(stiffness = Spring.StiffnessLow),
        ),
    ) {
        AnimatedContent(
            targetState = status.pagePhase(),
            label = "Update stage",
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 })
                    .togetherWith(fadeOut(tween(140)))
            },
        ) { phase ->
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (phase == UpdatePagePhase.CHECKING || phase == UpdatePagePhase.DOWNLOADING) {
                        LoadingIndicator(modifier = Modifier.size(44.dp))
                    } else {
                        Icon(
                            imageVector = when (phase) {
                                UpdatePagePhase.CURRENT -> Icons.Rounded.CheckCircle
                                UpdatePagePhase.AVAILABLE, UpdatePagePhase.READY -> Icons.Rounded.SystemUpdate
                                UpdatePagePhase.FAILED, UpdatePagePhase.PERMISSION -> Icons.Rounded.ErrorOutline
                                else -> Icons.Rounded.Refresh
                            },
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = when (phase) {
                                    UpdatePagePhase.IDLE -> "Check for updates"
                                    UpdatePagePhase.CHECKING -> "Checking for updates"
                                    UpdatePagePhase.CURRENT -> "You're up to date"
                                    UpdatePagePhase.AVAILABLE -> "New update available"
                                    UpdatePagePhase.DOWNLOADING -> "Downloading update"
                                    UpdatePagePhase.READY -> "Ready to install"
                                    UpdatePagePhase.PERMISSION -> "Installation permission needed"
                                    UpdatePagePhase.FAILED -> "Update needs attention"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = when (val current = status) {
                                UpdateStatus.Idle -> "Check whether a newer release is available."
                                UpdateStatus.Checking -> "Contacting the update source…"
                                UpdateStatus.Current -> "You have the latest version${installed?.let { " (${displayVersion(it)})" } ?: ""}."
                                is UpdateStatus.Available -> "${current.release.tag} is available for download."
                                is UpdateStatus.Downloading -> "Downloading and verifying ${current.release.tag}."
                                is UpdateStatus.Ready -> current.notice ?: "${current.release.tag} has been verified."
                                is UpdateStatus.NeedsInstallPermission -> "Allow this app to install updates in Android settings."
                                is UpdateStatus.Failed -> current.message
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (phase == UpdatePagePhase.DOWNLOADING) {
                    val progress = (status as? UpdateStatus.Downloading)?.progress
                    Spacer(Modifier.size(14.dp))
                    if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}


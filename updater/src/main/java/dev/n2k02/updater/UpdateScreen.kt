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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import org.json.JSONObject

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
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(timeMillis))

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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            ) {
                MetaDate(
                    title = "Last updated",
                    date = appInfo?.lastUpdatedAt ?: 0L,
                    modifier = Modifier.weight(1f),
                )
                HorizontalDivider(
                    modifier = Modifier.size(width = 1.dp, height = 34.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                MetaDate(
                    title = "Last checked",
                    date = lastCheckedAt,
                    modifier = Modifier.weight(1f),
                    checking = status is UpdateStatus.Checking,
                )
            }
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

        // Only actual updater capabilities are surfaced: downloading supports Cancel,
        // not Pause. Available updates keep Skip and Download side-by-side.
        when (val current = status) {
            is UpdateStatus.Downloading -> {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    onClick = controller::cancel,
                ) {
                    CenteredAction(Icons.Rounded.Close, "Cancel download")
                }
            }
            else -> {
                val primaryEnabled = current !is UpdateStatus.Checking &&
                    (activity != null || current !is UpdateStatus.Ready &&
                        current !is UpdateStatus.NeedsInstallPermission &&
                        !(current is UpdateStatus.Failed && current.retryAction == UpdateRetryAction.INSTALL))
                val primaryAction: () -> Unit = {
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
                }
                val primaryIcon = when (current) {
                    is UpdateStatus.Available -> Icons.Rounded.Download
                    is UpdateStatus.Ready -> Icons.Rounded.SystemUpdate
                    else -> Icons.Rounded.Refresh
                }
                if (current is UpdateStatus.Available) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { controller.skip(current.release) },
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            CenteredAction(Icons.Rounded.SkipNext, "Skip this version")
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = primaryEnabled,
                            onClick = primaryAction,
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            CenteredAction(primaryIcon, current.primaryActionLabel())
                        }
                    }
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = primaryEnabled,
                        onClick = primaryAction,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        CenteredAction(primaryIcon, current.primaryActionLabel())
                    }
                }
                if (current is UpdateStatus.NeedsInstallPermission) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { activity?.let { controller.install(it, installGuard) } },
                        enabled = activity != null,
                    ) { Text("Try installation again") }
                }
            }
        }

        val failedStatus = status as? UpdateStatus.Failed
        if (failedStatus != null && !failedStatus.diagnostics.isNullOrBlank()) {
            ExpandableDetailsCard(
                title = "Error details",
                content = formatUpdaterDiagnostics(failedStatus.diagnostics.orEmpty()),
                isError = true,
                resetKey = "error-${failedStatus.message}-${failedStatus.diagnostics}",
            )
        }
        if (release != null && release.notes.isNotEmpty()) {
            ExpandableDetailsCard(
                title = "What's new",
                content = release.notes.joinToString("\n") { "• $it" },
                resetKey = release.tag,
            )
        }
    }
}

@Composable
private fun MetaDate(title: String, date: Long, modifier: Modifier = Modifier, checking: Boolean = false) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(if (checking) "Checking now…" else displayDate(date),
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

/** Icon and text are measured as one centered group at every available screen width. */
@Composable
private fun CenteredAction(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
    }
}

/** Keep arbitrary diagnostics as text if they cannot be parsed as JSON. */
internal fun formatUpdaterDiagnostics(raw: String): String =
    runCatching { JSONObject(raw).toString(2) }.getOrDefault(raw)

@Composable
private fun ExpandableDetailsCard(
    title: String,
    content: String,
    resetKey: String,
    isError: Boolean = false,
) {
    var expanded by remember(resetKey) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(
            animationSpec = spring(stiffness = Spring.StiffnessLow)),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Feed,
                    contentDescription = null)
                Text(title, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                content,
                style = if (isError) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = !expanded },
            ) {
                CenteredAction(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    if (expanded) "Show less" else "Show more",
                )
            }
        }
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
                val selectedRelease = when (val current = status) {
                    is UpdateStatus.Available -> current.release
                    is UpdateStatus.Downloading -> current.release
                    is UpdateStatus.Ready -> current.release
                    is UpdateStatus.NeedsInstallPermission -> current.release
                    is UpdateStatus.Failed -> current.release
                    else -> null
                }
                if (selectedRelease != null) {
                    Spacer(Modifier.size(10.dp))
                    Text(
                        (if (selectedRelease.experimental) "Experimental" else "Stable") +
                            " update" +
                            (selectedRelease.apkSize?.takeIf { it > 0L }
                                ?.let { " · ${displaySize(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (phase == UpdatePagePhase.CURRENT) {
                    Spacer(Modifier.size(12.dp))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f),
                    ) {
                        Text(
                            "No update required",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
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


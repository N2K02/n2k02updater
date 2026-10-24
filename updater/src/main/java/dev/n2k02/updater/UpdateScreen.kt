@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.n2k02.updater

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
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
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val lastUpdatedAt: Long,
)

@Suppress("DEPRECATION")
private fun Context.installedAppInfo(): InstalledAppInfo {
    val info = packageManager.getPackageInfo(packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    return InstalledAppInfo(
        appName = packageManager.getApplicationLabel(applicationInfo).toString(),
        versionName = info.versionName ?: "Unknown",
        versionCode = code,
        lastUpdatedAt = info.lastUpdateTime,
    )
}

private fun displayVersion(version: String): String =
    if (version.startsWith("v", ignoreCase = true)) version else "v$version"

/** Never make a metered network warning mandatory if the host denies status access. */
private fun Context.isMeteredConnection(): Boolean = runCatching {
    getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
}.getOrDefault(false)

private fun displayDate(timeMillis: Long): String =
    if (timeMillis <= 0L) "Not available"
    else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timeMillis))

/**
 * Complete, scrollable About & updates page. Put it in any app's Settings submenu
 * and keep the host's own top-bar Back button, theme, and install-safety guard.
 * The experimental switch appears only when a host supplies both arguments.
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
        modifier = modifier.verticalScroll(rememberScrollState()),
        experimentalEnabled = experimentalEnabled,
        onExperimentalEnabledChange = onExperimentalEnabledChange,
        showHeader = showHeader,
    )
}

/**
 * Backwards-compatible embeddable content for apps that already provide scrolling.
 * New dedicated settings pages should prefer N2K02UpdatePage.
 */
@Composable
fun N2K02UpdateScreen(
    controller: UpdaterController,
    installGuard: UpdateInstallGuard,
    modifier: Modifier = Modifier,
) {
    UpdatePageContent(controller, installGuard, modifier)
}

@Composable
private fun UpdatePageContent(
    controller: UpdaterController,
    installGuard: UpdateInstallGuard,
    modifier: Modifier = Modifier,
    experimentalEnabled: Boolean? = null,
    onExperimentalEnabledChange: ((Boolean) -> Unit)? = null,
    showHeader: Boolean = true,
) {
    val status by controller.status.collectAsState()
    val lastCheckedAt by controller.lastCheckedAt.collectAsState()
    val lastSuccessfulCheckAt by controller.lastSuccessfulCheckAt.collectAsState()
    val lastCheckFailed by controller.lastCheckFailed.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val appInfo = remember(context.packageName) { runCatching { context.installedAppInfo() }.getOrNull() }
    var showDiagnostics by remember { mutableStateOf(false) }
    var confirmLargeDownload by remember { mutableStateOf<UpdateRelease?>(null) }
    fun downloadOrConfirm(release: UpdateRelease) {
        if ((release.apkSize ?: 0L) >= 20L * 1024 * 1024 && context.isMeteredConnection()) {
            confirmLargeDownload = release
        } else {
            controller.download(release)
        }
    }

    // An app can stay open for days. Recheck whenever the page is entered
    // (respecting the shared throttle), even if the previous result was Current.
    LaunchedEffect(controller) { controller.refreshIfDue() }

    val release = when (val state = status) {
        is UpdateStatus.Available -> state.release
        is UpdateStatus.Downloading -> state.release
        is UpdateStatus.Ready -> state.release
        is UpdateStatus.NeedsInstallPermission -> state.release
        is UpdateStatus.Failed -> state.release
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
            dismissButton = {
                TextButton(onClick = { confirmLargeDownload = null }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showHeader) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("About & updates", style = MaterialTheme.typography.headlineMedium)
                N2K02UpdateBadge(status)
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Installed app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DetailLine("App", appInfo?.appName ?: context.packageName)
                DetailLine("Installed version", appInfo?.let { "${displayVersion(it.versionName)} (build ${it.versionCode})" } ?: "Unavailable")
                DetailLine("Last installed / updated", appInfo?.let { displayDate(it.lastUpdatedAt) } ?: "Unavailable")
                HorizontalDivider()
                DetailLine("Last successful check", displayDate(lastSuccessfulCheckAt))
                if (lastCheckFailed) {
                    DetailLine("Last check attempt", "${displayDate(lastCheckedAt)} · Could not connect")
                }
                DetailLine("Available version", release?.let { "${it.tag} (build ${it.versionCode})" }
                    ?: when (status) {
                        UpdateStatus.Current -> "You're up to date"
                        UpdateStatus.Checking -> "Checking…"
                        else -> "No pending update"
                    })
                if (release != null) {
                    DetailLine("Release channel", if (release.experimental) "Experimental" else "Stable")
                    release.publishedAtMillis?.let {
                        DetailLine("Release published", displayDate(it))
                    }
                    release.apkSize?.takeIf { it > 0L }?.let {
                        DetailLine("Download size", displaySize(it))
                    }
                }
            }
        }

        if (experimentalEnabled != null && onExperimentalEnabledChange != null) {
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Experimental updates", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Include test builds as well as stable releases. Test builds may contain bugs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = experimentalEnabled,
                        onCheckedChange = onExperimentalEnabledChange,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Update status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    N2K02UpdateBadge(status)
                }
                when (val current = status) {
                    UpdateStatus.Idle -> Text("Check for an available update.")
                    UpdateStatus.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoadingIndicator()
                        Text("Checking for updates…")
                    }
                    UpdateStatus.Current -> Text("You're up to date.")
                    is UpdateStatus.Available -> {
                        Text(
                            if (current.release.experimental)
                                "Experimental build ${current.release.tag} is available."
                            else "Update ${current.release.tag} is available.",
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { downloadOrConfirm(current.release) },
                        ) { Text("Download update") }
                        TextButton(onClick = { controller.skip(current.release) }) {
                            Text("Skip this version")
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Text("Downloading and verifying ${current.release.tag}…")
                        val progress = current.progress
                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%")
                        }
                        TextButton(onClick = controller::cancel) { Text("Cancel download") }
                    }
                    is UpdateStatus.Ready -> {
                        Text(current.notice ?: "Verified update ${current.release.tag} is ready to install.")
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = activity != null,
                            onClick = { activity?.let { controller.install(it, installGuard) } },
                        ) { Text("Install update") }
                    }
                    is UpdateStatus.NeedsInstallPermission -> {
                        Text("Allow this app to install updates in Android settings.")
                        Button(
                            enabled = activity != null,
                            onClick = { activity?.let(controller::openInstallPermission) },
                        ) { Text("Open install permission") }
                        TextButton(
                            enabled = activity != null,
                            onClick = { activity?.let { controller.install(it, installGuard) } },
                        ) { Text("Try installation again") }
                    }
                    is UpdateStatus.Failed -> {
                        Text(current.message, color = MaterialTheme.colorScheme.error)
                        current.release?.let { failedRelease ->
                            when (current.retryAction) {
                                UpdateRetryAction.DOWNLOAD -> Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { downloadOrConfirm(failedRelease) },
                                ) { Text("Retry download") }
                                UpdateRetryAction.INSTALL -> Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = activity != null,
                                    onClick = { activity?.let { controller.install(it, installGuard) } },
                                ) { Text("Retry installation") }
                                UpdateRetryAction.CHECK -> Unit
                            }
                        }
                        if (!current.diagnostics.isNullOrBlank()) {
                            TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                                Text(if (showDiagnostics) "Hide error details" else "Show error details")
                            }
                            if (showDiagnostics) {
                                Text(
                                    current.diagnostics.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                if (status !is UpdateStatus.Checking &&
                    status !is UpdateStatus.Downloading &&
                    status !is UpdateStatus.Ready &&
                    status !is UpdateStatus.NeedsInstallPermission &&
                    !(status is UpdateStatus.Failed && (status as UpdateStatus.Failed).release != null)) {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { controller.check(manual = true) },
                    ) { Text("Check again") }
                }
            }
        }

        if (release?.notes?.isNotEmpty() == true) {
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("What's new", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    var expanded by remember(release.tag) { mutableStateOf(false) }
                    var overflowed by remember(release.tag) { mutableStateOf(false) }
                    Text(
                        release.notes.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) overflowed = it.hasVisualOverflow },
                    )
                    if (overflowed || expanded) TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Show less" else "Show more")
                    }
                }
            }
        }
        Text(
            "Updates are downloaded securely and checked for APK identity, version, and signing certificate before installation. Android confirms the final install.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun displaySize(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1) String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    else "${bytes / 1024} KB"
}

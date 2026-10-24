@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.n2k02.updater

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Reusable Settings > About & updates content. The host provides its own install guard.
 * Compose colors and typography inherit the host's Material 3 Expressive theme.
 * The host owns the theme; this screen must not override its colors or motion.
 */
@Composable
fun N2K02UpdateScreen(
    controller: UpdaterController,
    installGuard: UpdateInstallGuard,
    modifier: Modifier = Modifier,
) {
    val status by controller.status.collectAsState()
    val activity = LocalContext.current.findActivity()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("App updates", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Keep the same New indicator visible across available, downloading,
                // ready-to-install, and install-permission states.
                if (status.hasPendingUpdate()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        N2K02UpdateBadge(status)
                    }
                }
                when (val state = status) {
                    UpdateStatus.Idle -> {
                        Text("Check for the latest version.")
                        FilledTonalButton(onClick = controller::check) { Text("Check again") }
                    }
                    UpdateStatus.Checking -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LoadingIndicator()
                            Text("Checking for updates…")
                        }
                    }
                    UpdateStatus.Current -> {
                        Text("You're up to date.")
                        FilledTonalButton(onClick = controller::check) { Text("Check again") }
                    }
                    is UpdateStatus.Available -> {
                        val release = state.release
                        var notesExpanded by remember(release.tag) { mutableStateOf(false) }
                        var notesOverflowed by remember(release.tag) { mutableStateOf(false) }
                        Text("Update available: ${release.tag}", style = MaterialTheme.typography.titleMedium)

                        // All shared updater actions precede the changelog: long notes cannot
                        // push Download out of sight. No app-specific fallback action.
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { controller.download(release) },
                        ) { Text("Download update") }

                        if (release.notes.isNotEmpty()) {
                            Text("What's new", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = release.notes.joinToString("\n") { "• $it" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (notesExpanded) Int.MAX_VALUE else 8,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { result ->
                                    if (!notesExpanded) notesOverflowed = result.hasVisualOverflow
                                },
                            )
                            if (notesOverflowed || notesExpanded) {
                                TextButton(onClick = { notesExpanded = !notesExpanded }) {
                                    Text(if (notesExpanded) "Show less" else "Show more")
                                }
                            }
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Text("Downloading and verifying ${state.release.tag}")
                        if (state.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = controller::cancel) { Text("Cancel") }
                    }
                    is UpdateStatus.Ready -> {
                        Text("Verified update ${state.release.tag} is ready.")
                        Button(
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
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        state.diagnostics?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        FilledTonalButton(onClick = controller::check) { Text("Retry") }
                    }
                }
            }
        }
    }
}

package dev.n2k02.updater

import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A host-controlled page title with the installed application's launcher icon.
 *
 * Mirrors MRadio's app-branded header without hardcoding a package icon, name,
 * or Material theme. Compose it in a host TopAppBar title slot, or use
 * N2K02UpdatePage(showHeader = true) when the page owns its own title.
 *
 * The icon is decorative (the title itself remains readable to accessibility
 * services). A host TopAppBar should provide any Back navigation separately.
 */
@Composable
fun N2K02UpdateTitle(
    modifier: Modifier = Modifier,
    title: String = "About & updates",
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AndroidView(
            modifier = Modifier.size(28.dp),
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    setImageDrawable(
                        runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName) }
                            .getOrNull()
                    )
                }
            },
            update = {
                it.setImageDrawable(
                    runCatching {
                        context.packageManager.getApplicationIcon(context.packageName)
                    }.getOrNull()
                )
            },
        )
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

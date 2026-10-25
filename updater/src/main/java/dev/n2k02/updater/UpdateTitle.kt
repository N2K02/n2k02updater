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

/** Android launcher icon, not a fixed updater logo. Shared by page content and host top bars. */
@Composable
fun N2K02AppIcon(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }
        },
        update = { view ->
            view.setImageDrawable(
                runCatching {
                    context.packageManager.getApplicationIcon(context.packageName)
                }.getOrNull()
            )
        },
    )
}

/**
 * MRadio-style title using the current app's actual icon. Use in the host app's
 * TopAppBar to avoid a second "About & updates" heading inside the page.
 */
@Composable
fun N2K02UpdateTitle(
    modifier: Modifier = Modifier,
    title: String = "About & updates",
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        N2K02AppIcon(Modifier.size(28.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

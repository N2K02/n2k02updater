package dev.n2k02.updater

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveCurrentStateLayoutTest {
    private fun source(): String = listOf(
        File("src/main/java/dev/n2k02/updater/UpdateScreen.kt"),
        File("updater/src/main/java/dev/n2k02/updater/UpdateScreen.kt"),
    ).first { it.isFile }.readText()

    @Test fun centeredMetadataUsesEqualColumnsInExpressiveSurface() {
        val ui = source()
        assertTrue(ui.contains("title = \"Last updated\""))
        assertTrue(ui.contains("title = \"Last checked\""))
        assertTrue(ui.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue(ui.contains("color = MaterialTheme.colorScheme.surfaceContainerLow"))
        assertTrue(ui.contains("color = MaterialTheme.colorScheme.outlineVariant"))
    }

    @Test fun currentStateShowsNoUpdateRequiredWithoutFakeReleaseNotes() {
        val ui = source()
        assertTrue(ui.contains("phase == UpdatePagePhase.CURRENT"))
        assertTrue(ui.contains("\"No update required\""))
        assertTrue(ui.contains("if (release != null && release.notes.isNotEmpty())"))
        assertTrue(ui.contains("CenteredAction(primaryIcon, current.primaryActionLabel())"))
    }
}

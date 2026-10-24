package dev.n2k02.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateOfferSnapshotTest {
    private val config = UpdateConfig(
        repository = "N2K02/MRadio",
        packageName = "com.mradio.app",
        installedVersionCode = 10L,
        experimentalEnabled = true,
    )
    private val release = UpdateRelease(
        tag = "v1.2",
        versionCode = 12L,
        notes = listOf("Fixed stations", "Improved playback"),
        apkUrl = "https://example.org/app.apk",
        apkSize = 123L,
        sha256 = "a".repeat(64),
        experimental = false,
    )

    @Test
    fun restoresKnownOfferAcrossProcessRestartWithoutNetwork() {
        val data = UpdateOfferSnapshot.encode(config, release)
        assertNotNull(data)
        assertEquals(release, UpdateOfferSnapshot.decode(data, config, skippedTag = null))
    }

    @Test
    fun experimentalOfferRestoresOnlyForMatchingChannel() {
        val experimental = release.copy(experimental = true, tag = "preview-12")
        val data = UpdateOfferSnapshot.encode(config, experimental)
        assertEquals(experimental, UpdateOfferSnapshot.decode(data, config, null))
        assertNull(UpdateOfferSnapshot.decode(data, config.copy(experimentalEnabled = false), null))
    }

    @Test
    fun neverRestoresAnotherAppOrAlreadyInstalledVersion() {
        val data = UpdateOfferSnapshot.encode(config, release)
        assertNull(UpdateOfferSnapshot.decode(data, config.copy(packageName = "com.example.other"), null))
        assertNull(UpdateOfferSnapshot.decode(data, config.copy(repository = "N2K02/BCam"), null))
        assertNull(UpdateOfferSnapshot.decode(data, config.copy(installedVersionCode = 12L), null))
    }

    @Test
    fun skippedVersionIsNotRestored() {
        val data = UpdateOfferSnapshot.encode(config, release)
        assertNull(UpdateOfferSnapshot.decode(data, config, skippedTag = "v1.2"))
        assertNotNull(UpdateOfferSnapshot.decode(data, config, skippedTag = "v1.1"))
    }

    @Test
    fun invalidOrTamperedMetadataIsDiscarded() {
        assertNull(UpdateOfferSnapshot.decode("invalid", config, null))
        assertNull(UpdateOfferSnapshot.encode(config, release.copy(sha256 = null)))
        assertNull(UpdateOfferSnapshot.encode(config, release.copy(apkUrl = "http://example.org/app.apk")))
        assertNull(UpdateOfferSnapshot.encode(config, release.copy(versionCode = 10L)))
        val data = UpdateOfferSnapshot.encode(config, release)!!
        assertNull(UpdateOfferSnapshot.decode(data.replace("https://", "http://"), config, null))
        assertNull(UpdateOfferSnapshot.decode(data.replace("a".repeat(64), "bad"), config, null))
    }

    @Test
    fun notesAreBoundedToTwelveItems() {
        val data = UpdateOfferSnapshot.encode(config, release.copy(notes = (1..20).map { "$it" }))
        assertEquals(12, UpdateOfferSnapshot.decode(data, config, null)?.notes?.size)
    }
}

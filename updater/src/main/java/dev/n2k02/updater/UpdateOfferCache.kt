package dev.n2k02.updater

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Only discovered offers are persisted. Verified APK files and install permissions are
 * never restored from preferences: after process death an offer starts at Available
 * and must be downloaded and verified again before installation.
 */
internal class UpdateOfferCache(
    private val preferences: SharedPreferences?,
    private val config: UpdateConfig,
) {
    companion object {
        private const val KEY = "n2k02_pending_offer_v1"
    }

    fun restore(): UpdateRelease? {
        val serialized = preferences?.getString(KEY, null) ?: return null
        return runCatching {
            val saved = JSONObject(serialized)
            if (saved.optInt("schema") != 1 ||
                saved.optString("repository") != config.repository ||
                saved.optString("packageName") != config.packageName ||
                saved.optLong("installedVersionCode") != config.installedVersionCode ||
                saved.optBoolean("experimentalEnabled") != config.experimentalEnabled) return@runCatching null

            val offer = saved.getJSONObject("release")
            val tag = offer.getString("tag").trim()
            val code = offer.getLong("versionCode")
            val url = offer.getString("apkUrl")
            val checksum = offer.getString("sha256").lowercase()
            val experimental = offer.getBoolean("experimental")
            if (tag.isBlank() || code <= config.installedVersionCode ||
                !url.startsWith("https://") ||
                !checksum.matches(Regex("[a-f0-9]{64}")) ||
                (experimental && !config.experimentalEnabled) ||
                preferences.getString("skipped", null) == tag) return@runCatching null

            val notesJson = offer.optJSONArray("notes") ?: JSONArray()
            val notes = (0 until minOf(notesJson.length(), 12))
                .map { notesJson.optString(it) }
            UpdateRelease(
                tag = tag,
                versionCode = code,
                notes = notes,
                apkUrl = url,
                apkSize = offer.optLong("apkSize").takeIf { it > 0 },
                sha256 = checksum,
                experimental = experimental,
            )
        }.getOrNull()
    }

    fun save(release: UpdateRelease) {
        if (preferences == null) return
        // Don't persist offers that cannot subsequently pass download verification.
        if (release.tag.isBlank() || release.versionCode <= config.installedVersionCode ||
            !release.apkUrl.startsWith("https://") ||
            release.sha256?.matches(Regex("[a-fA-F0-9]{64}")) != true) {
            clear()
            return
        }
        val json = JSONObject()
            .put("schema", 1)
            .put("repository", config.repository)
            .put("packageName", config.packageName)
            .put("installedVersionCode", config.installedVersionCode)
            .put("experimentalEnabled", config.experimentalEnabled)
            .put("release", JSONObject()
                .put("tag", release.tag)
                .put("versionCode", release.versionCode)
                .put("apkUrl", release.apkUrl)
                .put("sha256", release.sha256)
                .put("apkSize", release.apkSize)
                .put("experimental", release.experimental)
                .put("notes", JSONArray(release.notes.take(12))))
        preferences.edit().putString(KEY, json.toString()).apply()
    }

    fun clear() {
        preferences?.edit()?.remove(KEY)?.apply()
    }
}

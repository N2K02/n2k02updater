package dev.n2k02.updater

/** Verified migration records for releases published before version codes were included in notes.
 * Keys bind the source repository, exact tag and GitHub asset SHA-256; later releases use
 * their own release metadata and never depend on these frozen compatibility records.
 */
internal object HistoricalStableVersions {
    private val versions = mapOf(
        "N2K02/AAYoutube|v8.3|8514bd8a7524bf4c210768edde5cc16daacb2db1f2fae919ff9402a2abc56f24" to 100750L,
        "N2K02/BCam|v5.9|4b4072a5050a244844b7841fa37647406c1f51d69b70c61bd20ea0f452b4eace" to 100222L,
        "N2K02/ChargeWatt|v2.2|d65c3c5baf74b6b20313dd18ab24dab9629d5ac9f4913c94427ef654debc8ada" to 100110L,
        "N2K02/HFreebuds|v6.5|4a37f6115d1d50ae45924ae3f11d1fa184c4dc8d7ac789cafa4296197efa8d06" to 100211L,
        "N2K02/McpAdbAndroidLogger|v3.3|06bb5357ca1153c3305eb23f1d4d82790a46c39d3357349a1232c762393fe9e2" to 100538L,
        "N2K02/NetSpeed|v6.3|0f075fac5ef3ae8b43895505ce6e2f73616a51a2b45994dba03da2e70a238171" to 100208L,
        "N2K02/PBar|v7.3|eae48bf302ea21027c6731e83f5648a45481af18ad995c12d364964cc413f0ce" to 100223L,
        "N2K02/PCL|v7.1|b6cfe375efba3603116fcbcb4f293e9421a1baa32a485574a3ad9cb097b33540" to 100586L,
        "N2K02/PixelCast|v5.6|4946bb02a5543f3adc279b290cf9b1a7c0fdc9030ddda11e213e1ec233c91c7c" to 100173L,
        "N2K02/SDCleaner|sdcleaner-v2.1|ed4ad355ec1c0069cdf58c74e998fcd61d11c42608d12feb7449b4e320ba3c26" to 100251L,
        "N2K02/SpeedTester|v9.4|7ad478da148a488b39d69c6e3f1c88ffaeed08d22651f954d7331e6052cb08ed" to 100343L,
        "N2K02/VREC|v7.4|237418a642b7ec72cdd469c19a06423a1c21818e5cd9d244ea02ede4ba7d71d9" to 101168L,
    )

    fun find(repository: String?, tag: String, digest: String): Long? {
        if (repository == null || !digest.startsWith("sha256:")) return null
        return versions["$repository|$tag|${digest.removePrefix("sha256:").lowercase()}"]
    }
}

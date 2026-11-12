package com.v2ray.ang

import android.os.Build
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class MoghimiUpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val mandatory: Boolean
)

/**
 * Checks the public Supabase release table first, then the GitHub releases feed.
 * Only public release metadata is requested; no privileged key is embedded here.
 */
object MoghimiUpdateChecker {
    private const val SUPABASE_URL =
        "https://atrwhvyhopifugphfcpe.supabase.co/rest/v1/app_releases" +
            "?select=version_code,version_name,download_url,release_notes,is_mandatory" +
            "&platform=eq.android&is_active=eq.true&order=version_code.desc&limit=1"
    private const val SUPABASE_PUBLISHABLE_KEY =
        "sb_publishable_9jauFOO0mFzTSs5rtI2Orw_ZekPGGdt"
    private const val GITHUB_RELEASES_URL =
        "https://api.github.com/repos/MOGHIMI-IR/Moghimi_VPN/releases?per_page=12"

    fun check(): MoghimiUpdateInfo? {
        fetchSupabaseRelease()?.let { return it }
        return fetchGitHubRelease()
    }

    private fun fetchSupabaseRelease(): MoghimiUpdateInfo? {
        return try {
            val rows = JSONArray(request(SUPABASE_URL, supabase = true))
            if (rows.length() == 0) return null
            val row = rows.optJSONObject(0) ?: return null
            val code = row.optInt("version_code", 0)
            if (code <= BuildConfig.VERSION_CODE) return null
            val version = row.optString("version_name").takeIf { it.isNotBlank() } ?: return null
            val link = row.optString("download_url").takeIf { it.startsWith("https://") } ?: return null
            MoghimiUpdateInfo(
                versionName = version,
                downloadUrl = link,
                releaseNotes = row.optString("release_notes").take(1400),
                mandatory = row.optBoolean("is_mandatory", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchGitHubRelease(): MoghimiUpdateInfo? {
        return try {
            val releases = JSONArray(request(GITHUB_RELEASES_URL, supabase = false))
            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                val assets = release.optJSONArray("assets") ?: continue
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.optJSONObject(assetIndex) ?: continue
                    val name = asset.optString("name")
                    val version = extractVersion(name) ?: continue
                    if (compareVersions(version, BuildConfig.VERSION_NAME) <= 0) continue
                    val expectedSuffixes = Build.SUPPORTED_ABIS
                        .distinct()
                        .map { "_$it.apk" } + "_universal.apk"
                    if (expectedSuffixes.none { name.endsWith(it) }) continue
                    val link = asset.optString("browser_download_url")
                        .takeIf { it.startsWith("https://") } ?: continue
                    return MoghimiUpdateInfo(
                        versionName = version,
                        downloadUrl = link,
                        releaseNotes = release.optString("body")
                            .ifBlank { release.optString("name") }
                            .take(1400),
                        mandatory = false
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractVersion(assetName: String): String? {
        return Regex("""^MoghimiVPN_(\d+(?:\.\d+)*)_.+\.apk$""")
            .find(assetName)?.groupValues?.getOrNull(1)
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun request(address: String, supabase: Boolean): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 4_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MoghimiVPN-Android/${BuildConfig.VERSION_NAME}")
            if (supabase) {
                setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
            } else {
                setRequestProperty("Accept", "application/vnd.github+json")
            }
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) return ""
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

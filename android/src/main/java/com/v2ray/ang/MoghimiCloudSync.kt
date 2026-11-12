package com.v2ray.ang

import android.net.Uri
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads only active public entries from the Moghimi V2Ray Supabase project.
 *
 * This uses the same publishable browser key as the website. It is not a service-role
 * credential. RLS remains the authority: inactive entries and private order data are
 * never requested by this client.
 *
 * VPN traffic itself is routed from Android's VpnService to the selected V2Ray endpoint.
 * It does not pass through Supabase.
 */
object MoghimiCloudSync {
    const val CLOUD_GROUP_ID = "moghimi-cloud"

    private const val PROJECT_URL = "https://atrwhvyhopifugphfcpe.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_9jauFOO0mFzTSs5rtI2Orw_ZekPGGdt"
    private const val STORAGE_BUCKET = "v2ray-files"

    private const val CONFIG_TABLE_URL =
        "$PROJECT_URL/rest/v1/v2ray_entries?select=id,title,entry_type,config_text,file_path,file_name&is_active=eq.true&order=created_at.desc"

    data class SyncResult(
        val activeEntries: Int,
        val importedProfiles: Int,
        val importedSubscriptions: Int
    )

    /**
     * Fetch all active payloads before replacing the app-owned group. If a file download
     * fails, the previously working Moghimi group is left untouched.
     */
    fun sync(): SyncResult {
        val entries = JSONArray(request(CONFIG_TABLE_URL, "GET"))
        if (entries.length() == 0) {
            throw IOException("در سایت هنوز کانفیگ فعالی منتشر نشده است.")
        }

        val payloads = ArrayList<String>(entries.length())
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val type = entry.optString("entry_type")

            val payload = when (type) {
                "text" -> entry.optString("config_text")
                "file" -> {
                    val path = entry.optString("file_path")
                    if (path.isBlank()) {
                        throw IOException("مسیر یکی از فایل‌های کانفیگ خالی است.")
                    }
                    downloadPrivateFile(path)
                }
                else -> ""
            }

            if (payload.isNotBlank()) {
                payloads += payload.trim()
            }
        }

        if (payloads.isEmpty()) {
            throw IOException("هیچ محتوای کانفیگ قابل واردکردنی در سایت پیدا نشد.")
        }

        val subscription = MmkvManager.decodeSubscription(CLOUD_GROUP_ID)
            ?: SubscriptionItem(
                remarks = "کانفیگ‌های سایت مقیمی",
                enabled = true,
                autoUpdate = false
            )
        subscription.remarks = "کانفیگ‌های سایت مقیمی"
        subscription.enabled = true
        subscription.autoUpdate = false
        MmkvManager.encodeSubscription(CLOUD_GROUP_ID, subscription)

        // Parse each entry separately. This preserves pretty-printed JSON files as well as
        // multiple independent text entries. Only the first successfully imported entry replaces
        // the Moghimi cloud group; subsequent entries append to that same group.
        var importedProfiles = 0
        var importedSubscriptions = 0
        var firstCommittedPayload = true

        for (payload in payloads) {
            val (profiles, subscriptions) = AngConfigManager.importBatchConfig(
                payload,
                CLOUD_GROUP_ID,
                append = !firstCommittedPayload
            )
            importedProfiles += profiles
            importedSubscriptions += subscriptions
            if (profiles > 0 || subscriptions > 0) {
                firstCommittedPayload = false
            }
        }

        if (importedProfiles == 0 && importedSubscriptions == 0) {
            throw IOException("فایل‌ها دریافت شدند، اما کانفیگ معتبر شناسایی نشد.")
        }

        return SyncResult(
            activeEntries = entries.length(),
            importedProfiles = importedProfiles,
            importedSubscriptions = importedSubscriptions
        )
    }

    private fun downloadPrivateFile(filePath: String): String {
        val encodedPath = Uri.encode(filePath, "/")
        val signEndpoint = "$PROJECT_URL/storage/v1/object/sign/$STORAGE_BUCKET/$encodedPath"
        val signRequest = JSONObject().put("expiresIn", 600).toString()
        val signResponse = JSONObject(request(signEndpoint, "POST", signRequest))
        val signedPath = signResponse.optString("signedURL")

        if (signedPath.isBlank()) {
            throw IOException("ساخت لینک موقت دریافت فایل ناموفق بود.")
        }

        val signedUrl = if (
            signedPath.startsWith("https://") || signedPath.startsWith("http://")
        ) {
            signedPath
        } else {
            val relative = signedPath.removePrefix("/")
            val storageRelative = if (relative.startsWith("storage/v1/")) {
                relative
            } else {
                "storage/v1/$relative"
            }
            "$PROJECT_URL/$storageRelative"
        }

        // The signed token in the URL grants temporary access; no API key is sent to this URL.
        return request(signedUrl, "GET", includeApiKey = false)
    }

    private fun request(
        address: String,
        method: String,
        body: String? = null,
        includeApiKey: Boolean = true
    ): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "*/*")
            setRequestProperty("User-Agent", "MoghimiVPN-Android/1.0.1.1")
            if (includeApiKey) {
                // Supabase sb_publishable keys are opaque API keys, not JWTs.
                // Send them only in the apikey header; Authorization is reserved for user JWTs.
                setRequestProperty("apikey", PUBLISHABLE_KEY)
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                // Do not include configuration payloads, signed URLs or credentials in errors.
                throw IOException("ارتباط با سرویس کانفیگ ناموفق بود (HTTP $status).")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}

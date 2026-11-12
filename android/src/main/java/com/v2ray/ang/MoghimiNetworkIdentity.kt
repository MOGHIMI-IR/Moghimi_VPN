package com.v2ray.ang

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class MoghimiNetworkIdentity(
    val ipAddress: String,
    val countryCode: String?,
    val countryName: String?
)

/**
 * Resolves the public egress address seen by the app itself.
 * The VpnService is configured to route this app through the tunnel before this lookup.
 */
object MoghimiNetworkIdentityLookup {
    fun lookup(): MoghimiNetworkIdentity? {
        return try {
            lookupCountryIs()
        } catch (_: Exception) {
            try {
                lookupIpApi()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun lookupCountryIs(): MoghimiNetworkIdentity {
        val json = JSONObject(request("https://api.country.is/"))
        val ip = json.optString("ip").takeIf(::looksLikeIp)
            ?: throw IOException("IP address was not returned")
        val code = json.optString("country").takeIf { it.length == 2 }
        return MoghimiNetworkIdentity(ip, code, code?.let(::countryName))
    }

    private fun lookupIpApi(): MoghimiNetworkIdentity {
        val json = JSONObject(request("https://ipapi.co/json/"))
        val ip = json.optString("ip").takeIf(::looksLikeIp)
            ?: throw IOException("IP address was not returned")
        val code = json.optString("country_code")
            .takeIf { it.length == 2 }
            ?: json.optString("country").takeIf { it.length == 2 }
        val name = json.optString("country_name").takeIf { it.isNotBlank() }
            ?: code?.let(::countryName)
        return MoghimiNetworkIdentity(ip, code, name)
    }

    private fun request(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 6_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MoghimiVPN-Android/1.0.2")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("IP lookup returned HTTP $status")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun countryName(code: String): String? {
        val normalized = code.uppercase(Locale.ROOT)
        return Locale.Builder()
            .setLanguage("fa")
            .setRegion(normalized)
            .build()
            .getDisplayCountry(Locale.forLanguageTag("fa"))
            .takeIf { it.isNotBlank() && it != normalized }
    }

    private fun looksLikeIp(value: String): Boolean {
        if (value.isBlank() || value.length > 45) return false
        if (value.contains(':')) return value.matches(Regex("[0-9a-fA-F:%.]+"))
        val parts = value.split('.')
        return parts.size == 4 && parts.all {
            it.toIntOrNull()?.let { octet -> octet in 0..255 } == true
        }
    }
}

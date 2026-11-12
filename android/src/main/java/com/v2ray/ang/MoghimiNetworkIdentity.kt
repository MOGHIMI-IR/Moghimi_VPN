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
    /**
     * Confirms that ordinary HTTPS traffic can leave this app through the active VPN.
     * This check is independent of country/IP lookup and never controls tunnel startup.
     */
    fun verifyInternetThroughTunnel(): Boolean {
        data class Probe(
            val url: String,
            val expectedStatus: Int,
            val expectedBody: String? = null
        )

        // Use independent providers. A generic HTTP 200/302 can be a captive-portal
        // page, so each probe must match its expected status or response text.
        val probes = listOf(
            Probe("https://www.gstatic.com/generate_204", 204),
            Probe("https://cp.cloudflare.com/generate_204", 204),
            Probe(
                "https://www.msftconnecttest.com/connecttest.txt",
                200,
                "Microsoft Connect Test"
            ),
            Probe(
                "https://captive.apple.com/hotspot-detect.html",
                200,
                "Success"
            )
        )

        for (probe in probes) {
            val connection = try {
                (URL(probe.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 2_200
                    readTimeout = 2_200
                    instanceFollowRedirects = true
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("User-Agent", "MoghimiVPN-Android/1.0.16")
                }
            } catch (_: Exception) {
                continue
            }

            try {
                if (connection.responseCode != probe.expectedStatus) continue
                val expectedBody = probe.expectedBody
                if (expectedBody == null) return true

                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText().take(2_048)
                }
                if (body.contains(expectedBody, ignoreCase = true)) return true
            } catch (_: Exception) {
                // Try another independent reachability endpoint.
            } finally {
                connection.disconnect()
            }
        }
        return false
    }

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
            connectTimeout = 3_000
            readTimeout = 3_500
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MoghimiVPN-Android/1.0.16")
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

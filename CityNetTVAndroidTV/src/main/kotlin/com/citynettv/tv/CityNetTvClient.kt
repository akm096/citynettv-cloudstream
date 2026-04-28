package com.citynettv.tv

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CityNetTvClient(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("citynettv_tv", Context.MODE_PRIVATE)
    private val mapper = jacksonObjectMapper()
    private val http = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
    private var lastDiagnostics: List<TvDiagnostic> = emptyList()

    var lastError: String? = null
        private set

    private data class DeviceProfile(
        val key: String,
        val deviceClass: String,
        val deviceType: String,
        val deviceOs: String,
        val userAgent: String
    )

    companion object {
        private const val API_BASE = "https://tvapi.citynettv.az:11610/api/client"
        private const val ACCESS_KEY = "WkVjNWNscFhORDBLCg=="
        private const val PREF_USER = "user"
        private const val PREF_PASS = "pass"
        private const val PREF_ACCESS = "access"
        private const val PREF_REFRESH = "refresh"
        private const val PREF_UID = "uid"
        private const val PREF_PID = "pid"
        private const val PREF_DEVICE = "device"
        private const val PREF_PROFILE = "profile"

        private val MOBILE = DeviceProfile(
            "mobile",
            "MOBILE",
            "ANDROID",
            "ANDROID",
            "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
        )
        private val DESKTOP = DeviceProfile(
            "desktop",
            "DESKTOP",
            "WEB",
            "WEB",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
    }

    fun savedUser(): String = prefs.getString(PREF_USER, "").orEmpty()
    fun savedPass(): String = prefs.getString(PREF_PASS, "").orEmpty()
    fun isLoggedIn(): Boolean = !prefs.getString(PREF_ACCESS, null).isNullOrBlank()
    fun diagnostics(): List<TvDiagnostic> = lastDiagnostics
    fun webUserAgent(): String = profile().userAgent
    fun webUrl(): String = "https://tv.citynettv.az/"

    fun logout() {
        prefs.edit()
            .remove(PREF_ACCESS)
            .remove(PREF_REFRESH)
            .remove(PREF_UID)
            .remove(PREF_PID)
            .apply()
    }

    fun login(user: String = savedUser(), pass: String = savedPass()): Boolean {
        lastError = null
        if (user.isBlank() || pass.isBlank()) {
            lastError = "Login ve sifre daxil edin."
            return false
        }
        prefs.edit().putString(PREF_USER, user).putString(PREF_PASS, pass).apply()
        val profiles = (listOf(profile()) + listOf(MOBILE, DESKTOP)).distinctBy { it.key }
        for (profile in profiles) {
            if (loginWithProfile(user, pass, profile)) return true
        }
        return false
    }

    fun channels(): List<TvChannel> {
        if (!isLoggedIn() && !login()) return emptyList()
        val uid = prefs.getString(PREF_UID, null) ?: return emptyList()
        val pid = prefs.getString(PREF_PID, null) ?: "0"
        val profileIds = listOf(pid, "0", uid).filter { it.isNotBlank() }.distinct()
        val directUrls = listOf(
            "$API_BASE/v1/citynet/users/$uid/channels",
            "$API_BASE/v2/citynet/users/$uid/channels",
            "$API_BASE/v1/users/$uid/channels",
            "$API_BASE/v2/users/$uid/channels",
            "$API_BASE/v2/global/channels"
        )
        val profileUrls = profileIds.flatMap {
            listOf(
                "$API_BASE/v1/citynet/users/$uid/profiles/$it/channels",
                "$API_BASE/v2/citynet/users/$uid/profiles/$it/channels",
                "$API_BASE/v1/users/$uid/profiles/$it/channels",
                "$API_BASE/v2/users/$uid/profiles/$it/channels"
            )
        }
        val urls = (directUrls + profileUrls).distinct()
        for (url in urls) {
            val res = authRequest("GET", url)
            if (res.first in 200..299) {
                val parsed = mapper.readValue(res.second, TvChannelsResponse::class.java)
                val list = parsed.data ?: parsed.channels
                if (!list.isNullOrEmpty()) return list.sortedBy { it.number ?: 9999 }
            }
        }
        lastError = "Kanal siyahisi alinmadi."
        return emptyList()
    }

    fun playback(channel: TvChannel): PlaybackItem? {
        val keyCandidates = listOf(channel.uid, channel.slug, channel.id).filterNotNull().filter { it.isNotBlank() }.distinct()
        val cached = channel.streamUrl ?: channel.manifestUrl ?: channel.url
        if (!cached.isNullOrBlank() && cached.looksLikeStream()) {
            return PlaybackItem(channel.titleText(), cached, playbackHeaders())
        }
        if (keyCandidates.isEmpty()) return null
        if (!isLoggedIn() && !login()) return null
        val uid = prefs.getString(PREF_UID, null) ?: return null
        val pid = prefs.getString(PREF_PID, null) ?: "0"
        val profileIds = listOf(pid, "0", uid).filter { it.isNotBlank() }.distinct()
        val attempts = mutableListOf<TvDiagnostic>()
        for (key in keyCandidates) {
            val baseUrls = mutableListOf(
                "$API_BASE/v1/citynet/users/$uid/live/channels/$key?translation=az&format=hls",
                "$API_BASE/v1/citynet/users/$uid/live/channels/$key?format=hls",
                "$API_BASE/v2/citynet/users/$uid/live/channels/$key?format=hls",
                "$API_BASE/v1/citynet/users/$uid/channels/$key/playback",
                "$API_BASE/v2/citynet/users/$uid/channels/$key/playback",
                "$API_BASE/v1/users/$uid/channels/$key/playback",
                "$API_BASE/v2/users/$uid/channels/$key/playback",
                "$API_BASE/v1/citynet/users/$uid/vod/channels/$key/playback",
                "$API_BASE/v2/citynet/users/$uid/vod/channels/$key/playback"
            )
            profileIds.forEach { profileId ->
                baseUrls += listOf(
                    "$API_BASE/v1/citynet/users/$uid/profiles/$profileId/channels/$key/playback",
                    "$API_BASE/v2/citynet/users/$uid/profiles/$profileId/channels/$key/playback",
                    "$API_BASE/v1/users/$uid/profiles/$profileId/channels/$key/playback",
                    "$API_BASE/v2/users/$uid/profiles/$profileId/channels/$key/playback"
                )
            }
            val urls = (baseUrls + baseUrls.map { it.withDashWidevineFormat() }).distinct()
            for (url in urls) {
                val get = authRequest("GET", url)
                attempts.add(TvDiagnostic("GET", url.removePrefix(API_BASE), get.first, profile().key))
                parseStream(get.second)?.toPlayback(channel.titleText())?.let {
                    lastDiagnostics = attempts.takeLast(16)
                    return it
                }
                if (get.first in setOf(400, 403, 405, 422) || get.first in 200..299) {
                    val bodies = listOf(
                        mapOf("device" to deviceId(), "device_id" to deviceId(), "profile_id" to pid, "format" to "hls"),
                        mapOf("device" to deviceId(), "device_id" to deviceId(), "profile_id" to pid, "drm" to false, "format" to "hls"),
                        mapOf("device" to deviceId(), "device_id" to deviceId(), "profile_id" to pid, "drm" to "widevine", "format" to "dash")
                    )
                    for (body in bodies) {
                        val post = authRequest("POST", url, mapper.writeValueAsString(body))
                        attempts.add(TvDiagnostic("POST", url.removePrefix(API_BASE), post.first, profile().key))
                        parseStream(post.second)?.toPlayback(channel.titleText())?.let {
                            lastDiagnostics = attempts.takeLast(16)
                            return it
                        }
                    }
                }
            }
        }
        lastDiagnostics = attempts.takeLast(16)
        lastError = lastDiagnostics.joinToString("\n")
        return null
    }

    fun manualPlayback(url: String): PlaybackItem? =
        url.trim().takeIf { it.looksLikeStream() }?.let { PlaybackItem("Manual stream", it, playbackHeaders()) }

    private fun loginWithProfile(user: String, pass: String, profile: DeviceProfile): Boolean {
        prefs.edit().putString(PREF_PROFILE, profile.key).apply()
        val body = mapper.writeValueAsString(
            TvLoginRequest(username = user, password = pass, device = deviceId(), deviceClass = profile.deviceClass, deviceType = profile.deviceType, deviceOs = profile.deviceOs)
        )
        val res = request("POST", "$API_BASE/v2/global/login", body, auth = false, accessKey = false)
        if (res.first !in 200..299) {
            lastError = "Login xetasi: ${res.first}"
            return false
        }
        val parsed = mapper.readValue(res.second, TvLoginResponse::class.java)
        val token = parsed.accessToken() ?: return false.also { lastError = parsed.error ?: "Token alinmadi." }
        prefs.edit()
            .putString(PREF_ACCESS, token)
            .putString(PREF_REFRESH, parsed.refreshToken())
            .putString(PREF_UID, parsed.user()?.uid ?: parsed.user()?.id ?: jwtText(token, "uid") ?: jwtText(token, "sub"))
            .putString(PREF_PID, parsed.user()?.profiles?.firstOrNull()?.id ?: parsed.user()?.profileId ?: jwtText(token, "profile_id"))
            .apply()
        return true
    }

    private fun request(method: String, url: String, json: String? = null, auth: Boolean = true, accessKey: Boolean = true): Pair<Int, String> {
        val builder = Request.Builder().url(url)
        headers(auth, accessKey).forEach { (k, v) -> builder.header(k, v) }
        if (method == "POST") {
            builder.post((json ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()))
        } else {
            builder.get()
        }
        http.newCall(builder.build()).execute().use { response ->
            if (response.code == 401 && auth) {
                prefs.edit().remove(PREF_ACCESS).apply()
            }
            return response.code to response.body?.string().orEmpty()
        }
    }

    private fun authRequest(method: String, url: String, json: String? = null, retried: Boolean = false): Pair<Int, String> {
        val res = request(method, url, json)
        if (res.first == 401 && !retried && refreshToken()) {
            return authRequest(method, url, json, retried = true)
        }
        return res
    }

    private fun refreshToken(): Boolean {
        val refresh = prefs.getString(PREF_REFRESH, null) ?: return login()
        val body = mapper.writeValueAsString(mapOf("refresh_token" to refresh))
        val res = request("POST", "$API_BASE/v2/global/refresh", body, auth = false, accessKey = false)
        if (res.first !in 200..299) return login()
        val parsed = runCatching { mapper.readValue(res.second, TvLoginResponse::class.java) }.getOrNull() ?: return login()
        val token = parsed.accessToken() ?: return login()
        prefs.edit()
            .putString(PREF_ACCESS, token)
            .putString(PREF_REFRESH, parsed.refreshToken() ?: refresh)
            .apply()
        return true
    }

    private fun parseStream(text: String): TvStream? {
        if (text.isBlank()) return null
        return runCatching {
            val response = mapper.readValue(text, TvStreamResponse::class.java)
            val root = mapper.readTree(text)
            val direct = response.data ?: TvStream(url = response.streamUrl ?: response.url)
            val hls = findText(root, setOf("stream_url", "manifest_url", "manifest", "hls_url", "hls", "m3u8", "file", "src", "uri", "url")) { key, value ->
                value.looksLikeStream() && !key.contains("license", true)
            }
            val dash = findText(root, setOf("dash_url", "dash", "mpd", "manifest_url", "manifest", "stream_url", "url")) { key, value ->
                value.looksLikeDash() && !key.contains("license", true)
            }
            val license = findText(root, setOf("license_url", "widevine_license_url", "licenseUrl", "license")) { _, value -> value.startsWith("http") }
            val streamUrl = when {
                hls.isCencHls() && !dash.isNullOrBlank() -> dash
                direct.streamUrl().isCencHls() && !dash.isNullOrBlank() -> dash
                else -> hls ?: direct.streamUrl() ?: dash
            } ?: return null
            direct.copy(url = streamUrl, drm = direct.drm ?: license?.let { TvDrm(licenseUrl = it) })
        }.getOrNull()
    }

    private fun TvStream.toPlayback(name: String): PlaybackItem? {
        val url = streamUrl() ?: return null
        val license = drm?.licenseUrl() ?: buildLicenseUrl(lat, jwt, server ?: url.apiServer()?.toString())
        return PlaybackItem(name, url, playbackHeaders(), license.takeIf { url.looksLikeDash() || !drm?.licenseUrl().isNullOrBlank() })
    }

    private fun headers(auth: Boolean = true, accessKey: Boolean = true): Map<String, String> {
        val p = profile()
        val h = mutableMapOf("User-Agent" to p.userAgent, "Accept" to "application/json", "Content-Type" to "application/json")
        if (p.key == DESKTOP.key) {
            h["Origin"] = "https://tv.citynettv.az"
            h["Referer"] = "https://tv.citynettv.az/"
        }
        if (accessKey) h["Access-Key"] = ACCESS_KEY
        if (auth) prefs.getString(PREF_ACCESS, null)?.let { h["Authorization"] = "Bearer $it" }
        return h
    }

    private fun playbackHeaders(): Map<String, String> = headers().filterKeys { it != "Content-Type" } + mapOf(
        "Origin" to "https://tv.citynettv.az",
        "Referer" to "https://tv.citynettv.az/"
    )
    private fun profile(): DeviceProfile = if (prefs.getString(PREF_PROFILE, MOBILE.key) == DESKTOP.key) DESKTOP else MOBILE
    private fun deviceId(): String = prefs.getString(PREF_DEVICE, null) ?: UUID.randomUUID().toString().replace("-", "").take(16).also { prefs.edit().putString(PREF_DEVICE, it).apply() }
    private fun String.looksLikeStream(): Boolean = startsWith("http", true) && (contains(".m3u8", true) || contains(".mpd", true) || contains("/hls", true) || contains("/dash", true) || contains("stream", true))
    private fun String.looksLikeDash(): Boolean = contains(".mpd", true) || contains("/dash", true)
    private fun String?.isCencHls(): Boolean = this?.contains("MPEG-CENC", true) == true && contains("/hls", true)
    private fun String.apiServer(): Int? = runCatching { Regex("""api(\d+)\.citynettv\.az""").find(URI(this).host.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull() }.getOrNull()
    private fun buildLicenseUrl(lat: String?, jwt: String?, server: String?): String? {
        if (lat.isNullOrBlank() && jwt.isNullOrBlank()) return null
        val serverNumber = server?.let { Regex("""\d+""").find(it)?.value } ?: "1"
        val params = listOfNotNull(lat?.let { "lat=${it.enc()}" }, jwt?.let { "jwt=${it.enc()}" }).joinToString("&")
        return "https://api$serverNumber.citynettv.az:11610/drmproxy/wv/license?$params"
    }

    private fun String.withDashWidevineFormat(): String {
        val parts = split("?", limit = 2)
        val base = parts[0]
        val params = parts.getOrNull(1)
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.filterNot {
                val key = it.substringBefore("=").lowercase()
                key == "format" || key == "drm"
            }
            ?.toMutableList()
            ?: mutableListOf()
        params += "format=dash"
        params += "drm=widevine"
        return "$base?${params.joinToString("&")}"
    }
    private fun String.enc(): String = URLEncoder.encode(this, "UTF-8")
    private fun jwtText(token: String, key: String): String? = runCatching {
        val payload = String(Base64.decode(token.split(".")[1], Base64.URL_SAFE))
        mapper.readTree(payload).get(key)?.asText()
    }.getOrNull()
    private fun findText(node: JsonNode?, keys: Set<String>, predicate: (String, String) -> Boolean): String? {
        if (node == null) return null
        if (node.isObject) {
            val fields = node.fields()
            while (fields.hasNext()) {
                val (key, value) = fields.next()
                if (keys.any { it.equals(key, true) } && value.isTextual && predicate(key, value.asText())) return value.asText()
            }
            val nested = node.fields()
            while (nested.hasNext()) findText(nested.next().value, keys, predicate)?.let { return it }
        } else if (node.isArray) {
            for (item in node) findText(item, keys, predicate)?.let { return it }
        }
        return null
    }
}

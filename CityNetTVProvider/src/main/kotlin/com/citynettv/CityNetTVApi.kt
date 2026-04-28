package com.citynettv

import android.content.SharedPreferences
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class CityNetTVApi(private val prefs: SharedPreferences?) {

    companion object {
        const val API_BASE = "https://tvapi.citynettv.az:11610/api/client"
        const val ACCESS_KEY = "WkVjNWNscFhORDBLCg=="
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"

        private const val PREF_ACCESS_TOKEN  = "citynettv_access_token"
        private const val PREF_REFRESH_TOKEN = "citynettv_refresh_token"
        private const val PREF_USER_UID      = "citynettv_user_uid"
        private const val PREF_PROFILE_ID    = "citynettv_profile_id"
        private const val PREF_DEVICE_ID     = "citynettv_device_id"
        private const val PREF_USERNAME      = "citynettv_username"
        private const val PREF_PASSWORD      = "citynettv_password"
    }

    private val mapper = jacksonObjectMapper()

    // ── Credentials ──────────────────────────────────────────────────────────

    fun saveCredentials(username: String, password: String) {
        prefs?.edit()?.putString(PREF_USERNAME, username)?.putString(PREF_PASSWORD, password)?.apply()
    }

    fun getUsername(): String? = prefs?.getString(PREF_USERNAME, null)
    fun getPassword(): String? = prefs?.getString(PREF_PASSWORD, null)
    fun hasCredentials() = !getUsername().isNullOrEmpty() && !getPassword().isNullOrEmpty()

    fun getAccessToken(): String? = prefs?.getString(PREF_ACCESS_TOKEN, null)
    private fun getRefreshToken(): String? = prefs?.getString(PREF_REFRESH_TOKEN, null)
    fun getUserUid(): String? = prefs?.getString(PREF_USER_UID, null)
    fun getProfileId(): String? = prefs?.getString(PREF_PROFILE_ID, null)

    fun getDeviceId(): String {
        // Use saved device ID if available
        var id = prefs?.getString(PREF_DEVICE_ID, null)
        if (!id.isNullOrEmpty()) return id

        // Generate a new deterministic device ID using the username
        val user = getUsername()
        id = if (!user.isNullOrEmpty()) {
            // Some systems expect a 16-character hex string for Android IDs
            UUID.nameUUIDFromBytes("CityNetTV-CS-$user".toByteArray()).toString().replace("-", "").substring(0, 16)
        } else {
            UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        }
        prefs?.edit()?.putString(PREF_DEVICE_ID, id)?.apply()
        return id
    }

    /**
     * Cihaz ID-ni yeniləyir.
     * "Cihaz limiti aşılıb" xətası olduqda istifadəçi tərəfindən çağırılır.
     */
    fun resetDeviceId() {
        val newId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        prefs?.edit()?.putString(PREF_DEVICE_ID, newId)?.apply()
        android.util.Log.d("CityNetTV", "Device ID sıfırlandı, yeni ID: $newId")
    }

    private fun saveTokens(access: String?, refresh: String?) {
        prefs?.edit()?.putString(PREF_ACCESS_TOKEN, access)?.putString(PREF_REFRESH_TOKEN, refresh)?.apply()
    }

    private fun saveUserInfo(uid: String?, pid: String?) {
        prefs?.edit()?.putString(PREF_USER_UID, uid)?.putString(PREF_PROFILE_ID, pid)?.apply()
    }

    fun isLoggedIn() = !getAccessToken().isNullOrEmpty()

    fun logout() {
        prefs?.edit()
            ?.remove(PREF_ACCESS_TOKEN)?.remove(PREF_REFRESH_TOKEN)
            ?.remove(PREF_USER_UID)?.remove(PREF_PROFILE_ID)
            // PREF_DEVICE_ID is deliberately NOT removed to keep it persistent across sessions
            ?.apply()
    }

    /**
     * API-dən cihaz sessiyasını silir — device slot-unu azad edir.
     */
    /**
     * API-dən cihaz sessiyasını silir — device slot-unu azad edir.
     */
    private suspend fun logoutDevice(deviceId: String) {
        try {
            // Unregister via v2/global/logout, usually requires the login payload
            android.util.Log.d("CityNetTV", "Köhnə cihaz sessiyası silinir: $deviceId")
            app.post(
                "$API_BASE/v2/global/logout",
                headers = headers(withAuth = true, withAccessKey = false),
                requestBody = "{\"device\":\"$deviceId\"}".toOkHttpBody()
            )
        } catch (e: Exception) {
            android.util.Log.w("CityNetTV", "Logout device failed (non-critical): ${e.message}")
        }
    }

    // ── Headers ───────────────────────────────────────────────────────────────

    private fun headers(withAuth: Boolean = true, withAccessKey: Boolean = true): Map<String, String> {
        val h = mutableMapOf(
            "User-Agent"   to USER_AGENT,
            "Accept"       to "application/json",
            "Content-Type" to "application/json"
        )
        if (withAccessKey) h["Access-Key"] = ACCESS_KEY
        if (withAuth) getAccessToken()?.let { h["Authorization"] = "Bearer $it" }
        return h
    }

    fun playbackHeaders(): Map<String, String> =
        headers().filterKeys { it != "Content-Type" }

    // ── Auth ──────────────────────────────────────────────────────────────────

    var lastLoginError: String? = null
        private set

    suspend fun login(username: String? = null, password: String? = null): Boolean {
        val user = username ?: getUsername() ?: return false
        val pass = password ?: getPassword() ?: return false
        lastLoginError = null
        return try {
            // Save credentials early so getDeviceId() can generate correct UUID
            saveCredentials(user, pass)

            val currentDeviceId = getDeviceId()
            // Try to logout old device session first (frees up device slot)
            logoutDevice(currentDeviceId)

            val body = mapper.writeValueAsString(
                LoginRequest(
                    username = user,
                    password = pass,
                    device = currentDeviceId,
                    // Sending a static deviceClass/Type/Os to ensure consistency
                    deviceClass = "MOBILE",
                    deviceType = "ANDROID",
                    deviceOs = "ANDROID"
                )
            )
            android.util.Log.d("CityNetTV", "Login request to: $API_BASE/v2/global/login")
            android.util.Log.d("CityNetTV", "Login body: $body")
            val res = app.post(
                "$API_BASE/v2/global/login",
                headers = headers(withAuth = false, withAccessKey = false),
                requestBody = body.toOkHttpBody()
            )
            android.util.Log.d("CityNetTV", "Login response code: ${res.code}")
            android.util.Log.d("CityNetTV", "Login response body: ${res.text}")
            if (res.isSuccessful) {
                val lr = mapper.readValue(res.text, LoginResponse::class.java)
                val token = lr.resolveAccessToken()
                if (!token.isNullOrEmpty()) {
                    saveTokens(token, lr.resolveRefreshToken())
                    val u = lr.resolveUser()
                    var resolvedUid = u?.uid ?: u?.id
                    var resolvedPid = u?.profiles?.firstOrNull()?.id ?: u?.profileId

                    // Decode JWT token to extract missing user info if needed
                    if (resolvedUid == null || resolvedPid == null) {
                        try {
                            val parts = token.split(".")
                            if (parts.size >= 2) {
                                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                                val jwtNode = mapper.readTree(payload)
                                if (resolvedUid == null) {
                                    resolvedUid = jwtNode.get("uid")?.asText() ?: jwtNode.get("sub")?.asText() ?: jwtNode.get("user_id")?.asText() ?: jwtNode.get("id")?.asText()
                                }
                                if (resolvedPid == null) {
                                    resolvedPid = jwtNode.get("profile_id")?.asText() ?: jwtNode.get("pid")?.asText()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CityNetTV", "JWT Decode error: ${e.message}")
                        }
                    }

                    saveUserInfo(resolvedUid, resolvedPid)
                    lastLoginError = null
                    return true
                }
                lastLoginError = lr.error ?: "Token alınmadı: Məlumatlar səhvdir."
            } else {
                // Parse error response
                try {
                    val errBody = res.text
                    val errNode = mapper.readTree(errBody)
                    val errCode = errNode?.get("error_code")?.asInt() ?: errNode?.get("code")?.asInt()

                    // The error message can sometimes be nested inside "data" -> "message"
                    val dataNode = errNode?.get("data")
                    val errMsg = dataNode?.get("message")?.asText()
                        ?: errNode?.get("message")?.asText()
                        ?: errNode?.get("error")?.asText()
                        ?: errNode?.get("error_message")?.asText()

                    if (errCode == 1067) {
                        lastLoginError = "Cihaz limiti aşılıb. Rəsmi CityNet TV proqramından köhnə cihazları silin və ya Ayarlardan Cihazı Sıfırlayın."
                        android.util.Log.w("CityNetTV", "Device limit aşılıb")
                        return false
                    }
                    if (errCode == 4290) {
                        lastLoginError = "Çox sayda cəhd etdiniz! Bir neçə dəqiqə gözləyin və yenidən yoxlayın."
                        return false
                    }
                    if (errCode == 3010) {
                        lastLoginError = "İstifadəçi adı və ya şifrə səhvdir."
                        return false
                    }

                    lastLoginError = errMsg ?: "Server xətası: ${res.code}"
                    android.util.Log.e("CityNetTV", "Login error $errCode: $lastLoginError")
                } catch (pe: Exception) {
                    lastLoginError = "Server xətası: ${res.code}"
                    android.util.Log.e("CityNetTV", "Login failed: ${res.code} - ${res.text}")
                }
            }
            false
        } catch (e: Exception) {
            lastLoginError = "Şəbəkə xətası: ${e.message}"
            e.printStackTrace()
            false
        }
    }

    suspend fun refreshToken(): Boolean {
        val rt = getRefreshToken() ?: return login()
        return try {
            val body = mapper.writeValueAsString(RefreshRequest(rt))
            val res = app.post(
                "$API_BASE/v2/global/refresh",
                headers = headers(withAuth = false, withAccessKey = false),
                requestBody = body.toOkHttpBody()
            )
            if (res.isSuccessful) {
                val rr = mapper.readValue(res.text, RefreshResponse::class.java)
                val token = rr.resolveAccessToken()
                if (!token.isNullOrEmpty()) {
                    saveTokens(token, rr.resolveRefreshToken() ?: rt)
                    return true
                }
            }
            login()
        } catch (e: Exception) { e.printStackTrace(); login() }
    }

    private suspend fun authGet(url: String, retried: Boolean = false): com.lagradost.nicehttp.NiceResponse {
        val res = app.get(url, headers = headers())
        if (res.code == 401 && !retried) {
            refreshToken()
            return authGet(url, true)
        }
        return res
    }

    private suspend fun authPost(
        url: String,
        body: String,
        retried: Boolean = false
    ): com.lagradost.nicehttp.NiceResponse {
        val res = app.post(url, headers = headers(), requestBody = body.toOkHttpBody())
        if (res.code == 401 && !retried) {
            refreshToken()
            return authPost(url, body, true)
        }
        return res
    }

    // ── Channels ──────────────────────────────────────────────────────────────

    var lastChannelsError: String? = null
        private set

    suspend fun getChannels(): List<ChannelData> {
        lastChannelsError = null
        if (!isLoggedIn()) {
            if (!login()) {
                lastChannelsError = "Login failed: ${lastLoginError ?: "Bilinməyən xəta"}"
                return emptyList()
            }
        }
        val uid = getUserUid()
        var pid = getProfileId()

        return try {
            if (uid != null) {
                // 1) user-specific list
                // UniqCast (the underlying IPTV platform) sometimes doesn't use explicit profiles.
                // Defaulting to "0" or the "uid" itself often works when pid is missing.
                val possiblePids = listOfNotNull(pid, "0", uid).distinct()
                var anyV1AttemptFailed = false

                val endpointsWithPid = listOf(
                    "$API_BASE/v1/citynet/users/$uid/profiles/{pid}/channels",
                    "$API_BASE/v2/citynet/users/$uid/profiles/{pid}/channels",
                    "$API_BASE/v2/users/$uid/profiles/{pid}/channels",
                    "$API_BASE/v1/users/$uid/profiles/{pid}/channels"
                )

                val endpointsWithoutPid = listOf(
                    "$API_BASE/v1/citynet/users/$uid/channels",
                    "$API_BASE/v2/citynet/users/$uid/channels",
                    "$API_BASE/v1/users/$uid/channels",
                    "$API_BASE/v2/global/channels"
                )

                // Try endpoints that don't need a profile ID first, to save requests
                for (url in endpointsWithoutPid) {
                    try {
                        val r1 = authGet(url)
                        if (r1.isSuccessful) {
                            val ch = mapper.readValue(r1.text, ChannelsResponse::class.java)
                            val list = ch.data ?: ch.channels
                            if (!list.isNullOrEmpty()) {
                                return list
                            }
                        } else {
                            anyV1AttemptFailed = true
                            val variant = url.split("/").takeLast(2).joinToString("/")
                            lastChannelsError = (lastChannelsError ?: "") + "[$variant: ${r1.code}] "
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                for (testPid in possiblePids) {
                    for (endpointPattern in endpointsWithPid) {
                        val url = endpointPattern.replace("{pid}", testPid)
                        try {
                            val r1 = authGet(url)
                            if (r1.isSuccessful) {
                                val ch = mapper.readValue(r1.text, ChannelsResponse::class.java)
                                val list = ch.data ?: ch.channels
                                if (!list.isNullOrEmpty()) {
                                    // If a fallback PID worked, save it to avoid looping next time
                                    if (testPid != pid) {
                                        prefs?.edit()?.putString(PREF_PROFILE_ID, testPid)?.apply()
                                    }
                                    return list
                                }
                            } else {
                                anyV1AttemptFailed = true
                                // Record the first segment of the URL path and status code
                                val variant = url.split("/").take(6).lastOrNull() ?: "url"
                                lastChannelsError = (lastChannelsError ?: "") + "[$variant-PID_$testPid: ${r1.code}] "
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (anyV1AttemptFailed) {
                    lastChannelsError = "v1 API uğursuz oldu: $lastChannelsError"
                } else {
                    lastChannelsError = "v1 API boş siyahı qaytardı."
                }
            } else {
                lastChannelsError = "UID ($uid) tapılmadı."
                android.util.Log.w("CityNetTV", "uid is null, falling back to public list")
            }

            // 2) public list fallback (test both v1 and v2, with and without query params)
            val fallbacks = listOf(
                "$API_BASE/v2/citynet/channels?translation=az",
                "$API_BASE/v2/citynet/channels",
                "$API_BASE/v1/citynet/channels"
            )
            for (fallback in fallbacks) {
                val r2 = authGet(fallback)
                if (r2.isSuccessful) {
                    val ch = mapper.readValue(r2.text, ChannelsResponse::class.java)
                    val list = ch.data ?: ch.channels
                    if (!list.isNullOrEmpty()) return list
                } else {
                    val variant = fallback.split("/").takeLast(2).joinToString("/")
                    lastChannelsError = (lastChannelsError ?: "") + " | fallback [$variant xətası: ${r2.code}]"
                }
            }

            return emptyList()
        } catch (e: Exception) {
            lastChannelsError = "Şəbəkə xətası: ${e.message}"
            e.printStackTrace()
            emptyList()
        }
    }

    // ── Stream ────────────────────────────────────────────────────────────────

    var lastStreamError: String? = null
        private set

    suspend fun getStreamData(slug: String, id: String? = null, channelUid: String? = null, preferredShowId: String? = null): StreamData? {
        lastStreamError = null
        if (!isLoggedIn()) { if (!login()) return null }
        val uid = getUserUid() ?: return null
        return try {
            val showId = preferredShowId ?: getCurrentShowId(slug)
            val pid = getProfileId() ?: "0"

            val possibleEndpoints = mutableListOf<String>()
            val channelKeys = listOfNotNull(channelUid, slug, id).filter { it.isNotBlank() }.distinct()
            if (channelKeys.isEmpty()) {
                lastStreamError = "Kanal slug/id boÅŸdur"
                return null
            }

            for (channelKey in channelKeys) {
                possibleEndpoints.addAll(listOf(
                    "$API_BASE/v1/citynet/users/$uid/live/channels/$channelKey?translation=az&format=hls",
                    "$API_BASE/v1/citynet/users/$uid/live/channels/$channelKey?format=hls",
                    "$API_BASE/v2/citynet/users/$uid/live/channels/$channelKey?format=hls",
                    "$API_BASE/v1/citynet/users/$uid/live/channels/$channelKey?translation=az",
                    "$API_BASE/v1/citynet/users/$uid/live/channels/$channelKey",
                    "$API_BASE/v2/citynet/users/$uid/live/channels/$channelKey",
                    "$API_BASE/v1/citynet/users/$uid/channels/$channelKey/stream",
                    "$API_BASE/v2/citynet/users/$uid/channels/$channelKey/stream",
                    "$API_BASE/v1/citynet/users/$uid/channels/$channelKey/play",
                    "$API_BASE/v2/citynet/users/$uid/channels/$channelKey/play",
                    "$API_BASE/v1/citynet/users/$uid/channels/$channelKey/playback",
                    "$API_BASE/v2/citynet/users/$uid/channels/$channelKey/playback",
                    "$API_BASE/v1/citynet/users/$uid/channels/$channelKey/watch",
                    "$API_BASE/v2/citynet/users/$uid/channels/$channelKey/watch",
                    "$API_BASE/v1/citynet/users/$uid/vod/channels/$channelKey/live",
                    "$API_BASE/v2/citynet/users/$uid/vod/channels/$channelKey/live",
                    "$API_BASE/v1/citynet/users/$uid/profiles/$pid/vod/channels/$channelKey/live",
                    "$API_BASE/v2/citynet/users/$uid/profiles/$pid/vod/channels/$channelKey/live",
                    "$API_BASE/v1/users/$uid/vod/channels/$channelKey/live",
                    "$API_BASE/v2/users/$uid/vod/channels/$channelKey/live",
                    "$API_BASE/v1/users/$uid/profiles/$pid/vod/channels/$channelKey/live",
                    "$API_BASE/v2/users/$uid/profiles/$pid/vod/channels/$channelKey/live",
                    // Endpoints without "vod/"
                    "$API_BASE/v1/citynet/users/$uid/channels/$channelKey/live",
                    "$API_BASE/v2/citynet/users/$uid/channels/$channelKey/live",
                    "$API_BASE/v1/citynet/users/$uid/profiles/$pid/channels/$channelKey/live",
                    "$API_BASE/v2/citynet/users/$uid/profiles/$pid/channels/$channelKey/live",
                    "$API_BASE/v1/citynet/users/$uid/profiles/$pid/channels/$channelKey/stream",
                    "$API_BASE/v2/citynet/users/$uid/profiles/$pid/channels/$channelKey/stream",
                    "$API_BASE/v1/citynet/users/$uid/profiles/$pid/channels/$channelKey/playback",
                    "$API_BASE/v2/citynet/users/$uid/profiles/$pid/channels/$channelKey/playback",
                    "$API_BASE/v1/users/$uid/channels/$channelKey/live",
                    "$API_BASE/v2/users/$uid/channels/$channelKey/live",
                    "$API_BASE/v1/users/$uid/profiles/$pid/channels/$channelKey/live",
                    "$API_BASE/v2/users/$uid/profiles/$pid/channels/$channelKey/live"
                ))

                if (!showId.isNullOrEmpty()) {
                    possibleEndpoints.addAll(listOf(
                        "$API_BASE/v1/citynet/users/$uid/vod/channels/$channelKey/shows/$showId?translation=az&format=hls",
                        "$API_BASE/v1/citynet/users/$uid/vod/channels/$channelKey/shows/$showId",
                        "$API_BASE/v2/citynet/users/$uid/vod/channels/$channelKey/shows/$showId",
                        "$API_BASE/v1/citynet/users/$uid/channels/$channelKey/shows/$showId/stream",
                        "$API_BASE/v2/citynet/users/$uid/channels/$channelKey/shows/$showId/stream",
                        "$API_BASE/v1/citynet/users/$uid/channels/$channelKey/shows/$showId/playback",
                        "$API_BASE/v2/citynet/users/$uid/channels/$channelKey/shows/$showId/playback",
                        "$API_BASE/v1/citynet/users/$uid/profiles/$pid/vod/channels/$channelKey/shows/$showId",
                        "$API_BASE/v2/citynet/users/$uid/profiles/$pid/vod/channels/$channelKey/shows/$showId",
                        "$API_BASE/v1/users/$uid/vod/channels/$channelKey/shows/$showId",
                        "$API_BASE/v2/users/$uid/vod/channels/$channelKey/shows/$showId",
                        "$API_BASE/v1/users/$uid/profiles/$pid/vod/channels/$channelKey/shows/$showId",
                        "$API_BASE/v2/users/$uid/profiles/$pid/vod/channels/$channelKey/shows/$showId",
                        // Endpoints without "vod/"
                        "$API_BASE/v1/citynet/users/$uid/channels/$channelKey/shows/$showId",
                        "$API_BASE/v2/citynet/users/$uid/channels/$channelKey/shows/$showId",
                        "$API_BASE/v1/citynet/users/$uid/profiles/$pid/channels/$channelKey/shows/$showId",
                        "$API_BASE/v2/citynet/users/$uid/profiles/$pid/channels/$channelKey/shows/$showId",
                        "$API_BASE/v1/users/$uid/channels/$channelKey/shows/$showId",
                        "$API_BASE/v2/users/$uid/channels/$channelKey/shows/$showId"
                    ))
                }
            }

            val playbackBodies = listOf(
                mapOf(
                    "device" to getDeviceId(),
                    "device_id" to getDeviceId(),
                    "profile_id" to pid,
                    "format" to "hls"
                ),
                mapOf(
                    "device" to getDeviceId(),
                    "device_id" to getDeviceId(),
                    "profile_id" to pid,
                    "drm" to false,
                    "format" to "hls"
                ),
                mapOf(
                    "device" to getDeviceId(),
                    "device_id" to getDeviceId(),
                    "profile_id" to pid,
                    "drm" to "widevine",
                    "format" to "dash"
                )
            ).map { mapper.writeValueAsString(it) }
            val attempts = mutableListOf<String>()
            var drmDashFallback: StreamData? = null

            for (url in possibleEndpoints.distinct()) {
                val getRes = authGet(url)
                val getData = parseStreamResponse("GET", url, getRes, attempts)
                if (getData?.resolveStreamUrl().isNullOrEmpty().not()) {
                    if (getData.needsDrmFallback()) {
                        drmDashFallback = drmDashFallback ?: getData
                    } else {
                        return getData
                    }
                }

                if ((getRes.isSuccessful && (getData == null || getData.needsDrmFallback())) || getRes.code in setOf(400, 403, 405, 422)) {
                    for (playbackBody in playbackBodies) {
                        val postRes = authPost(url, playbackBody)
                        val postData = parseStreamResponse("POST", url, postRes, attempts)
                        if (postData?.resolveStreamUrl().isNullOrEmpty().not()) {
                            if (postData.needsDrmFallback()) {
                                drmDashFallback = drmDashFallback ?: postData
                            } else {
                                return postData
                            }
                        }
                        if (postRes.code !in setOf(400, 403, 405, 422) && postData?.needsDrmFallback() != true) break
                    }
                }
            }

            lastStreamError = attempts.takeLast(8).joinToString(" | ").ifBlank { "Stream endpoint tapÄ±lmadÄ±" }
            if (drmDashFallback != null) return drmDashFallback

            android.util.Log.e("CityNetTV", "All stream endpoints failed: $lastStreamError")
            null
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private fun parseStreamResponse(
        method: String,
        url: String,
        res: com.lagradost.nicehttp.NiceResponse,
        attempts: MutableList<String>
    ): StreamData? {
        val endpoint = url.removePrefix(API_BASE)
        return if (res.isSuccessful) {
            val streamData = parseStreamData(res.text)
            if (streamData?.resolveStreamUrl().isNullOrEmpty()) {
                attempts.add("$method $endpoint: boÅŸ")
                null
            } else {
                streamData
            }
        } else {
            attempts.add("$method $endpoint: ${res.code}")
            null
        }
    }

    private fun parseStreamData(text: String): StreamData? {
        return try {
            val sr = mapper.readValue(text, StreamResponse::class.java)
            val typed = sr.data ?: StreamData(url = sr.streamUrl ?: sr.url)

            val root = mapper.readTree(text)
            val hlsUrl = findTextValue(
                root,
                setOf("stream_url", "manifest_url", "manifest", "hls_url", "hls", "m3u8", "file", "src", "uri", "url")
            ) { key, value ->
                value.startsWith("http", ignoreCase = true) &&
                    value.looksLikeHlsStream() &&
                    !key.contains("license", ignoreCase = true) &&
                    !key.contains("logo", ignoreCase = true) &&
                    !key.contains("image", ignoreCase = true)
            }
            val dashUrl = findTextValue(
                root,
                setOf("dash_url", "dash", "mpd", "manifest_url", "manifest", "stream_url", "url")
            ) { key, value ->
                value.startsWith("http", ignoreCase = true) &&
                    value.looksLikeDashStream() &&
                    !key.contains("license", ignoreCase = true) &&
                    !key.contains("logo", ignoreCase = true) &&
                    !key.contains("image", ignoreCase = true)
            }
            val typedUrl = typed.resolveStreamUrl()
            val streamUrl = when {
                hlsUrl.isCencHlsStream() && !dashUrl.isNullOrEmpty() -> dashUrl
                typedUrl.isCencHlsStream() && !dashUrl.isNullOrEmpty() -> dashUrl
                else -> hlsUrl ?: typedUrl ?: findTextValue(
                    root,
                    setOf("stream_url", "manifest_url", "manifest", "hls_url", "hls", "dash_url", "dash", "mpd", "m3u8", "file", "src", "uri")
                ) { key, value ->
                    value.startsWith("http", ignoreCase = true) &&
                        !key.contains("license", ignoreCase = true) &&
                        !key.contains("logo", ignoreCase = true) &&
                        !key.contains("image", ignoreCase = true)
                } ?: findTextValue(root, setOf("url")) { key, value ->
                    value.startsWith("http", ignoreCase = true) &&
                        !key.contains("license", ignoreCase = true) &&
                        !key.contains("logo", ignoreCase = true) &&
                        !key.contains("image", ignoreCase = true)
                }
            }
            if (streamUrl.isNullOrEmpty()) return null

            val licenseUrl = findTextValue(
                root,
                setOf("license_url", "widevine_license_url", "licenseUrl", "license")
            ) { _, value -> value.startsWith("http", ignoreCase = true) }
            val lat = findTextValue(root, setOf("lat"))
            val jwt = findTextValue(root, setOf("jwt", "token"))
            val server = findTextValue(root, setOf("server"))
            val drm = typed.drm?.takeIf { !it.resolveLicenseUrl().isNullOrEmpty() }
                ?: if (licenseUrl.isNullOrEmpty()) null else DrmInfo(licenseUrl = licenseUrl)

            typed.copy(
                url = streamUrl,
                lat = typed.lat ?: lat,
                jwt = typed.jwt ?: jwt,
                server = typed.server ?: server,
                drm = drm
            )
        } catch (e: Exception) {
            android.util.Log.w("CityNetTV", "Stream parse failed: ${e.message}")
            null
        }
    }

    private fun findTextValue(
        node: JsonNode?,
        keys: Set<String>,
        predicate: (key: String, value: String) -> Boolean = { _, value -> value.isNotBlank() }
    ): String? {
        if (node == null) return null
        if (node.isObject) {
            val fields = node.fields()
            while (fields.hasNext()) {
                val (key, valueNode) = fields.next()
                if (keys.any { it.equals(key, ignoreCase = true) } && valueNode.isTextual) {
                    val value = valueNode.asText()
                    if (predicate(key, value)) return value
                }
            }

            val nested = node.fields()
            while (nested.hasNext()) {
                val found = findTextValue(nested.next().value, keys, predicate)
                if (!found.isNullOrEmpty()) return found
            }
        } else if (node.isArray) {
            for (item in node) {
                val found = findTextValue(item, keys, predicate)
                if (!found.isNullOrEmpty()) return found
            }
        }
        return null
    }

    fun buildLicenseUrl(lat: String?, jwt: String?, server: Int = 1): String {
        val params = buildList {
            if (!lat.isNullOrEmpty()) add("lat=${lat.urlEncode()}")
            if (!jwt.isNullOrEmpty()) add("jwt=${jwt.urlEncode()}")
        }
        val qs = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return "https://api$server.citynettv.az:11610/drmproxy/wv/license$qs"
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    private fun StreamData.needsDrmFallback(): Boolean {
        val streamUrl = resolveStreamUrl() ?: return false
        val isHls = streamUrl.looksLikeHlsStream()
        val isCencHls = isHls && streamUrl.contains("MPEG-CENC", ignoreCase = true)
        return streamUrl.contains(".mpd", ignoreCase = true) ||
            streamUrl.contains("/dash", ignoreCase = true) ||
            isCencHls ||
            (!isHls && (
                !drm?.resolveLicenseUrl().isNullOrEmpty() ||
                    !lat.isNullOrEmpty() ||
                    !jwt.isNullOrEmpty()
                ))
    }

    private fun String.looksLikeHlsStream(): Boolean {
        return contains(".m3u8", ignoreCase = true) ||
            contains("/hls", ignoreCase = true) ||
            contains("playlist", ignoreCase = true)
    }

    private fun String.looksLikeDashStream(): Boolean {
        return contains(".mpd", ignoreCase = true) ||
            contains("/dash", ignoreCase = true)
    }

    private fun String?.isCencHlsStream(): Boolean {
        return this?.looksLikeHlsStream() == true && contains("MPEG-CENC", ignoreCase = true)
    }

    // ── EPG ───────────────────────────────────────────────────────────────────

    suspend fun getCurrentShowId(slug: String): String? {
        return try {
            val now = System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Baku") }
            val start = sdf.format(java.util.Date(now - 3_600_000))
            val end   = sdf.format(java.util.Date(now + 3_600_000))
            val res = authGet("$API_BASE/v2/citynet/shows/grid?start_date=$start&end_date=$end&channels=$slug")
            if (res.isSuccessful) {
                val er = mapper.readValue(res.text, EpgResponse::class.java)
                val shows = er.data ?: er.shows
                shows?.firstOrNull()?.showId ?: shows?.firstOrNull()?.id
            } else null
        } catch (e: Exception) { null }
    }

    suspend fun getEpg(slug: String): List<EpgItem> {
        return try {
            val now = System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Baku") }
            val start = sdf.format(java.util.Date(now - 3_600_000 * 3))
            val end   = sdf.format(java.util.Date(now + 3_600_000 * 12))
            val res = authGet("$API_BASE/v2/citynet/shows/grid?start_date=$start&end_date=$end&channels=$slug")
            if (res.isSuccessful) {
                val er = mapper.readValue(res.text, EpgResponse::class.java)
                er.data ?: er.shows ?: emptyList()
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Converts a String to a NiceHttp-compatible RequestBody */
    private fun String.toOkHttpBody() =
        this.toRequestBody("application/json; charset=utf-8".toMediaType())
}

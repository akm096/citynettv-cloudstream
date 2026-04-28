package com.citynettv

import android.content.SharedPreferences
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import java.net.URI
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
        private const val PREF_DEVICE_PROFILE = "citynettv_device_profile"
        private const val PREF_USERNAME      = "citynettv_username"
        private const val PREF_PASSWORD      = "citynettv_password"

        private val MOBILE_PROFILE = DeviceProfile(
            key = "mobile",
            deviceClass = "MOBILE",
            deviceType = "ANDROID",
            deviceOs = "ANDROID",
            userAgent = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
        )
        private val DESKTOP_PROFILE = DeviceProfile(
            key = "desktop",
            deviceClass = "DESKTOP",
            deviceType = "WEB",
            deviceOs = "WEB",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        private val DEVICE_PROFILES = listOf(MOBILE_PROFILE, DESKTOP_PROFILE)
    }

    private val mapper = jacksonObjectMapper()

    data class DeviceProfile(
        val key: String,
        val deviceClass: String,
        val deviceType: String,
        val deviceOs: String,
        val userAgent: String
    )

    data class StreamDiagnostic(
        val method: String,
        val endpoint: String,
        val code: Int,
        val profile: String,
        val streamType: String? = null,
        val server: String? = null,
        val note: String? = null
    ) {
        override fun toString(): String {
            val type = streamType?.let { " $it" }.orEmpty()
            val srv = server?.let { " api$it" }.orEmpty()
            val suffix = note?.let { " - $it" }.orEmpty()
            return "$method $endpoint: $code [$profile$type$srv]$suffix"
        }
    }

    // â”€â”€ Credentials â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
    fun getDeviceProfileKey(): String = prefs?.getString(PREF_DEVICE_PROFILE, MOBILE_PROFILE.key) ?: MOBILE_PROFILE.key

    private fun getDeviceProfile(): DeviceProfile =
        DEVICE_PROFILES.firstOrNull { it.key == getDeviceProfileKey() } ?: MOBILE_PROFILE

    private fun saveDeviceProfile(profile: DeviceProfile) {
        prefs?.edit()?.putString(PREF_DEVICE_PROFILE, profile.key)?.apply()
    }

    private fun loginProfiles(): List<DeviceProfile> {
        val preferred = getDeviceProfile()
        return (listOf(preferred) + DEVICE_PROFILES).distinctBy { it.key }
    }

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
     * Cihaz ID-ni yenilÉ™yir.
     * "Cihaz limiti aÅŸÄ±lÄ±b" xÉ™tasÄ± olduqda istifadÉ™Ã§i tÉ™rÉ™findÉ™n Ã§aÄŸÄ±rÄ±lÄ±r.
     */
    fun resetDeviceId() {
        val newId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        prefs?.edit()?.putString(PREF_DEVICE_ID, newId)?.apply()
        android.util.Log.d("CityNetTV", "Device ID sÄ±fÄ±rlandÄ±, yeni ID: $newId")
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
            ?.remove(PREF_DEVICE_PROFILE)
            // PREF_DEVICE_ID is deliberately NOT removed to keep it persistent across sessions
            ?.apply()
    }

    /**
     * API-dÉ™n cihaz sessiyasÄ±nÄ± silir â€” device slot-unu azad edir.
     */
    /**
     * API-dÉ™n cihaz sessiyasÄ±nÄ± silir â€” device slot-unu azad edir.
     */
    private suspend fun logoutDevice(deviceId: String) {
        try {
            // Unregister via v2/global/logout, usually requires the login payload
            android.util.Log.d("CityNetTV", "KÃ¶hnÉ™ cihaz sessiyasÄ± silinir: $deviceId")
            app.post(
                "$API_BASE/v2/global/logout",
                headers = headers(withAuth = true, withAccessKey = false),
                requestBody = "{\"device\":\"$deviceId\"}".toOkHttpBody()
            )
        } catch (e: Exception) {
            android.util.Log.w("CityNetTV", "Logout device failed (non-critical): ${e.message}")
        }
    }

    // â”€â”€ Headers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun headers(withAuth: Boolean = true, withAccessKey: Boolean = true): Map<String, String> {
        val profile = getDeviceProfile()
        val h = mutableMapOf(
            "User-Agent"   to profile.userAgent,
            "Accept"       to "application/json",
            "Content-Type" to "application/json"
        )
        if (profile.key == DESKTOP_PROFILE.key) {
            h["Origin"] = "https://tv.citynettv.az"
            h["Referer"] = "https://tv.citynettv.az/"
        }
        if (withAccessKey) h["Access-Key"] = ACCESS_KEY
        if (withAuth) getAccessToken()?.let { h["Authorization"] = "Bearer $it" }
        return h
    }

    fun playbackHeaders(): Map<String, String> =
        headers().filterKeys { it != "Content-Type" }

    // â”€â”€ Auth â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    var lastLoginError: String? = null
        private set

    suspend fun login(username: String? = null, password: String? = null): Boolean {
        val user = username ?: getUsername() ?: return false
        val pass = password ?: getPassword() ?: return false
        lastLoginError = null
        saveCredentials(user, pass)
        for (profile in loginProfiles()) {
            if (loginWithProfile(user, pass, profile)) {
                saveDeviceProfile(profile)
                return true
            }
            val fatal = lastLoginError?.contains("limiti", ignoreCase = true) == true ||
                lastLoginError?.contains("sayda", ignoreCase = true) == true ||
                lastLoginError?.contains("sÃ‰â„¢hvdir", ignoreCase = true) == true
            if (fatal) return false
        }
        return false
    }

    private suspend fun loginWithProfile(user: String, pass: String, profile: DeviceProfile): Boolean {
        return try {
            val currentDeviceId = getDeviceId()
            logoutDevice(currentDeviceId)
            saveDeviceProfile(profile)

            val body = mapper.writeValueAsString(
                LoginRequest(
                    username = user,
                    password = pass,
                    device = currentDeviceId,
                    deviceClass = profile.deviceClass,
                    deviceType = profile.deviceType,
                    deviceOs = profile.deviceOs
                )
            )
            val res = app.post(
                "$API_BASE/v2/global/login",
                headers = headers(withAuth = false, withAccessKey = false),
                requestBody = body.toOkHttpBody()
            )
            android.util.Log.d("CityNetTV", "Login ${profile.key}: ${res.code}")
            if (res.isSuccessful) {
                val lr = mapper.readValue(res.text, LoginResponse::class.java)
                val token = lr.resolveAccessToken()
                if (!token.isNullOrEmpty()) {
                    saveTokens(token, lr.resolveRefreshToken())
                    saveResolvedUserInfo(token, lr.resolveUser())
                    lastLoginError = null
                    return true
                }
                lastLoginError = lr.error ?: "Token alÃ„Â±nmadÃ„Â±: MÃ‰â„¢lumatlar sÃ‰â„¢hvdir."
            } else {
                parseLoginError(res.code, res.text)
            }
            false
        } catch (e: Exception) {
            lastLoginError = "Ã…ÂÃ‰â„¢bÃ‰â„¢kÃ‰â„¢ xÃ‰â„¢tasÃ„Â± (${profile.key}): ${e.message}"
            e.printStackTrace()
            false
        }
    }

    private fun saveResolvedUserInfo(token: String, user: UserInfo?) {
        var resolvedUid = user?.uid ?: user?.id
        var resolvedPid = user?.profiles?.firstOrNull()?.id ?: user?.profileId
        if (resolvedUid == null || resolvedPid == null) {
            try {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                    val jwtNode = mapper.readTree(payload)
                    if (resolvedUid == null) {
                        resolvedUid = jwtNode.get("uid")?.asText()
                            ?: jwtNode.get("sub")?.asText()
                            ?: jwtNode.get("user_id")?.asText()
                            ?: jwtNode.get("id")?.asText()
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
    }

    private fun parseLoginError(code: Int, text: String) {
        try {
            val errNode = mapper.readTree(text)
            val errCode = errNode?.get("error_code")?.asInt() ?: errNode?.get("code")?.asInt()
            val dataNode = errNode?.get("data")
            val errMsg = dataNode?.get("message")?.asText()
                ?: errNode?.get("message")?.asText()
                ?: errNode?.get("error")?.asText()
                ?: errNode?.get("error_message")?.asText()

            lastLoginError = when (errCode) {
                1067 -> "Cihaz limiti aÃ…Å¸Ã„Â±lÃ„Â±b. RÃ‰â„¢smi CityNet TV proqramÃ„Â±ndan kÃƒÂ¶hnÃ‰â„¢ cihazlarÃ„Â± silin vÃ‰â„¢ ya Ayarlardan CihazÃ„Â± SÃ„Â±fÃ„Â±rlayÃ„Â±n."
                4290 -> "Ãƒâ€¡ox sayda cÃ‰â„¢hd etdiniz! Bir neÃƒÂ§Ã‰â„¢ dÃ‰â„¢qiqÃ‰â„¢ gÃƒÂ¶zlÃ‰â„¢yin vÃ‰â„¢ yenidÃ‰â„¢n yoxlayÃ„Â±n."
                3010 -> "Ã„Â°stifadÃ‰â„¢ÃƒÂ§i adÃ„Â± vÃ‰â„¢ ya Ã…Å¸ifrÃ‰â„¢ sÃ‰â„¢hvdir."
                else -> errMsg ?: "Server xÃ‰â„¢tasÃ„Â±: $code"
            }
        } catch (_: Exception) {
            lastLoginError = "Server xÃ‰â„¢tasÃ„Â±: $code"
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

    // â”€â”€ Channels â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    var lastChannelsError: String? = null
        private set

    suspend fun getChannels(): List<ChannelData> {
        lastChannelsError = null
        if (!isLoggedIn()) {
            if (!login()) {
                lastChannelsError = "Login failed: ${lastLoginError ?: "BilinmÉ™yÉ™n xÉ™ta"}"
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
                    lastChannelsError = "v1 API uÄŸursuz oldu: $lastChannelsError"
                } else {
                    lastChannelsError = "v1 API boÅŸ siyahÄ± qaytardÄ±."
                }
            } else {
                lastChannelsError = "UID ($uid) tapÄ±lmadÄ±."
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
                    lastChannelsError = (lastChannelsError ?: "") + " | fallback [$variant xÉ™tasÄ±: ${r2.code}]"
                }
            }

            return emptyList()
        } catch (e: Exception) {
            lastChannelsError = "ÅÉ™bÉ™kÉ™ xÉ™tasÄ±: ${e.message}"
            e.printStackTrace()
            emptyList()
        }
    }

    // â”€â”€ Stream â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    var lastStreamError: String? = null
        private set
    var lastStreamDiagnostics: List<StreamDiagnostic> = emptyList()
        private set

    suspend fun getStreamData(slug: String, id: String? = null, channelUid: String? = null, preferredShowId: String? = null): StreamData? {
        lastStreamError = null
        lastStreamDiagnostics = emptyList()
        if (!isLoggedIn()) { if (!login()) return null }
        val uid = getUserUid() ?: return null
        return try {
            val showId = preferredShowId ?: getCurrentShowId(slug)
            val pid = getProfileId() ?: "0"

            val possibleEndpoints = mutableListOf<String>()
            val channelKeys = listOfNotNull(channelUid, slug, id).filter { it.isNotBlank() }.distinct()
            if (channelKeys.isEmpty()) {
                lastStreamError = "Kanal slug/id boÃ…Å¸dur"
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

            possibleEndpoints.addAll(
                possibleEndpoints.toList().map { it.withDashWidevineFormat() }
            )

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
            val diagnostics = mutableListOf<StreamDiagnostic>()
            var drmDashFallback: StreamData? = null
            var sawCencHls = false

            for (url in possibleEndpoints.distinct()) {
                val getRes = authGet(url)
                val getData = inspectCencHlsIfNeeded(parseStreamResponse("GET", url, getRes, attempts), diagnostics, attempts)
                diagnostics.add(getRes.toDiagnostic("GET", url, getData))
                if (getData?.resolveStreamUrl().isNullOrEmpty().not()) {
                    if (getData.isUnsupportedCencHls()) {
                        sawCencHls = true
                        attempts.add("GET ${url.removePrefix(API_BASE)}: CENC-HLS unsupported, DASH fallback not found")
                    } else if (getData.needsDrmFallback()) {
                        drmDashFallback = drmDashFallback ?: getData
                        if (sawCencHls) return getData
                    } else {
                        return getData
                    }
                }

                if ((getRes.isSuccessful && (getData == null || getData.needsDrmFallback())) || getRes.code in setOf(400, 403, 405, 422)) {
                    for (playbackBody in playbackBodies) {
                        val postRes = authPost(url, playbackBody)
                        val postData = inspectCencHlsIfNeeded(parseStreamResponse("POST", url, postRes, attempts), diagnostics, attempts)
                        diagnostics.add(postRes.toDiagnostic("POST", url, postData))
                        if (postData?.resolveStreamUrl().isNullOrEmpty().not()) {
                            if (postData.isUnsupportedCencHls()) {
                                sawCencHls = true
                                attempts.add("POST ${url.removePrefix(API_BASE)}: CENC-HLS unsupported, DASH fallback not found")
                            } else if (postData.needsDrmFallback()) {
                                drmDashFallback = drmDashFallback ?: postData
                                if (sawCencHls) return postData
                            } else {
                                return postData
                            }
                        }
                        if (postRes.code !in setOf(400, 403, 405, 422) && postData?.needsDrmFallback() != true) break
                    }
                }
            }

            lastStreamError = if (sawCencHls && drmDashFallback == null) {
                "CENC-HLS unsupported, DASH fallback not found | ${attempts.takeLast(8).joinToString(" | ")}"
            } else {
                attempts.takeLast(8).joinToString(" | ").ifBlank { "Stream endpoint tapÃ„Â±lmadÃ„Â±" }
            }
            lastStreamDiagnostics = diagnostics.takeLast(16)
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
                attempts.add("$method $endpoint: boÃ…Å¸")
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
            val responseHasCencMarkers = text.hasCencHlsMarkers()
            val hlsUrlIsCenc = hlsUrl.isCencHlsStream() || (hlsUrl?.looksLikeHlsStream() == true && responseHasCencMarkers)
            val typedUrlIsCenc = typedUrl.isCencHlsStream() || (typedUrl?.looksLikeHlsStream() == true && responseHasCencMarkers)
            val streamUrl = when {
                hlsUrlIsCenc && !dashUrl.isNullOrEmpty() -> dashUrl
                typedUrlIsCenc && !dashUrl.isNullOrEmpty() -> dashUrl
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
                streamUrl = streamUrl,
                lat = typed.lat ?: lat,
                jwt = typed.jwt ?: jwt,
                server = typed.server ?: server,
                drm = drm,
                cencHls = (streamUrl == hlsUrl && hlsUrlIsCenc) || (streamUrl == typedUrl && typedUrlIsCenc)
            )
        } catch (e: Exception) {
            android.util.Log.w("CityNetTV", "Stream parse failed: ${e.message}")
            null
        }
    }

    private fun com.lagradost.nicehttp.NiceResponse.toDiagnostic(
        method: String,
        url: String,
        streamData: StreamData?
    ): StreamDiagnostic {
        val streamUrl = streamData?.resolveStreamUrl()
        val streamType = when {
            streamUrl == null -> null
            streamData?.cencHls == true -> "HLS-CENC"
            streamUrl.looksLikeDashStream() -> "DASH"
            streamUrl.looksLikeHlsStream() -> "HLS"
            else -> "VIDEO"
        }
        val server = streamData?.server ?: streamUrl?.resolveCitynetApiServer()?.toString()
        return StreamDiagnostic(
            method = method,
            endpoint = url.removePrefix(API_BASE),
            code = code,
            profile = getDeviceProfileKey(),
            streamType = streamType,
            server = server,
            note = when {
                streamData?.cencHls == true -> "CENC-HLS unsupported, DASH fallback not found"
                streamData?.needsDrmFallback() == true -> "DRM/DASH candidate"
                else -> null
            }
        )
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
        return streamUrl.contains(".mpd", ignoreCase = true) ||
            streamUrl.contains("/dash", ignoreCase = true) ||
            (!isHls && (
                !drm?.resolveLicenseUrl().isNullOrEmpty() ||
                    !lat.isNullOrEmpty() ||
                    !jwt.isNullOrEmpty()
                ))
    }

    private fun StreamData.isUnsupportedCencHls(): Boolean {
        return cencHls || resolveStreamUrl().isCencHlsStream()
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
        return this?.looksLikeHlsStream() == true && hasCencHlsMarkers()
    }

    private fun String.hasCencHlsMarkers(): Boolean {
        return contains("MPEG-CENC", ignoreCase = true) ||
            contains("#EXT-X-KEY", ignoreCase = true) ||
            contains("METHOD=SAMPLE-AES", ignoreCase = true) ||
            contains("KEYFORMAT=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\"", ignoreCase = true) ||
            Regex("""METHOD\s*=\s*SAMPLE-AES""", RegexOption.IGNORE_CASE).containsMatchIn(this) ||
            Regex("""KEYFORMAT\s*=\s*"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"""", RegexOption.IGNORE_CASE).containsMatchIn(this)
    }

    private suspend fun inspectCencHlsIfNeeded(
        streamData: StreamData?,
        diagnostics: MutableList<StreamDiagnostic>,
        attempts: MutableList<String>
    ): StreamData? {
        val streamUrl = streamData?.resolveStreamUrl() ?: return streamData
        if (streamData.cencHls || streamUrl.isCencHlsStream() || !streamUrl.looksLikeHlsStream()) return streamData

        return try {
            val res = app.get(streamUrl, headers = playbackHeaders())
            val isCenc = res.isSuccessful && res.text.hasCencHlsMarkers()
            diagnostics.add(
                StreamDiagnostic(
                    method = "GET",
                    endpoint = streamUrl,
                    code = res.code,
                    profile = getDeviceProfileKey(),
                    streamType = if (isCenc) "HLS-CENC" else "HLS",
                    server = streamData.server ?: streamUrl.resolveCitynetApiServer()?.toString(),
                    note = if (isCenc) "manifest CENC marker" else "manifest sniff"
                )
            )
            if (isCenc) {
                attempts.add("GET manifest: CENC-HLS unsupported, DASH fallback not found")
                streamData.copy(cencHls = true)
            } else {
                streamData
            }
        } catch (e: Exception) {
            diagnostics.add(
                StreamDiagnostic(
                    method = "GET",
                    endpoint = streamUrl,
                    code = 0,
                    profile = getDeviceProfileKey(),
                    streamType = "HLS",
                    server = streamData.server ?: streamUrl.resolveCitynetApiServer()?.toString(),
                    note = "manifest sniff failed: ${e.message}"
                )
            )
            streamData
        }
    }

    private fun String.withDashWidevineFormat(): String {
        val fragmentIndex = indexOf('#')
        val withoutFragment = if (fragmentIndex >= 0) substring(0, fragmentIndex) else this
        val fragment = if (fragmentIndex >= 0) substring(fragmentIndex) else ""
        val queryIndex = withoutFragment.indexOf('?')
        val base = if (queryIndex >= 0) withoutFragment.substring(0, queryIndex) else withoutFragment
        val query = if (queryIndex >= 0) withoutFragment.substring(queryIndex + 1) else ""
        val params = query.split("&")
            .filter { it.isNotBlank() }
            .filterNot {
                val key = it.substringBefore("=").lowercase()
                key == "format" || key == "drm"
            }
            .toMutableList()
        params.add("format=dash")
        params.add("drm=widevine")
        return "$base?${params.joinToString("&")}$fragment"
    }

    // â”€â”€ EPG â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ Helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun String.resolveCitynetApiServer(): Int? {
        return runCatching {
            Regex("""api(\d+)\.citynettv\.az""")
                .find(URI(this).host.orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }.getOrNull()
    }

    /** Converts a String to a NiceHttp-compatible RequestBody */
    private fun String.toOkHttpBody() =
        this.toRequestBody("application/json; charset=utf-8".toMediaType())
}

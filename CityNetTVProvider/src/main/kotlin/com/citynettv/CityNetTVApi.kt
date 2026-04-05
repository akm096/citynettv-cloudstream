package com.citynettv

import android.content.SharedPreferences
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
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
            UUID.nameUUIDFromBytes("CityNetTV-CS-$user".toByteArray()).toString()
        } else {
            UUID.randomUUID().toString()
        }
        prefs?.edit()?.putString(PREF_DEVICE_ID, id)?.apply()
        return id
    }

    /**
     * Cihaz ID-ni yeniləyir.
     * "Cihaz limiti aşılıb" xətası olduqda istifadəçi tərəfindən çağırılır.
     */
    fun resetDeviceId() {
        val newId = UUID.randomUUID().toString()
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
                    deviceClass = "STB",
                    deviceType = "ANDROID_TV",
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
                if (!lr.accessToken.isNullOrEmpty()) {
                    saveTokens(lr.accessToken, lr.refreshToken)
                    val pid = lr.user?.profiles?.firstOrNull()?.id ?: lr.user?.profileId
                    saveUserInfo(lr.user?.uid, pid)
                    lastLoginError = null
                    return true
                }
                lastLoginError = lr.error ?: "Token alınmadı"
            } else {
                // Parse error response
                try {
                    val errBody = res.text
                    val errNode = mapper.readTree(errBody)
                    val errCode = errNode?.get("error_code")?.asInt() ?: errNode?.get("code")?.asInt()
                    val errMsg = errNode?.get("message")?.asText()
                        ?: errNode?.get("error")?.asText()
                        ?: errNode?.get("error_message")?.asText()

                    if (errCode == 1067) {
                        lastLoginError = "Cihaz limiti aşılıb. Rəsmi CityNet TV proqramından köhnə cihazları silin və ya Ayarlardan Cihazı Sıfırlayın."
                        android.util.Log.w("CityNetTV", "Device limit aşılıb")
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
                if (!rr.accessToken.isNullOrEmpty()) {
                    saveTokens(rr.accessToken, rr.refreshToken ?: rt)
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

    // ── Channels ──────────────────────────────────────────────────────────────

    suspend fun getChannels(): List<ChannelData> {
        if (!isLoggedIn()) { if (!login()) return emptyList() }
        val uid = getUserUid() ?: return emptyList()
        val pid = getProfileId() ?: return emptyList()
        return try {
            // 1) user-specific list
            val r1 = authGet("$API_BASE/v1/citynet/users/$uid/profiles/$pid/channels")
            if (r1.isSuccessful) {
                val ch = mapper.readValue(r1.text, ChannelsResponse::class.java)
                val list = ch.data ?: ch.channels
                if (!list.isNullOrEmpty()) return list
            }
            // 2) public list fallback
            val r2 = authGet("$API_BASE/v2/citynet/channels?translation=az")
            if (r2.isSuccessful) {
                val ch = mapper.readValue(r2.text, ChannelsResponse::class.java)
                return ch.data ?: ch.channels ?: emptyList()
            }
            emptyList()
        } catch (e: Exception) { e.printStackTrace(); emptyList() }
    }

    // ── Stream ────────────────────────────────────────────────────────────────

    suspend fun getStreamData(slug: String): StreamData? {
        if (!isLoggedIn()) { if (!login()) return null }
        val uid = getUserUid() ?: return null
        return try {
            val showId = getCurrentShowId(slug)
            val url = if (showId != null)
                "$API_BASE/v1/citynet/users/$uid/vod/channels/$slug/shows/$showId"
            else
                "$API_BASE/v1/citynet/users/$uid/vod/channels/$slug/live"
            val res = authGet(url)
            if (res.isSuccessful) {
                val sr = mapper.readValue(res.text, StreamResponse::class.java)
                sr.data ?: StreamData(url = sr.streamUrl ?: sr.url)
            } else null
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    fun buildLicenseUrl(lat: String?, jwt: String?, server: Int = 1): String {
        val params = buildList {
            if (!lat.isNullOrEmpty()) add("lat=$lat")
            if (!jwt.isNullOrEmpty()) add("jwt=$jwt")
        }
        val qs = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return "https://api$server.citynettv.az:11610/drmproxy/wv/license$qs"
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

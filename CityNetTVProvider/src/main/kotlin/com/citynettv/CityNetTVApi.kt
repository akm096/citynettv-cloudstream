package com.citynettv

import android.content.SharedPreferences
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.NiceResponse
import java.util.UUID

/**
 * CityNetTV API Client
 *
 * Bütün API əməliyyatları (login, token refresh, kanallar, stream URL)
 * bu class vasitəsilə yerinə yetirilir.
 *
 * VACIB: device_class=MOBILE, device_type=ANDROID olaraq göndərilir ki,
 * "Go" aboneliyi TV limiti keçsin.
 */
class CityNetTVApi(private val prefs: SharedPreferences?) {

    companion object {
        const val API_BASE = "https://tvapi.citynettv.az:11610/api/client"
        const val ACCESS_KEY = "WkVjNWNscFhORDBLCg=="

        private const val PREF_ACCESS_TOKEN = "citynettv_access_token"
        private const val PREF_REFRESH_TOKEN = "citynettv_refresh_token"
        private const val PREF_USER_UID = "citynettv_user_uid"
        private const val PREF_PROFILE_ID = "citynettv_profile_id"
        private const val PREF_DEVICE_ID = "citynettv_device_id"
        private const val PREF_USERNAME = "citynettv_username"
        private const val PREF_PASSWORD = "citynettv_password"

        // Mobile User-Agent (TV limiti keçmək üçün)
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
    }

    private val mapper = jacksonObjectMapper()

    // ============== CREDENTIALS ==============

    fun saveCredentials(username: String, password: String) {
        prefs?.edit()?.apply {
            putString(PREF_USERNAME, username)
            putString(PREF_PASSWORD, password)
            apply()
        }
    }

    fun getUsername(): String? = prefs?.getString(PREF_USERNAME, null)
    fun getPassword(): String? = prefs?.getString(PREF_PASSWORD, null)

    fun hasCredentials(): Boolean {
        return !getUsername().isNullOrEmpty() && !getPassword().isNullOrEmpty()
    }

    private fun getAccessToken(): String? = prefs?.getString(PREF_ACCESS_TOKEN, null)
    private fun getRefreshToken(): String? = prefs?.getString(PREF_REFRESH_TOKEN, null)
    fun getUserUid(): String? = prefs?.getString(PREF_USER_UID, null)
    fun getProfileId(): String? = prefs?.getString(PREF_PROFILE_ID, null)

    private fun getDeviceId(): String {
        var deviceId = prefs?.getString(PREF_DEVICE_ID, null)
        if (deviceId.isNullOrEmpty()) {
            deviceId = UUID.randomUUID().toString()
            prefs?.edit()?.putString(PREF_DEVICE_ID, deviceId)?.apply()
        }
        return deviceId
    }

    private fun saveTokens(accessToken: String?, refreshToken: String?) {
        prefs?.edit()?.apply {
            putString(PREF_ACCESS_TOKEN, accessToken)
            putString(PREF_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    private fun saveUserInfo(uid: String?, profileId: String?) {
        prefs?.edit()?.apply {
            putString(PREF_USER_UID, uid)
            putString(PREF_PROFILE_ID, profileId)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrEmpty()

    fun logout() {
        prefs?.edit()?.apply {
            remove(PREF_ACCESS_TOKEN)
            remove(PREF_REFRESH_TOKEN)
            remove(PREF_USER_UID)
            remove(PREF_PROFILE_ID)
            apply()
        }
    }

    // ============== AUTH HEADERS ==============

    private fun getHeaders(withAuth: Boolean = true): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "Access-Key" to ACCESS_KEY
        )
        if (withAuth) {
            val token = getAccessToken()
            if (!token.isNullOrEmpty()) {
                headers["Authorization"] = "Bearer $token"
            }
        }
        return headers
    }

    // ============== LOGIN ==============

    /**
     * CityNetTV-yə giriş edir
     * @return true əgər uğurlu olsa
     */
    suspend fun login(username: String? = null, password: String? = null): Boolean {
        val user = username ?: getUsername() ?: return false
        val pass = password ?: getPassword() ?: return false

        try {
            val loginRequest = LoginRequest(
                username = user,
                password = pass,
                device = getDeviceId()
            )

            val body = mapper.writeValueAsString(loginRequest)

            val response = app.post(
                url = "$API_BASE/v2/global/login",
                headers = getHeaders(withAuth = false),
                requestBody = body,
                cacheTime = 0
            )

            if (response.isSuccessful) {
                val loginResponse = mapper.readValue(response.text, LoginResponse::class.java)

                if (!loginResponse.accessToken.isNullOrEmpty()) {
                    saveTokens(loginResponse.accessToken, loginResponse.refreshToken)

                    val uid = loginResponse.user?.uid
                    val profileId = loginResponse.user?.profiles?.firstOrNull()?.id
                        ?: loginResponse.user?.profileId

                    saveUserInfo(uid, profileId)
                    saveCredentials(user, pass)

                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Token-i yeniləyir
     */
    suspend fun refreshToken(): Boolean {
        val refreshToken = getRefreshToken() ?: return login()

        try {
            val body = mapper.writeValueAsString(RefreshRequest(refreshToken))

            val response = app.post(
                url = "$API_BASE/v2/global/refresh",
                headers = getHeaders(withAuth = false),
                requestBody = body,
                cacheTime = 0
            )

            if (response.isSuccessful) {
                val refreshResponse = mapper.readValue(response.text, RefreshResponse::class.java)
                if (!refreshResponse.accessToken.isNullOrEmpty()) {
                    saveTokens(refreshResponse.accessToken, refreshResponse.refreshToken ?: refreshToken)
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Refresh uğursuz olsa, tam login et
        return login()
    }

    /**
     * Autentifikasiyalı API sorğusu göndərir (401-də avtomatik yeniləyir)
     */
    private suspend fun authenticatedGet(url: String, retried: Boolean = false): NiceResponse {
        val response = app.get(
            url = url,
            headers = getHeaders(withAuth = true),
            cacheTime = 60 * 5 // 5 dəqiqə cache
        )

        // Token expired — yenilə və yenidən sına
        if (response.code == 401 && !retried) {
            if (refreshToken()) {
                return authenticatedGet(url, retried = true)
            }
        }

        return response
    }

    // ============== CHANNELS ==============

    /**
     * İstifadəçinin bütün kanallarını çəkir
     */
    suspend fun getChannels(): List<ChannelData> {
        if (!isLoggedIn()) {
            if (!login()) return emptyList()
        }

        val uid = getUserUid() ?: return emptyList()
        val pid = getProfileId() ?: return emptyList()

        try {
            // Əvvəlcə user-specific channel list sına
            val userChannelsUrl = "$API_BASE/v1/citynet/users/$uid/profiles/$pid/channels"
            val response = authenticatedGet(userChannelsUrl)

            if (response.isSuccessful) {
                val channelsResponse = mapper.readValue(response.text, ChannelsResponse::class.java)
                val channels = channelsResponse.data ?: channelsResponse.channels
                if (!channels.isNullOrEmpty()) return channels
            }

            // Fallback: public channel list
            val publicUrl = "$API_BASE/v2/citynet/channels?translation=az"
            val publicResponse = authenticatedGet(publicUrl)

            if (publicResponse.isSuccessful) {
                val channelsResponse = mapper.readValue(publicResponse.text, ChannelsResponse::class.java)
                return channelsResponse.data ?: channelsResponse.channels ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return emptyList()
    }

    /**
     * Kanalları ölkəyə görə qruplaşdırır
     */
    suspend fun getChannelsByCountry(): Map<String, List<ChannelData>> {
        val channels = getChannels()
        return channels.groupBy { it.getCountryCategory() }
            .toSortedMap(compareBy {
                when {
                    it.contains("Azərbaycan") -> 0
                    it.contains("Türkiyə") -> 1
                    it.contains("Rusiya") -> 2
                    else -> 3
                }
            })
    }

    /**
     * Kanalları janra görə qruplaşdırır
     */
    suspend fun getChannelsByGenre(): Map<String, List<ChannelData>> {
        val channels = getChannels()
        return channels.groupBy { it.getGenreCategory() }
            .toSortedMap(compareBy {
                when {
                    it.contains("Xəbərlər") -> 0
                    it.contains("İdman") -> 1
                    it.contains("Kino") -> 2
                    it.contains("Əyləncə") -> 3
                    it.contains("Uşaq") -> 4
                    it.contains("Musiqi") -> 5
                    it.contains("Sənədli") -> 6
                    else -> 7
                }
            })
    }

    // ============== STREAM ==============

    /**
     * Kanalın canlı yayın URL-ni alır
     */
    suspend fun getStreamUrl(channelSlug: String): StreamData? {
        if (!isLoggedIn()) {
            if (!login()) return null
        }

        val uid = getUserUid() ?: return null

        try {
            // Əvvəlcə aktiv show ID-ni tap
            val showId = getCurrentShowId(channelSlug)

            val streamUrl = if (showId != null) {
                "$API_BASE/v1/citynet/users/$uid/vod/channels/$channelSlug/shows/$showId"
            } else {
                "$API_BASE/v1/citynet/users/$uid/vod/channels/$channelSlug/live"
            }

            val response = authenticatedGet(streamUrl)

            if (response.isSuccessful) {
                val streamResponse = mapper.readValue(response.text, StreamResponse::class.java)
                return streamResponse.data ?: StreamData(
                    url = streamResponse.streamUrl ?: streamResponse.url
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * DRM lisenziya URL-ni qurur
     */
    fun buildLicenseUrl(lat: String?, jwt: String?, serverNum: Int = 1): String {
        val server = "api$serverNum"
        val params = mutableListOf<String>()
        if (!lat.isNullOrEmpty()) params.add("lat=$lat")
        if (!jwt.isNullOrEmpty()) params.add("jwt=$jwt")
        val queryString = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return "https://$server.citynettv.az:11610/drmproxy/wv/license$queryString"
    }

    // ============== EPG ==============

    /**
     * Hal-hazırda oynayan proqramın show ID-sini qaytarır
     */
    suspend fun getCurrentShowId(channelSlug: String): String? {
        try {
            val now = System.currentTimeMillis()
            val startDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Baku") }
                .format(java.util.Date(now - 3600000)) // 1 saat əvvəl
            val endDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Baku") }
                .format(java.util.Date(now + 3600000)) // 1 saat sonra

            val epgUrl = "$API_BASE/v2/citynet/shows/grid?start_date=$startDate&end_date=$endDate&channels=$channelSlug"
            val response = authenticatedGet(epgUrl)

            if (response.isSuccessful) {
                val epgResponse = mapper.readValue(response.text, EpgResponse::class.java)
                val shows = epgResponse.data ?: epgResponse.shows
                // İlk uyğun show-u qaytar
                return shows?.firstOrNull()?.showId ?: shows?.firstOrNull()?.id
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Kanalın EPG proqramını çəkir
     */
    suspend fun getEpg(channelSlug: String): List<EpgItem> {
        try {
            val now = System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Baku") }

            val startDate = sdf.format(java.util.Date(now - 3600000 * 3)) // 3 saat əvvəl
            val endDate = sdf.format(java.util.Date(now + 3600000 * 12)) // 12 saat sonra

            val epgUrl = "$API_BASE/v2/citynet/shows/grid?start_date=$startDate&end_date=$endDate&channels=$channelSlug"
            val response = authenticatedGet(epgUrl)

            if (response.isSuccessful) {
                val epgResponse = mapper.readValue(response.text, EpgResponse::class.java)
                return epgResponse.data ?: epgResponse.shows ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }
}

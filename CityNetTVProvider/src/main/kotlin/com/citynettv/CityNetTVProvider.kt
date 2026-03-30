package com.citynettv

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

/**
 * CityNetTV CloudStream Provider
 *
 * Azərbaycan, Türkiyə, Rusiya TV kanallarını kateqoriyalara
 * bölüb göstərir. Hər kanalın logosu görünür.
 *
 * Kateqoriyalar:
 * - Ölkəyə görə: 🇦🇿 Azərbaycan, 🇹🇷 Türkiyə, 🇷🇺 Rusiya
 * - Janra görə: 📰 Xəbərlər, ⚽ İdman, 🎬 Kino, 👶 Uşaq, 🎵 Musiqi, 🎭 Əyləncə, 📚 Sənədli
 */
class CityNetTVProvider(val context: Context? = null) : MainAPI() {

    override var mainUrl = "https://tv.citynettv.az"
    override var name = "CityNetTV"
    override val hasMainPage = true
    override var lang = "az"

    override val supportedTypes = setOf(TvType.Live)

    override val hasQuickSearch = false

    private val mapper = jacksonObjectMapper()

    lateinit var api: CityNetTVApi

    fun initApi(ctx: Context) {
        val prefs = ctx.getSharedPreferences("citynettv_prefs", Context.MODE_PRIVATE)
        api = CityNetTVApi(prefs)
    }

    // ============== ANA SƏHIFƏ ==============

    /**
     * CloudStream ana səhifəsində kateqoriyalar və kanallar göstərir.
     * Hər kateqoriya üfüqi scrollable sıra kimi görünür.
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (!::api.isInitialized) {
            context?.let { initApi(it) }
        }

        if (!api.isLoggedIn()) {
            api.login()
        }

        val allChannels = api.getChannels()

        if (allChannels.isEmpty()) {
            return HomePageResponse(
                listOf(
                    HomePageList(
                        name = "⚠️ Giriş edin (Ayarlar → CityNetTV)",
                        list = emptyList(),
                        isHorizontalImages = true
                    )
                )
            )
        }

        val sections = mutableListOf<HomePageList>()

        // === ÖLKƏYƏ GÖRƏ KATEQORİYALAR ===
        val byCountry = allChannels.groupBy { it.getCountryCategory() }
            .toSortedMap(compareBy {
                when {
                    it.contains("Azərbaycan") -> 0
                    it.contains("Türkiyə") -> 1
                    it.contains("Rusiya") -> 2
                    else -> 3
                }
            })

        for ((countryName, channels) in byCountry) {
            val items = channels.sortedBy { it.number ?: 999 }.map { channel ->
                createChannelSearchResponse(channel)
            }
            if (items.isNotEmpty()) {
                sections.add(
                    HomePageList(
                        name = countryName,
                        list = items,
                        isHorizontalImages = true
                    )
                )
            }
        }

        // === JANRA GÖRƏ KATEQORİYALAR ===
        val byGenre = allChannels.groupBy { it.getGenreCategory() }
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

        for ((genreName, channels) in byGenre) {
            val items = channels.sortedBy { it.number ?: 999 }.map { channel ->
                createChannelSearchResponse(channel)
            }
            if (items.isNotEmpty()) {
                sections.add(
                    HomePageList(
                        name = genreName,
                        list = items,
                        isHorizontalImages = true
                    )
                )
            }
        }

        return HomePageResponse(sections)
    }

    /**
     * Kanal üçün SearchResponse yaradır (logo ilə)
     */
    private fun createChannelSearchResponse(channel: ChannelData): LiveSearchResponse {
        val loadData = ChannelLoadData(
            slug = channel.slug ?: channel.id ?: "",
            name = channel.getDisplayName()
        )
        val dataJson = mapper.writeValueAsString(loadData)

        return LiveSearchResponse(
            name = channel.getDisplayName(),
            url = dataJson,
            apiName = this.name,
            type = TvType.Live,
            posterUrl = channel.getLogoUrl(),
            quality = if (channel.isHd == true) SearchQuality.HD else null
        )
    }

    // ============== AXTARIŞ ==============

    override suspend fun search(query: String): List<SearchResponse> {
        if (!::api.isInitialized) {
            context?.let { initApi(it) }
        }

        if (!api.isLoggedIn()) {
            api.login()
        }

        val allChannels = api.getChannels()
        val queryLower = query.lowercase()

        return allChannels
            .filter { channel ->
                channel.getDisplayName().lowercase().contains(queryLower)
            }
            .map { channel -> createChannelSearchResponse(channel) }
    }

    // ============== KANAL DETALI ==============

    override suspend fun load(url: String): LoadResponse {
        if (!::api.isInitialized) {
            context?.let { initApi(it) }
        }

        val loadData = try {
            mapper.readValue(url, ChannelLoadData::class.java)
        } catch (e: Exception) {
            // Fallback: URL-i slug kimi istifadə et
            ChannelLoadData(slug = url, name = "CityNetTV Kanal")
        }

        // EPG məlumatını çək
        val epgItems = api.getEpg(loadData.slug)
        val currentShow = epgItems.firstOrNull()

        val plot = buildString {
            if (currentShow != null) {
                append("▶️ İndi: ${currentShow.getDisplayName()}\n")
                val startTime = currentShow.getStartTime()
                val endTime = currentShow.getEndTime()
                if (startTime != null && endTime != null) {
                    append("⏰ $startTime — $endTime\n")
                }
                if (!currentShow.description.isNullOrEmpty()) {
                    append("\n${currentShow.description}\n")
                }
            }
            if (epgItems.size > 1) {
                append("\n📋 Növbəti proqramlar:\n")
                epgItems.drop(1).take(5).forEach { item ->
                    append("• ${item.getStartTime() ?: ""} — ${item.getDisplayName()}\n")
                }
            }
        }

        // ChannelLoadData-ya show id əlavə et
        val dataWithShow = loadData.copy(
            showId = currentShow?.showId ?: currentShow?.id
        )

        return LiveStreamLoadResponse(
            name = loadData.name,
            url = mapper.writeValueAsString(dataWithShow),
            apiName = this.name,
            dataUrl = mapper.writeValueAsString(dataWithShow),
            posterUrl = null,
            plot = plot.ifEmpty { "CityNetTV — ${loadData.name}" },
            tags = listOf("Live", "TV")
        )
    }

    // ============== STREAM LINKLƏRI ==============

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!::api.isInitialized) {
            context?.let { initApi(it) }
        }

        val loadData = try {
            mapper.readValue(data, ChannelLoadData::class.java)
        } catch (e: Exception) {
            return false
        }

        val streamData = api.getStreamUrl(loadData.slug) ?: return false
        val streamUrl = streamData.getStreamUrl() ?: return false

        // DRM parametrləri
        val lat = streamData.lat
        val jwt = streamData.jwt
        val drmInfo = streamData.drm

        if (drmInfo != null || (!lat.isNullOrEmpty() && !jwt.isNullOrEmpty())) {
            // Widevine DRM ilə
            val licenseUrl = drmInfo?.licenseUrl
                ?: api.buildLicenseUrl(lat, jwt)

            val drmHeaders = mutableMapOf(
                "User-Agent" to CityNetTVApi.USER_AGENT
            )
            drmInfo?.headers?.let { drmHeaders.putAll(it) }

            // Server cycling (api1-api8 arasında)
            for (serverNum in 1..3) {
                val serverLicenseUrl = if (drmInfo?.licenseUrl != null) {
                    drmInfo.licenseUrl
                } else {
                    api.buildLicenseUrl(lat, jwt, serverNum)
                }

                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "${loadData.name} (Server $serverNum)",
                        url = streamUrl,
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = streamUrl.contains(".m3u8"),
                        headers = mapOf(
                            "User-Agent" to CityNetTVApi.USER_AGENT,
                            "Referer" to "$mainUrl/"
                        )
                    )
                )
            }
        } else {
            // Adi stream (DRM olmadan)
            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = loadData.name,
                    url = streamUrl,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = streamUrl.contains(".m3u8"),
                    headers = mapOf(
                        "User-Agent" to CityNetTVApi.USER_AGENT,
                        "Referer" to "$mainUrl/"
                    )
                )
            )
        }

        return true
    }
}

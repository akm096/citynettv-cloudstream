package com.citynettv

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.WIDEVINE_UUID

class CityNetTVProvider(val context: Context? = null) : MainAPI() {

    override var mainUrl  = "https://tv.citynettv.az"
    override var name     = "CityNetTV"
    override val hasMainPage = true
    override var lang     = "az"
    override val supportedTypes = setOf(TvType.Live)

    private val mapper = jacksonObjectMapper()
    lateinit var api: CityNetTVApi

    fun initApi(ctx: Context) {
        val prefs = ctx.getSharedPreferences("citynettv_prefs", Context.MODE_PRIVATE)
        api = CityNetTVApi(prefs)
    }

    // ── Main page ─────────────────────────────────────────────────────────────

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (!::api.isInitialized) context?.let { initApi(it) }

        val isLoggedIn = api.isLoggedIn() || api.login()

        val all = api.getChannels()
        if (all.isEmpty()) {
            val message = if (!isLoggedIn) {
                "⚠️ Giriş edin (Ayarlar → CityNetTV)"
            } else {
                val err = api.lastChannelsError ?: "Bilinməyən xəta"
                "⚠️ Kanallar yüklənmədi ($err). Təkrar daxil olun."
            }
            return newHomePageResponse(
                listOf(HomePageList(message, emptyList(), isHorizontalImages = true))
            )
        }

        val sections = mutableListOf<HomePageList>()

        // Country rows
        val byCountry = all.groupBy { it.getCountryCategory() }
            .toSortedMap(compareBy {
                when { it.contains("Azərbaycan") -> 0; it.contains("Türkiyə") -> 1; it.contains("Rusiya") -> 2; else -> 3 }
            })
        for ((cat, chs) in byCountry) {
            val items = chs.sortedBy { it.number ?: 999 }.map { toSearchResponse(it) }
            if (items.isNotEmpty()) sections.add(HomePageList(cat, items, isHorizontalImages = true))
        }

        // Genre rows
        val byGenre = all.groupBy { it.getGenreCategory() }
            .toSortedMap(compareBy {
                when { it.contains("Xəbərlər") -> 0; it.contains("İdman") -> 1; it.contains("Kino") -> 2; it.contains("Əyləncə") -> 3; it.contains("Uşaq") -> 4; it.contains("Musiqi") -> 5; it.contains("Sənədli") -> 6; else -> 7 }
            })
        for ((cat, chs) in byGenre) {
            val items = chs.sortedBy { it.number ?: 999 }.map { toSearchResponse(it) }
            if (items.isNotEmpty()) sections.add(HomePageList(cat, items, isHorizontalImages = true))
        }

        return newHomePageResponse(sections)
    }

    private fun toSearchResponse(ch: ChannelData): LiveSearchResponse {
        val data = mapper.writeValueAsString(
            ChannelLoadData(
                slug = ch.slug ?: ch.id ?: "",
                name = ch.getDisplayName(),
                id = ch.id
            )
        )
        return newLiveSearchResponse(
            name = ch.getDisplayName(),
            url  = data,
            type = TvType.Live
        ).apply {
            this.posterUrl = ch.resolveLogoUrl()
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    override suspend fun search(query: String): List<SearchResponse> {
        if (!::api.isInitialized) context?.let { initApi(it) }
        if (!api.isLoggedIn()) api.login()
        val q = query.lowercase()
        return api.getChannels()
            .filter { it.getDisplayName().lowercase().contains(q) }
            .map { toSearchResponse(it) }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    override suspend fun load(url: String): LoadResponse {
        if (!::api.isInitialized) context?.let { initApi(it) }

        val ld = try { mapper.readValue(url, ChannelLoadData::class.java) }
                 catch (_: Exception) { ChannelLoadData(slug = url, name = "Kanal") }

        val epg     = api.getEpg(ld.slug)
        val current = epg.firstOrNull()

        val plot = buildString {
            current?.let {
                append("▶️ İndi: ${it.getDisplayName()}\n")
                val s = it.resolveStartTime(); val e = it.resolveEndTime()
                if (s != null && e != null) append("⏰ $s — $e\n")
                if (!it.description.isNullOrEmpty()) append("\n${it.description}\n")
            }
            if (epg.size > 1) {
                append("\n📋 Növbəti:\n")
                epg.drop(1).take(5).forEach { append("• ${it.resolveStartTime() ?: ""} — ${it.getDisplayName()}\n") }
            }
        }

        val dataJson = mapper.writeValueAsString(ld.copy(showId = current?.showId ?: current?.id))

        return newLiveStreamLoadResponse(
            name    = ld.name,
            url     = dataJson,
            dataUrl = dataJson
        ).apply {
            this.plot = plot.ifEmpty { "CityNetTV — ${ld.name}" }
        }
    }

    // ── LoadLinks ─────────────────────────────────────────────────────────────

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!::api.isInitialized) context?.let { initApi(it) }

        val ld = try { mapper.readValue(data, ChannelLoadData::class.java) } catch (_: Exception) { return false }
        val sd = api.getStreamData(ld.slug, ld.id, ld.showId) ?: return false
        val streamUrl = sd.resolveStreamUrl() ?: return false

        val isM3u8 = streamUrl.contains(".m3u8")
        val isDash = streamUrl.contains(".mpd")
        val linkType = when {
            isM3u8 -> ExtractorLinkType.M3U8
            isDash -> ExtractorLinkType.DASH
            else -> ExtractorLinkType.VIDEO
        }
        val headers = api.playbackHeaders() + mapOf("Referer" to "$mainUrl/")
        val licenseUrl = sd.drm?.resolveLicenseUrl()
            ?: api.buildLicenseUrl(sd.lat, sd.jwt, sd.server?.toIntOrNull() ?: 1).takeIf {
                !sd.lat.isNullOrEmpty() || !sd.jwt.isNullOrEmpty()
            }

        for (serverNum in 1..3) {
            val link = if (!licenseUrl.isNullOrEmpty()) {
                newDrmExtractorLink(
                    source = this.name,
                    name = if (serverNum == 1) ld.name else "${ld.name} S$serverNum",
                    url = streamUrl,
                    type = linkType,
                    uuid = WIDEVINE_UUID
                ).apply {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                    this.headers = headers + (sd.drm?.headers ?: emptyMap())
                    this.licenseUrl = licenseUrl
                }
            } else {
                newExtractorLink(
                    source = this.name,
                    name   = if (serverNum == 1) ld.name else "${ld.name} S$serverNum",
                    url    = streamUrl,
                    type   = linkType
                ).apply {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
            }
            callback.invoke(link)
        }
        return true
    }
}

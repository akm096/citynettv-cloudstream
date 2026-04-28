package com.citynettv

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode

// ============== AUTH MODELS ==============

data class LoginRequest(
    @JsonProperty("login_type") val loginType: String = "Credentials",
    @JsonProperty("username") val username: String,
    @JsonProperty("password") val password: String,
    @JsonProperty("device") val device: String,
    @JsonProperty("device_class") val deviceClass: String = "MOBILE",
    @JsonProperty("device_type") val deviceType: String = "ANDROID",
    @JsonProperty("device_os") val deviceOs: String = "ANDROID"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginResponse(
    @JsonProperty("data") val data: LoginData? = null,
    // Fallbacks just in case API ever returns them at root
    @JsonProperty("access_token") val rootAccessToken: String? = null,
    @JsonProperty("refresh_token") val rootRefreshToken: String? = null,
    @JsonProperty("user") val rootUser: UserInfo? = null,
    @JsonProperty("error") val error: String? = null
) {
    fun resolveAccessToken(): String? = data?.accessToken ?: rootAccessToken
    fun resolveRefreshToken(): String? = data?.refreshToken ?: rootRefreshToken
    fun resolveUser(): UserInfo? = data?.user ?: rootUser
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginData(
    @JsonProperty("access_token") val accessToken: String? = null,
    @JsonProperty("refresh_token") val refreshToken: String? = null,
    @JsonProperty("user") val user: UserInfo? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Attachment(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("value") val value: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("src") val src: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("path") val path: String? = null,
    @JsonProperty("file") val file: String? = null
) {
    // Some APIs use `url` instead of `value` inside attachments
    val resolvedName: String? get() = name ?: key ?: type
    val resolvedValue: String? get() = value ?: url ?: src ?: link ?: path ?: file
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserInfo(
    @JsonProperty("uid") val uid: String? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("profile_id") val profileId: String? = null,
    @JsonProperty("profiles") val profiles: List<Profile>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Profile(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("name") val name: String? = null
)

data class RefreshRequest(
    @JsonProperty("refresh_token") val refreshToken: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RefreshResponse(
    @JsonProperty("data") val data: RefreshData? = null,
    @JsonProperty("access_token") val rootAccessToken: String? = null,
    @JsonProperty("refresh_token") val rootRefreshToken: String? = null
) {
    fun resolveAccessToken(): String? = data?.accessToken ?: rootAccessToken
    fun resolveRefreshToken(): String? = data?.refreshToken ?: rootRefreshToken
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RefreshData(
    @JsonProperty("access_token") val accessToken: String? = null,
    @JsonProperty("refresh_token") val refreshToken: String? = null
)

// ============== CHANNEL MODELS ==============

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChannelsResponse(
    @JsonProperty("data") val data: List<ChannelData>? = null,
    @JsonProperty("channels") val channels: List<ChannelData>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChannelData(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("uid") val uid: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("logo") val logo: String? = null,
    @JsonProperty("logo_url") val logoUrl: String? = null,
    @JsonProperty("logo_id") val logoId: String? = null,
    @JsonProperty("logo_image_id") val logoImageId: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("image_url") val imageUrl: String? = null,
    @JsonProperty("image_id") val imageId: String? = null,
    @JsonProperty("big_image_id") val bigImageId: String? = null,
    @JsonProperty("card_image_id") val cardImageId: String? = null,
    @JsonProperty("banner_image_id") val bannerImageId: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    @JsonProperty("poster_id") val posterId: String? = null,
    @JsonProperty("poster_image_id") val posterImageId: String? = null,
    @JsonProperty("thumbnail_url") val thumbnailUrl: String? = null,
    @JsonProperty("icon_id") val iconId: String? = null,
    @JsonProperty("thumbnail_id") val thumbnailId: String? = null,
    @JsonProperty("categories") val categories: List<String>? = null,
    @JsonProperty("category") val category: String? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("genres") val genres: List<String>? = null,
    @JsonProperty("country") val country: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("packages") val packages: List<String>? = null,
    @JsonProperty("number") val number: Int? = null,
    @JsonProperty("is_hd") val isHd: Boolean? = null,
    @JsonProperty("attachments") val attachments: JsonNode? = null,
    @JsonProperty("images") val images: JsonNode? = null,
    @JsonProperty("files") val files: JsonNode? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("stream_url") val streamUrl: String? = null,
    @JsonProperty("manifest_url") val manifestUrl: String? = null,
    @JsonProperty("manifest") val manifest: String? = null,
    @JsonProperty("hls_url") val hlsUrl: String? = null,
    @JsonProperty("hls") val hls: String? = null,
    @JsonProperty("dash_url") val dashUrl: String? = null,
    @JsonProperty("dash") val dash: String? = null,
    @JsonProperty("mpd") val mpd: String? = null,
    @JsonProperty("m3u8") val m3u8: String? = null,
    @JsonProperty("uri") val uri: String? = null,
    @JsonProperty("drm") val drm: DrmInfo? = null,
    @JsonProperty("lat") val lat: String? = null,
    @JsonProperty("jwt") val jwt: String? = null,
    @JsonProperty("server") val server: String? = null,
    @JsonProperty("stream") val stream: JsonNode? = null,
    @JsonProperty("playback") val playback: JsonNode? = null,
    @JsonProperty("source") val source: JsonNode? = null,
    @JsonProperty("media") val media: JsonNode? = null
) {
    fun getDisplayName(): String = name ?: title ?: slug ?: uid ?: "Unknown"

    fun resolveLogoUrl(): String? {
        val attachedLogo = findMediaUrlInNodes(listOf(attachments, images, files))
        return normalizeMediaUrl(
            logo ?: logoUrl ?: logoId ?: image ?: imageUrl ?: imageId ?: poster ?: posterUrl ?: posterId
                ?: logoImageId ?: bigImageId ?: cardImageId ?: bannerImageId ?: posterImageId
                ?: thumbnailUrl ?: iconId ?: thumbnailId ?: attachedLogo
        )
    }

    fun resolveStreamData(): StreamData? {
        val nested = findStreamDataInNodes(listOf(stream, playback, source, media))
        if (nested != null) return nested

        val stream = streamUrl ?: manifestUrl ?: manifest ?: hlsUrl ?: hls ?: dashUrl ?: dash ?: mpd ?: m3u8 ?: uri
            ?: url?.takeIf { looksLikeStreamUrl(it) }
        if (stream.isNullOrBlank()) return null
        return StreamData(
            url = stream,
            lat = lat,
            jwt = jwt,
            drm = drm,
            server = server
        )
    }

    fun resolveCategory(): String {
        // Return first category/genre found
        if (!categories.isNullOrEmpty()) return categories.first()
        if (!genres.isNullOrEmpty()) return genres.first()
        if (!category.isNullOrEmpty()) return category!!
        if (!genre.isNullOrEmpty()) return genre!!
        return guessCategory()
    }

    /**
     * Kanal adına görə kateqoriyanı təxmin edir
     */
    private fun guessCategory(): String {
        val lowerName = getDisplayName().lowercase()
        return when {
            // Ölkələrə görə
            isAzerbaijaniChannel(lowerName) -> "Azərbaycan"
            isTurkishChannel(lowerName) -> "Türkiyə"
            isRussianChannel(lowerName) -> "Rusiya"
            // Janrlara görə
            isNewsChannel(lowerName) -> "Xəbərlər"
            isSportsChannel(lowerName) -> "İdman"
            isMovieChannel(lowerName) -> "Kino"
            isKidsChannel(lowerName) -> "Uşaq"
            isMusicChannel(lowerName) -> "Musiqi"
            isEntertainmentChannel(lowerName) -> "Əyləncə"
            isDocumentaryChannel(lowerName) -> "Sənədli"
            else -> "Digər"
        }
    }

    fun getCountryCategory(): String {
        val lowerName = getDisplayName().lowercase()
        val lang = language?.lowercase() ?: ""
        val ctry = country?.lowercase() ?: ""

        return when {
            ctry.contains("az") || lang.contains("az") || isAzerbaijaniChannel(lowerName) -> "🇦🇿 Azərbaycan"
            ctry.contains("tr") || lang.contains("tr") || isTurkishChannel(lowerName) -> "🇹🇷 Türkiyə"
            ctry.contains("ru") || lang.contains("ru") || isRussianChannel(lowerName) -> "🇷🇺 Rusiya"
            else -> "🌍 Digər"
        }
    }

    fun getGenreCategory(): String {
        val lowerName = getDisplayName().lowercase()

        // Check categories/genres from API first
        val apiCategory = (categories?.firstOrNull() ?: genres?.firstOrNull() ?: category ?: genre ?: "")
            .lowercase()

        return when {
            apiCategory.contains("news") || apiCategory.contains("xəbər") || apiCategory.contains("haber") || isNewsChannel(lowerName) -> "📰 Xəbərlər"
            apiCategory.contains("sport") || apiCategory.contains("idman") || isSportsChannel(lowerName) -> "⚽ İdman"
            apiCategory.contains("movie") || apiCategory.contains("film") || apiCategory.contains("kino") || isMovieChannel(lowerName) -> "🎬 Kino"
            apiCategory.contains("kids") || apiCategory.contains("child") || apiCategory.contains("uşaq") || isKidsChannel(lowerName) -> "👶 Uşaq"
            apiCategory.contains("music") || apiCategory.contains("musiqi") || apiCategory.contains("müzik") || isMusicChannel(lowerName) -> "🎵 Musiqi"
            apiCategory.contains("entertainment") || apiCategory.contains("əyləncə") || isEntertainmentChannel(lowerName) -> "🎭 Əyləncə"
            apiCategory.contains("documentary") || apiCategory.contains("sənədli") || isDocumentaryChannel(lowerName) -> "📚 Sənədli"
            else -> "📺 Digər"
        }
    }

    companion object {
        private val logoKeys = setOf("logo", "icon", "image", "poster", "thumbnail", "thumb")
        private val streamKeys = setOf("stream_url", "manifest_url", "manifest", "hls_url", "hls", "dash_url", "dash", "mpd", "m3u8", "file", "src", "uri", "url")
        private val licenseKeys = setOf("license_url", "widevine_license_url", "licenseUrl", "license")

        private fun normalizeMediaUrl(raw: String?): String? {
            val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return when {
                value.startsWith("http://") || value.startsWith("https://") -> value
                value.startsWith("//") -> "https:$value"
                value.startsWith("/") -> "https://tv.citynettv.az$value"
                value.contains("/") -> "https://tv.citynettv.az/$value"
                else -> "https://tvapi.citynettv.az:11610/api/client/v1/global/images/$value?accessKey=WkVjNWNscFhORDBLCg=="
            }
        }

        private fun findMediaUrlInNodes(nodes: List<JsonNode?>): String? {
            nodes.forEach { node ->
                val preferred = findTextValue(node, logoKeys) { _, value -> looksLikeMediaPath(value) }
                if (!preferred.isNullOrBlank()) return preferred
            }
            nodes.forEach { node ->
                val anyMedia = findTextValue(node, emptySet()) { _, value -> looksLikeMediaPath(value) }
                if (!anyMedia.isNullOrBlank()) return anyMedia
            }
            return null
        }

        private fun looksLikeMediaPath(value: String): Boolean {
            val lower = value.lowercase()
            return (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/") || value.contains("/")) &&
                listOf(".png", ".jpg", ".jpeg", ".webp", ".svg", "logo", "icon", "poster", "image").any { lower.contains(it) }
        }

        private fun findStreamDataInNodes(nodes: List<JsonNode?>): StreamData? {
            nodes.forEach { node ->
                if (node?.isTextual == true) {
                    val value = node.asText()
                    if (looksLikeStreamUrl(value)) return StreamData(url = value)
                }
                val streamUrl = findTextValue(node, streamKeys) { key, value ->
                    looksLikeStreamUrl(value) &&
                        !key.contains("license", ignoreCase = true) &&
                        !key.contains("logo", ignoreCase = true) &&
                        !key.contains("image", ignoreCase = true)
                }
                if (!streamUrl.isNullOrBlank()) {
                    val licenseUrl = findTextValue(node, licenseKeys) { _, value -> value.startsWith("http", ignoreCase = true) }
                    val lat = findTextValue(node, setOf("lat"))
                    val jwt = findTextValue(node, setOf("jwt", "token"))
                    val server = findTextValue(node, setOf("server"))
                    return StreamData(
                        url = streamUrl,
                        lat = lat,
                        jwt = jwt,
                        server = server,
                        drm = if (licenseUrl.isNullOrBlank()) null else DrmInfo(licenseUrl = licenseUrl)
                    )
                }
            }
            return null
        }

        private fun looksLikeStreamUrl(value: String): Boolean {
            val lower = value.lowercase()
            return value.startsWith("http", ignoreCase = true) &&
                listOf(".m3u8", ".mpd", "/hls", "/dash", "/live", "/stream", "manifest", "playlist").any { lower.contains(it) }
        }

        private fun findTextValue(
            node: JsonNode?,
            keys: Set<String>,
            predicate: (key: String, value: String) -> Boolean = { _, value -> value.isNotBlank() }
        ): String? {
            if (node == null) return null
            if (node.isTextual && keys.isEmpty()) {
                val value = node.asText()
                if (predicate("", value)) return value
            }
            if (node.isObject) {
                val fields = node.fields()
                while (fields.hasNext()) {
                    val (key, valueNode) = fields.next()
                    if ((keys.isEmpty() || keys.any { it.equals(key, ignoreCase = true) }) && valueNode.isTextual) {
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

        private fun isAzerbaijaniChannel(name: String): Boolean {
            val azKeywords = listOf(
                "az tv", "aztv", "idman", "medeniyyet", "space", "cbc", "xezer",
                "arb", "real tv", "ictimai", "region", "az24", "baku",
                "dalga", "gunaz", "anews az", "muz tv az", "kanal s",
                "atv az", "utv", "canli", "azeri", "naxcivan", "ntv az",
                "apa tv", "mctv", "aztruck", "azad az"
            )
            return azKeywords.any { name.contains(it) }
        }

        private fun isTurkishChannel(name: String): Boolean {
            val trKeywords = listOf(
                "trt", "star tv", "show tv", "kanal d", "atv", "fox tv",
                "tv8", "360", "now tv", "teve2", "beyaz tv", "haber",
                "cnn turk", "cnn türk", "ntv tr", "a haber", "tgrt",
                "tv24", "ulke", "ülke", "dizi", "blu tv", "digiturk",
                "show max", "salon", "turk", "türk", "haberturk",
                "kanal 7", "bloomberg ht", "habertürk"
            )
            return trKeywords.any { name.contains(it) }
        }

        private fun isRussianChannel(name: String): Boolean {
            val ruKeywords = listOf(
                "первый", "россия", "нтв", "тнт", "стс", "рен тв",
                "пятница", "звезда", "матч", "мир", "домашний", "ю тв",
                "russia", "rtvi", "perviy", "mir ", "ctc", "tnt",
                "ren tv", "pyatnica", "pyatnitsa", "match", "tv3 ru",
                "friday", "рбк", "rain", "дождь", "че", "муз тв",
                "карусель", "моя планета"
            )
            return ruKeywords.any { name.contains(it) }
        }

        private fun isNewsChannel(name: String): Boolean {
            val newsKeywords = listOf(
                "news", "xəbər", "xeber", "haber", "cnn", "bbc",
                "euronews", "al jazeera", "france 24", "dw",
                "sky news", "новост", "вести", "ntv"
            )
            return newsKeywords.any { name.contains(it) }
        }

        private fun isSportsChannel(name: String): Boolean {
            val sportsKeywords = listOf(
                "sport", "idman", "setanta", "bein", "match", "матч",
                "eurosport", "fight", "football", "futbol"
            )
            return sportsKeywords.any { name.contains(it) }
        }

        private fun isMovieChannel(name: String): Boolean {
            val movieKeywords = listOf(
                "movie", "kino", "film", "cinema", "amedia", "filmbox",
                "hbo", "paramount", "sony", "universal", "кино"
            )
            return movieKeywords.any { name.contains(it) }
        }

        private fun isKidsChannel(name: String): Boolean {
            val kidsKeywords = listOf(
                "kids", "child", "uşaq", "cocuk", "çocuk", "cartoon",
                "nick", "disney", "jimjam", "baby", "карусель",
                "мультимания", "детский"
            )
            return kidsKeywords.any { name.contains(it) }
        }

        private fun isMusicChannel(name: String): Boolean {
            val musicKeywords = listOf(
                "music", "musiqi", "müzik", "muzik", "mtv", "vh1",
                "muz tv", "bridge", "тмтв"
            )
            return musicKeywords.any { name.contains(it) }
        }

        private fun isEntertainmentChannel(name: String): Boolean {
            val entertainmentKeywords = listOf(
                "entertainment", "əyləncə", "eglence", "show",
                "comedy", "тнт", "пятница", "стс", "friday"
            )
            return entertainmentKeywords.any { name.contains(it) }
        }

        private fun isDocumentaryChannel(name: String): Boolean {
            val docKeywords = listOf(
                "documentary", "sənədli", "belgesel", "discovery",
                "national geographic", "nat geo", "animal planet",
                "history", "viasat", "science", "travel",
                "познавательн", "моя планета"
            )
            return docKeywords.any { name.contains(it) }
        }
    }
}

// ============== STREAM MODELS ==============

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamResponse(
    @JsonProperty("data") val data: StreamData? = null,
    @JsonProperty("stream_url") val streamUrl: String? = null,
    @JsonProperty("url") val url: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamData(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("stream_url") val streamUrl: String? = null,
    @JsonProperty("manifest_url") val manifestUrl: String? = null,
    @JsonProperty("manifest") val manifest: String? = null,
    @JsonProperty("hls_url") val hlsUrl: String? = null,
    @JsonProperty("hls") val hls: String? = null,
    @JsonProperty("dash_url") val dashUrl: String? = null,
    @JsonProperty("dash") val dash: String? = null,
    @JsonProperty("mpd") val mpd: String? = null,
    @JsonProperty("m3u8") val m3u8: String? = null,
    @JsonProperty("uri") val uri: String? = null,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("src") val src: String? = null,
    @JsonProperty("lat") val lat: String? = null,
    @JsonProperty("jwt") val jwt: String? = null,
    @JsonProperty("drm") val drm: DrmInfo? = null,
    @JsonProperty("show_id") val showId: String? = null,
    @JsonProperty("server") val server: String? = null,
    @get:JsonIgnore val cencHls: Boolean = false
) {
    fun resolveStreamUrl(): String? =
        streamUrl ?: manifestUrl ?: manifest ?: hlsUrl ?: hls ?: dashUrl ?: dash ?: mpd ?: m3u8 ?: uri ?: file ?: src ?: url
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DrmInfo(
    @JsonProperty("license_url") val licenseUrl: String? = null,
    @JsonProperty("license") val license: String? = null,
    @JsonProperty("licenseUrl") val camelLicenseUrl: String? = null,
    @JsonProperty("widevine_license_url") val widevineLicenseUrl: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("headers") val headers: Map<String, String>? = null
) {
    fun resolveLicenseUrl(): String? = licenseUrl ?: camelLicenseUrl ?: widevineLicenseUrl ?: license
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamContainer(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("stream_url") val streamUrl: String? = null,
    @JsonProperty("manifest_url") val manifestUrl: String? = null,
    @JsonProperty("manifest") val manifest: String? = null,
    @JsonProperty("hls_url") val hlsUrl: String? = null,
    @JsonProperty("hls") val hls: String? = null,
    @JsonProperty("dash_url") val dashUrl: String? = null,
    @JsonProperty("dash") val dash: String? = null,
    @JsonProperty("mpd") val mpd: String? = null,
    @JsonProperty("m3u8") val m3u8: String? = null,
    @JsonProperty("uri") val uri: String? = null,
    @JsonProperty("drm") val drm: DrmInfo? = null,
    @JsonProperty("lat") val lat: String? = null,
    @JsonProperty("jwt") val jwt: String? = null,
    @JsonProperty("server") val server: String? = null
) {
    fun resolveStreamData(): StreamData? {
        val stream = streamUrl ?: manifestUrl ?: manifest ?: hlsUrl ?: hls ?: dashUrl ?: dash ?: mpd ?: m3u8 ?: uri ?: url
        if (stream.isNullOrBlank()) return null
        return StreamData(url = stream, lat = lat, jwt = jwt, drm = drm, server = server)
    }
}

// ============== EPG MODELS ==============

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpgResponse(
    @JsonProperty("data") val data: List<EpgItem>? = null,
    @JsonProperty("shows") val shows: List<EpgItem>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpgItem(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("start_time") val startTime: String? = null,
    @JsonProperty("end_time") val endTime: String? = null,
    @JsonProperty("start") val start: String? = null,
    @JsonProperty("end") val end: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("channel_slug") val channelSlug: String? = null,
    @JsonProperty("show_id") val showId: String? = null
) {
    fun getDisplayName(): String = title ?: name ?: "Proqram"
    fun resolveStartTime(): String? = startTime ?: start
    fun resolveEndTime(): String? = endTime ?: end
}

// ============== INTERNAL MODELS ==============

/**
 * loadLinks-ə ötürülən data modeli
 * Slug + aktiv show ID + kanal adını saxlayır
 */
data class ChannelLoadData(
    @JsonProperty("slug") val slug: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("uid") val uid: String? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    @JsonProperty("showId") val showId: String? = null,
    @JsonProperty("stream") val stream: StreamData? = null
)

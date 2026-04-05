package com.citynettv

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

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
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("logo") val logo: String? = null,
    @JsonProperty("logo_url") val logoUrl: String? = null,
    @JsonProperty("image") val image: String? = null,
    @JsonProperty("image_url") val imageUrl: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    @JsonProperty("categories") val categories: List<String>? = null,
    @JsonProperty("category") val category: String? = null,
    @JsonProperty("genre") val genre: String? = null,
    @JsonProperty("genres") val genres: List<String>? = null,
    @JsonProperty("country") val country: String? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("packages") val packages: List<String>? = null,
    @JsonProperty("number") val number: Int? = null,
    @JsonProperty("is_hd") val isHd: Boolean? = null
) {
    fun getDisplayName(): String = name ?: title ?: slug ?: "Unknown"

    fun resolveLogoUrl(): String? = logo ?: logoUrl ?: image ?: imageUrl ?: poster ?: posterUrl

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
    @JsonProperty("lat") val lat: String? = null,
    @JsonProperty("jwt") val jwt: String? = null,
    @JsonProperty("drm") val drm: DrmInfo? = null,
    @JsonProperty("show_id") val showId: String? = null,
    @JsonProperty("server") val server: String? = null
) {
    fun resolveStreamUrl(): String? = url ?: streamUrl ?: manifestUrl
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DrmInfo(
    @JsonProperty("license_url") val licenseUrl: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("headers") val headers: Map<String, String>? = null
)

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
    @JsonProperty("showId") val showId: String? = null
)

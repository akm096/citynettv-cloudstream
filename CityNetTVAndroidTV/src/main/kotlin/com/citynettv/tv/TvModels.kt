package com.citynettv.tv

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvLoginRequest(
    @JsonProperty("login_type") val loginType: String = "Credentials",
    @JsonProperty("username") val username: String,
    @JsonProperty("password") val password: String,
    @JsonProperty("device") val device: String,
    @JsonProperty("device_class") val deviceClass: String,
    @JsonProperty("device_type") val deviceType: String,
    @JsonProperty("device_os") val deviceOs: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvLoginResponse(
    @JsonProperty("data") val data: TvLoginData? = null,
    @JsonProperty("access_token") val rootAccessToken: String? = null,
    @JsonProperty("refresh_token") val rootRefreshToken: String? = null,
    @JsonProperty("user") val rootUser: TvUserInfo? = null,
    @JsonProperty("error") val error: String? = null
) {
    fun accessToken(): String? = data?.accessToken ?: rootAccessToken
    fun refreshToken(): String? = data?.refreshToken ?: rootRefreshToken
    fun user(): TvUserInfo? = data?.user ?: rootUser
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvLoginData(
    @JsonProperty("access_token") val accessToken: String? = null,
    @JsonProperty("refresh_token") val refreshToken: String? = null,
    @JsonProperty("user") val user: TvUserInfo? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvUserInfo(
    @JsonProperty("uid") val uid: String? = null,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("profile_id") val profileId: String? = null,
    @JsonProperty("profiles") val profiles: List<TvProfile>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvProfile(@JsonProperty("id") val id: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvChannelsResponse(
    @JsonProperty("data") val data: List<TvChannel>? = null,
    @JsonProperty("channels") val channels: List<TvChannel>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvChannel(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("uid") val uid: String? = null,
    @JsonProperty("slug") val slug: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("number") val number: Int? = null,
    @JsonProperty("stream_url") val streamUrl: String? = null,
    @JsonProperty("manifest_url") val manifestUrl: String? = null,
    @JsonProperty("url") val url: String? = null
) {
    fun key(): String = uid ?: slug ?: id ?: ""
    fun titleText(): String = name ?: title ?: slug ?: uid ?: "Channel"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvStreamResponse(
    @JsonProperty("data") val data: TvStream? = null,
    @JsonProperty("stream_url") val streamUrl: String? = null,
    @JsonProperty("url") val url: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvStream(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("stream_url") val streamUrl: String? = null,
    @JsonProperty("manifest_url") val manifestUrl: String? = null,
    @JsonProperty("hls_url") val hlsUrl: String? = null,
    @JsonProperty("dash_url") val dashUrl: String? = null,
    @JsonProperty("mpd") val mpd: String? = null,
    @JsonProperty("m3u8") val m3u8: String? = null,
    @JsonProperty("lat") val lat: String? = null,
    @JsonProperty("jwt") val jwt: String? = null,
    @JsonProperty("server") val server: String? = null,
    @JsonProperty("drm") val drm: TvDrm? = null
) {
    fun streamUrl(): String? = streamUrl ?: manifestUrl ?: hlsUrl ?: dashUrl ?: mpd ?: m3u8 ?: url
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TvDrm(
    @JsonProperty("license_url") val licenseUrl: String? = null,
    @JsonProperty("widevine_license_url") val widevineLicenseUrl: String? = null,
    @JsonProperty("licenseUrl") val camelLicenseUrl: String? = null,
    @JsonProperty("license") val license: String? = null
) {
    fun licenseUrl(): String? = licenseUrl ?: widevineLicenseUrl ?: camelLicenseUrl ?: license
}

data class PlaybackItem(
    val name: String,
    val url: String,
    val headers: Map<String, String>,
    val licenseUrl: String? = null
)

data class TvDiagnostic(val method: String, val endpoint: String, val code: Int, val profile: String) {
    override fun toString(): String = "$method $endpoint: $code [$profile]"
}

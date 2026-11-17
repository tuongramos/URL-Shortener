package vn.lienket.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateLinkRequest(
    @SerialName("long_url") val longUrl: String,
    @SerialName("redirect_status") val redirectStatus: Int = 302,
)

@Serializable
data class ShortLink(
    val id: Long,
    @SerialName("short_code") val shortCode: String,
    @SerialName("short_url") val shortUrl: String,
    @SerialName("long_url") val longUrl: String,
    @SerialName("redirect_status") val redirectStatus: Int,
    @SerialName("created_at") val createdAt: String,
    val reused: Boolean,
)

@Serializable
data class DemoStats(
    @SerialName("redirect_requests") val redirectRequests: Long = 0,
    @SerialName("cache_hits") val cacheHits: Long = 0,
    @SerialName("cache_misses") val cacheMisses: Long = 0,
    @SerialName("db_lookup_count") val dbLookupCount: Long = 0,
    @SerialName("not_found") val notFound: Long = 0,
    @SerialName("cache_size") val cacheSize: Int = 0,
    @SerialName("hit_rate") val hitRate: Double = 0.0,
)

@Serializable
data class ApiMessage(val detail: String)

@Serializable
data class HealthResponse(val status: String = "ok")

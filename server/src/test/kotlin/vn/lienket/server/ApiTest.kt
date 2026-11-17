package vn.lienket.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import vn.lienket.shared.*
import java.nio.file.Files
import kotlin.test.*

class ApiTest {
    private fun withServer(demo: Boolean = true, body: suspend ApplicationTestBuilder.() -> Unit) {
        val directory = Files.createTempDirectory("lienket-api-")
        try {
            testApplication {
                application { shortenerModule(Settings(databasePath = directory.resolve("test.db"), demoMode = demo)) }
                body()
            }
        } finally { directory.toFile().deleteRecursively() }
    }
    private fun ApplicationTestBuilder.apiClient() = createClient {
        followRedirects = false
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
    }
    private suspend fun HttpClient.create(url: String = "https://example.com/a%2Fb?chapter=8#part", mode: Int = 302) = post("/api/v1/links") {
        contentType(ContentType.Application.Json); setBody(CreateLinkRequest(url, mode))
    }
    @Test fun healthAndDocs() = withServer {
        val http = apiClient()
        assertEquals(HealthResponse(), http.get("/api/v1/health").body())
        assertEquals(HttpStatusCode.OK, http.get("/docs").status)
    }
    @Test fun createsAndReusesPreservingDestination() = withServer {
        val http = apiClient()
        val first = http.create()
        assertEquals(HttpStatusCode.Created, first.status)
        val link = first.body<ShortLink>()
        assertFalse(link.reused)
        assertEquals("http://127.0.0.1:8000/r/1", link.shortUrl)
        val second = http.create()
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(link.copy(reused = true), second.body())
        val redirect = http.get("/r/${link.shortCode}")
        assertEquals(HttpStatusCode.Found, redirect.status)
        assertEquals(link.longUrl, redirect.headers["Location"])
    }
    @Test fun modesHaveSeparateCodesAndCacheHeaders() = withServer {
        val http = apiClient()
        val temporary = http.create(mode = 302).body<ShortLink>()
        val permanent = http.create(mode = 301).body<ShortLink>()
        assertNotEquals(temporary.shortCode, permanent.shortCode)
        assertEquals("no-store", http.get("/r/${temporary.shortCode}").headers["Cache-Control"])
        val response = http.get("/r/${permanent.shortCode}")
        assertEquals(HttpStatusCode.MovedPermanently, response.status)
        assertEquals("private, max-age=300", response.headers["Cache-Control"])
    }
    @Test fun rejectsBadInputsAndJsonTypes() = withServer {
        val http = apiClient()
        listOf("", "bad", "ftp://example.com", "https://example.com/a b", "https://example.com/" + "x".repeat(4096)).forEach {
            assertEquals(HttpStatusCode.UnprocessableEntity, http.create(it).status, it)
        }
        listOf("{}", "{\"long_url\":42}", "{\"long_url\":\"https://example.com\",\"redirect_status\":301.0}", "{\"long_url\":\"https://example.com\",\"redirect_status\":\"301\"}", "{\"long_url\":\"https://example.com\",\"extra\":1}", "null", "{bad").forEach {
            assertEquals(HttpStatusCode.UnprocessableEntity, http.post("/api/v1/links") { contentType(ContentType.Application.Json); setBody(it) }.status, it)
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, http.create(mode = 307).status)
    }
    @Test fun cacheMissHitAndMetadataDoNotCount() = withServer {
        val http = apiClient()
        val link = http.create().body<ShortLink>()
        assertEquals(0, http.get("/api/v1/demo/stats").body<DemoStats>().cacheSize)
        http.get("/api/v1/links/${link.shortCode}")
        assertEquals(0L, http.get("/api/v1/demo/stats").body<DemoStats>().redirectRequests)
        assertEquals("MISS", http.get("/r/${link.shortCode}").headers["X-Cache"])
        assertEquals("HIT", http.get("/r/${link.shortCode}").headers["X-Cache"])
        val stats = http.get("/api/v1/demo/stats").body<DemoStats>()
        assertEquals(DemoStats(2, 1, 1, 1, 0, 1, 0.5), stats)
        assertEquals(stats, http.get("/api/v1/demo/stats").body())
        http.post("/api/v1/demo/stats/reset")
        assertEquals("HIT", http.get("/r/${link.shortCode}").headers["X-Cache"])
        http.post("/api/v1/demo/cache/clear")
        assertEquals("MISS", http.get("/r/${link.shortCode}").headers["X-Cache"])
    }
    @Test fun missingCodeReturns404AndCounts() = withServer {
        val http = apiClient()
        val response = http.get("/r/missing-code")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ApiMessage("Liên kết không tồn tại"), response.body())
        assertEquals(1L, http.get("/api/v1/demo/stats").body<DemoStats>().notFound)
        http.get("/api/v1/links/missing-code")
        assertEquals(1L, http.get("/api/v1/demo/stats").body<DemoStats>().redirectRequests)
    }
    @Test fun demoOffDoesNotDisableShortening() = withServer(demo = false) {
        val http = apiClient()
        assertEquals(HttpStatusCode.NotFound, http.get("/api/v1/demo/stats").status)
        assertEquals(HttpStatusCode.NotFound, http.post("/api/v1/demo/cache/clear").status)
        assertEquals(HttpStatusCode.NotFound, http.post("/api/v1/demo/stats/reset").status)
        assertEquals(HttpStatusCode.Created, http.create().status)
    }
    @Test fun restartKeepsSqliteAndResetsRam() {
        val directory = Files.createTempDirectory("lienket-restart-")
        try {
            val settings = Settings(databasePath = directory.resolve("test.db"), demoMode = true)
            val first = LinkService(settings)
            first.database.initialize()
            val link = first.create(CreateLinkRequest("https://example.com/restart"))
            first.cache.resolve(link.shortCode, first.database::find)
            val second = LinkService(settings)
            second.database.initialize()
            assertEquals(0L, second.cache.stats().redirectRequests)
            assertEquals(0, second.cache.stats().cacheSize)
            assertEquals(link.longUrl, second.cache.resolve(link.shortCode, second.database::find).first?.longUrl)
            assertEquals(link.id, second.create(CreateLinkRequest(link.longUrl)).id)
        } finally { directory.toFile().deleteRecursively() }
    }
    @Test fun codesRemainCaseSensitive() = withServer {
        val http = apiClient()
        for (id in 1..36) http.create("https://example.com/$id")
        assertEquals("https://example.com/10", http.get("/r/a").headers["Location"])
        assertEquals("https://example.com/36", http.get("/r/A").headers["Location"])
    }
}

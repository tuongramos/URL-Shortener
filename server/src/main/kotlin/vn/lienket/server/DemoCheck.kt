package vn.lienket.server

import kotlinx.serialization.json.Json
import vn.lienket.shared.CreateLinkRequest
import vn.lienket.shared.DemoStats
import vn.lienket.shared.ShortLink
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun demoCheckMain() {
    val base = System.getenv("DEMO_BASE_URL") ?: "http://127.0.0.1:8000"
    val target = "$base/demo/target?chapter=8&source=android#chapter-8"
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build()
    fun request(method: String, path: String, body: String = ""): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI(base + path)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
            .method(method, if (body.isEmpty()) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString())
    check(request("GET", "/api/v1/health").statusCode() == 200)
    val body = json.encodeToString(CreateLinkRequest(target))
    val created = request("POST", "/api/v1/links", body)
    check(created.statusCode() in setOf(200, 201))
    val link = json.decodeFromString<ShortLink>(created.body())
    val reused = request("POST", "/api/v1/links", body)
    check(reused.statusCode() == 200 && json.decodeFromString<ShortLink>(reused.body()).reused)
    check(request("POST", "/api/v1/demo/cache/clear").statusCode() == 200) { "Cần bật DEMO_MODE=true" }
    request("POST", "/api/v1/demo/stats/reset")
    for (expected in listOf("MISS", "HIT")) {
        val response = request("GET", "/r/${link.shortCode}")
        check(response.statusCode() == 302 && response.headers().firstValue("Location").orElse("") == target)
        check(response.headers().firstValue("X-Cache").orElse("") == expected)
        println("302 | X-Cache: $expected | Location: $target")
    }
    request("GET", "/api/v1/links/${link.shortCode}")
    val stats = json.decodeFromString<DemoStats>(request("GET", "/api/v1/demo/stats").body())
    check(stats == DemoStats(2, 1, 1, 1, 0, 1, 0.5)) { "Bộ đếm bị thay đổi bởi request khác: $stats" }
    println(json.encodeToString(stats))
    val permanent = json.decodeFromString<ShortLink>(request("POST", "/api/v1/links", json.encodeToString(CreateLinkRequest(target, 301))).body())
    check(permanent.shortCode != link.shortCode)
    check(request("GET", "/r/${permanent.shortCode}").statusCode() == 301)
    check(request("GET", "/r/missing-code").statusCode() == 404)
    check(request("POST", "/api/v1/links", json.encodeToString(CreateLinkRequest("ftp://example.com"))).statusCode() == 422)
    val follow = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().send(
        HttpRequest.newBuilder(URI(link.shortUrl)).GET().build(), HttpResponse.BodyHandlers.ofString())
    check(follow.statusCode() == 200 && "Chuyển hướng thành công" in follow.body())
    println("PASS: tạo/tái sử dụng, 301/302, MISS/HIT, bộ đếm, lỗi và chuyển hướng tới trang đích thật.")
}

object DemoCheck { @JvmStatic fun main(args: Array<String>) = demoCheckMain() }

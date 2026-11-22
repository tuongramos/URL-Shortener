package vn.lienket.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import vn.lienket.shared.ApiMessage
import vn.lienket.shared.CreateLinkRequest
import vn.lienket.shared.HealthResponse
import java.net.URI
import java.sql.SQLException

private val apiJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }

fun main() {
    val settings = Settings.fromEnvironment()
    embeddedServer(Netty, host = settings.host, port = settings.port) { shortenerModule(settings) }.start(wait = true)
}

fun Application.shortenerModule(settings: Settings, service: LinkService = LinkService(settings)) {
    val logger = environment.log
    service.database.initialize()
    install(ContentNegotiation) { json(apiJson) }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, _ ->
            call.respond(HttpStatusCode.UnprocessableEntity, ApiMessage("Dữ liệu chưa hợp lệ. Hãy nhập URL HTTP/HTTPS tối đa 4096 ký tự và chế độ 301 hoặc 302."))
        }
        exception<SQLException> { call, error ->
            logger.error("Không thể truy cập SQLite", error)
            call.respond(HttpStatusCode.ServiceUnavailable, ApiMessage("Máy chủ đang bận. Bạn hãy thử lại."))
        }
        exception<Throwable> { call, error ->
            if (error is CancellationException) throw error
            logger.error("Không thể xử lý yêu cầu", error)
            call.respond(HttpStatusCode.InternalServerError, ApiMessage("Có lỗi xảy ra. Bạn hãy thử lại."))
        }
    }
    routing {
        get("/") { call.respond(HealthResponse()) }
        get("/api/v1/health") {
            withContext(Dispatchers.IO) { service.database.healthy() }
            call.respond(HealthResponse())
        }
        // Nhận yêu cầu rút gọn URL từ app.
        post("/api/v1/links") {
            // Đọc nội dung gửi lên thành JSON.
            val element = apiJson.parseToJsonElement(call.receiveText())
            // JSON phải là một đối tượng và long_url phải là chuỗi.
            require(element is JsonObject)
            require((element["long_url"] as? JsonPrimitive)?.isString == true)
            // Nếu có redirect_status thì giá trị phải là số nguyên.
            element["redirect_status"]?.let { value ->
                require(value is JsonPrimitive && !value.isString && value.content.toIntOrNull() != null)
            }
            // Chuyển JSON thành dữ liệu đầu vào cho service.
            val request = apiJson.decodeFromJsonElement<CreateLinkRequest>(element)
            // Xử lý tạo link trên luồng dành cho đọc/ghi dữ liệu.
            val result = withContext(Dispatchers.IO) { service.create(request) }
            // Trả kết quả dạng JSON: 200 nếu dùng lại, 201 nếu tạo mới.
            call.respond(if (result.reused) HttpStatusCode.OK else HttpStatusCode.Created, result)
        }
        get("/api/v1/links/{code}") {
            val link = withContext(Dispatchers.IO) { service.database.find(call.parameters["code"].orEmpty()) }
            if (link == null) call.respond(HttpStatusCode.NotFound, ApiMessage("Liên kết không tồn tại"))
            else call.respond(service.response(link, true))
        }
        get("/r/{code}") {
            val (link, cacheStatus) = withContext(Dispatchers.IO) {
                service.cache.resolve(call.parameters["code"].orEmpty(), service.database::find)
            }
            call.response.header("X-Cache", cacheStatus)
            if (link == null) {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respond(HttpStatusCode.NotFound, ApiMessage("Liên kết không tồn tại"))
            } else {
                call.response.header(HttpHeaders.CacheControl, if (link.redirectStatus == 302) "no-store" else "private, max-age=300")
                call.response.header(HttpHeaders.Location, URI(link.longUrl).toASCIIString())
                call.respondText("", status = HttpStatusCode.fromValue(link.redirectStatus))
            }
        }
        get("/api/v1/demo/stats") {
            if (!settings.demoMode) call.respond(HttpStatusCode.NotFound, ApiMessage("Không tìm thấy trang"))
            else call.respond(service.cache.stats())
        }
        post("/api/v1/demo/cache/clear") {
            if (!settings.demoMode) call.respond(HttpStatusCode.NotFound, ApiMessage("Không tìm thấy trang"))
            else { service.cache.clear(); call.respond(service.cache.stats()) }
        }
        post("/api/v1/demo/stats/reset") {
            if (!settings.demoMode) call.respond(HttpStatusCode.NotFound, ApiMessage("Không tìm thấy trang"))
            else { service.cache.resetStats(); call.respond(service.cache.stats()) }
        }
        get("/demo/target") {
            call.respondText("""<!doctype html><html lang="vi"><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Trang đích demo</title><style>body{font:18px/1.7 sans-serif;background:#f4f6ef;color:#234b38;margin:0;min-height:100vh;display:grid;place-items:center}main{margin:24px;padding:32px;background:white;border:1px solid #dfe6d8;border-radius:20px;max-width:560px}h1{font-size:30px}</style><main id="chapter-8"><h1>Chuyển hướng thành công</h1><p>Bạn đã đến trang đích qua liên kết ngắn. Server Kotlin phục vụ trang này ngay trên máy tính, không cần Internet.</p></main></html>""", ContentType.Text.Html)
        }
        get("/docs") {
            call.respondText("""<!doctype html><html lang="vi"><meta charset="UTF-8"><title>API — Liên kết</title><style>body{font:16px/1.7 sans-serif;max-width:850px;margin:40px auto;padding:20px;color:#234b38;background:#f4f6ef}code,pre{overflow-wrap:anywhere;white-space:pre-wrap}</style><h1>URL Shortener — Kotlin / Ktor</h1><p>Giao diện chính là app Android native Jetpack Compose. Các API giữ hợp đồng của phiên bản trước.</p><pre>GET  /api/v1/health
POST /api/v1/links
GET  /api/v1/links/{code}
GET  /r/{code}
GET  /api/v1/demo/stats
POST /api/v1/demo/cache/clear
POST /api/v1/demo/stats/reset</pre><h2>Request tạo liên kết</h2><pre>{"long_url":"${settings.publicBaseUrl}/demo/target?chapter=8#chapter-8","redirect_status":302}</pre><p>201: mới; 200: dùng lại; 422: dữ liệu sai; 404: không có mã. Redirect trả Location và X-Cache.</p><p>Chạy <code>.\gradlew.bat :server:demoCheck</code> để minh họa 301/302, MISS/HIT và chuyển hướng thật. Chỉ bật DEMO_MODE tại máy demo.</p><a href="/api/v1/demo/stats">Xem thống kê</a> · <a href="/demo/target">Trang đích ngoại tuyến</a></html>""", ContentType.Text.Html)
        }
    }
}

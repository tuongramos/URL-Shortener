package vn.lienket.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import vn.lienket.shared.CreateLinkRequest
import vn.lienket.shared.ShortLink
import vn.lienket.shared.UrlValidator
import java.io.Closeable
import java.net.URI

class ApiException(val status: Int, message: String) : Exception(message)

interface ShortenerRepository {
    suspend fun create(serverUrl: String, longUrl: String): ShortLink
}

object ServerAddress {
    fun normalize(raw: String): String {
        val value = UrlValidator.validate(raw).trimEnd('/')
        val uri = URI(value)
        require(uri.rawPath.orEmpty().isEmpty() && uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null) {
            "Hãy nhập địa chỉ máy chủ HTTP/HTTPS, không kèm đường dẫn."
        }
        return value
    }
}

class HttpShortenerRepository : ShortenerRepository, Closeable {
    private val client = HttpClient(OkHttp) {
        followRedirects = false
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 10_000 }
    }

    override suspend fun create(serverUrl: String, longUrl: String): ShortLink {
        try {
            // Gửi yêu cầu tạo link đến API của server.
            val response = client.post("${ServerAddress.normalize(serverUrl)}/api/v1/links") {
                // Dữ liệu gửi đi có dạng JSON.
                contentType(ContentType.Application.Json)
                // Gửi URL gốc; mặc định dùng chuyển hướng 302.
                setBody(CreateLinkRequest(longUrl))
            }
            // 200: dùng lại link cũ; 201: tạo link mới. Mã khác là lỗi.
            if (response.status.value !in setOf(200, 201)) {
                if (response.status.value == 422) throw ApiException(422, "Liên kết chưa hợp lệ. Hãy kiểm tra địa chỉ HTTP/HTTPS và thử lại.")
                throw ApiException(response.status.value, "Máy chủ chưa xử lý được yêu cầu. Bạn hãy thử lại.")
            }
            // Đọc JSON thành ShortLink để trả cho ViewModel.
            return response.body<ShortLink>().also {
                // Kiểm tra URL ngắn và kiểu chuyển hướng server trả về.
                UrlValidator.validate(it.shortUrl)
                require(it.redirectStatus in setOf(301, 302))
            }
        } catch (error: CancellationException) { throw error }
        catch (error: ApiException) { throw error }
        // Đổi các lỗi còn lại thành thông báo cho người dùng.
        catch (_: Exception) { throw ApiException(0, "Chưa kết nối được máy chủ. Bạn hãy thử lại.") }
    }

    override fun close() { client.close() }
}

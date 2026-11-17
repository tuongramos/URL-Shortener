package vn.lienket.server

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

data class Settings(
    val publicBaseUrl: String = "http://127.0.0.1:8000",
    val databasePath: Path = Path.of("server/data/shortener.db").toAbsolutePath(),
    val cacheMaxItems: Int = 1000,
    val cacheTtlSeconds: Double = 300.0,
    val demoMode: Boolean = false,
    val host: String = "127.0.0.1",
    val port: Int = 8000,
) {
    init {
        val uri = URI(publicBaseUrl)
        require(uri.scheme in setOf("http", "https") && uri.host != null && uri.port in -1..65535 &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.orEmpty().isEmpty()) {
            "PUBLIC_BASE_URL phải là origin HTTP/HTTPS, không có path, query hoặc fragment"
        }
        require(cacheMaxItems > 0 && cacheTtlSeconds.isFinite() && cacheTtlSeconds > 0)
        require(port in 1..65535)
    }

    companion object {
        fun fromEnvironment(root: Path = projectRoot(), environment: Map<String, String> = System.getenv()): Settings {
            val envFile = root.resolve(".env")
            val fileValues = if (envFile.exists()) envFile.readLines().mapNotNull { line ->
                val text = line.trim()
                if (text.isEmpty() || text.startsWith('#') || '=' !in text) null
                else text.substringBefore('=').trim() to text.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
            }.toMap() else emptyMap()
            val values = fileValues + environment
            val demo = values.getOrDefault("DEMO_MODE", "false").lowercase()
            require(demo in setOf("true", "false", "1", "0")) { "DEMO_MODE chỉ nhận true/false hoặc 1/0" }
            return Settings(
                publicBaseUrl = values.getOrDefault("PUBLIC_BASE_URL", "http://127.0.0.1:8000").trimEnd('/'),
                databasePath = root.resolve(values.getOrDefault("DATABASE_PATH", "server/data/shortener.db")).normalize().toAbsolutePath(),
                cacheMaxItems = values.getOrDefault("CACHE_MAX_ITEMS", "1000").toInt(),
                cacheTtlSeconds = values.getOrDefault("CACHE_TTL_SECONDS", "300").toDouble(),
                demoMode = demo in setOf("true", "1"),
                host = values.getOrDefault("SERVER_HOST", "127.0.0.1"),
                port = values.getOrDefault("SERVER_PORT", "8000").toInt(),
            )
        }

        fun projectRoot(): Path {
            var path = Path.of(System.getProperty("user.dir")).toAbsolutePath()
            while (!path.resolve("settings.gradle.kts").exists() && path.parent != null) path = path.parent
            return if (path.resolve("settings.gradle.kts").exists()) path else Path.of(System.getProperty("user.dir"))
        }
    }
}

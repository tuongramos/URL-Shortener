package vn.lienket.shared

import java.net.IDN
import java.net.URI

object UrlValidator {
    const val MAX_LENGTH = 4096
    const val INVALID_MESSAGE = "Hãy nhập liên kết HTTP/HTTPS hợp lệ, tối đa 4096 ký tự."

    fun validate(raw: String): String {
        val value = raw.trim()
        require(value.isNotEmpty() && value.length <= MAX_LENGTH) { INVALID_MESSAGE }
        require(value.none { it.isWhitespace() || it.code <= 31 || it.code == 127 || it == '\\' }) { INVALID_MESSAGE }
        val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException(INVALID_MESSAGE) }
        require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.rawAuthority.isNullOrBlank()) { INVALID_MESSAGE }
        val address = try { uri.toURL() } catch (_: Exception) { throw IllegalArgumentException(INVALID_MESSAGE) }
        require(address.host.isNotBlank() && address.port in -1..65535) { INVALID_MESSAGE }
        if (address.host.startsWith("[")) {
            require(uri.host != null) { INVALID_MESSAGE }
        } else {
            try {
                require(IDN.toASCII(address.host, IDN.USE_STD3_ASCII_RULES).isNotBlank())
            } catch (_: Exception) { throw IllegalArgumentException(INVALID_MESSAGE) }
        }
        return value
    }
}

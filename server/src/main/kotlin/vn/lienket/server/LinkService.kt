package vn.lienket.server

import vn.lienket.shared.CreateLinkRequest
import vn.lienket.shared.ShortLink
import vn.lienket.shared.UrlValidator

class LinkService(val settings: Settings) {
    val database = Database(settings.databasePath)
    val cache = RedirectCache(settings.cacheMaxItems, settings.cacheTtlSeconds)

    fun create(request: CreateLinkRequest): ShortLink {
        // Chỉ nhận hai kiểu chuyển hướng 301 hoặc 302.
        require(request.redirectStatus in setOf(301, 302)) { "Chỉ hỗ trợ chuyển hướng 301 hoặc 302" }
        // Kiểm tra URL gốc trước khi lưu.
        val url = UrlValidator.validate(request.longUrl)
        // Lấy link đã có hoặc tạo bản ghi mới trong database.
        val creation = database.createOrGet(url, request.redirectStatus)
        // Tạo dữ liệu trả về cho app.
        return response(creation.link, creation.reused)
    }

    // Ghép địa chỉ server + /r/ + mã ngắn;
    fun response(link: Link, reused: Boolean) = ShortLink(link.id, link.shortCode,
        "${settings.publicBaseUrl}/r/${link.shortCode}", link.longUrl, link.redirectStatus, link.createdAt, reused)
}

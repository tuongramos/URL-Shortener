package vn.lienket.server

import vn.lienket.shared.CreateLinkRequest
import vn.lienket.shared.ShortLink
import vn.lienket.shared.UrlValidator

class LinkService(val settings: Settings) {
    val database = Database(settings.databasePath)
    val cache = RedirectCache(settings.cacheMaxItems, settings.cacheTtlSeconds)

    fun create(request: CreateLinkRequest): ShortLink {
        require(request.redirectStatus in setOf(301, 302)) { "Chỉ hỗ trợ chuyển hướng 301 hoặc 302" }
        val url = UrlValidator.validate(request.longUrl)
        val creation = database.createOrGet(url, request.redirectStatus)
        return response(creation.link, creation.reused)
    }

    fun response(link: Link, reused: Boolean) = ShortLink(link.id, link.shortCode,
        "${settings.publicBaseUrl}/r/${link.shortCode}", link.longUrl, link.redirectStatus, link.createdAt, reused)
}

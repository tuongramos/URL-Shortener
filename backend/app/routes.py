from fastapi import APIRouter, Depends, HTTPException, Request, Response
from fastapi.responses import RedirectResponse

from .schemas import CreateLink, DemoStats, LinkResponse
from .services import LinkService

router = APIRouter()


def service(request: Request) -> LinkService:
    return request.app.state.service


def demo_service(current: LinkService = Depends(service)) -> LinkService:
    if not current.settings.demo_mode:
        raise HTTPException(404, "Không tìm thấy trang")
    return current


@router.get("/api/v1/health", tags=["Hệ thống"])
def health(current: LinkService = Depends(service)) -> dict[str, str]:
    current.database.healthy()
    return {"status": "ok"}


@router.post("/api/v1/links", response_model=LinkResponse, status_code=201,
             responses={200: {"model": LinkResponse, "description": "Liên kết đã tồn tại"}}, tags=["Liên kết"])
def create_link(payload: CreateLink, response: Response, current: LinkService = Depends(service)) -> LinkResponse:
    result = current.create(payload)
    response.status_code = 200 if result.reused else 201
    return result


@router.get("/api/v1/links/{code}", response_model=LinkResponse, tags=["Liên kết"])
def metadata(code: str, current: LinkService = Depends(service)) -> LinkResponse:
    link = current.database.find(code)
    if not link:
        raise HTTPException(404, "Liên kết không tồn tại")
    return current.response(link, reused=True)


@router.get("/r/{code}", response_class=RedirectResponse, status_code=302, tags=["Chuyển hướng"],
            responses={301: {"description": "Chuyển hướng vĩnh viễn"}, 302: {"description": "Chuyển hướng tạm thời"}, 404: {"description": "Không có liên kết"}})
def redirect(code: str, current: LinkService = Depends(service)) -> RedirectResponse:
    link, cache_status = current.cache.resolve(code, current.database.find)
    if not link:
        raise HTTPException(404, "Liên kết không tồn tại", headers={"Cache-Control": "no-store", "X-Cache": cache_status})
    return RedirectResponse(link.long_url, status_code=link.redirect_status, headers={
        "X-Cache": cache_status,
        "Cache-Control": "no-store" if link.redirect_status == 302 else "private, max-age=300",
    })


@router.get("/api/v1/demo/stats", response_model=DemoStats, tags=["Demo"])
def stats(current: LinkService = Depends(demo_service)) -> dict:
    return current.cache.stats()


@router.post("/api/v1/demo/cache/clear", response_model=DemoStats, tags=["Demo"])
def clear_cache(current: LinkService = Depends(demo_service)) -> dict:
    current.cache.clear()
    return current.cache.stats()


@router.post("/api/v1/demo/stats/reset", response_model=DemoStats, tags=["Demo"])
def reset_stats(current: LinkService = Depends(demo_service)) -> dict:
    current.cache.reset_stats()
    return current.cache.stats()

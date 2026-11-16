import logging
import sqlite3
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from .config import Settings
from .routes import router
from .services import LinkService

logger = logging.getLogger(__name__)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    current = LinkService(settings)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        current.database.initialize()
        yield

    app = FastAPI(title="URL Shortener", version="1.0.0", lifespan=lifespan,
                  description="Rút gọn URL với SQLite, Base62 và cache RAM. Demo chạy một tiến trình.")
    app.state.service = current

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, error: RequestValidationError):
        return JSONResponse(status_code=422, content={"detail": "Dữ liệu chưa hợp lệ. Hãy nhập URL HTTP/HTTPS tối đa 4096 ký tự và chế độ 301 hoặc 302."})

    @app.exception_handler(sqlite3.Error)
    async def database_error(request: Request, error: sqlite3.Error):
        logger.exception("Không thể truy cập SQLite", exc_info=error)
        return JSONResponse(status_code=503, content={"detail": "Máy chủ đang bận. Bạn hãy thử lại."})

    app.include_router(router)

    @app.get("/", include_in_schema=False)
    def index():
        page = settings.frontend_dist / "index.html"
        if page.is_file():
            return FileResponse(page, headers={"Cache-Control": "no-cache"})
        return JSONResponse(status_code=503, content={"detail": "Chưa build giao diện. Chạy npm.cmd run build trong thư mục frontend."})

    if (settings.frontend_dist / "assets").is_dir():
        app.mount("/assets", StaticFiles(directory=settings.frontend_dist / "assets"), name="assets")
    return app


app = create_app()

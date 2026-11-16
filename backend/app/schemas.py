import re
from datetime import datetime
from typing import Literal
from urllib.parse import urlsplit

from pydantic import AnyHttpUrl, BaseModel, ConfigDict, TypeAdapter, field_validator

http_url = TypeAdapter(AnyHttpUrl)


class CreateLink(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    long_url: str
    redirect_status: Literal[301, 302] = 302

    @field_validator("redirect_status", mode="before")
    @classmethod
    def integer_status(cls, value: object) -> object:
        if type(value) is not int:
            raise ValueError("Chế độ chuyển hướng phải là số nguyên 301 hoặc 302")
        return value

    @field_validator("long_url")
    @classmethod
    def valid_url(cls, value: str) -> str:
        value = value.strip()
        if not value or len(value) > 4096:
            raise ValueError("Liên kết cần có từ 1 đến 4096 ký tự")
        if re.search(r"[\s\x00-\x1f\x7f]", value) or "\\" in value:
            raise ValueError("Liên kết không được chứa khoảng trắng hoặc ký tự điều khiển")
        parts = urlsplit(value)
        if parts.scheme.lower() not in {"http", "https"} or not parts.netloc:
            raise ValueError("Hãy nhập liên kết đầy đủ, bắt đầu bằng http:// hoặc https://")
        http_url.validate_python(value)
        # Chỉ kiểm tra bằng parser; giữ nguyên path/query/fragment của chuỗi đầu vào.
        return value


class LinkResponse(BaseModel):
    id: int
    short_code: str
    short_url: str
    long_url: str
    redirect_status: Literal[301, 302]
    created_at: datetime
    reused: bool


class DemoStats(BaseModel):
    redirect_requests: int
    cache_hits: int
    cache_misses: int
    db_lookup_count: int
    not_found: int
    cache_size: int
    hit_rate: float

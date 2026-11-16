import sqlite3
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator

from .base62 import encode


@dataclass(frozen=True)
class Link:
    id: int
    short_code: str
    long_url: str
    redirect_status: int
    created_at: str


class Database:
    def __init__(self, path: Path):
        self.path = path

    @contextmanager
    def connect(self) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(self.path, timeout=10)
        connection.row_factory = sqlite3.Row
        try:
            yield connection
        finally:
            connection.close()

    def initialize(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as connection:
            connection.execute("PRAGMA journal_mode=WAL")
            connection.execute("""
                CREATE TABLE IF NOT EXISTS links (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    short_code TEXT COLLATE BINARY UNIQUE,
                    long_url TEXT NOT NULL,
                    redirect_status INTEGER NOT NULL CHECK (redirect_status IN (301, 302)),
                    created_at TEXT NOT NULL,
                    UNIQUE (long_url, redirect_status)
                )
            """)
            connection.commit()

    def create_or_get(self, long_url: str, redirect_status: int) -> tuple[Link, bool]:
        with self.connect() as connection:
            # Khóa ghi trước khi kiểm tra trùng; ID và mã chỉ được commit cùng nhau.
            with connection:
                connection.execute("BEGIN IMMEDIATE")
                row = connection.execute(
                    "SELECT * FROM links WHERE long_url = ? AND redirect_status = ?",
                    (long_url, redirect_status),
                ).fetchone()
                if row:
                    return Link(**dict(row)), True
                created_at = datetime.now(timezone.utc).isoformat()
                cursor = connection.execute(
                    "INSERT INTO links (long_url, redirect_status, created_at) VALUES (?, ?, ?)",
                    (long_url, redirect_status, created_at),
                )
                link_id = cursor.lastrowid
                assert link_id is not None
                code = encode(link_id)
                connection.execute("UPDATE links SET short_code = ? WHERE id = ?", (code, link_id))
                return Link(link_id, code, long_url, redirect_status, created_at), False

    def find(self, code: str) -> Link | None:
        with self.connect() as connection:
            row = connection.execute("SELECT * FROM links WHERE short_code = ?", (code,)).fetchone()
            return Link(**dict(row)) if row else None

    def healthy(self) -> bool:
        with self.connect() as connection:
            connection.execute("SELECT 1 FROM links LIMIT 1").fetchone()
            return True

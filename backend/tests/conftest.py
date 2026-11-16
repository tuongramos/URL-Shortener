import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


@pytest.fixture
def settings(tmp_path):
    return Settings(database_path=tmp_path / "test.db", demo_mode=True,
                    frontend_dist=tmp_path / "dist")


@pytest.fixture
def application(settings):
    return create_app(settings)


@pytest.fixture
def client(application):
    with TestClient(application, follow_redirects=False) as current:
        yield current

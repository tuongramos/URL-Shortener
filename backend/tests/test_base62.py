import pytest

from app.base62 import encode


@pytest.mark.parametrize(("number", "expected"), [
    (0, "0"), (1, "1"), (61, "Z"), (62, "10"),
    (11157, "2TX"), (2009215674938, "zn9edcu"),
    (62**7, "10000000"),
])
def test_known_encoding(number, expected):
    assert encode(number) == expected


@pytest.mark.parametrize("value", [-1, True, 1.5, "12"])
def test_reject_invalid_number(value):
    with pytest.raises(ValueError):
        encode(value)

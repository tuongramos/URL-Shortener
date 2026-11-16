ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"


def encode(number: int) -> str:
    """Biểu diễn một số nguyên không âm, không cắt hoặc đệm kết quả."""
    if isinstance(number, bool) or not isinstance(number, int) or number < 0:
        raise ValueError("ID phải là số nguyên không âm")
    if number == 0:
        return "0"
    digits: list[str] = []
    while number:
        number, remainder = divmod(number, 62)
        digits.append(ALPHABET[remainder])
    return "".join(reversed(digits))

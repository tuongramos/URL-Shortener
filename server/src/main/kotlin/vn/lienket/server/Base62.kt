package vn.lienket.server

object Base62 {
    const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    fun encode(id: Long): String {
        require(id >= 0) { "ID phải là số nguyên không âm" }
        // Xử lý riêng số 0 vì vòng lặp bên dưới không chạy với 0.
        if (id == 0L) return "0"
        // Dùng bản sao của ID để chia dần cho 62.
        var number = id
        // Chứa các ký tự tìm được.
        val result = StringBuilder()
        while (number > 0) {
            // Lấy số dư làm chỉ số ký tự: 15 % 62 = 15, ALPHABET[15] = 'f'.
            result.append(ALPHABET[(number % 62).toInt()])
            // Chia nguyên cho 62 để tính ký tự tiếp theo.
            number /= 62
        }
        // Các ký tự được tìm từ phải sang trái nên cần đảo lại.
        return result.reverse().toString()
    }
}

package vn.lienket.server

object Base62 {
    const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun encode(id: Long): String {
        require(id >= 0) { "ID phải là số nguyên không âm" }
        if (id == 0L) return "0"
        var number = id
        val result = StringBuilder()
        while (number > 0) {
            result.append(ALPHABET[(number % 62).toInt()])
            number /= 62
        }
        return result.reverse().toString()
    }
}

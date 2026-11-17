package vn.lienket.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UrlValidatorTest {
    @Test fun preservesQueryFragmentAndEncoding() {
        val value = "https://example.com/a%2Fb?x=%2F&y=1#part"
        assertEquals(value, UrlValidator.validate("  $value  "))
    }
    @Test fun acceptsHttpHttpsAndLocalhost() {
        listOf("http://localhost:8000/demo/target", "https://example.com", "http://127.0.0.1:9000/").forEach { assertEquals(it, UrlValidator.validate(it)) }
    }
    @Test fun rejectsUnsupportedOrMalformedAddresses() {
        listOf("", " ", "example.com", "ftp://example.com", "javascript:alert(1)", "https://", "http:example.com", "https://example.com:bad", "https://example.com:99999", "https://example.com/a b", "https://example.com/\nheader", "https://example.com/\\evil").forEach {
            assertFailsWith<IllegalArgumentException>(it) { UrlValidator.validate(it) }
        }
    }
    @Test fun enforcesLengthAfterTrim() {
        val prefix = "https://example.com/"
        val max = prefix + "a".repeat(4096 - prefix.length)
        assertEquals(max, UrlValidator.validate(" $max "))
        assertFailsWith<IllegalArgumentException> { UrlValidator.validate(max + "a") }
    }
}

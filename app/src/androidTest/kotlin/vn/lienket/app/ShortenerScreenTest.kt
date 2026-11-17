package vn.lienket.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import vn.lienket.app.ui.LienKetTheme
import vn.lienket.app.ui.ShortenerScreen
import vn.lienket.shared.ShortLink
import kotlin.test.assertEquals

class ShortenerScreenTest {
    @get:Rule val compose = createComposeRule()
    private val sample = ShortLink(1, "1", "http://127.0.0.1:8000/r/1", "https://example.com/a?chapter=8#part", 302, "2026-11-17T00:00:00Z", false)

    @Test fun inputAndPrimaryActionAreNativeCompose() {
        val state = mutableStateOf(ShortenerUiState())
        var submits = 0
        compose.setContent { LienKetTheme { ShortenerScreen(state.value, { state.value = state.value.copy(input = it) }, { submits++ }, {}, {}, { null }) } }
        compose.onNodeWithTag("url_input").performTextInput(sample.longUrl)
        compose.onNodeWithTag("shorten_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(sample.longUrl, state.value.input); assertEquals(1, submits) }
    }
    @Test fun pendingDisablesDuplicateSubmit() {
        compose.setContent { LienKetTheme { ShortenerScreen(ShortenerUiState(isLoading = true), {}, {}, {}, {}, { null }) } }
        compose.onNodeWithTag("shorten_button").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Đang rút gọn...").assertExists()
    }
    @Test fun resultSupportsCopyAndOpenWithoutAutoNavigation() {
        var copied = 0; var opened = 0
        compose.setContent { LienKetTheme { ShortenerScreen(ShortenerUiState(result = sample), {}, {}, { copied++ }, { opened++ }, { null }) } }
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithTag("copy_button").performScrollTo().performClick()
        compose.onNodeWithTag("open_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, copied); assertEquals(1, opened) }
    }
    @Test fun errorAndLongTextRemainVisible() {
        compose.setContent { LienKetTheme { ShortenerScreen(ShortenerUiState(error = "Hãy nhập liên kết HTTP/HTTPS hợp lệ."), {}, {}, {}, {}, { null }) } }
        compose.onNodeWithTag("url_error").performScrollTo().assertIsDisplayed()
    }
    @Test fun manualCopyFallbackIsSelectable() {
        compose.setContent { LienKetTheme { ShortenerScreen(ShortenerUiState(result = sample, manualCopy = true), {}, {}, {}, {}, { null }) } }
        compose.onNodeWithTag("manual_copy").performScrollTo().assertTextEquals(sample.shortUrl)
        compose.onNodeWithTag("original_url").performScrollTo().assertTextEquals(sample.longUrl)
    }
}

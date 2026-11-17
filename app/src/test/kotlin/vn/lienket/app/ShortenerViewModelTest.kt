package vn.lienket.app

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import vn.lienket.app.data.ApiException
import vn.lienket.app.data.ServerAddress
import vn.lienket.app.data.ShortenerRepository
import vn.lienket.shared.ShortLink
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShortenerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sample = ShortLink(1, "1", "http://127.0.0.1:8000/r/1", "https://example.com/article?chapter=8#part", 302, "2026-11-17T00:00:00Z", false)
    private var calls = 0
    private var behavior: suspend (String, String) -> ShortLink = { _, _ -> sample }
    private val repository = object : ShortenerRepository {
        override suspend fun create(serverUrl: String, longUrl: String): ShortLink { calls++; return behavior(serverUrl, longUrl) }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    @Test fun rejectsInvalidBeforeCallingServer() = runTest {
        val model = ShortenerViewModel(repository)
        model.updateInput("ftp://example.com")
        model.shorten()
        advanceUntilIdle()
        assertEquals(0, calls)
        assertNotNull(model.state.value.error)
        assertEquals("ftp://example.com", model.state.value.input)
    }
    @Test fun trimsAndDisplaysResult() = runTest {
        behavior = { base, url -> assertEquals("http://127.0.0.1:8000", base); assertEquals(sample.longUrl, url); sample }
        val model = ShortenerViewModel(repository)
        model.updateInput(" ${sample.longUrl} ")
        model.shorten()
        advanceUntilIdle()
        assertEquals(sample, model.state.value.result)
        assertFalse(model.state.value.isLoading)
        assertTrue(model.state.value.message.contains("thành công"))
    }
    @Test fun preventsDuplicateRequestsAndInputChangesWhileLoading() = runTest {
        val pending = CompletableDeferred<ShortLink>()
        behavior = { _, _ -> pending.await() }
        val model = ShortenerViewModel(repository)
        model.updateInput(sample.longUrl)
        model.shorten(); model.shorten(); model.updateInput("https://example.com/changed")
        runCurrent()
        assertEquals(1, calls)
        assertTrue(model.state.value.isLoading)
        assertEquals(sample.longUrl, model.state.value.input)
        pending.complete(sample)
        advanceUntilIdle()
        assertFalse(model.state.value.isLoading)
    }
    @Test fun reportsReuse() = runTest {
        behavior = { _, _ -> sample.copy(reused = true) }
        val model = ShortenerViewModel(repository)
        model.updateInput(sample.longUrl); model.shorten(); advanceUntilIdle()
        assertTrue(model.state.value.message.contains("đã được tạo trước đó"))
    }
    @Test fun keepsInputOnConnectionFailureAndAllowsRetry() = runTest {
        behavior = { _, _ -> throw ApiException(0, "Chưa kết nối được máy chủ. Bạn hãy thử lại.") }
        val model = ShortenerViewModel(repository)
        model.updateInput(sample.longUrl); model.shorten(); advanceUntilIdle()
        assertNull(model.state.value.result)
        assertFalse(model.state.value.isLoading)
        assertEquals(sample.longUrl, model.state.value.input)
        assertTrue(model.state.value.message.contains("Chưa kết nối"))
        behavior = { _, _ -> sample }
        model.shorten(); advanceUntilIdle()
        assertEquals(sample, model.state.value.result)
    }
    @Test fun shows422AsInputError() = runTest {
        behavior = { _, _ -> throw ApiException(422, "Liên kết chưa hợp lệ") }
        val model = ShortenerViewModel(repository)
        model.updateInput(sample.longUrl); model.shorten(); advanceUntilIdle()
        assertEquals("Liên kết chưa hợp lệ", model.state.value.error)
        model.updateInput(sample.longUrl)
        assertNull(model.state.value.error)
    }
    @Test fun clipboardFallbackAndSuccess() {
        val model = ShortenerViewModel(repository)
        model.copyFailed()
        assertTrue(model.state.value.manualCopy)
        model.copySucceeded()
        assertFalse(model.state.value.manualCopy)
        assertTrue(model.state.value.copied)
        assertEquals("Đã sao chép liên kết", model.state.value.message)
    }
    @Test fun restoresInputAndResultAfterRecreation() = runTest {
        val saved = SavedStateHandle()
        val first = ShortenerViewModel(repository, saved)
        first.updateInput(sample.longUrl); first.shorten(); advanceUntilIdle()
        val restored = ShortenerViewModel(repository, saved)
        assertEquals(sample.longUrl, restored.state.value.input)
        assertEquals(sample, restored.state.value.result)
    }
    @Test fun validatesAndPersistsServerAddress() {
        var persisted = ""
        val model = ShortenerViewModel(repository, persistServerUrl = { persisted = it })
        assertNotNull(model.setServerUrl("http://example.com/path"))
        assertNull(model.setServerUrl(" http://192.168.1.20:8000/ "))
        assertEquals("http://192.168.1.20:8000", persisted)
        assertEquals(persisted, model.state.value.serverUrl)
    }
    @Test fun serverAddressRejectsQueryAndCredentials() {
        listOf("https://example.com?a=1", "https://a:b@example.com", "ftp://example.com").forEach { assertFails { ServerAddress.normalize(it) } }
    }
}

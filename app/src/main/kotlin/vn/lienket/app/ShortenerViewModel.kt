package vn.lienket.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import vn.lienket.app.data.ApiException
import vn.lienket.app.data.HttpShortenerRepository
import vn.lienket.app.data.ServerAddress
import vn.lienket.app.data.ShortenerRepository
import vn.lienket.shared.ShortLink
import vn.lienket.shared.UrlValidator
import java.io.Closeable

data class ShortenerUiState(
    val input: String = "",
    val isLoading: Boolean = false,
    val result: ShortLink? = null,
    val error: String? = null,
    val message: String = "",
    val copied: Boolean = false,
    val manualCopy: Boolean = false,
    val serverUrl: String = BuildConfig.DEFAULT_SERVER_URL,
)

class ShortenerViewModel(
    private val repository: ShortenerRepository = HttpShortenerRepository(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
    initialServerUrl: String = BuildConfig.DEFAULT_SERVER_URL,
    private val persistServerUrl: (String) -> Unit = {},
) : ViewModel() {
    private val restoredLink = savedState.get<String>("result")?.let { runCatching { Json.decodeFromString<ShortLink>(it) }.getOrNull() }
    private val mutableState = MutableStateFlow(ShortenerUiState(
        input = savedState["input"] ?: "",
        result = restoredLink,
        serverUrl = savedState["serverUrl"] ?: initialServerUrl,
    ))
    val state = mutableState.asStateFlow()

    fun updateInput(value: String) {
        if (mutableState.value.isLoading) return
        savedState["input"] = value
        mutableState.update { it.copy(input = value, error = null) }
    }

    fun shorten() {
        if (mutableState.value.isLoading) return
        val url = try { UrlValidator.validate(mutableState.value.input) }
        catch (_: IllegalArgumentException) {
            mutableState.update { it.copy(error = UrlValidator.INVALID_MESSAGE, result = null, message = "") }
            savedState["result"] = null
            return
        }
        val serverUrl = mutableState.value.serverUrl
        savedState["result"] = null
        mutableState.update { it.copy(isLoading = true, error = null, result = null, copied = false, manualCopy = false, message = "Đang tạo liên kết của bạn...") }
        viewModelScope.launch {
            try {
                val link = repository.create(serverUrl, url)
                savedState["result"] = Json.encodeToString(link)
                mutableState.update { it.copy(result = link, message = if (link.reused)
                    "Liên kết này đã được tạo trước đó. Bạn có thể dùng lại ngay." else "Đã tạo liên kết ngắn thành công.") }
            } catch (error: CancellationException) { throw error }
            catch (error: ApiException) {
                mutableState.update { if (error.status == 422) it.copy(error = error.message, message = "") else it.copy(message = error.message.orEmpty()) }
            } catch (_: Exception) {
                mutableState.update { it.copy(message = "Có lỗi xảy ra. Bạn hãy thử lại.") }
            } finally { mutableState.update { it.copy(isLoading = false) } }
        }
    }

    fun copySucceeded() { mutableState.update { it.copy(copied = true, manualCopy = false, message = "Đã sao chép liên kết") } }
    fun copyFailed() { mutableState.update { it.copy(manualCopy = true, message = "Chưa sao chép tự động được. Hãy chọn và sao chép liên kết bên dưới.") } }
    fun openFailed() { mutableState.update { it.copy(message = "Chưa mở được trình duyệt. Bạn hãy sao chép và mở liên kết bằng tay.") } }

    fun setServerUrl(raw: String): String? {
        if (mutableState.value.isLoading) return "Hãy chờ yêu cầu hiện tại hoàn tất."
        val normalized = try { ServerAddress.normalize(raw) } catch (_: Exception) { return "Hãy nhập địa chỉ máy chủ HTTP/HTTPS, không kèm đường dẫn." }
        persistServerUrl(normalized)
        savedState["serverUrl"] = normalized
        mutableState.update { it.copy(serverUrl = normalized, message = "Đã lưu địa chỉ máy chủ.") }
        return null
    }

    override fun onCleared() { (repository as? Closeable)?.close(); super.onCleared() }
}

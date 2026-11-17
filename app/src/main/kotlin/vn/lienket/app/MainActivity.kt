package vn.lienket.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.lienket.app.ui.LienKetTheme
import vn.lienket.app.ui.ShortenerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("connection", Context.MODE_PRIVATE)
        setContent {
            LienKetTheme {
                val model: ShortenerViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        ShortenerViewModel(savedState = createSavedStateHandle(),
                            initialServerUrl = preferences.getString("serverUrl", BuildConfig.DEFAULT_SERVER_URL)!!,
                            persistServerUrl = { preferences.edit().putString("serverUrl", it).apply() })
                    }
                })
                val state by model.state.collectAsStateWithLifecycle()
                ShortenerScreen(state, model::updateInput, model::shorten,
                    onCopy = {
                        state.result?.let { result ->
                            try {
                                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Liên kết ngắn", result.shortUrl))
                                model.copySucceeded()
                            } catch (_: Exception) { model.copyFailed() }
                        }
                    },
                    onOpen = {
                        state.result?.let { result ->
                            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.shortUrl))) }
                            catch (_: Exception) { model.openFailed() }
                        }
                    },
                    onServerChange = model::setServerUrl,
                )
            }
        }
    }
}

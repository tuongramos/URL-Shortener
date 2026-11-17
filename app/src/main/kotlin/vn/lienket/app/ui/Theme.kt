package vn.lienket.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PageBackground = Color(0xFFF4F6EF)
val Forest = Color(0xFF244E3A)
val Ink = Color(0xFF20392E)
val Muted = Color(0xFF68766A)
val Olive = Color(0xFF648647)
val PaleGreen = Color(0xFFE7EFDB)
val Border = Color(0xFFDFE6D8)

@Composable
fun LienKetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, onPrimary = Color.White,
        background = PageBackground, onBackground = Ink, surface = Color.White,
        onSurface = Ink, secondary = Olive, error = Color(0xFFA23C29)), content = content)
}

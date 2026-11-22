package vn.lienket.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.lienket.app.ShortenerUiState
import vn.lienket.shared.ShortLink

@Composable
fun ShortenerScreen(
    state: ShortenerUiState,
    onInputChange: (String) -> Unit,
    onShorten: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onServerChange: (String) -> String?,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(state.error) { if (state.error != null) inputFocus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFEAF0E0), PageBackground), radius = 1200f)).safeDrawingPadding().imePadding()) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight).padding(horizontal = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            BrandHeader(onSettings = { settingsOpen = true })
            Column(Modifier.fillMaxWidth().widthIn(max = 620.dp).align(Alignment.CenterHorizontally).padding(vertical = 36.dp)) {
                Intro()
                Spacer(Modifier.height(28.dp))
                Surface(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Border), shadowElevation = 5.dp) {
                    Column(Modifier.padding(22.dp)) {
                        Text("Dán liên kết của bạn", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            // Hiển thị URL đang nhập.
                            value = state.input,
                            // Khi gõ hoặc dán URL, gọi updateInput() trong ViewModel.
                            onValueChange = onInputChange,
                            modifier = Modifier.fillMaxWidth().testTag("url_input").focusRequester(inputFocus),
                            placeholder = { Text("https://example.com/lien-ket-cua-ban", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { LinkGlyph(Modifier.size(21.dp), Color(0xFF84927E)) },
                            singleLine = true,
                            readOnly = state.isLoading,
                            isError = state.error != null,
                            shape = RoundedCornerShape(10.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Ink),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                            // Nhấn Go trên bàn phím cũng gửi yêu cầu rút gọn.
                            keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); onShorten(); keyboard?.hide() }),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFFAFBF8), focusedContainerColor = Color(0xFFFAFBF8), unfocusedBorderColor = Color(0xFFD6DFD1), focusedBorderColor = Olive),
                        )
                        Spacer(Modifier.height(12.dp))
                        // Bấm nút để gọi shorten(); khóa nút trong lúc chờ kết quả.
                        Button(onClick = { focus.clearFocus(); onShorten(); keyboard?.hide() }, enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("shorten_button"), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Forest, disabledContainerColor = Forest.copy(alpha = 0.65f), disabledContentColor = Color.White)) {
                            Text(if (state.isLoading) "Đang rút gọn..." else "Rút gọn", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(12.dp))
                            if (state.isLoading) CircularProgressIndicator(Modifier.size(17.dp), color = Color(0xFFD6EDAF), strokeWidth = 2.dp)
                            else Text("↗", fontSize = 21.sp, color = Color(0xFFD6EDAF))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Hỗ trợ liên kết bắt đầu bằng http:// hoặc https://.", fontSize = 11.sp, lineHeight = 17.sp, color = Color(0xFF818B7C))
                        state.error?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, modifier = Modifier.testTag("url_error").semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        // Có kết quả từ server thì hiện phần liên kết ngắn.
                        AnimatedVisibility(state.result != null) {
                            state.result?.let { link -> ResultContent(link, state.copied, state.manualCopy, onCopy, onOpen) }
                        }
                        if (state.message.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(state.message, modifier = Modifier.testTag("status_message").semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 12.sp, lineHeight = 18.sp, color = Color(0xFF63775A))
                        } else Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(26.dp))
                Steps()
            }
            Footer()
        }
    }
    if (settingsOpen) ServerDialog(state.serverUrl, onDismiss = { settingsOpen = false }, onSave = onServerChange)
}

@Composable
private fun BrandHeader(onSettings: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(35.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF234B38)), contentAlignment = Alignment.Center) { LinkGlyph(Modifier.size(26.dp), Color(0xFFDEF3B0)) }
            Spacer(Modifier.width(9.dp))
            Text("liên kết.", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.8).sp)
            Spacer(Modifier.weight(1f))
            Text("Gọn hơn.\nDễ chia sẻ hơn.", fontSize = 9.sp, lineHeight = 14.sp, color = Muted, textAlign = TextAlign.End)
            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp).testTag("connection_settings")) { Text("⋮", fontSize = 25.sp, color = Muted, modifier = Modifier.semantics { contentDescription = "Cài đặt kết nối" }) }
        }
        HorizontalDivider(color = Color(0xFFDCE2D6))
    }
}

@Composable
private fun Intro() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(Color(0xFF7E9E56), CircleShape))
            Spacer(Modifier.width(7.dp))
            Text("MỘT LIÊN KẾT, ÍT KÝ TỰ HƠN", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color(0xFF637D51))
        }
        Spacer(Modifier.height(18.dp))
        Text(buildAnnotatedString { append("Rút gọn "); withStyle(SpanStyle(color = Olive)) { append("liên kết") } },
            modifier = Modifier.semantics { heading() }, fontSize = 35.sp, lineHeight = 43.sp, fontWeight = FontWeight.SemiBold,
            color = Ink, letterSpacing = (-1.4).sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Tạo liên kết ngắn để chia sẻ dễ dàng hơn.", fontSize = 13.sp, lineHeight = 21.sp, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ResultContent(link: ShortLink, copied: Boolean, manualCopy: Boolean, onCopy: () -> Unit, onOpen: () -> Unit) {
    Column {
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Border)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).background(Color(0xFFEDF5E5), CircleShape), contentAlignment = Alignment.Center) { Text("✓", fontSize = 13.sp, color = Color(0xFF4B743C)) }
            Spacer(Modifier.width(8.dp))
            Text("Liên kết đã sẵn sàng", color = Color(0xFF5B7550), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        // Hiển thị URL ngắn server trả về, ví dụ http://127.0.0.1:8000/r/f.
        Text(link.shortUrl, modifier = Modifier.fillMaxWidth().testTag("short_link").clickable(onClick = onOpen).padding(vertical = 4.dp), color = Forest, fontSize = 22.sp, lineHeight = 29.sp, letterSpacing = (-0.5).sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCopy, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("copy_button"), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = PaleGreen, contentColor = Color(0xFF345B2D))) {
                CopyGlyph(Modifier.size(16.dp), Color(0xFF345B2D)); Spacer(Modifier.width(7.dp))
                Text(if (copied) "Đã sao chép" else "Sao chép", fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_button"), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64735C))) {
                Text("Mở liên kết", fontSize = 12.sp); Spacer(Modifier.width(7.dp)); Text("↗", fontSize = 16.sp)
            }
        }
        if (manualCopy) {
            Spacer(Modifier.height(16.dp))
            Text("Chọn và sao chép liên kết bằng tay", fontSize = 12.sp, color = Muted)
            SelectionContainer { Text(link.shortUrl, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("manual_copy"), fontSize = 14.sp, color = Forest) }
        }
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF6F8F2)).padding(14.dp)) {
            Text("LIÊN KẾT GỐC", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Color(0xFF8A9580))
            Spacer(Modifier.height(7.dp))
            SelectionContainer { Text(link.longUrl, modifier = Modifier.testTag("original_url"), fontSize = 11.sp, lineHeight = 18.sp, color = Color(0xFF78836E)) }
        }
    }
}

@Composable
private fun Steps() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        listOf("Dán liên kết", "Rút gọn", "Chia sẻ").forEachIndexed { index, label ->
            if (index > 0) Box(Modifier.padding(horizontal = 10.dp).width(14.dp).height(1.dp).background(Color(0xFFD5DFCB)))
            Text("0${index + 1}", fontSize = 8.sp, color = Color(0xFF8B9A7D))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 10.sp, color = Color(0xFF7B8774))
        }
    }
}

@Composable
private fun Footer() {
    Column {
        HorizontalDivider(color = Color(0xFFDCE2D6))
        Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Đường dẫn gọn. Kết nối trọn vẹn.", fontSize = 9.sp, color = Color(0xFF85907D))
            Text("URL Shortener ↗", fontSize = 9.sp, color = Color(0xFF85907D))
        }
    }
}

@Composable
private fun ServerDialog(serverUrl: String, onDismiss: () -> Unit, onSave: (String) -> String?) {
    var value by rememberSaveable { mutableStateOf(serverUrl) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Kết nối máy chủ", fontSize = 21.sp) },
        text = { Column {
            Text("Nhập địa chỉ máy chủ đang chạy trên máy tính của bạn.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value, onValueChange = { value = it; error = null }, label = { Text("Địa chỉ máy chủ") }, singleLine = true,
                modifier = Modifier.testTag("server_address"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = error != null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        } },
        confirmButton = { TextButton(onClick = { error = onSave(value); if (error == null) onDismiss() }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

@Composable
private fun LinkGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        withTransform({ scale(size.width / 24, size.height / 24, Offset.Zero) }) {
            val path = Path().apply {
                moveTo(9f, 15f); lineTo(15f, 9f)
                moveTo(8f, 10f); lineTo(6f, 12f); cubicTo(2f, 16f, 8f, 22f, 12f, 18f); lineTo(14f, 16f)
                moveTo(10f, 8f); lineTo(12f, 6f); cubicTo(16f, 2f, 22f, 8f, 18f, 12f); lineTo(16f, 14f)
            }
            drawPath(path, color, style = Stroke(1.7f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun CopyGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        withTransform({ scale(size.width / 24, size.height / 24, Offset.Zero) }) {
            drawRoundRect(color, Offset(8f, 8f), Size(12f, 12f), style = Stroke(1.7f))
            drawPath(Path().apply { moveTo(16f, 8f); lineTo(16f, 4f); lineTo(4f, 4f); lineTo(4f, 16f); lineTo(8f, 16f) }, color, style = Stroke(1.7f))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun IdlePreview() { LienKetTheme { ShortenerScreen(ShortenerUiState(), {}, {}, {}, {}, { null }) } }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ResultPreview() {
    LienKetTheme { ShortenerScreen(ShortenerUiState(result = ShortLink(1, "1", "http://127.0.0.1:8000/r/1", "https://example.com/article?chapter=8", 302, "2026-11-17T00:00:00Z", false), message = "Đã tạo liên kết ngắn thành công."), {}, {}, {}, {}, { null }) }
}

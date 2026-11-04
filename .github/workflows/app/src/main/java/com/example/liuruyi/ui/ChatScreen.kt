package com.example.liuruyi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liuruyi.data.Message
import com.example.liuruyi.ui.components.Avatar
import com.example.liuruyi.ui.components.HardLimitDialog
import com.example.liuruyi.ui.components.InputBar
import com.example.liuruyi.ui.components.MessageBubble
import com.example.liuruyi.ui.components.TokenBar
import com.example.liuruyi.ui.theme.WeChatBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.sendImage(uri)
    }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance.setLanguage(Locale.CHINA)
                instance.setPitch(1.15f)
                instance.setSpeechRate(0.92f)
            }
        }
        tts = instance
        onDispose {
            instance.stop()
            instance.shutdown()
            tts = null
        }
    }
    val speak: (String) -> Unit = { text ->
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_" + System.currentTimeMillis())
    }
    val listState = rememberLazyListState()

    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var longPressed by remember { mutableStateOf<Message?>(null) }
    var matchIndex by remember { mutableIntStateOf(0) }

    val matches = remember(searchQuery, state.messages) {
        if (searchQuery.isBlank()) emptyList<Int>()
        else state.messages.indices.filter { state.messages[it].content.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(state.messages.size, state.streamingText.length) {
        if (!searchActive) {
            val target = state.messages.size + if (state.isStreaming) 1 else 0
            if (target > 0) listState.animateScrollToItem(target - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(state.avatarPath, size = 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(state.sessionTitle.ifBlank { "新会话" })
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchActive = !searchActive
                            if (!searchActive) { searchQuery = ""; matchIndex = 0 }
                        },
                    ) {
                        Icon(
                            if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "查找",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchActive) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; matchIndex = 0 },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("查找对话内容") },
                        singleLine = true,
                    )
                    IconButton(
                        enabled = matches.isNotEmpty() && matchIndex > 0,
                        onClick = {
                            matchIndex = (matchIndex - 1 + matches.size) % matches.size
                            listState.scrollToItem(matches[matchIndex])
                        },
                    ) { Icon(Icons.Filled.KeyboardArrowUp, "上一条") }
                    IconButton(
                        enabled = matches.isNotEmpty() && matchIndex < matches.size - 1,
                        onClick = {
                            matchIndex = (matchIndex + 1) % matches.size
                            listState.scrollToItem(matches[matchIndex])
                        },
                    ) { Icon(Icons.Filled.KeyboardArrowDown, "下一条") }
                    Text(
                        if (matches.isEmpty()) "无结果" else ((matchIndex + 1).toString() + "/" + matches.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TokenBar(state.tokenEstimate, state.modelName)
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().background(WeChatBg)) {
                itemsIndexed(state.messages, key = { _, m -> m.id }) { index, m ->
                    Column {
                        if (shouldShowTime(state.messages, index)) {
                            Text(
                                text = timeLabel(m.createdAt),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                                color = Color(0xFF8A8791),
                                fontSize = 12.sp,
                            )
                        }
                        Box {
                        MessageBubble(
                            message = m,
                            highlight = if (searchActive) searchQuery else null,
                            onLongClick = { longPressed = m },
                            onPlayVoice = speak,
                            peerAvatarPath = state.avatarPath,
                            myAvatarPath = state.myAvatarPath,
                        )
                        DropdownMenu(
                            expanded = longPressed?.id == m.id,
                            onDismissRequest = { longPressed = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("引用") },
                                onClick = { vm.setQuote(m); longPressed = null },
                            )
                            DropdownMenuItem(
                                text = { Text("撤回") },
                                onClick = { vm.recallMessage(m.id); longPressed = null },
                            )
                            DropdownMenuItem(
                                text = { Text("记住这条") },
                                onClick = { vm.pinToMemory(m.id); longPressed = null },
                            )
                            DropdownMenuItem(
                                text = { Text("复制") },
                                onClick = {
                                    if (m.imagePath?.isNotBlank() == true) {
                                        Toast.makeText(context, "图片消息暂不支持复制", Toast.LENGTH_SHORT).show()
                                    } else {
                                        copyText(context, m.content)
                                    }
                                    longPressed = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除这条") },
                                onClick = { vm.deleteSingle(m.id); longPressed = null },
                            )
                        }
                    }
                    }
                }
                if (state.isStreaming) {
                    item(key = "streaming") {
                        MessageBubble(
                            Message(
                                id = "streaming",
                                role = "assistant",
                                content = state.streamingText.ifEmpty { "正在输入…" },
                                createdAt = 0L,
                                kind = "chat",
                                status = "streaming",
                            ),
                            isStreaming = true,
                        )
                    }
                }
            }
            if (state.error != null) {
                Surface(color = Color(0xFFFDECEC)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text(
                            state.error.orEmpty(),
                            Modifier.weight(1f).padding(vertical = 8.dp),
                            color = Color(0xFFD93025),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { vm.retryLast() }) { Text("重试") }
                        TextButton(onClick = { vm.dismissError() }) { Text("关闭") }
                    }
                }
            }
            InputBar(
                input = state.input,
                onInputChange = vm::onInputChange,
                onSend = { vm.sendMessage(state.input) },
                onStop = vm::stopStreaming,
                isStreaming = state.isStreaming,
                enabled = state.activeSessionId.isNotBlank(),
                quoteMessage = state.quoteMessage,
                onClearQuote = vm::clearQuote,
                onPickImage = { imagePicker.launch("image/*") },
            )
        }
    }

    if (state.showHardConfirm) {
        HardLimitDialog(
            estimatedTokens = state.hardConfirmTokens,
            fullRawTokens = state.fullRawTokens,
            onContinue = vm::confirmHardContinue,
            onCancel = vm::cancelHardContinue,
        )
    }
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private const val TIME_GAP_MS = 5 * 60 * 1000L

private fun shouldShowTime(msgs: List<Message>, index: Int): Boolean {
    if (index <= 0) return true
    val cur = msgs[index].createdAt
    val prev = msgs[index - 1].createdAt
    return prev > 0 && cur > 0 && cur - prev > TIME_GAP_MS
}

private fun timeLabel(ts: Long): String {
    if (ts <= 0) return ""
    val zone = ZoneId.systemDefault()
    val now = Instant.now().atZone(zone).toLocalDate()
    val t = Instant.ofEpochMilli(ts).atZone(zone)
    val d = t.toLocalDate()
    val hour = t.hour
    val h = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
    val ampm = when { hour < 6 -> "凌晨"; hour < 12 -> "上午"; hour < 13 -> "中午"; hour < 18 -> "下午"; else -> "晚上" }
    val hm = ampm + " " + h + ":" + t.minute.toString().padStart(2, '0')
    return when {
        d == now -> hm
        d == now.minusDays(1) -> "昨天 $hm"
        d == now.minusDays(2) -> "前天 $hm"
        d.year == now.year -> "${d.monthValue}月${d.dayOfMonth}日 $hm"
        else -> "${d.year}年${d.monthValue}月${d.dayOfMonth}日 $hm"
    }
}

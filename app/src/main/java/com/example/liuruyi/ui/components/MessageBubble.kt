package com.example.liuruyi.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liuruyi.data.Message
import com.example.liuruyi.ui.theme.BubbleMine
import com.example.liuruyi.ui.theme.BubbleTheirs
import com.example.liuruyi.ui.theme.Pink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isStreaming: Boolean = false,
    highlight: String? = null,
    onLongClick: (() -> Unit)? = null,
    onPlayVoice: ((String) -> Unit)? = null,
    peerAvatarPath: String? = null,
    myAvatarPath: String? = null,
) {
    val isUser = message.role == "user"
    val imagePath = message.imagePath?.takeIf { it.isNotBlank() }
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, imagePath) {
        value = if (imagePath == null) null
        else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(imagePath)?.asImageBitmap() }.getOrNull()
        }
    }
    val bmp = bitmap
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            Avatar(filePath = peerAvatarPath, size = 36.dp, label = "靓")
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (isUser) BubbleMine else BubbleTheirs,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(onClick = {}, onLongClick = { onLongClick?.invoke() }),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!message.recalled && !message.quoteText.isNullOrBlank()) {
                    Text(
                        text = "引用：" + message.quoteText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFF8A8791),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (message.recalled) {
                    Text(
                        text = "撤回了一条消息",
                        color = Color(0xFF8A8791),
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                    )
                } else {
                    if (imagePath != null && bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "图片",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    val showText = imagePath == null || (message.content.isNotBlank() && message.content != "[图片]")
                    if (showText) {
                        Text(
                            text = if (isStreaming) message.content + "▌" else highlightText(message.content, highlight),
                            color = Color(0xFF16181D),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        )
                    }
                }
                if (message.kind == "proactive") {
                    Spacer(Modifier.height(4.dp))
                    Text("邱岳靓主动找你", color = Pink, fontSize = 11.sp)
                }
                if (message.status == "stopped") {
                    Spacer(Modifier.height(4.dp))
                    Text("已停止", color = Color.Gray, fontSize = 11.sp)
                } else if (message.status == "error") {
                    Spacer(Modifier.height(4.dp))
                    Text("发送失败", color = Color(0xFFD93025), fontSize = 11.sp)
                }
            }
        }
        if (message.role == "assistant" && !message.recalled && !isStreaming && message.content.isNotBlank() && onPlayVoice != null) {
            IconButton(
                onClick = { onPlayVoice(message.content) },
                modifier = Modifier.align(Alignment.CenterVertically).padding(start = 2.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "播放语音", tint = Pink)
            }
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Avatar(filePath = myAvatarPath, size = 36.dp, label = "我", fallbackColor = Color(0xFF7E8A97))
        }
    }
}

private fun highlightText(text: String, query: String?): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val lower = text.lowercase()
    val q = query.lowercase()
    if (!lower.contains(q)) return AnnotatedString(text)
    return buildAnnotatedString {
        var start = 0
        while (true) {
            val idx = lower.indexOf(q, start)
            if (idx < 0) { append(text.substring(start)); break }
            append(text.substring(start, idx))
            withStyle(SpanStyle(background = Color(0xFFFFE082), color = Color(0xFF1D1B20))) {
                append(text.substring(idx, idx + q.length))
            }
            start = idx + q.length
        }
    }
}

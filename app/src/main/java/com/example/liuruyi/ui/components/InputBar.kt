package com.example.liuruyi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liuruyi.data.Message

@Composable
fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    enabled: Boolean,
    quoteMessage: Message? = null,
    onClearQuote: () -> Unit = {},
    onPickImage: () -> Unit = {},
) {
    Surface(shadowElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (quoteMessage != null && !quoteMessage.recalled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF2F0FE), RoundedCornerShape(10.dp))
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "回复：" + (quoteMessage.quoteText ?: quoteMessage.content).take(40),
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = Color(0xFF6A6480),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onClearQuote) {
                        Icon(Icons.Filled.Close, contentDescription = "取消引用", tint = Color(0xFF8A8791))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onPickImage, enabled = enabled) {
                    Icon(Icons.Filled.Add, contentDescription = "发图片")
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("和邱岳靓说点什么…") },
                    maxLines = 5,
                    enabled = enabled,
                )
                if (isStreaming) {
                    FilledTonalButton(onClick = onStop) { Text("停止") }
                } else {
                    Button(onClick = onSend, enabled = enabled && input.isNotBlank()) { Text("发送") }
                }
            }
        }
    }
}

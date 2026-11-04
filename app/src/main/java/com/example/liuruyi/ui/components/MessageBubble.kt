package com.example.liuruyi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liuruyi.data.Message

@Composable
fun MessageBubble(
    message: Message,
    isStreaming: Boolean = false,
    onLongClick: () -> Unit = {},
    onPlayVoice: (String) -> Unit = {},
    peerAvatarPath: String? = null,
    myAvatarPath: String? = null,
) {
    val isMe = message.role == "user"
    val bgColor = if (isMe) Color(0xFF95EC69) else Color.White
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
    ) {
        if (!isMe) {
            Avatar(peerAvatarPath, size = 40.dp)
            Spacer(Modifier.width(8.dp))
        }
        
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .background(bgColor, RoundedCornerShape(8.dp))
                .clickable(onClick = onLongClick)
                .padding(12.dp)
        ) {
            Text(
                text = if (isStreaming) message.content + "▍" else message.content,
                color = Color(0xFF16181D),
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
        }
        
        if (isMe) {
            Spacer(Modifier.width(8.dp))
            Avatar(myAvatarPath, size = 40.dp)
        }
    }
}

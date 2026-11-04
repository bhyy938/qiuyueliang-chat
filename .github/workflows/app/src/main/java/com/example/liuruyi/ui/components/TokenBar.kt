package com.example.liuruyi.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TokenBar(estimatedTokens: Int, modelName: String) {
    val color = when {
        estimatedTokens > 7000 -> Color(0xFFD93025)
        estimatedTokens > 4500 -> Color(0xFFB25800)
        else -> Color.Gray
    }
    Text(
        text = "上下文约 " + estimatedTokens + " token · 软限 4500 · 告警 7000 · " + modelName,
        fontSize = 11.sp,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

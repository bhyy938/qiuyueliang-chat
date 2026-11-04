package com.example.liuruyi.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun HardLimitDialog(
    estimatedTokens: Int,
    fullRawTokens: Int,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("上下文超限") },
        text = {
            Text(
                "上下文已超过 7000 token（约 " + estimatedTokens +
                    "）。继续会带上全部旧原文（约 " + fullRawTokens + " token），消耗较多 token。",
            )
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("继续发送") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

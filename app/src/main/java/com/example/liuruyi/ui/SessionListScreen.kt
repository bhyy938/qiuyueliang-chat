package com.example.liuruyi.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SessionListScreen(vm: MainViewModel, onOpenSession: (String) -> Unit, onOpenSettings: () -> Unit) {
    Text("会话列表 (已清空)")
}

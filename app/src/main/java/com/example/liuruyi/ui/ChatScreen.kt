package com.example.liuruyi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.liuruyi.ui.components.InputBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("简单聊天 (编译测试版)") },
                navigationIcon = {
                    IconButton(onClick = onBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") 
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            Text("这里显示聊天记录（省略复杂UI）", modifier = Modifier.weight(1f))
            
            InputBar(
                input = state.input,
                onInputChange = vm::onInputChange,
                onSend = { vm.sendMessage(state.input) },
                onStop = vm::stopStreaming,
                isStreaming = state.isStreaming,
                enabled = state.activeSessionId.isNotBlank(),
                quoteMessage = state.quoteMessage,
                onClearQuote = vm::clearQuote,
                onPickImage = { /* 省略选图 */ },
            )
        }
    }
}

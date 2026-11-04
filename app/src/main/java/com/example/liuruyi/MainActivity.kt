package com.example.liuruyi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.liuruyi.ui.AppNavHost
import com.example.liuruyi.ui.MainViewModel
import com.example.liuruyi.ui.theme.LiuruyiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiuruyiTheme {
                val vm: MainViewModel = viewModel()
                AppNavHost(vm)
            }
        }
    }
}

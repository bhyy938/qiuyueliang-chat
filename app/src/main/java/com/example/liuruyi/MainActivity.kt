package com.example.liuruyi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.liuruyi.ui.ChatScreen
import com.example.liuruyi.ui.MainViewModel
import com.example.liuruyi.ui.SessionListScreen
import com.example.liuruyi.ui.SettingsScreen
import com.example.liuruyi.ui.theme.LiuruyiTheme
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scheduleProactive()
        requestNotificationPermissionIfNeeded()
        setContent {
            LiuruyiTheme {
                val vm: MainViewModel = viewModel()
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) vm.onAppForeground()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                AppNavHost(vm)
            }
        }
    }

    private fun scheduleProactive() {
        val request = PeriodicWorkRequestBuilder<ProactiveWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "liuruyi-proactive",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED,
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun AppNavHost(vm: MainViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "sessions") {
        composable("sessions") {
            SessionListScreen(
                vm = vm,
                onOpenSession = { id -> navController.navigate("chat/" + id) },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("chat/{sessionId}") {
            ChatScreen(vm = vm, onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(vm = vm, onBack = { navController.popBackStack() })
        }
    }
}

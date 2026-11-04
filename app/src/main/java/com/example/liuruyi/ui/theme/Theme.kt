package com.example.liuruyi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple = Color(0xFF7C5CFC)
val Pink = Color(0xFFFF7AB8)
val BubbleMine = Color(0xFF95EC69)
val BubbleTheirs = Color(0xFFFFFFFF)
val WeChatBg = Color(0xFFEDEDED)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = Pink,
    surface = Color(0xFFFDFCFF),
    background = Color(0xFFFDFCFF),
)

private val DarkColors = darkColorScheme(
    primary = Purple,
    secondary = Pink,
)

@Composable
fun LiuruyiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

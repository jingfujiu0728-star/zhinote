package com.example.zhinote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.zhinote.data.repository.UserSettings

val DropColors = listOf(Color(0xFFA4C6DD), Color(0xFFA8CCBC), Color(0xFFC5B8DE), Color(0xFFE6BCCB),
    Color(0xFFB6D8C9), Color(0xFFE4D8AC), Color(0xFFB0D1D5))
private val LightColors = lightColorScheme(primary = Color(0xFF456C70), onPrimary = Color.White,
    secondary = Color(0xFF877A99), background = Color(0xFFF3F5F1), surface = Color(0xFFF9FAF6),
    surfaceVariant = Color(0xFFE6EBE5), onSurface = Color(0xFF293C3E), onSurfaceVariant = Color(0xFF657779), outline = Color(0xFFB7C7C2))
private val DarkColors = darkColorScheme(primary = Color(0xFFA6CEC9), onPrimary = Color(0xFF193A3E),
    secondary = Color(0xFFCFBEDD), background = Color(0xFF172627), surface = Color(0xFF223436),
    surfaceVariant = Color(0xFF304446), onSurface = Color(0xFFE3ECE6), onSurfaceVariant = Color(0xFFADC2BF), outline = Color(0xFF536B69))
@Composable
fun ZhiTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val dark = settings.theme == "DARK" || (settings.theme == "SYSTEM" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

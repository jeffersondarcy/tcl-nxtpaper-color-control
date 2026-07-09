package com.jeff.tclcolorcontrol.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color.White,
    secondary = Color(0xFF475569),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFE7E5E4),
    onSurfaceVariant = Color(0xFF57534E),
)

@Composable
fun TclColorControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}

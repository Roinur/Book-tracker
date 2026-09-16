package com.roinur.booktracker

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun BookTrackerApp(vm: BookTrackerViewModel) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDark = when (vm.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val fallbackScheme = if (useDark) {
        darkColorScheme(
            primary = Color(0xFF8BC1FF),
            onPrimary = Color(0xFF002B52),
            secondary = Color(0xFF8CC8A8),
            background = Color(0xFF1D2127),
            onBackground = Color(0xFFE9EDF2),
            surface = Color(0xFF292E36),
            onSurface = Color(0xFFE9EDF2),
            onSurfaceVariant = Color(0xFFB4BEC8),
            error = Color(0xFFFF8A8A)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1F63D8),
            onPrimary = Color.White,
            secondary = Color(0xFF0D8F4F),
            background = Color(0xFFF6F8FB),
            onBackground = Color(0xFF1F2935),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1F2935),
            onSurfaceVariant = Color(0xFF5C6470),
            error = Color(0xFFB00020)
        )
    }
    val baseScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        fallbackScheme
    }
    val colorScheme = applyAccentMode(baseScheme, vm.accentMode, useDark)
    MaterialTheme(colorScheme = colorScheme) {
        ApplySystemBars(
            darkContent = !useDark,
            barColor = colorScheme.background.toArgb()
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BookAdaptiveViewport { BookTrackerScreen(vm) }
        }
    }
}

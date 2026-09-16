package com.roinur.booktracker

import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Reference viewport for the existing 420 x 933 dp portrait design.
internal fun bookViewportScale(widthDp: Float, heightDp: Float): Float =
    minOf(1f, widthDp / 420f, heightDp / (2800f / 3f)).coerceAtLeast(0.1f)

@Composable
internal fun BookAdaptiveViewport(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    BoxWithConstraints(
        Modifier.fillMaxSize().windowInsetsPadding(
            WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        )
    ) {
        // TopAppBar already applies top insets. Do not add them a second time.
        val scale = bookViewportScale(maxWidth.value, maxHeight.value)
        val adaptedConfiguration = Configuration(configuration).apply {
            screenWidthDp = (configuration.screenWidthDp / scale).toInt()
            screenHeightDp = (configuration.screenHeightDp / scale).toInt()
        }
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * scale, density.fontScale),
            LocalConfiguration provides adaptedConfiguration,
            content = content
        )
    }
}

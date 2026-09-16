package com.roinur.booktracker

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class BookAccentContrastTest {
    @Test fun customAccentsKeepReadableForegroundsInBothThemes() {
        for (dark in listOf(false, true)) {
            val base = if (dark) darkColorScheme() else lightColorScheme()
            for (mode in AccentMode.entries) {
                if (accentColorForMode(mode) == null) continue
                val scheme = applyAccentMode(base, mode, dark)
                for ((foreground, background) in listOf(
                    scheme.primary to scheme.surface,
                    scheme.primary to scheme.background,
                    scheme.onPrimary to scheme.primary,
                    scheme.onPrimaryContainer to scheme.primaryContainer,
                    scheme.onSecondaryContainer to scheme.secondaryContainer,
                    scheme.onTertiaryContainer to scheme.tertiaryContainer
                )) assertTrue("$mode dark=$dark", contrastRatio(foreground, background) >= 4.5f)
                assertTrue(scheme.primaryContainer.alpha == 1f)
            }
        }
    }
}

package com.roinur.booktracker

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var bookVm: BookTrackerViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookVm = ViewModelProvider(this)[BookTrackerViewModel::class.java]
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 120f
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            BookTrackerApp(bookVm)
        }

        handleIncomingShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::bookVm.isInitialized) bookVm.reload()
    }

    private fun handleIncomingShareIntent(incoming: Intent?) {
        if (!::bookVm.isInitialized) return
        val intent = incoming ?: return
        if (!Intent.ACTION_SEND.equals(intent.action, ignoreCase = true)) return

        val sharedText = buildList {
            val direct = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (direct.isNotBlank()) add(direct)
            val clipText = runCatching {
                intent.clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(this@MainActivity)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            }.getOrDefault("")
            if (clipText.isNotBlank()) add(clipText)
        }.firstOrNull { it.isNotBlank() }.orEmpty()

        if (sharedText.isBlank()) return
        bookVm.queueIncomingText(sharedText)

        intent.removeExtra(Intent.EXTRA_TEXT)
    }
}

package com.crinklecodone.mochibun.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.crinklecodone.mochibun.ui.theme.MochibunTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.crinklecodone.mochibun.data.repository.IconPackManager


class MainActivity : ComponentActivity() {
    var homePressCount by mutableStateOf(0)
        private set

    var requestedPage by mutableStateOf<Int?>(null)
        private set

    fun consumeRequestedPage() {
        requestedPage = null
    }

    var appListRefreshCount by mutableIntStateOf(0)

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (
                intent?.action == Intent.ACTION_PACKAGE_ADDED ||
                intent?.action == Intent.ACTION_PACKAGE_REMOVED ||
                intent?.action == Intent.ACTION_PACKAGE_REPLACED
            ) {
                IconPackManager.clearCache()
                appListRefreshCount++
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: IllegalArgumentException) {}
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.hasExtra("OPEN_PAGE")) {
            val page = intent.getIntExtra("OPEN_PAGE", 1)
            requestedPage = page.coerceIn(0, 1)
        }

        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePressCount++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.hasExtra("OPEN_PAGE") == true) {
            val page = intent.getIntExtra("OPEN_PAGE", 1)
            requestedPage = page.coerceIn(0, 1)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        registerReceiver(packageChangeReceiver, packageFilter)

        enableEdgeToEdge()
        setContent {
            MochibunTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainLauncher(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding),
                        homePressCount = homePressCount,
                        appListRefreshCount = appListRefreshCount,
                        requestedPage = requestedPage,
                        onPageConsumed = ::consumeRequestedPage
                    )
                }
            }
        }
    }
}

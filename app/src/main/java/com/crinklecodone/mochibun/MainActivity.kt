package com.crinklecodone.mochibun

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
import com.crinklecodone.mochibun.ui.theme.M120LauncherTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// main entry point activity for our kawaii home launcher :3
class MainActivity : ComponentActivity() {
    // tracks how many times user pressed physical home button <3
    var homePressCount by mutableStateOf(0)
        private set

    // tracks external requests to switch pages (e.g. from Button Remapper service)
    var requestedPage by mutableStateOf<Int?>(null)
        private set

    fun consumeRequestedPage() {
        requestedPage = null
    }

    // counter to trigger app list refresh when apps change :3
    var appListRefreshCount by mutableIntStateOf(0)

    // listens for package added, removed, or replaced broadcasts <3
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

    // unregister broadcast receiver safely when activity is destroyed :3
    override fun onDestroy() {
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: IllegalArgumentException) {}
        super.onDestroy()
    }

    // handles physical home button presses when already running <3
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.hasExtra("OPEN_PAGE")) {
            val page = intent.getIntExtra("OPEN_PAGE", 1)
            // TODO: Re-enable AI Chat page for future release
            // Clamp requested page to active pages [0, 1] (Widgets, Home) while AI Chat is disconnected
            requestedPage = page.coerceIn(0, 1)
        }

        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePressCount++
        }
    }

    // sets up edge-to-edge system bars and sets root compose content :3
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.hasExtra("OPEN_PAGE") == true) {
            val page = intent.getIntExtra("OPEN_PAGE", 1)
            // TODO: Re-enable AI Chat page for future release
            // Clamp requested page to active pages [0, 1] (Widgets, Home) while AI Chat is disconnected
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
            M120LauncherTheme {
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

package com.crinklecodone.mochibun.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.crinklecodone.mochibun.core.system.ClamshellListener
import com.crinklecodone.mochibun.core.system.ClamshellManager
import com.crinklecodone.mochibun.ui.theme.MochibunTheme
import com.crinklecodone.mochibun.ui.theme.ThemeBackground
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.crinklecodone.mochibun.data.repository.IconPackManager
import com.crinklecodone.mochibun.service.MochibunAccessibilityService

class MainActivity : ComponentActivity() {
    private val launcherViewModel: LauncherViewModel by viewModels()

    var homePressCount by mutableStateOf(0)
        private set

    // True when the latest Home press arrived while the launcher already had window focus
    // (user was looking at Mochibun), as opposed to returning from another app.
    var homePressedWhileVisible by mutableStateOf(false)
        private set

    var requestedPage by mutableStateOf<Int?>(null)
        private set

    fun consumeRequestedPage() {
        requestedPage = null
    }

    var pendingThemeBundleUri by mutableStateOf<android.net.Uri?>(null)
        private set

    fun consumePendingThemeBundleUri() {
        pendingThemeBundleUri = null
        try {
            intent?.data = null
        } catch (_: Exception) {}
    }

    var appListRefreshCount by mutableIntStateOf(0)

    var lidOpenCount by mutableIntStateOf(0)
        private set

    var lidClosedCount by mutableIntStateOf(0)
        private set

    private val clamshellListener = object : ClamshellListener {
        override fun onLidClosed() {
            lidClosedCount++
        }

        override fun onLidOpened() {
            lidOpenCount++
        }
    }

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

    override fun onResume() {
        super.onResume()
        MochibunAccessibilityService.isLauncherForeground = true
        com.crinklecodone.mochibun.core.system.RootAccessibilityWatchdog.checkAsync(this, force = false)
    }

    override fun onPause() {
        super.onPause()
        MochibunAccessibilityService.isLauncherForeground = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ClamshellManager.onConfigurationChanged(newConfig)
    }

    companion object {
        @Volatile
        var keyEventInterceptor: ((KeyEvent) -> Boolean)? = null
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Already handled by MochibunAccessibilityService; don't run it through the launcher again
        if (MochibunAccessibilityService.wasConsumedByService(event)) return true
        val interceptor = keyEventInterceptor
        if (interceptor != null && interceptor(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        keyEventInterceptor = null
        MochibunAccessibilityService.isLauncherForeground = false
        ClamshellManager.removeListener(clamshellListener)
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: IllegalArgumentException) {}
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.hasExtra("OPEN_PAGE")) {
            val page = intent.getIntExtra("OPEN_PAGE", 1)
            requestedPage = page.coerceIn(0, 2)
        }

        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // When SystemUI triggers Recents on Android 12 with a 3rd party launcher,
            // it starts the default Home activity with FLAG_ACTIVITY_RESET_TASK_IF_NEEDED (0x00200000)
            // without FLAG_ACTIVITY_TASK_ON_HOME (0x00000100).
            val isRecentAppsInvocation = (intent.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED != 0) &&
                    (intent.flags and 0x00000100 == 0)
            if (isRecentAppsInvocation) {
                val recentsIntent = Intent(this, com.android.quickstep.RecentsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(recentsIntent)
            } else {
                homePressedWhileVisible = hasWindowFocus() &&
                    (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0)
                homePressCount++
            }
        }

        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            pendingThemeBundleUri = intent.data
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.hasExtra("OPEN_PAGE") == true) {
            val page = intent.getIntExtra("OPEN_PAGE", 1)
            requestedPage = page.coerceIn(0, 2)
        }

        if (intent?.action == Intent.ACTION_VIEW && intent?.data != null) {
            pendingThemeBundleUri = intent?.data
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.isSoundEffectsEnabled = false
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
        ClamshellManager.init(this)
        ClamshellManager.addListener(clamshellListener)

        enableEdgeToEdge()
        setContent {
            MochibunTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ThemeBackground
                ) { innerPadding ->
                    MainLauncher(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = launcherViewModel,
                        homePressCount = homePressCount,
                        homePressedWhileVisible = homePressedWhileVisible,
                        appListRefreshCount = appListRefreshCount,
                        lidOpenCount = lidOpenCount,
                        lidClosedCount = lidClosedCount,
                        requestedPage = requestedPage,
                        onPageConsumed = ::consumeRequestedPage,
                        incomingBundleUri = pendingThemeBundleUri,
                        onConsumeIncomingBundleUri = ::consumePendingThemeBundleUri
                    )
                }
            }
        }
    }
}

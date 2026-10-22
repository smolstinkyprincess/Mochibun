package com.crinklecodone.mochibun

import android.app.Application
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.ExternalThemeManager
import com.topjohnwu.superuser.Shell

class MochibunApp : Application() {

    init {
        // Set flags to enable verbose logging in debug builds
        Shell.enableVerboseLogging = BuildConfig.DEBUG

        // Establish default builder configuration
        // Process creation is lazy: no su process or thread is spawned
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(10))
    }

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
        ExternalThemeManager.init(this)
    }
}

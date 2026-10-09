/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep

import android.app.Service
import android.content.Intent
import android.graphics.Rect
import android.graphics.Region
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.systemui.shared.recents.IOverviewProxy
import com.android.systemui.shared.recents.ISystemUiProxy

/**
 * Service connected by System UI for handling touch interaction and Quickstep recents.
 */
class TouchInteractionService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var systemUiProxy: ISystemUiProxy? = null

    private val binder = object : IOverviewProxy.Stub() {

        override fun onInitialize(bundle: Bundle?) {
            Log.d(TAG, "!!! onInitialize IPC RECEIVED !!!")
            Log.d(TAG, "bundle=$bundle")

            sIsInitialized = true
        }

        override fun onOverviewToggle() {
            Log.d(TAG, "onOverviewToggle called from SystemUI")
            showRecentsActivity()
        }

        override fun onOverviewShown(triggeredFromAltTab: Boolean) {
            Log.d(TAG, "onOverviewShown called from SystemUI (altTab=$triggeredFromAltTab)")
            showRecentsActivity()
        }

        override fun onOverviewHidden(triggeredFromAltTab: Boolean, triggeredFromHomeKey: Boolean) {
            Log.d(TAG, "onOverviewHidden called from SystemUI")
        }

        override fun onTip(actionType: Int, viewType: Int) {}

        override fun onAssistantAvailable(available: Boolean) {}

        override fun onAssistantVisibilityChanged(visibility: Float) {}

        override fun onBackAction(
            completed: Boolean,
            downX: Int,
            downY: Int,
            isButton: Boolean,
            gestureSwipeLeft: Boolean
        ) {}

        override fun onSystemUiStateChanged(stateFlags: Int) {}

        override fun onActiveNavBarRegionChanges(region: Region?) {}

        override fun onSplitScreenSecondaryBoundsChanged(bounds: Rect?, insets: Rect?) {}

        override fun onImeWindowStatusChanged(
            displayId: Int,
            token: IBinder?,
            vis: Int,
            backDisposition: Int,
            showImeSwitcher: Boolean
        ) {}
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "TouchInteractionService bound by intent: $intent")
        sIsConnected = true
        Log.d(TAG, "Returning IOverviewProxy binder: $binder")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "TouchInteractionService unbound")
        sIsConnected = false
        sIsInitialized = false
        return super.onUnbind(intent)
    }

    private fun showRecentsActivity() {
        mainHandler.post {
            try {
                val recentsIntent = Intent(this, RecentsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(recentsIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start RecentsActivity", e)
            }
        }
    }

    companion object {
        private const val TAG = "TouchInteractionService"
        private const val KEY_EXTRA_SYSUI_PROXY = "extra_sysui_proxy"

        @Volatile
        private var sIsConnected = false

        @Volatile
        private var sIsInitialized = false

        fun isConnected(): Boolean = sIsConnected
        fun isInitialized(): Boolean = sIsInitialized
    }
}

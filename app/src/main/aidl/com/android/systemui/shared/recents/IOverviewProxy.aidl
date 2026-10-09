/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.shared.recents;

import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;

interface IOverviewProxy {
    void onInitialize(in Bundle bundle) = 11;
    void onOverviewToggle() = 0;
    void onOverviewShown(boolean triggeredFromAltTab) = 1;
    void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) = 2;
    void onTip(int actionType, int viewType) = 3;
    void onAssistantAvailable(boolean available) = 4;
    void onAssistantVisibilityChanged(float visibility) = 5;
    void onBackAction(boolean completed, int downX, int downY, boolean isButton, boolean gestureSwipeLeft) = 6;
    void onSystemUiStateChanged(int stateFlags) = 7;
    void onActiveNavBarRegionChanges(in Region region) = 8;
    void onSplitScreenSecondaryBoundsChanged(in Rect bounds, in Rect insets) = 9;
    void onImeWindowStatusChanged(int displayId, IBinder token, int vis, int backDisposition, boolean showImeSwitcher) = 10;
}

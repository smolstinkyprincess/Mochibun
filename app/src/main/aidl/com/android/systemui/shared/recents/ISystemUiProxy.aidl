/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;

interface ISystemUiProxy {
    void startScreenPinning(int taskId) = 0;
    void onOverviewShown(boolean fromHome) = 1;
    Rect getNonMinimizedSplitScreenSecondaryBounds() = 2;
    void setNavBarButtonAlpha(float alpha, boolean animate) = 3;
    void onBackPressed() = 4;
    void setHomeRotationEnabled(boolean enabled) = 5;
    void notifyAccessibilityButtonClicked(int displayId) = 6;
    void notifyAccessibilityButtonLongClicked() = 7;
    void stopScreenPinning() = 8;
    void handleImageAsScreenshot(in Bitmap bitmap, in Rect rect, in Insets insets, int taskId) = 9;
    void setSplitScreenMinimized(boolean minimized) = 10;
    void notifySwipeUpGestureStarted() = 11;
    void notifySwipeToHomeFinished() = 12;
    void notifyPrioritizedRotation(int rotation) = 13;
    void expandNotificationPanel() = 14;
}

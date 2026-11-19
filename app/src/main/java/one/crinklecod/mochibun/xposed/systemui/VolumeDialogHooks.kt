package one.crinklecod.mochibun.xposed.systemui

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.crinklecod.mochibun.xposed.systemui.SystemUiDrawables.createRingerCircleDrawable
import one.crinklecod.mochibun.xposed.systemui.SystemUiDrawables.createSquircleDrawable
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.ensureReceiverRegistered
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedVolumeDialogs

/** Stage C: VolumeDialogImpl squircle container, single-stream enforcement, slider tinting, ringer drawer collapse, compact 128dp sizing. */
internal object VolumeDialogHooks {

    /** Installs this module's hooks; called once by [MochibunSystemUiHook] after the kill-switch check. */
    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        hookVolume(classLoader)
    }

    internal fun styleVolumeRow(row: Any) {
        val slider = XposedHelpers.getObjectField(row, "slider") as? SeekBar
        val icon = XposedHelpers.getObjectField(row, "icon") as? ImageButton
        val sliderProgressSolid = XposedHelpers.getObjectField(row, "sliderProgressSolid") as? Drawable
        val sliderBgSolid = XposedHelpers.getObjectField(row, "sliderBgSolid") as? Drawable
        val sliderProgressIcon = XposedHelpers.getObjectField(row, "sliderProgressIcon") as? Drawable
        val sliderBgIcon = XposedHelpers.getObjectField(row, "sliderBgIcon") as? Drawable

        sliderProgressSolid?.setTintList(ActiveHookTheme.primaryColorStateList)
        slider?.progressTintList = ActiveHookTheme.primaryColorStateList

        sliderBgSolid?.setTintList(ActiveHookTheme.trackColorStateList)
        slider?.progressBackgroundTintList = ActiveHookTheme.trackColorStateList
        val progressDrawable = slider?.progressDrawable as? LayerDrawable
        progressDrawable?.findDrawableByLayerId(android.R.id.background)?.setTintList(ActiveHookTheme.trackColorStateList)

        icon?.imageTintList = ActiveHookTheme.accentDeepColorStateList
        icon?.imageAlpha = 255
        sliderProgressIcon?.setTintList(ActiveHookTheme.onPrimaryColorStateList)
        sliderBgIcon?.setTintList(ActiveHookTheme.onTrackColorStateList)

        val number = XposedHelpers.getObjectField(row, "number") as? TextView
        number?.setTextColor(ActiveHookTheme.accentDeep)
    }

    @android.annotation.SuppressLint("DiscouragedApi")
    internal fun applySquircleToDialog(instance: Any) {
        val dialog = XposedHelpers.getObjectField(instance, "mDialog") as? Dialog ?: return
        val context = dialog.context
        val res = context.resources
        val density = res.displayMetrics.density

        fun findView(idName: String): View? {
            val id = res.getIdentifier(idName, "id", TARGET_PACKAGE)
            return if (id != 0) dialog.findViewById(id) else null
        }

        val topContainer = findView("volume_dialog_top_container")
        if (topContainer != null) {
            topContainer.background = createSquircleDrawable(density)
            topContainer.clipToOutline = true
        }

        val ringerDrawerContainer = findView("volume_ringer_and_drawer_container")
        ringerDrawerContainer?.background = LayerDrawable(arrayOf(ColorDrawable(0)))

        val settingsContainer = findView("settings_container")
        settingsContainer?.background = ColorDrawable(0)

        val captionsView = findView("odi_captions")
        if (captionsView != null) {
            captionsView.background = createSquircleDrawable(density)
            captionsView.clipToOutline = true
        }

        val isDrawerOpen = try {
            XposedHelpers.getBooleanField(instance, "mIsRingerDrawerOpen")
        } catch (_: Throwable) {
            false
        }
        if (!isDrawerOpen) {
            val drawerContainer = findView("volume_drawer_container")
            drawerContainer?.visibility = View.GONE
        }

        val settingsIcon = findView("settings") as? ImageButton
        settingsIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        val ringerMuteIcon = findView("volume_drawer_mute_icon") as? ImageView
        ringerMuteIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        val ringerVibrateIcon = findView("volume_drawer_vibrate_icon") as? ImageView
        ringerVibrateIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        val ringerNormalIcon = findView("volume_drawer_normal_icon") as? ImageView
        ringerNormalIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        val captionsIcon = findView("odi_captions_icon") as? ImageView
        captionsIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        styleRingerButton(instance)
    }

    /**
     * Styles the volume dialog's sound-mode (ringer) button.
     * On this Android 12 build the visible button is the ringer drawer's
     * `volume_new_ringer_active_icon_container` (stock: colorAccent circle) holding
     * `volume_new_ringer_active_icon` (stock: textColorPrimaryInverse glyph). The open drawer's
     * `volume_drawer_selection_background` uses the same accent circle. The legacy `ringer` /
     * `ringer_icon` pair is `visibility="gone"` in the stock layout but is styled too in case a
     * ROM variant shows it. Each view is looked up separately so a missing field is just skipped.
     * SystemUI never resets these backgrounds after initDialog, so frequent callers
     * (updateRingerH) pass includeBackgrounds = false and only re-tint the glyphs.
     */
    private fun styleRingerButton(instance: Any, includeBackgrounds: Boolean = true) {
        fun field(name: String): View? = try {
            XposedHelpers.getObjectField(instance, name) as? View
        } catch (_: Throwable) {
            null
        }

        val circleViews = if (!includeBackgrounds) emptyList() else listOf(
            field("mSelectedRingerContainer"),
            field("mRingerDrawerNewSelectionBg"),
            field("mRinger"),
        )
        for (view in circleViews) {
            if (view == null) continue
            try {
                view.background = createRingerCircleDrawable(view.resources.displayMetrics.density)
                view.backgroundTintList = null
                view.clipToOutline = true
            } catch (t: Throwable) {
                Log.d(TAG, "Failed styling volume ringer circle", t)
            }
        }

        for (name in listOf("mSelectedRingerIcon", "mRingerIcon")) {
            try {
                (field(name) as? ImageView)?.imageTintList = ActiveHookTheme.onPrimaryColorStateList
            } catch (t: Throwable) {
                Log.d(TAG, "Failed tinting volume ringer glyph $name", t)
            }
        }
    }

    /**
     * Stage C: Squircle volume dialog in VolumeDialogImpl.
     * - Target VolumeDialogImpl.initDialog() (signature: private void initDialog()).
     * - Apply a GradientDrawable (corner radius 20dp * density, fill ActiveHookTheme.container,
     *   1.5dp stroke ActiveHookTheme.volumeStroke) to dialog content views by id name (do NOT touch window background).
     * - Style volume rows (initRow & updateVolumeRowTintH):
     *   slider progress tint ActiveHookTheme.primaryColorStateList,
     *   slider track tint ActiveHookTheme.trackColorStateList,
     *   icons ActiveHookTheme.accentDeepColorStateList.
     * - Re-apply on updates without allocating drawables per update.
     */
    @android.annotation.SuppressLint("DiscouragedApi")
    private fun hookVolume(classLoader: ClassLoader) {
        val volumeDialogImplClassName = "com.android.systemui.volume.VolumeDialogImpl"
        val volumeRowClass = try {
            XposedHelpers.findClass(
                "com.android.systemui.volume.VolumeDialogImpl\$VolumeRow",
                classLoader
            )
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to find VolumeRow class", t)
            null
        }

        // 1. Hook initDialog() to style dialog content views with squircle drawables
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "initDialog",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            trackedVolumeDialogs.add(param.thisObject)
                            val dialog = XposedHelpers.getObjectField(param.thisObject, "mDialog") as? Dialog
                            val context = dialog?.context
                            if (context != null) {
                                ensureReceiverRegistered(context.applicationContext ?: context)
                            }
                            XposedHelpers.setBooleanField(param.thisObject, "mShowActiveStreamOnly", true)
                            applySquircleToDialog(param.thisObject)
                            Log.d(TAG, "Applied squircle backgrounds to VolumeDialogImpl content views")
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.initDialog hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.initDialog")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.initDialog", t)
        }

        // 2. Hook setTopContainerBackgroundDrawable() so subsequent layout calls do not overwrite squircle
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "setTopContainerBackgroundDrawable",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val topContainer = XposedHelpers.getObjectField(param.thisObject, "mTopContainer") as? View
                            if (topContainer != null) {
                                val density = topContainer.resources.displayMetrics.density
                                topContainer.background = createSquircleDrawable(density)
                                topContainer.clipToOutline = true
                            }
                            val ringerDrawer = XposedHelpers.getObjectField(param.thisObject, "mRingerAndDrawerContainer") as? View
                            ringerDrawer?.background = LayerDrawable(arrayOf(ColorDrawable(0)))
                            val settingsView = XposedHelpers.getObjectField(param.thisObject, "mSettingsView") as? View
                            settingsView?.background = ColorDrawable(0)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.setTopContainerBackgroundDrawable hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.setTopContainerBackgroundDrawable")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.setTopContainerBackgroundDrawable", t)
        }

        // 3. Hook initRow to style rows and scale slider height for 480x800 display
        if (volumeRowClass != null) {
            try {
                XposedHelpers.findAndHookMethod(
                    volumeDialogImplClassName,
                    classLoader,
                    "initRow",
                    volumeRowClass,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val row = param.args[0] ?: return
                                styleVolumeRow(row)

                                val rowView = XposedHelpers.getObjectField(row, "view") as? View
                                rowView?.background = ColorDrawable(0)

                                val res = rowView?.resources ?: return
                                val density = res.displayMetrics.density
                                val sliderFrameId = res.getIdentifier("volume_row_slider_frame", "id", TARGET_PACKAGE)
                                val sliderFrame = if (sliderFrameId != 0) rowView.findViewById<View>(sliderFrameId) else null
                                val slider = XposedHelpers.getObjectField(row, "slider") as? SeekBar

                                // Scale down from stock 192dp to 128dp for compact 480x800 display
                                val targetHeight = (128f * density).toInt()
                                sliderFrame?.layoutParams?.let { lp ->
                                    lp.height = targetHeight
                                    sliderFrame.layoutParams = lp
                                }
                                slider?.layoutParams?.let { lp ->
                                    lp.width = targetHeight
                                    slider.layoutParams = lp
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in VolumeDialogImpl.initRow hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: VolumeDialogImpl.initRow")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook VolumeDialogImpl.initRow", t)
            }

            // 4. Hook updateVolumeRowTintH to re-apply custom tints whenever SystemUI re-tints sliders
            try {
                XposedHelpers.findAndHookMethod(
                    volumeDialogImplClassName,
                    classLoader,
                    "updateVolumeRowTintH",
                    volumeRowClass,
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val row = param.args[0]
                                if (row != null) {
                                    styleVolumeRow(row)
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in VolumeDialogImpl.updateVolumeRowTintH hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: VolumeDialogImpl.updateVolumeRowTintH")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook VolumeDialogImpl.updateVolumeRowTintH", t)
            }

            // 5. Hook shouldBeVisibleH to enforce strictly single active slider
            try {
                XposedHelpers.findAndHookMethod(
                    volumeDialogImplClassName,
                    classLoader,
                    "shouldBeVisibleH",
                    volumeRowClass,
                    volumeRowClass,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val row = param.args[0]
                                val activeRow = param.args[1]
                                if (row != null && activeRow != null) {
                                    val rowStream = XposedHelpers.getIntField(row, "stream")
                                    val activeStream = XposedHelpers.getIntField(activeRow, "stream")
                                    param.result = (rowStream == activeStream)
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in VolumeDialogImpl.shouldBeVisibleH hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: VolumeDialogImpl.shouldBeVisibleH")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook VolumeDialogImpl.shouldBeVisibleH", t)
            }

            // 6. Hook updateRowsH to ensure all row views keep transparent backgrounds (no dark capsule)
            try {
                XposedHelpers.findAndHookMethod(
                    volumeDialogImplClassName,
                    classLoader,
                    "updateRowsH",
                    volumeRowClass,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val rows = XposedHelpers.getObjectField(param.thisObject, "mRows") as? List<*> ?: return
                                for (row in rows) {
                                    if (row != null) {
                                        val rowView = XposedHelpers.getObjectField(row, "view") as? View
                                        rowView?.background = ColorDrawable(0)
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in VolumeDialogImpl.updateRowsH hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: VolumeDialogImpl.updateRowsH")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook VolumeDialogImpl.updateRowsH", t)
            }
        }

        // 7a. Hook updateRingerH so ringer mode changes (Ring / Vibrate / Silent) keep the themed button
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "updateRingerH",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            styleRingerButton(param.thisObject, includeBackgrounds = false)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.updateRingerH hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.updateRingerH")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.updateRingerH", t)
        }

        // 7. Hook showH to ensure drawer void remains GONE when dialog opens
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "showH",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            // Force active stream to STREAM_MUSIC (3) whenever the dialog is shown
                            XposedHelpers.setIntField(param.thisObject, "mActiveStream", android.media.AudioManager.STREAM_MUSIC)

                            val isDrawerOpen = XposedHelpers.getBooleanField(param.thisObject, "mIsRingerDrawerOpen")
                            if (!isDrawerOpen) {
                                val drawer = XposedHelpers.getObjectField(param.thisObject, "mRingerDrawerContainer") as? View
                                drawer?.visibility = View.GONE
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.showH hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.showH")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.showH", t)
        }

        // 8. Hook hideRingerDrawer to set drawer container back to GONE when closed
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "hideRingerDrawer",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val drawer = XposedHelpers.getObjectField(param.thisObject, "mRingerDrawerContainer") as? View
                            drawer?.visibility = View.GONE
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.hideRingerDrawer hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.hideRingerDrawer")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.hideRingerDrawer", t)
        }
    }
}

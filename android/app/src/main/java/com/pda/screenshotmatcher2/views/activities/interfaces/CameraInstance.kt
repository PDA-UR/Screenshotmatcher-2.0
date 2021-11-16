package com.pda.screenshotmatcher2.views.activities.interfaces

import android.app.Activity
import android.view.TextureView

interface CameraInstance {
    fun getActivity(): Activity
    fun getTextureView(): TextureView
    fun getOrientation(): Int
}
package com.pda.screenshotmatcher2.views.interfaces

import android.app.Activity
import android.view.TextureView

/**
 * Interface that has to be implemented by any class using [CameraProvider][com.pda.screenshotmatcher2.viewHelpers.CameraProvider].
 */
interface CameraInstance {
    /**
     * Returns the current [Activity]
	 */
    fun getActivity(): Activity

    /**
     * Returns the [TextureView] on which the camera preview image will be displayed
	 * @return
	 */
    fun getTextureView(): TextureView

    /**
     * Returns the rotation of the current view
	 */
    fun getOrientation(): Int
}
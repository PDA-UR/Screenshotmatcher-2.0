package com.pda.screenshotmatcher2.models

import android.graphics.Bitmap

/**
 * Data model that stores all relevant information about the last capture process.
 *
 * Accessed through [CaptureModel][com.pda.screenshotmatcher2.models.CaptureModel].
 *
 * @property matchID The ID of the matching process, as returned by the server.
 * @property serverURL The server url, where the [cameraImage] has been sent to.
 * @property cameraImage The photo taken by the camera, sent to the server for matching.
 * @property fullScreenshot The full screenshot returned by the server as a result of the matching process. This is null until the user requests the full screenshot in the [ResultsActivity][com.pda.screenshotmatcher2.views.activities.ResultsActivity].
 * @property croppedScreenshot The cropped screenshot returned by the server as a result of the matching process.
 */
object CaptureModel {
    private var matchID: String? = null
    private var serverURL: String? = null
    private var fullScreenshot: Bitmap? = null
    private var croppedScreenshot: Bitmap? = null
    private var cameraImage: Bitmap? = null


    fun getMatchID(): String? {
        return this.matchID
    }

    fun getCameraImage(): Bitmap? {
        return this.cameraImage
    }

    fun getFullScreenshot(): Bitmap? {
        return this.fullScreenshot
    }

    fun getCroppedScreenshot(): Bitmap? {
        return this.croppedScreenshot
    }

    fun getServerURL(): String? {
        return serverURL
    }


    fun setServerURL(serverURL: String): String? {
        this.serverURL = serverURL
        return this.serverURL
    }

    fun setCameraImage(cameraImage: Bitmap): Bitmap? {
        this.cameraImage = cameraImage
        return this.cameraImage
    }

    fun setMatchID(matchID: String): String? {
        this.matchID = matchID
        return this.matchID
    }

    fun setCroppedScreenshot(croppedScreenshot: Bitmap?): Bitmap? {
        this.croppedScreenshot = croppedScreenshot
        return this.croppedScreenshot
    }

    fun setFullScreenshot(fullScreenshot: Bitmap?): Bitmap? {
        this.fullScreenshot = fullScreenshot
        return this.fullScreenshot
    }


    /**
     * Resets all properties to null. Called at the start of a new capture process.
     */
    fun clear() {
        this.matchID = null
        this.serverURL = null
        this.fullScreenshot = null
        this.croppedScreenshot = null
        this.cameraImage = null
    }
}
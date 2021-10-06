package com.pda.screenshotmatcher2.models

import android.graphics.Bitmap

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

    fun clear() {
        this.matchID = null
        this.serverURL = null
        this.fullScreenshot = null
        this.croppedScreenshot = null
        this.cameraImage = null
    }
}
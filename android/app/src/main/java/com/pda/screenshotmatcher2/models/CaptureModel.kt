package com.pda.screenshotmatcher2.models

import android.content.Context
import android.graphics.Bitmap
import com.pda.screenshotmatcher2.network.requestFullScreenshot


object CaptureModel {
    private var matchID: String? = null
    private var serverURL: String? = null
    private var fullScreenshot: Bitmap? = null
    private var croppedScreenshot: Bitmap? = null
    private var cameraImage: Bitmap? = null

    fun setCaptureRequestData(serverURL: String, cameraImage: Bitmap) {
        clear()
        this.serverURL = serverURL
        this.cameraImage = cameraImage
    }

    fun setCaptureResultData(matchID: String, croppedScreenshot: Bitmap?) {
        this.matchID = matchID
        this.croppedScreenshot = croppedScreenshot
    }

    fun setFullScreenshot(fullScreenshot: Bitmap?) {
        this.fullScreenshot = fullScreenshot
    }

    fun getMatchID(): String? {
        return this.matchID
    }

    fun getServerURL(): String? {
        return this.serverURL
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

    fun getValue(): CaptureModel {
        return this
    }

    fun clear() {
        this.matchID = null
        this.serverURL = null
        this.fullScreenshot = null
        this.croppedScreenshot = null
        this.cameraImage = null
    }
}
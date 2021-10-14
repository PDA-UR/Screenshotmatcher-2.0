package com.pda.screenshotmatcher2.viewModels

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pda.screenshotmatcher2.models.CaptureModel
import com.pda.screenshotmatcher2.network.requestFullScreenshot


class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val matchID: MutableLiveData<String> by lazy {
        MutableLiveData<String>(CaptureModel.getMatchID())
    }
    private val serverURL: MutableLiveData<String> by lazy {
        MutableLiveData<String>(CaptureModel.getServerURL())
    }
    private val cameraImage: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>(CaptureModel.getCameraImage())
    }
    private val fullScreenshot: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>(CaptureModel.getFullScreenshot())
    }
    private val croppedScreenshot: MutableLiveData<Bitmap> by lazy {
        MutableLiveData<Bitmap>(CaptureModel.getCroppedScreenshot())
    }


    fun setCaptureRequestData(serverURL: String, cameraImage: Bitmap) {
        clear()
        this.serverURL.value = CaptureModel.setServerURL(serverURL)
        this.cameraImage.value = CaptureModel.setCameraImage(cameraImage)
        // Log.d("CM", serverURL)
    }

    fun setCaptureResultData(matchID: String, croppedScreenshot: Bitmap?) {
        this.matchID.value = CaptureModel.setMatchID(matchID)
        this.croppedScreenshot.value = CaptureModel.setCroppedScreenshot(croppedScreenshot)
    }

    fun loadFullScreenshot() {
        // Log.d("CVM", "calling load")
        if (CaptureModel.getMatchID() != null && CaptureModel.getServerURL() != null) {
            Thread {
                requestFullScreenshot(
                    matchID = CaptureModel.getMatchID()!!,
                    serverURL = CaptureModel.getServerURL()!!,
                    context = getApplication<Application>().applicationContext,
                    onDownload = ::onDownloadFullScreenshot
                )
            }.start()
        }
    }

    private fun onDownloadFullScreenshot(fullScreenshot: Bitmap?) {
        this.fullScreenshot.value = CaptureModel.setFullScreenshot(fullScreenshot)
    }

    fun getMatchID(): String? {
        return this.matchID.value
    }

    fun getLiveDataMatchID(): MutableLiveData<String> {
        return this.matchID
    }

    fun getServerURL(): String? {
        return this.serverURL.value
    }

    fun getLiveDataServerURL(): MutableLiveData<String> {
        return this.serverURL
    }

    fun getCameraImage(): Bitmap? {
        return this.cameraImage.value
    }

    fun getLiveDataCameraImage(): MutableLiveData<Bitmap> {
        return this.cameraImage
    }

    fun getFullScreenshot(): Bitmap? {
        return this.fullScreenshot.value
    }

    fun getLiveDataFullScreenshot(): MutableLiveData<Bitmap> {
        return this.fullScreenshot
    }

    fun getCroppedScreenshot(): Bitmap? {
        return this.croppedScreenshot.value
    }

    fun getLiveDataCroppedScreenshot(): MutableLiveData<Bitmap> {
        return this.croppedScreenshot
    }

    private fun clear() {
        CaptureModel.clear()
        this.croppedScreenshot.value = null
        this.fullScreenshot.value = null
        this.matchID.value = null
        this.serverURL.value = null
        this.cameraImage.value = null
    }


    internal class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CaptureViewModel::class.java)) {
                return CaptureViewModel(
                    application
                ) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

}
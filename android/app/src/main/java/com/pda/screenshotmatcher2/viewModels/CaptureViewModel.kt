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


/**
 * [ViewModel] that provides two way data bindings for [CaptureModel].
 * Use this class to retrieve/manipulate data stored in [CaptureModel].
 *
 * @see [MVVM Architecture](https://developer.android.com/jetpack/guide) For more information about how this software architectural pattern works.
 *
 * @constructor An instance of the current [Application]
 *
 * @property matchID Data binding for [CaptureModel.matchID]
 * @property serverURL Data binding for [CaptureModel.serverURL]
 * @property cameraImage Data binding for [CaptureModel.cameraImage]
 * @property fullScreenshot Data binding for [CaptureModel.fullScreenshot]
 * @property croppedScreenshot Data binding for [CaptureModel.croppedScreenshot]
 */
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


    /**
     * Set the data of a **new** capture request.
     *
     * Called when a new capture request is being made (user presses capture button).
     *
	 * @param serverURL The url of the server the capture request is being sent to
	 * @param cameraImage The camera image that's being sent
	 */
    fun setCaptureRequestData(serverURL: String, cameraImage: Bitmap) {
        clear()
        this.serverURL.value = CaptureModel.setServerURL(serverURL)
        this.cameraImage.value = CaptureModel.setCameraImage(cameraImage)
        Log.d("CM", serverURL)
    }

    /**
     * Set the data of a capture request **response**
     *
     * Called when a server responds to a capture request (matching process finishes)
     *
	 * @param matchID The id of the match
	 * @param croppedScreenshot The cropped screenshot returned by the matching process (null if no match)
	 */
    fun setCaptureResultData(matchID: String, croppedScreenshot: Bitmap?) {
        this.matchID.value = CaptureModel.setMatchID(matchID)
        this.croppedScreenshot.value = CaptureModel.setCroppedScreenshot(croppedScreenshot)
    }

    /**
     * Requests the full screenshot from the server and calls [onDownloadFullScreenshot]
     *
     */
    fun loadFullScreenshot() {
        Log.d("CVM", "calling load")
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

    /**
     * Callback that updates [fullScreenshot] with [fullScreenshot].
     *
     * Used in [loadFullScreenshot].
	 */
    private fun onDownloadFullScreenshot(fullScreenshot: Bitmap?) {
        this.fullScreenshot.value = CaptureModel.setFullScreenshot(fullScreenshot)
    }

    /**
     * Getter that returns the value of [matchID].
	 */
    fun getMatchID(): String? {
        return this.matchID.value
    }

    fun getServerUrl(): String? {
        return this.serverURL.value
    }

    /**
     * Getter that returns [serverURL] as [MutableLiveData]
	 */
    fun getLiveDataServerURL(): MutableLiveData<String> {
        return this.serverURL
    }

    /**
     * Getter that returns the value of [cameraImage]
	 */
    fun getCameraImage(): Bitmap? {
        return this.cameraImage.value
    }

    /**
     * Getter that returns [cameraImage] as [MutableLiveData]
	 */
    fun getLiveDataCameraImage(): MutableLiveData<Bitmap> {
        return this.cameraImage
    }

    /**
     * Getter that returns the value of [fullScreenshot]
	 */
    fun getFullScreenshot(): Bitmap? {
        return this.fullScreenshot.value
    }

    /**
     * Getter that returns [fullScreenshot] as [MutableLiveData]
	 */
    fun getLiveDataFullScreenshot(): MutableLiveData<Bitmap> {
        return this.fullScreenshot
    }

    /**
     * Getter that returns the value of [croppedScreenshot]
	 */
    fun getCroppedScreenshot(): Bitmap? {
        return this.croppedScreenshot.value
    }

    /**
     * Getter that returns [croppedScreenshot] as [MutableLiveData]
	 */
    fun getLiveDataCroppedScreenshot(): MutableLiveData<Bitmap> {
        return this.croppedScreenshot
    }

    /**
     * Clears all properties of [CaptureModel].
     *
     * Called when a new capture request is initiated via [setCaptureRequestData]
     */
    private fun clear() {
        CaptureModel.clear()
        this.croppedScreenshot.value = null
        this.fullScreenshot.value = null
        this.matchID.value = null
        this.serverURL.value = null
        this.cameraImage.value = null
    }


    /**
     * Factory to initiate this [CaptureViewModel]
     *
     * @constructor An instance of the current [Application]
     */
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
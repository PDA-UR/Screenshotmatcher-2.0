package com.pda.screenshotmatcher2.viewModels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pda.screenshotmatcher2.models.CaptureModel
import com.pda.screenshotmatcher2.network.requestFullScreenshot


class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    val captureModel: MutableLiveData<CaptureModel> by lazy {
        MutableLiveData<CaptureModel>()
    }

    fun loadFullScreenshot() {
        if (captureModel.value?.getMatchID() != null && captureModel.value?.getServerURL() != null) {
            Thread {
                requestFullScreenshot(
                    matchID = captureModel.value?.getMatchID()!!,
                    serverURL = captureModel.value?.getServerURL()!!,
                    context = getApplication<Application>().applicationContext,
                    onDownload = ::onDownloadFullScreenshot
                )
            }.start()
        }
    }

    private fun onDownloadFullScreenshot(fullScreenshot: Bitmap?) {
        captureModel.value?.setFullScreenshot(fullScreenshot)
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
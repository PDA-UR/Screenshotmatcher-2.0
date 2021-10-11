package com.pda.screenshotmatcher2.viewModels

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pda.screenshotmatcher2.models.ServerConnectionModel
import com.pda.screenshotmatcher2.network.discoverServersOnNetwork
import com.pda.screenshotmatcher2.network.sendHeartbeatRequest

class ServerConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val mServerURL: MutableLiveData<String> by lazy {
        ServerConnectionModel.serverUrl.also {
            start()
        }
    }

    private val mServerUrlList: MutableLiveData<List<Pair<String, String>>> = ServerConnectionModel.serverUrlList

    val isConnectedToServer: MutableLiveData<Boolean> = ServerConnectionModel.isConnected
    private val isDiscovering: MutableLiveData<Boolean> = ServerConnectionModel.isDiscovering
    private val isSendingHeartbeat: MutableLiveData<Boolean> = ServerConnectionModel.isHeartbeating

    private fun start() {
        ServerConnectionModel.start(getApplication())
    }

    fun getConnectedServerName(): String {
        mServerUrlList.value?.forEach {
            Log.d("SCVM","Comparing ${it.first}/${it.second} with url ${mServerURL.value}")
            if (it.first == mServerURL.value) {
                return it.second
            }
        }
        return ""
    }

    fun setServerUrl(hostname: String) {
        ServerConnectionModel.setServerUrl(hostname)
    }

    fun getServerUrl(): String {
        return this.mServerURL.value ?: ""
    }

    fun getServerUrlLiveData(): MutableLiveData<String> {
        return this.mServerURL
    }

    fun getServerUrlList(): List<Pair<String, String>> {
        return mServerUrlList.value!!
    }

    fun getServerUrlListLiveData(): MutableLiveData<List<Pair<String, String>>> {
        return this.mServerUrlList
    }

    fun getConnectionStatus(): Boolean {
        return isConnectedToServer.value ?: false
    }

    internal class Factory(private val application: Application) : ViewModelProvider.Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ServerConnectionViewModel::class.java)) {
                return ServerConnectionViewModel(
                    application
                ) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

}
package com.pda.screenshotmatcher2.viewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pda.screenshotmatcher2.models.ServerConnectionModel

/**
 * [ViewModel that provides two way data bindings for [ServerConnectionModel]
 * Use this class to retrieve/manipulate data stored in [ServerConnectionModel]
 *
 * @see [MVVM Architecture](https://developer.android.com/jetpack/guide) For more information about how this software architectural pattern works.
 *
 * @constructor An instance of the current [Application]
 *
 * @property serverUrl Data binding for [ServerConnectionModel.serverUrl]
 * @property serverUrlList Data binding for [ServerConnectionModel.serverUrlList]
 * @property isConnectedToServer Data binding for [ServerConnectionModel.isConnected]
 * @property isDiscovering Data binding for [ServerConnectionModel.isDiscovering]
 * @property isSendingHeartbeat Data binding for [ServerConnectionModel.isHeartbeating]
 */
class ServerConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val serverUrl: MutableLiveData<String> by lazy {
        ServerConnectionModel.serverUrl.also {
            start()
        }
    }
    private val serverUrlList: MutableLiveData<List<Pair<String, String>>> = ServerConnectionModel.serverUrlList
    val isConnectedToServer: MutableLiveData<Boolean> = ServerConnectionModel.isConnected
    private val isDiscovering: MutableLiveData<Boolean> = ServerConnectionModel.isDiscovering
    private val isSendingHeartbeat: MutableLiveData<Boolean> = ServerConnectionModel.isHeartbeating

    /**
     * Starts the discover/heartbeat cycle of [ServerConnectionModel]
     */
    private fun start() {
        ServerConnectionModel.start(getApplication(), true)
    }

    /**
     * Getter method that returns the hostname of the currently connected server (or "" if not connected)
	 */
    fun getConnectedServerName(): String {
        serverUrlList.value?.forEach {
            Log.d("SCVM","Comparing ${it.first}/${it.second} with url ${serverUrl.value}")
            if (it.first == serverUrl.value) {
                return it.second
            }
        }
        return ""
    }

    /**
     * Set the currently connected server by hostname
	 * @param hostname
	 */
    fun setServerUrl(hostname: String) {
        ServerConnectionModel.setServerUrl(hostname)
    }


    /**
     * Getter that returns the value of [serverUrl]
	 */
    fun getServerUrl(): String {
        return this.serverUrl.value ?: ""
    }

    /**
     * Getter that returns [serverUrl] as [MutableLiveData]
	 */
    fun getServerUrlLiveData(): MutableLiveData<String> {
        return this.serverUrl
    }

    /**
     * Getter that returns the value of [serverUrlList]
	 * @return
	 */
    fun getServerUrlList(): List<Pair<String, String>> {
        return serverUrlList.value!!
    }

    /**
     * Getter that returns [serverUrlList] as [MutableLiveData]
	 * @return
	 */
    fun getServerUrlListLiveData(): MutableLiveData<List<Pair<String, String>>> {
        return this.serverUrlList
    }

    /**
     * Getter that returns the value of [isConnectedToServer]
	 */
    fun getConnectionStatus(): Boolean {
        return isConnectedToServer.value ?: false
    }

    /**
     * Factory to initiate this [ServerConnectionViewModel]
     *
     * @constructor An instance of the current [Application]
     */
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
package com.pda.screenshotmatcher2.models

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.pda.screenshotmatcher2.network.discoverServersOnNetwork
import com.pda.screenshotmatcher2.network.sendHeartbeatRequest

/**
 * Data model used to store all relevant information about the current server connection.
 * Also handles server discovery and heartbeats.
 *
 * @property serverUrlList Variable that stores all currently available servers
 * @property serverUrl Variable that stores the currently connected server
 * @property isConnected Variable that indicates whether or not the application is connected to a server
 * @property isDiscovering Variable that indicates whether or not the application is currently searching for servers
 * @property isHeartbeating Variable that indicates whether or not the application is currently sending out heartbeats to a server
 */

object ServerConnectionModel {
    var serverUrlList = MutableLiveData<List<Pair<String, String>>>(emptyList())
    var serverUrl = MutableLiveData<String>("")

    var isConnected = MutableLiveData<Boolean>(false)
    var isDiscovering = MutableLiveData<Boolean>(false)
    var isHeartbeating = MutableLiveData<Boolean>(false)

    private var application: Application? = null

    //Handlers for discover/heartbeat thread
    private lateinit var handlerThread: HandlerThread
    private lateinit var looper: Looper
    private lateinit var mHandler: Handler

    /**
     * All potential messages
     */
    private object HANDLER_MESSAGES {
        val END_ALL_THREADS: Int = 0
        val START_DISCOVER: Int = 1
        val START_HEARTBEAT: Int = 2
    }

    /**
     * Replaces the current [serverUrlList] with the provided [serverUrlList]
     *
	 * @return The **value** of the new [serverUrlList]
	 */
    fun setServerUrlList(serverUrlList: List<Pair<String, String>>): List<Pair<String, String>> {
        this.serverUrlList.postValue(serverUrlList)
        return this.serverUrlList.value!!
    }

    /**
     * Changes the current [serverUrl] to the **url** that is associated with the provided [hostname]
	 */
    fun setServerUrl(hostname: String) {
        serverUrlList.value?.forEach {
            if (it.second == hostname) {
                serverUrl.postValue(it.first)
                onConnectionChanged(true)
            }
        }
    }

    /**
     * Callback that gets executed when the current connection status changes.
     * Updates the connection state variables and starts a new runnable depending on [newConnectionState]
     * @param newConnectionState The new connection status (true = connected, false = disconnected)
	 */
    private fun onConnectionChanged(newConnectionState: Boolean) {
        isDiscovering.postValue(!newConnectionState)
        isHeartbeating.postValue(newConnectionState)
        this.isConnected.postValue(newConnectionState)
        if (newConnectionState) startHeartbeatThread()
        else {
            serverUrl.postValue("")
            startDiscoverThread()
        }
    }


    /**
     * Initiates the threads & handlers. Starts the first [Runnable].
     *
	 * @param isForeground Whether or not the model is called from a background service or a foreground activity
	 */
    fun start(application: Application, isForeground: Boolean) {
        this.application = application
        if (!isDiscovering.value!! && !isHeartbeating.value!!) {
            handlerThread = HandlerThread(this.javaClass.simpleName).apply { start() }
            looper = handlerThread.looper
            mHandler = object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        HANDLER_MESSAGES.END_ALL_THREADS -> {
                            this.removeCallbacksAndMessages(null)
                        }
                        HANDLER_MESSAGES.START_DISCOVER -> {
                            this.removeCallbacksAndMessages(null)
                            this.post(discoverRunnable)
                        }
                        HANDLER_MESSAGES.START_HEARTBEAT -> {
                            this.removeCallbacksAndMessages(null)
                            this.post(heartbeatRunnable)
                        }
                    }
                }
            }
            when (isConnected.value) {
                true -> startHeartbeatThread()
                false -> startDiscoverThread()
            }
        }
    }

    /**
     * Starts [heartbeatRunnable]
     */
    private fun startHeartbeatThread() {
        if (handlerThread.isAlive) {
            isHeartbeating.postValue(true)
            mHandler.sendMessage(mHandler.obtainMessage(HANDLER_MESSAGES.START_HEARTBEAT))
        }
    }

    /**
     * Starts [discoverRunnable]
     */
    private fun startDiscoverThread() {
        if (handlerThread.isAlive) {
            isDiscovering.postValue(true)
            mHandler.sendMessage(mHandler.obtainMessage(HANDLER_MESSAGES.START_DISCOVER))
        }
    }

    /**
     * [Runnable] that discovers all available servers on the network
     */
    private val discoverRunnable = object : Runnable {
        override fun run() {
            Log.d("SCVM", "Discovering")
            requestServerURL(application!!.applicationContext)
            mHandler.postDelayed(this, 1000)
        }
    }

    /**
     * Retrieves all available servers on the network and updates [serverUrlList]
	 */
    private fun requestServerURL(context: Context) {
        Thread {
                discoverServersOnNetwork(
                    context,
                    49050,
                    "",
                    ::onServerURLsGet
                )
        }.start()
    }

    /**
     * Helper function to update [serverUrlList] with [newServers]
	 */
    private fun updateServerUrlList(newServers: List<Pair<String, String>>) {
        serverUrlList.postValue(newServers)
    }


    /**
     * Callback that gets executed when new servers have been discovered.
     * Updates [serverUrlList] with the new list of [servers].
	 */
    private fun onServerURLsGet(servers: List<Pair<String, String>>) {
        if (servers.isNotEmpty()) {
            updateServerUrlList(servers).also {
                // Connect to first server if currently disconnected
                if (serverUrl.value!! == "")
                    setServerUrl(hostname = servers[0].second)
            }
        }
    }

    /**
     * [Runnable] that sends heartbeats to the currently connected server ([serverUrl]
     */
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isConnected.value!! && serverUrl.value != "") {
                Log.d("SCM", "Sending heartbeat")
                sendHeartbeatRequest(
                    serverUrl.value,
                    application!!.applicationContext,
                    ::onHeartbeatFail
                )
            }
            mHandler.postDelayed(this, 1000)
        }
    }

    /**
     * Callback that gets executed when sending a heartbeat fails
     */
    private fun onHeartbeatFail() {
        onConnectionChanged(false)
        Log.d("SCVM", "Failed HB")
    }
}
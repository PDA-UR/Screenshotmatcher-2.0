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
 * Data model that stores all relevant information about the current server connection.
 * Also handles server discovery and heartbeats.
 *
 * Accessed through [ServerConnectionViewModel][com.pda.screenshotmatcher2.viewModels.ServerConnectionViewModel].
 *
 * @property serverUrlList All currently available servers
 * @property serverUrl The currently connected server
 * @property isConnected Whether or not the application is connected to a server
 * @property isDiscovering Whether or not the application is currently searching for servers
 * @property isHeartbeating Whether or not the application is currently sending out heartbeats to a server
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
     * All potential messages that can be sent to [mHandler]
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
     * Updates [isDiscovering] & [isHeartbeating] and calls either [startDiscoverThread] or [startHeartbeatThread] depending on [newConnectionState]
     * @param newConnectionState The new connection status (true = connected -> heartbeat, false = disconnected -> discover)
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
        //Log.d("SCM", "call start, foreground: $isForeground")
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
     * [Runnable] that discovers all available servers on the network.
     *
     * Calls [discover] every 1000ms
     */
    private val discoverRunnable = object : Runnable {
        override fun run() {
            discover(application!!.applicationContext)
            mHandler.postDelayed(this, 1000)
        }
    }

    /**
     * Calls [discoverServersOnNetwork] to discover all available servers on the network, passes [onServerURLsGet] as a callback.
	 */
    private fun discover(context: Context) {
        //Log.d("SCM", "starting discover thread")
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
     * Callback that gets executed when new servers have been discovered via [discoverServersOnNetwork].
     *
     * Updates [serverUrlList] with the new list of [servers].
	 */
    private fun onServerURLsGet(servers: List<Pair<String, String>>) {
        //Log.d("SCM", "onserverurlget, list len: " + servers.size)

        if (servers.isNotEmpty()) {
            updateServerUrlList(servers)
        }
    }

    /**
     * [Runnable] that calls [heartbeat] every 1000ms.
     */
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            heartbeat()
            mHandler.postDelayed(this, 1000)
        }
    }

    /**
     * Calls [sendHeartbeatRequest] if the application is currently connected to a server;
     * Passes [onHeartbeatFail] as a callback.
     */
    private fun heartbeat () {
        if (isConnected.value!! && serverUrl.value != "") {
            //Log.d("SCM", "Sending heartbeat")
            sendHeartbeatRequest(
                serverUrl.value,
                application!!.applicationContext,
                ::onHeartbeatFail
            )
        }
    }

    /**
     * Callback that gets executed when sending a heartbeat fails.
     * Calls [onConnectionChanged] to update the connection state
     */
    private fun onHeartbeatFail() {
        onConnectionChanged(false)
        //Log.d("SCVM", "Failed HB")
    }
}
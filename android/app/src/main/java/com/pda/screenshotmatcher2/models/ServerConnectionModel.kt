@file:Suppress("unused")

package com.pda.screenshotmatcher2.models

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.pda.screenshotmatcher2.R
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
    var serverUrl = MutableLiveData("")

    var isConnected = MutableLiveData(false)
    var isDiscovering = MutableLiveData(false)
    var isHeartbeating = MutableLiveData(false)

    private var application: Application? = null
    private var isForeground = true

    //Handlers for discover/heartbeat thread
    private lateinit var handlerThread: HandlerThread
    private lateinit var looper: Looper
    private lateinit var mHandler: Handler

    /**
     * All potential messages that can be sent to [mHandler]
     */
    private object HandlerMessages {
        const val END_ALL_THREADS: Int = 0
        const val START_DISCOVER: Int = 1
        const val START_HEARTBEAT: Int = 2
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
        if (newConnectionState) {
            startHeartbeatThread()
        }
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
        this.isForeground = isForeground
        if (!isDiscovering.value!! && !isHeartbeating.value!!) {
            handlerThread = HandlerThread(this.javaClass.simpleName).apply { start() }
            looper = handlerThread.looper
            mHandler = object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        HandlerMessages.END_ALL_THREADS -> {
                            //Log.d("SCM", "end all threads")
                            this.removeCallbacksAndMessages(null)
                        }
                        HandlerMessages.START_DISCOVER -> {
                            this.removeCallbacksAndMessages(null)
                            this.post(discoverRunnable)
                        }
                        HandlerMessages.START_HEARTBEAT -> {
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
            mHandler.sendMessage(mHandler.obtainMessage(HandlerMessages.START_HEARTBEAT))
        }
    }

    /**
     * Starts [discoverRunnable]
     */
    private fun startDiscoverThread() {
        if (handlerThread.isAlive) {
            isDiscovering.postValue(true)
            mHandler.sendMessage(mHandler.obtainMessage(HandlerMessages.START_DISCOVER))
        }
    }

    /**
     * Stops all runnables
     */
    fun stopThreads() {
        if (handlerThread.isAlive) {
            isDiscovering.postValue(false)
            isHeartbeating.postValue(false)
            mHandler.sendMessage(mHandler.obtainMessage(HandlerMessages.END_ALL_THREADS))
        }
    }

    /**
     * [Runnable] that discovers all available servers on the network.
     *
     * Calls [discover] in an interval of [getRunnableInterval] ms.
     */
    private val discoverRunnable = object : Runnable {
        override fun run() {
            discover(application!!.applicationContext)
            mHandler.postDelayed(this, this@ServerConnectionModel.getRunnableInterval())
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
     * Callback that gets executed when new servers have been discovered via [discoverServersOnNetwork].
     *
     * Updates [serverUrlList] with the new list of [servers].
	 */
    private fun onServerURLsGet(servers: List<Pair<String, String>>) {
        if (servers.isNotEmpty()) {
            updateServerUrlList(servers)
            tryConnectToFirstServer(servers)
        }
    }

    /**
     * Automatically connects to the first known server in [serverUrlList].
     *
     * Called in [onServerURLsGet]
     *
     * @param servers The list of available servers
     */
    private fun tryConnectToFirstServer(servers: List<Pair<String, String>>) {
        for (server in servers) {
            if (isKnownServer(server.second)) {
                connectToServer(server.first)
                break
            }
        }
    }

    /**
     * Checks if a given [hostname] is already known (= has been connected to before)
     *
     * @param hostname The hostname of the server
     * @return True if the server is known, false otherwise
     */
    private fun isKnownServer(hostname: String) : Boolean{
        val knownServers = PreferenceManager.getDefaultSharedPreferences(application).getStringSet(
            application?.getString(R.string.KNOWN_SERVERS_KEY), setOf())
        return knownServers?.contains(hostname) ?: false
    }

    /**
     * Connects to the server with the given [url].
     *
     * @param url The url of the server
     */
    private fun connectToServer(url: String) {
        serverUrl.postValue(url)
        isConnected.postValue(true)
        startHeartbeatThread()
    }

    /**
     * Helper function to update [serverUrlList] with [newServers]
     */
    private fun updateServerUrlList(newServers: List<Pair<String, String>>) {
        serverUrlList.postValue(newServers)
    }


    /**
     * [Runnable] that calls [heartbeat] in an interval of [getRunnableInterval] ms.
     */
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            heartbeat()
            mHandler.postDelayed(this, this@ServerConnectionModel.getRunnableInterval())
        }
    }

    /**
     * Returns the interval in which [heartbeatRunnable]/[discoverRunnable] should be called.
     *
     * @return 1000 if [isForeground] is true, 10000 otherwise
     */
    private fun getRunnableInterval (): Long {
        return if (isForeground)
            1000
        else
            10000
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
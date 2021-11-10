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

    private val END_ALL_THREADS: Int = 0
    private val START_DISCOVER: Int = 1
    private val START_HEARTBEAT: Int = 2

    fun setServerUrlList(serverUrlList: List<Pair<String, String>>): List<Pair<String, String>> {
        this.serverUrlList.postValue(serverUrlList)
        return this.serverUrlList.value!!
    }

    fun setServerUrl(hostname: String) {
        serverUrlList.value?.forEach {
            if (it.second == hostname) {
                serverUrl.postValue(it.first)
                onConnectionChanged(true)
            }
        }
    }
    private fun onConnectionChanged(isConnected: Boolean) {
        isDiscovering.postValue(!isConnected)
        isHeartbeating.postValue(isConnected)
        this.isConnected.postValue(isConnected)
        if (isConnected) startHeartbeatThread()
        else {
            serverUrl.postValue("")
            startDiscoverThread()
        }
    }


    fun start(application: Application, isForeground: Boolean) {
        this.application = application
        if (!isDiscovering.value!! && !isHeartbeating.value!!) {
            handlerThread = HandlerThread(this.javaClass.simpleName).apply { start() }
            looper = handlerThread.looper
            mHandler = object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        END_ALL_THREADS -> {
                            this.removeCallbacksAndMessages(null)
                        }
                        START_DISCOVER -> {
                            this.removeCallbacksAndMessages(null)
                            this.post(discoverRunnable)
                        }
                        START_HEARTBEAT -> {
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

    private fun startHeartbeatThread() {
        if (handlerThread.isAlive) {
            isHeartbeating.postValue(true)
            mHandler.sendMessage(mHandler.obtainMessage(START_HEARTBEAT))
        }
    }
    private fun startDiscoverThread() {
        if (handlerThread.isAlive) {
            isDiscovering.postValue(true)
            mHandler.sendMessage(mHandler.obtainMessage(START_DISCOVER))
        }
    }

    val discoverRunnable = object : Runnable {
        override fun run() {
            Log.d("SCVM", "Discovering")
            requestServerURL(application!!.applicationContext)
            mHandler.postDelayed(this, 1000)
        }
    }

    private fun requestServerURL(context: Context) {
        Thread {
            serverUrlList.postValue(
                discoverServersOnNetwork(
                    context,
                    49050,
                    "",
                    ::onServerURLsGet
                )
            )
        }.start()
    }
    private fun updateServerUrlList(newServers: List<Pair<String, String>>) {
        serverUrlList.postValue(newServers)
    }


    private fun onServerURLsGet(servers: List<Pair<String, String>>) {
        if (servers.isNotEmpty()) {
            updateServerUrlList(servers).also {
                // Connect to first server if currently disconnected
                if (serverUrl.value!! == "")
                    setServerUrl(hostname = servers[0].second)
            }
        }
    }

    val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isConnected.value!! && serverUrl.value != "") {
                sendHeartbeatRequest(
                    serverUrl.value,
                    application!!.applicationContext,
                    ::onHeartbeatFail
                )
            }
            mHandler.postDelayed(this, 1000)
        }
    }
    fun onHeartbeatFail() {
        onConnectionChanged(false)
        Log.d("SCVM", "Failed HB")
    }
}
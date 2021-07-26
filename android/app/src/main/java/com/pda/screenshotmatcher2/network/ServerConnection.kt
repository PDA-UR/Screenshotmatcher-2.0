package com.pda.screenshotmatcher2.network

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.widget.Toast
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.activities.CameraActivity

class ServerConnection(cameraActivity: CameraActivity) {
    private val ca = cameraActivity

    private val END_ALL_THREADS: Int = 0
    private val START_DISCOVER: Int = 1
    private val START_HEARTBEAT: Int = 2

    var mServerURL: String = ""
    lateinit var mServerUrlList: List<Pair<String, String>>
    var isConnectedToServer = false
    var isDiscovering = false
    var isSendingHeartbeat = false

    //Handlers for discover/heartbeat thread
    private lateinit var handlerThread: HandlerThread
    private lateinit var looper: Looper
    private lateinit var mHandler: Handler

    fun start() {
        if (!isDiscovering && !isSendingHeartbeat){
            handlerThread = HandlerThread(this.javaClass.simpleName).apply { start() }
            looper = handlerThread.looper
            mHandler  = object : Handler(looper) {
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
            when(isConnectedToServer){
                true -> startHeartbeatThread()
                false -> startDiscoverThread()
            }
        }
    }

    val discoverRunnable = object: Runnable {
        override fun run() {
            requestServerURL()
                mHandler.postDelayed(this, 5000)
        }
    }

    val heartbeatRunnable = object: Runnable {
        override fun run() {
            if (isConnectedToServer && mServerURL != ""){
                sendHeartbeatRequest(
                    mServerURL,
                    ca
                )
            }
            mHandler.postDelayed(this, 5000)
        }
    }

    private fun startHeartbeatThread(){
        if (handlerThread.isAlive){
            isSendingHeartbeat = true
            mHandler.sendMessage(mHandler.obtainMessage(START_HEARTBEAT))
        }
    }

    fun onHeartbeatFail(){
        onConnectionChanged(false)
        Toast.makeText(ca, ca.getString(R.string.heartbeat_fail_en), Toast.LENGTH_SHORT).show()
    }

    private fun startDiscoverThread(){
        if (handlerThread.isAlive) {
            isDiscovering = true
            mHandler.sendMessage(mHandler.obtainMessage(START_DISCOVER))
        }
    }

    private fun requestServerURL() {
        Thread {
            mServerUrlList =
                discoverServersOnNetwork(
                    ca,
                    49050,
                    ""
                )
        }.start()
    }

    fun onServerURLsGet(servers: List<Pair<String, String>>) {
        if (ca.isFirstBoot){
            if (servers.isNotEmpty()){
                mServerUrlList = servers
                setServerUrl(servers[0].second)
                ca.isFirstBoot = false
            }
        }
        if (servers.isNotEmpty()) {
            updateServerUrlList(servers)
            if (isConnectedToServer && handlerThread.isAlive){
                ca.runOnUiThread {
                    onConnectionChanged(true)
                }
            }
        }
    }

    fun setServerUrl(hostname: String) {
        mServerUrlList.forEach {
            if (it.second == hostname){
                mServerURL = it.first
            }
        }
        onConnectionChanged(true)
    }

    private fun updateServerUrlList(newServers: List<Pair<String, String>>) {
        mServerUrlList = newServers
        mServerUrlList.forEach {
            if (it.first == mServerURL) onConnectionChanged(true)
        }
    }


    private fun onConnectionChanged(isConnected: Boolean){
        isDiscovering = !isConnected
        isSendingHeartbeat = isConnected
        isConnectedToServer = isConnected
        if (isConnected) startHeartbeatThread()
        else {
            startDiscoverThread()
            mServerURL = ""
        }
        ca.updateConnectionStatus()
    }

    fun getServerUrlList(): List<Pair<String, String>>? {
        return if (::mServerUrlList.isInitialized) {
            mServerUrlList
        } else null
    }

    fun getConnectedServerName(): String {
        mServerUrlList.forEach {
            if (it.first ==mServerURL){
                return it.second
            }
        }
        return ""
    }

    fun stop() {
        isDiscovering = false
        isSendingHeartbeat = false
        handlerThread.quitSafely()
    }

}
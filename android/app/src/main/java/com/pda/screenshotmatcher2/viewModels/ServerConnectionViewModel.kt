package com.pda.screenshotmatcher2.viewModels

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.*
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.network.discoverServersOnNetwork
import com.pda.screenshotmatcher2.network.sendHeartbeatRequest

class ServerConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val mServerURL: MutableLiveData<String> by lazy {
        MutableLiveData("").also {
            start()
        }
    }

    private val mServerUrlList: MutableLiveData<List<Pair<String, String>>>  =
        MutableLiveData(emptyList())

    private val END_ALL_THREADS: Int = 0
    private val START_DISCOVER: Int = 1
    private val START_HEARTBEAT: Int = 2

    var isConnectedToServer = false
    var isDiscovering = false
    var isSendingHeartbeat = false

    //Handlers for discover/heartbeat thread
    private lateinit var handlerThread: HandlerThread
    private lateinit var looper: Looper
    private lateinit var mHandler: Handler

    private fun start() {
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
            requestServerURL(application.applicationContext)
            mHandler.postDelayed(this, 1000)
        }
    }

    val heartbeatRunnable = object: Runnable {
        override fun run() {
            if (isConnectedToServer && mServerURL.value != ""){
                sendHeartbeatRequest(
                    mServerURL.value!!,
                    application.applicationContext,
                    ::onHeartbeatFail
                )
            }
            mHandler.postDelayed(this, 1000)
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
        Log.d("SCVM", "Failed HB")
    }

    private fun startDiscoverThread(){
        if (handlerThread.isAlive) {
            isDiscovering = true
            mHandler.sendMessage(mHandler.obtainMessage(START_DISCOVER))
        }
    }

    private fun requestServerURL(context: Context) {

        Thread {
            mServerUrlList.value =
                discoverServersOnNetwork(
                    context,
                    49050,
                    "",
                    ::onServerURLsGet
                )
        }.start()
    }

    fun onServerURLsGet(servers: List<Pair<String, String>>) {
        if (mServerURL.value == ""){
            if (servers.isNotEmpty()){
                mServerUrlList.value = servers
                setServerUrl(servers[0].second)
            }
        }
        if (servers.isNotEmpty()) {
            updateServerUrlList(servers)
            if (isConnectedToServer && handlerThread.isAlive){
                //TODO : UI REFRESH
            }
        }
    }

    fun setServerUrl(hostname: String) {
        mServerUrlList.value?.forEach {
            if (it.second == hostname){
                mServerURL.value = it.first
            }
        }
        onConnectionChanged(true)
    }

    private fun updateServerUrlList(newServers: List<Pair<String, String>>) {
        mServerUrlList.value = newServers
        mServerUrlList.value!!.forEach {
            if (it.first == mServerURL.value) onConnectionChanged(true)
        }
    }


    private fun onConnectionChanged(isConnected: Boolean){
        isDiscovering = !isConnected
        isSendingHeartbeat = isConnected
        isConnectedToServer = isConnected
        if (isConnected) startHeartbeatThread()
        else {
            startDiscoverThread()
            mServerURL.value = ""
        }
        // TODO ca.updateConnectionStatus
    }

    fun getConnectedServerName(): String {
        mServerUrlList.value?.forEach {
            if (it.first == mServerURL.value){
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




    fun getServerUrl() : String {
        return this.mServerURL.value!!
    }

    fun getServerUrlLiveData(): MutableLiveData<String> {
        return this.mServerURL
    }

    fun getServerUrlList () : List<Pair<String, String>>{
        return mServerUrlList.value!!
    }

    fun getServerUrlListLiveData (): MutableLiveData<List<Pair<String, String>>> {
        return this.mServerUrlList
    }

    internal class Factory (private val application: Application) : ViewModelProvider.Factory {

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
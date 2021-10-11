package com.pda.screenshotmatcher2.models

object ServerConnectionModel {
    private var serverUrlList: List<Pair<String, String>> = emptyList()
    private var serverUrl: String = ""

    private var isConnected: Boolean = false
    private var isDiscovering: Boolean = false
    private var isHeartbeating: Boolean = false


    fun setServerUrlList(serverUrlList: List<Pair<String, String>>): List<Pair<String, String>> {
        this.serverUrlList = serverUrlList
        return this.serverUrlList
    }

    fun setServerUrl(serverUrl: String): String {
        this.serverUrl = serverUrl
        return this.serverUrl
    }

    fun setConnected (isConnected: Boolean): Boolean {
        this.isConnected = isConnected
        return this.isConnected
    }

    fun setDiscovering (isDiscovering: Boolean): Boolean {
        this.isDiscovering = isDiscovering
        return this.isDiscovering
    }

    fun setHeartbeating (isHeartbeating: Boolean): Boolean {
        this.isHeartbeating = isHeartbeating
        return this.isHeartbeating
    }

    fun getServerUrlList(): List<Pair<String, String>> {
        return serverUrlList
    }

    fun getServerUrl(): String {
        return serverUrl
    }

    fun getDiscovering(): Boolean {
        return isDiscovering
    }
    fun getHeartbeating(): Boolean {
        return isHeartbeating
    }
    fun getConnected(): Boolean {
        return isConnected
    }
}
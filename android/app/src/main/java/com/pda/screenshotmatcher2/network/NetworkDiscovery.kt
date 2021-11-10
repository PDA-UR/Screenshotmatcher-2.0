package com.pda.screenshotmatcher2.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.lang.Exception
import java.net.*
import kotlin.jvm.Throws

const val PROTOCOL = "http://"
const val MAX_SERVERS = 5


fun discoverServersOnNetwork(context: Context, port: Int = 49050, message: String = "screenshot matcher client LF server", onGet : (servers: List<Pair<String, String>>) -> Unit) : List<Pair<String, String>> {
    val s = DatagramSocket().also {
        it.broadcast = true
        it.reuseAddress = true
        it.soTimeout = 500
        if(!it.isBound) it.bind(InetSocketAddress(port))
    }
    val bufS = message.toByteArray()   //send
    val bufR = ByteArray(1024)    //receive
    val bcAddress = getBroadcastAddress(
        context
    ) ?: return emptyList()
    val packetS = DatagramPacket(bufS, bufS.size, bcAddress, port)
    val packetR = DatagramPacket(bufR, bufR.size)
    val serverList = mutableListOf<Pair<String, String>>()

    try {
        s.send(packetS)
        for (i in 1..MAX_SERVERS){
            try {
                s.receive(packetR)  // receive will block here, until soTimeout gets reached
                val payload = String(packetR.data, 0, packetR.length).split('|')
                if (payload.size == 2) {
                    val pair = Pair(PROTOCOL + payload[0], payload[1])
                    //only add to list if it's not a duplicate
                    if (serverList.indexOf(pair) == -1) {
                        serverList.add(Pair(PROTOCOL + payload[0], payload[1]))
                    }
                }

            }
            catch(e: SocketTimeoutException) {
                break
            }
        }
        s.close()
        onGet(serverList)
    } catch (e: Exception) {
        e.message?.let { Log.e("ND", it) }
    }
    // try to get answers from every server on the LAN
    // expected answer from server: "192.168.0.45:49049|Desktop-5QFF67"
    return serverList
}

@Throws(IOException::class)
private fun getBroadcastAddress(context : Context): InetAddress? {
    val wifi: WifiManager = context.applicationContext.getSystemService(AppCompatActivity.WIFI_SERVICE) as WifiManager
    val dhcp = wifi.dhcpInfo ?: return null
    val broadcast = dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
    val quads = ByteArray(4)

    for (k in 0..3) quads[k] = (broadcast shr k * 8 and 0xFF).toByte()
    return InetAddress.getByAddress(quads)
}
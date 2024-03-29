package com.pda.screenshotmatcher2.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.lang.Exception
import java.net.*
import kotlin.jvm.Throws

/**
 * The protocol used for the connection between the app and the server.
 */
const val PROTOCOL = "http://"

/**
 * The maximum number of servers that can be discovered.
 */
const val MAX_SERVERS = 5

/**
 * Discovers and returns available servers on the network.
 *
 * @param context The context of the calling Activity or Service to use for the request.
 * @param port The port to use send the request to.
 * @param message The message to send to the servers on the network.
 * @param onGet The function to call when the request is complete.
 * @return A list of available servers.
 */
fun discoverServersOnNetwork(context: Context, port: Int = 49050, message: String = "screenshot matcher client LF server", onGet : (servers: List<Pair<String, String>>) -> Unit) : List<Pair<String, String>> {
    //Log.d("NetworkDiscovery", "Discovering servers on network")
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
        e.message?.let { //Log.e("ND", it)
        }
    }
    // try to get answers from every server on the LAN
    // expected answer from server: "192.168.0.45:49049|Desktop-5QFF67"
    return serverList
}

/**
 * Gets the broadcast address of the network.
 *
 * @param context The context of the calling Activity or Service to use for the request.
 * @return
 */
@Throws(IOException::class)
private fun getBroadcastAddress(context : Context): InetAddress? {
    val wifi: WifiManager = context.applicationContext.getSystemService(AppCompatActivity.WIFI_SERVICE) as WifiManager
    val dhcp = wifi.dhcpInfo ?: return null
    val broadcast = dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
    val quads = ByteArray(4)

    for (k in 0..3) quads[k] = (broadcast shr k * 8 and 0xFF).toByte()
    return InetAddress.getByAddress(quads)
}
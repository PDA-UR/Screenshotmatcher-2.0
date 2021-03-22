package com.pda.screenshotmatcher2

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.net.*

const val PROTOCOL = "http://"
const val MAX_SERVERS = 5

fun discoverServersOnNetwork(context: Context, port: Int = 49050, message: String = "screenshot matcher client LF server") : List<String> {
    val s = DatagramSocket().also {
        it.broadcast = true
        it.reuseAddress = true
        it.soTimeout = 500
        if(!it.isBound) it.bind(InetSocketAddress(port))
    }
    val bufS = message.toByteArray()   //send
    val bufR = ByteArray(1024)    //receive
    val bcAddress = getBroadcastAddress(context) ?: return emptyList()
    val packetS = DatagramPacket(bufS, bufS.size, bcAddress, port)
    val packetR = DatagramPacket(bufR, bufR.size)
    val serverList = mutableListOf<String>()

    s.send(packetS)
    // try to get answers from every server on the LAN
    for (i in 1..MAX_SERVERS){
        try {
            s.receive(packetR)  // receive will block here, until soTimeout gets reached
            serverList.add(
                PROTOCOL + String(packetR.data, 0, packetR.length)
            )
        }
        catch(e: SocketTimeoutException) {
            break
        }
    }
    s.close()

    // expected answer from server: "192.168.0.45:99887"
    val activity: CameraActivity = context as CameraActivity
    activity.onServerURLsGet(serverList)  // call function on main thread
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
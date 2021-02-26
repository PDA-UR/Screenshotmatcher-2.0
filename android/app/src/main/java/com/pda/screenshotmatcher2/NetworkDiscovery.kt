package com.pda.screenshotmatcher2

import android.content.Context
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

const val PROTOCOL = "http://"

fun discoverServerOnNetwork(context: Context, port: Int = 49050, message: String = "screenshot matcher client LF server") : String {
    val s = DatagramSocket().also {
        it.broadcast = true
        it.reuseAddress = true
        if(!it.isBound) it.bind(InetSocketAddress(port))
    }

    val bufS = message.toByteArray()   //send
    val bufR = ByteArray(1024)    //receive
    val bcAddress = getBroadcastAddress(context) ?: return ""
    val packetS = DatagramPacket(bufS, bufS.size, bcAddress, port)
    val packetR = DatagramPacket(bufR, bufR.size)

    s.send(packetS)
    s.receive(packetR)
    s.close()
    // expected answer from server: "192.168.0.45:99887"
    val serverURL = PROTOCOL + String(packetR.data, 0, packetR.length)
    return serverURL
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
package com.baxolino.apps.floats.tools

import android.content.ContentResolver
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.provider.Settings
import java.net.InetAddress
import java.net.NetworkInterface


object Utils {
  fun getDeviceName(contentResolver: ContentResolver): String {
    return Settings.Global.getString(
      contentResolver,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
        Settings.Global.DEVICE_NAME
      else "bluetooth_name"
    )
  }

  private fun getIpv4Alternate(): InetAddress? {
    val networkInterfaces = NetworkInterface.getNetworkInterfaces()
    while (networkInterfaces.hasMoreElements()) {
      val inetAddresses = networkInterfaces.nextElement().inetAddresses
      while (inetAddresses.hasMoreElements()) {
        val inetAddress = inetAddresses.nextElement()
        if (!inetAddress.isLoopbackAddress
          && !inetAddress.isLinkLocalAddress
          && inetAddress.isSiteLocalAddress
        ) {
          return InetAddress.getByName(inetAddress.hostAddress)
        }
      }
    }
    return null
  }

  fun getIpv4(context: Context): InetAddress {
    val ipv4 = getIpv4Alternate()
    ipv4?.let {
      return it
    }
    val connector = context.getSystemService(ConnectivityManager::class.java)

    // this may return null when not connected to any network
    val linkProperties = connector.getLinkProperties(connector.activeNetwork) as LinkProperties
    for (linkAddress in linkProperties.linkAddresses) {
      val address = linkAddress.address
      val hostAddress = address.hostAddress

      // we have to look for Ip4 address here
      if (isValidIpv4(hostAddress))
        return InetAddress.getByAddress(address.address)
    }
    throw Error("Could not find Ipv4 address")
  }

  private fun isValidIpv4(ip: String?): Boolean {
    return try {
      if (ip.isNullOrEmpty()) return false
      val parts = ip.split("\\.".toRegex())
        .dropLastWhile { it.isEmpty() }
        .toTypedArray()
      if (parts.size != 4) return false
      for (s in parts) {
        val i = s.toInt()
        if (i < 0 || i > 255) {
          return false
        }
      }
      !ip.endsWith(".")
    } catch (nfe: NumberFormatException) {
      false
    }
  }
}
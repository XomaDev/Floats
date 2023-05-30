package com.baxolino.apps.floats.tools

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.baxolino.apps.floats.core.http.SocketConnection

object Utils {
  fun getDeviceName(contentResolver: ContentResolver): String {
    return Settings.Global.getString(
      contentResolver,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
        Settings.Global.DEVICE_NAME
      else "bluetooth_name"
    )
  }

  // return a string containing device's Ipv4 address including
  // the name

  fun getIpv4WithDeviceNameString(context: Context): String {
    return "${
      SocketConnection.getIpv4(context)
        .hostAddress
    }%${
      getDeviceName(context.contentResolver)
    }"
  }

  fun getIpv4AndDeviceName(data: String): Pair<String, String> {
    val dividerIndex = data.indexOf("%")

    val hostAddress = data.substring(0, dividerIndex)
    val name = data.substring(dividerIndex + 1, data.length)
    return Pair(hostAddress, name)
  }
}
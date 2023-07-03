package com.baxolino.apps.floats

import android.Manifest
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.baxolino.apps.floats.camera.ScanActivity
import com.baxolino.apps.floats.core.KnowEachOther
import com.baxolino.apps.floats.core.SessionService
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.core.transfer.SocketUtils
import com.baxolino.apps.floats.tools.DynamicTheme
import com.baxolino.apps.floats.tools.Utils
import com.github.alexzhirkevich.customqrgenerator.QrData
import com.github.alexzhirkevich.customqrgenerator.vector.QrCodeDrawable
import com.github.alexzhirkevich.customqrgenerator.vector.QrVectorOptions
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorBallShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorColor
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorColors
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorFrameShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorPixelShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorShapes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class HomeFragment(
  private val activity: Activity
) {

  companion object {
    private const val TAG = "HomeActivity"
    const val STORAGE_REQUEST_CODE = 7
    const val CAMERA_REQUEST_CODE = 8
  }

  private var initialized = false

  private lateinit var view: View
  private lateinit var intent: Intent

  private var permissionDialog: AlertDialog? = null

  private lateinit var deviceConnectionInfo: String
  private lateinit var ourId: String

  private lateinit var connector: SocketConnection
  private val localPort = SocketUtils.findAvailableTcpPort()

  private var hasConnection = true

  fun onCreate(
    inflater: LayoutInflater,
    container: ViewGroup?
  ): View {
    if (initialized)
      return view
    initialized = true

    intent = activity.intent
    // Inflate the layout for this fragment
    view = inflater.inflate(R.layout.fragment_home, container, false)

    Log.d(TAG, "onCreate()")
    DynamicTheme.themeOfHomeActivity(view, activity)

    val deviceText = view.findViewById<TextView>(R.id.device_label)
    val deviceId = Utils.getDeviceName(activity.contentResolver)

    deviceText.text = deviceId
    ourId = deviceId

    val qrImageView = view.findViewById<ImageView>(R.id.qr_image)

    Log.d(TAG, "Port[$localPort]")
    connector = SocketConnection.getMainSocket()

    val ipv4 = Utils.getIpv4(activity)
    if (ipv4 == null) {
      hasConnection = false
      activity.startActivity(
        Intent(
          activity,
          ConnectionActivity::class.java
        )
      )
      return view
    } else {
      // just keep connection goes off
      lookOnDisconnect()
    }
    val connectionInfo = arrayOf(
      ByteBuffer.wrap(
        ipv4.address
      ).int,
      localPort,
    ).joinToString("\u0000")

    deviceConnectionInfo = connectionInfo
    generateQr(qrImageView, connectionInfo)


    val scanButton = view.findViewById<Button>(R.id.scanButton)

    scanButton.setOnClickListener {
      if (ContextCompat.checkSelfPermission(
          activity,
          Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_DENIED
      ) {
        ActivityCompat.requestPermissions(
          activity,
          arrayOf(Manifest.permission.CAMERA),
          CAMERA_REQUEST_CODE
        )
      } else {
        activity.startActivity(
          Intent(activity, ScanActivity::class.java)
        )
      }
    }

    if (intent.hasExtra("content")) {
      onScanResult()
    } else {
      // we put it over here because, we don't want it called multiple
      // times
      connector.acceptOnPort(localPort) {
        Log.d(TAG, "Accepted()")
        onConnectionSuccessful(true)
      }
    }
    if (intent.hasExtra("event_disconnect")) {
      val device = intent.getStringExtra("event_disconnect")
      // we are disconnected from a device we
      // were once connected to

      MaterialAlertDialogBuilder(activity, R.style.FloatsCustomDialogTheme)
        .setTitle("Disconnected")
        .setMessage("Connection to device $device was broken.")
        .setPositiveButton("Dismiss") { dialog, _ ->
          dialog.dismiss()
        }
        .show()
    }

    return view
  }

  private fun lookOnDisconnect() {
    val executor = ScheduledThreadPoolExecutor(1)
    executor.scheduleAtFixedRate({
      if (Utils.getIpv4(activity) == null) {
        executor.shutdown()
        activity.startActivity(
          Intent(
            activity,
            ConnectionActivity::class.java
          )
        )
      }
    }, 0, 1, TimeUnit.SECONDS)
  }

  fun onResume() {
    if (Utils.getIpv4(activity) == null) {
      activity.startActivity(
        Intent(
          activity,
          ConnectionActivity::class.java
        )
      )
      return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
      && permissionDialog == null
      && ContextCompat.checkSelfPermission(
        activity,
        WRITE_EXTERNAL_STORAGE
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        activity, arrayOf(WRITE_EXTERNAL_STORAGE),
        STORAGE_REQUEST_CODE
      )
    }
  }

  fun onCameraPermission(granted: Boolean) {
    Log.d(TAG, "Permission = $granted")
    if (granted) {
      activity.startActivity(
        Intent(activity, ScanActivity::class.java)
      )
    } else {
      Snackbar.make(
        view,
        "Camera Permission was denied.",
        Snackbar.LENGTH_LONG
      ).show()
    }
  }

  fun onStoragePermission(granted: Boolean) {
    if (!granted) {
      permissionDialog = MaterialAlertDialogBuilder(activity, R.style.FloatsCustomDialogTheme)
        .setCancelable(false)
        .setTitle("Permission Denied")
        .setMessage("App needs write permission for file saving.")
        .setNeutralButton("Settings") { _, _ ->
          permissionDialog = null
          activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
              .setData(
                Uri.parse("package:${activity.packageName}")
              )
          )
        }.setPositiveButton("Retry") { _, _ ->
          ActivityCompat.requestPermissions(activity, arrayOf(WRITE_EXTERNAL_STORAGE), 79)
        }
        .show()
    }
  }

  private fun onScanResult() {
    // we are back from the qr scan activity
    // and we can connect to that bluetooth device from the address
    val qrContent = intent.getStringExtra("content")

    // attempts to start connecting to the other
    // device
    qrContent?.let {
      val args = qrContent.split("\u0000")
      val ipv4Address = InetAddress.getByAddress(
        ByteBuffer
          .allocate(Integer.BYTES)
          .order(ByteOrder.BIG_ENDIAN)
          .putInt(args[0].toInt())
          .array()
      )
        .hostAddress!!

      val port = args[1].toInt()

      Log.d(TAG, "onScanResult: $args $ipv4Address")

      doConnect(port, ipv4Address)
    }
  }

  /*
    this could be also directly invoked by
    HomeActivity, when connecting from the People Fragment
  */
  private fun doConnect(port: Int, ipv4Address: String) {
    connector.connectOnPort(port, ipv4Address, false, {
      Log.d(TAG, "Connected()")
      onConnectionSuccessful(false)
    }, {
      Snackbar.make(
        view,
        it,
        Snackbar.LENGTH_LONG
      ).show()
    })
  }

  private fun onConnectionSuccessful(isServer: Boolean) {
    var localPort = -1
    if (isServer) {
      // if we are the server, we are the one who creates
      // and send to the client
      localPort = SocketUtils.findAvailableTcpPort()
    }
    val packetJson = JSONObject()
      .put("name", ourId)
    if (isServer) {
      packetJson.put("port", localPort)
    }
    KnowEachOther.initiate(
      packetJson.toString(), connector
    )
    { data, host ->
      val json = JSONObject(data)
      val name = json.getString("name")

      // hostPort is valid if we are client
      val service = Intent(activity, SessionService::class.java)
        .putExtra("partner_device", name)
        .putExtra("server", isServer)
        .putExtra("host", host)
        .putExtra("port", if (isServer) localPort else json.getInt("port"))

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        activity.startForegroundService(service)
      else activity.startService(service)

      activity.startActivity(
        Intent(activity, SessionActivity::class.java)
          .putExtra("deviceName", name)
          .putExtra("hostAddress", host)
      )
    }
  }

  private fun generateQr(qrImageView: ImageView, text: String) {
    val primaryColor = DynamicTheme.variant60Color(activity)

    val options = QrVectorOptions.Builder()
      .setPadding(.3f)
      .setColors(
        QrVectorColors(
          dark = QrVectorColor
            .Solid(primaryColor),
          ball = QrVectorColor.Solid(
            primaryColor
          )
        )
      )
      .setShapes(
        QrVectorShapes(
          darkPixel = QrVectorPixelShape
            .RoundCorners(.5f),
          ball = QrVectorBallShape
            .RoundCorners(.25f),
          frame = QrVectorFrameShape
            .RoundCorners(.25f),
        )
      )
      .build()
    qrImageView.background = QrCodeDrawable(
      QrData.Text(text),
      options
    )
  }
}
package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import com.baxolino.apps.floats.camera.ScanActivity
import com.baxolino.apps.floats.core.KRSystem
import com.baxolino.apps.floats.core.KRSystem.KnowListener
import com.baxolino.apps.floats.core.http.SocketConnection
import com.baxolino.apps.floats.core.http.SocketUtils
import com.baxolino.apps.floats.tools.ThemeHelper
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
import com.google.android.material.button.MaterialButton
import org.json.JSONObject


@ExperimentalGetImage
class HomeActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "HomeActivity"

    private const val JSON_IPV4 = "Ipv4"
    private const val JSON_PORT = "port"
    private const val JSON_DEVICE_NAME = "device_name"
  }

  private lateinit var adapter: BluetoothAdapter

  private lateinit var deviceConnectionInfo: String
  private lateinit var selfDeviceId: String

  private var localPort = -1
  private lateinit var connector: SocketConnection

  @SuppressLint("HardwareIds")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_home)
    ThemeHelper.themeOfHomeActivity(this)

    val bluetoothManager = getSystemService(BluetoothManager::class.java)
    adapter = bluetoothManager.adapter

    val deviceText = findViewById<TextView>(R.id.device_label)
    val deviceId = Utils.getDeviceName(contentResolver)

    deviceText.text = deviceId
    selfDeviceId = deviceId

    val qrImageView = findViewById<ImageView>(R.id.qr_image)

    // create a new socket connection with the local port
    localPort = SocketUtils.findAvailableTcpPort()
    connector = SocketConnection(localPort)
      .acceptOnPort {
        Log.d(TAG, "Connection was accepted.")
        onConnectionAccepted(
          connector.socket.inetAddress.hostAddress!!
        )
      }

    val connectionInfo = JSONObject()
      .put(
        JSON_IPV4, SocketConnection.getIpv4(this)
          .hostAddress
      ).put(
        JSON_PORT, localPort
      ).put(
        JSON_DEVICE_NAME, deviceId
      )
    deviceConnectionInfo = connectionInfo.toString()
    generateQr(qrImageView, connectionInfo.toString())

    if (intent.hasExtra("address")) {
      connect()
    } else {
      val scanButton = findViewById<MaterialButton>(R.id.scanButton)

      scanButton.setOnClickListener {
        startActivity(
          Intent(this, ScanActivity::class.java)
        )
      }
    }
  }

  private fun connect() {
    // we are back from the qr scan activity
    // and we can connect to that bluetooth device from the address
    val qrContent = intent.getStringExtra("address")

    // initiates nsd discovery and tries to find
    // the device with the {name}
    qrContent?.let {
      val json = JSONObject(qrContent)

      Toast.makeText(
        this,
        "Connecting", Toast.LENGTH_SHORT
      ).show()

      val ipv4Address = json.getString(JSON_IPV4)
      connector.connectOnPort(json.getInt(JSON_PORT), ipv4Address) {
        Log.d(TAG, "Connection was established")
        onConnectionSuccessful(json.getString(JSON_DEVICE_NAME), ipv4Address)
      }
    }
  }

  // called upon connection, from NsdFloats.kt

  private fun onConnectionAccepted(hostAddress: String) {
    val system = KRSystem.getInstance(selfDeviceId, connector)

    // we are yet to find out who has connected us
    system.readKnowRequest(object : KnowListener {
      override fun received(deviceName: String) {
        runOnUiThread { informConnection(deviceName, hostAddress) }
      }

      override fun timeout() {
        runOnUiThread {
          Toast.makeText(
            applicationContext,
            "Client failed to send to know request.",
            Toast.LENGTH_SHORT
          ).show()
        }
      }
    })
  }

  // called upon successful connection request from
  // NsdFloats.kt
  private fun onConnectionSuccessful(deviceName: String, hostAddress: String) {
    val system = KRSystem.getInstance(selfDeviceId, connector)
    system.postKnowRequest(deviceName, {
      // client received know-request
      Log.d(TAG, "Know Request Successful")
      runOnUiThread {
        informConnection(deviceName, hostAddress)
      }
    }, {
      Log.d(TAG, "Server failed to respond to KR")
      runOnUiThread {
        Toast.makeText(
          this,
          "$deviceName did not respond to request.",
          Toast.LENGTH_SHORT
        ).show()
      }
    })
  }


  private fun informConnection(deviceName: String, hostAddress: String) {
    startActivity(
      Intent(this, SessionActivity::class.java)
        .putExtra("deviceName", deviceName)
        .putExtra("hostAddress", hostAddress)
    )
  }

  private fun generateQr(qrImageView: ImageView, text: String) {
    Log.d(TAG, "generateQr: $text")
    val primaryColor = ThemeHelper.variant60Color(this)

    val data = QrData.Text(text)
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
    val drawable: Drawable = QrCodeDrawable(data, options)
    qrImageView.background = drawable
  }
}
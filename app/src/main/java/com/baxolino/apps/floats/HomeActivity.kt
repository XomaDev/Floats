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
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.core.transfer.SocketUtils
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
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder


@ExperimentalGetImage
class HomeActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "HomeActivity"
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
    connector = SocketConnection.getMainInstance(localPort)
      .acceptOnPort {
        Log.d(TAG, "Connection was accepted.")
        onConnectionAccepted(
          connector.socket.inetAddress.hostAddress!!
        )
      }


    // TODO:
    //  we have to also change the read mechanism
    //  after qr-scanning
    val connectionInfo = arrayOf(
      ByteBuffer.wrap(
        SocketConnection.getIpv4(this).address
      ).int,
      localPort,
      // TODO:
      //  let's make it much more smaller by using alternative
      //  method to embedding device id here
      deviceId
    ).joinToString("\u0000")

    deviceConnectionInfo = connectionInfo
    generateQr(qrImageView, connectionInfo)

    if (intent.hasExtra("content")) {
      onScanResult()
    } else {
      val scanButton = findViewById<MaterialButton>(R.id.scanButton)

      scanButton.setOnClickListener {
        startActivity(
          Intent(this, ScanActivity::class.java)
        )
      }
    }
  }

  private fun onScanResult() {
    // we are back from the qr scan activity
    // and we can connect to that bluetooth device from the address
    val qrContent = intent.getStringExtra("content")

    // initiates nsd discovery and tries to find
    // the device with the {name}
    qrContent?.let {
      val args = qrContent.split("\u0000")
      val ipv4Address = InetAddress.getByAddress(
        ByteBuffer
          .allocate(Integer.BYTES)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putInt(args[0].toInt())
          .array())
        .hostAddress!!

      val port = args[1].toInt()
      val deviceId = args[2]

      Log.d(TAG, "onScanResult: $args")
      Toast.makeText(
        this,
        "Connecting", Toast.LENGTH_SHORT
      ).show()

      connector.connectOnPort(port, ipv4Address) {
        Log.d(TAG, "Connection was established")
        onConnectionSuccessful(deviceId, ipv4Address)
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
      runOnUiThread {
        informConnection(deviceName, hostAddress)
      }
    }, {
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
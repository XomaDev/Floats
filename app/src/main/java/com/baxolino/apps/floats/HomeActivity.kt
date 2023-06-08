package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import com.baxolino.apps.floats.camera.ScanActivity
import com.baxolino.apps.floats.core.KnowEachOther
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.core.transfer.SocketUtils
import com.baxolino.apps.floats.tools.ThemeHelper
import com.baxolino.apps.floats.tools.Utils
import com.baxolino.apps.floats.tools.Utils.getIpv4
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
  private lateinit var ourId: String

  private var localPort = -1
  private lateinit var connector: SocketConnection

  @SuppressLint("HardwareIds")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_home)
    System.loadLibrary("native-lib")


    ThemeHelper.themeOfHomeActivity(this)

    val bluetoothManager = getSystemService(BluetoothManager::class.java)
    adapter = bluetoothManager.adapter

    val deviceText = findViewById<TextView>(R.id.device_label)
    val deviceId = Utils.getDeviceName(contentResolver)

    deviceText.text = deviceId
    ourId = deviceId

    val qrImageView = findViewById<ImageView>(R.id.qr_image)

    // create a new socket connection with the local port
    localPort = SocketUtils.findAvailableTcpPort()
    connector = SocketConnection.getMainSocket(localPort)


    val connectionInfo = arrayOf(
      ByteBuffer.wrap(
        getIpv4(this).address
      ).int,
      localPort,
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

      // we put it over here because, we don't want it called multiple
      // times
      connector.acceptOnPort {
        Log.d(TAG, "Accepted()")
        onConnectionSuccessful()
      }
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
      Toast.makeText(
        this,
        "Connecting", Toast.LENGTH_SHORT
      ).show()

      connector.connectOnPort(port, ipv4Address, false) {
        Log.d(TAG, "Connected()")
        onConnectionSuccessful()
      }
    }
  }


  private fun onConnectionSuccessful() {
    KnowEachOther.initiate(ourId, connector) { name, host ->
      startActivity(
        Intent(this, SessionActivity::class.java)
          .putExtra("deviceName", name)
          .putExtra("hostAddress", host)
      )
    }
  }

  private fun generateQr(qrImageView: ImageView, text: String) {
    val primaryColor = ThemeHelper.variant60Color(this)

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
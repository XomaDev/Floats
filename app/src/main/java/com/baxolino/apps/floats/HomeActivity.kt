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
import com.baxolino.apps.floats.core.http.NsdFloats
import com.baxolino.apps.floats.core.http.NsdInterface
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
import com.google.android.material.card.MaterialCardView


@ExperimentalGetImage
class HomeActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "HomeActivity"
  }

  private lateinit var adapter: BluetoothAdapter

  private lateinit var ipv4AddressWithDeviceName: String
  private lateinit var thisDeviceName: String

  private lateinit var nsdFloats: NsdFloats

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
    thisDeviceName = deviceId

    val qrImageView = findViewById<ImageView>(R.id.qr_image)

    // the Qr code will include Device name and the Ipv4 address
    ipv4AddressWithDeviceName = Utils.getIpv4WithDeviceNameString(this)
    generateQr(qrImageView, ipv4AddressWithDeviceName)

    val scanButton = findViewById<MaterialButton>(R.id.scanButton)

    // creating the instance will register
    // NSD service
    nsdFloats = NsdFloats.getInstance(this, thisDeviceName)!!

    if (intent.hasExtra("address")) {
      connect()
    } else {
      scanButton.setOnClickListener {
        nsdFloats
        startActivity(
          Intent(this, ScanActivity::class.java)
        )
      }
    }

    val deviceIdRecentConnection = findViewById<TextView>(R.id.recent_device_id_label)
    val otherRecentDeviceId = nsdFloats.retrieveSavedDevice()

    deviceIdRecentConnection.text = otherRecentDeviceId

    val recentConnectionStatus = findViewById<TextView>(R.id.recent_connection_status)
    val deviceStatusCard = findViewById<MaterialCardView>(R.id.device_status)

    nsdFloats.registerAvailabilityListener(
      otherRecentDeviceId,
      object : NsdInterface.ServiceAvailableListener {
        override fun available() {
          runOnUiThread {
            recentConnectionStatus.text = "Online"

            deviceStatusCard.setOnClickListener {
              Toast.makeText(
                applicationContext,
                "Connecting", Toast.LENGTH_SHORT
              ).show()

              nsdFloats.discover(deviceIdRecentConnection.text.toString())
            }
          }
        }

        override fun disappeared() {
          runOnUiThread {
            recentConnectionStatus.text = "Offline"
            deviceStatusCard.setOnClickListener(null)
          }
        }
      })
  }

  private fun connect() {
    // we are back from the qr scan activity
    // and we can connect to that bluetooth device from the address
    val qrContent = intent.getStringExtra("address")

    // initiates nsd discovery and tries to find
    // the device with the {name}
    qrContent?.let {
      val ipv4AddressWithDeviceName = Utils.getIpv4AndDeviceName(qrContent)
      Toast.makeText(
        this,
        "Connecting", Toast.LENGTH_SHORT
      ).show()
      nsdFloats.discover(ipv4AddressWithDeviceName.second)
    }
  }

  // called upon connection, from NsdFloats.kt

  fun onConnectionAccepted() {
    val system = KRSystem.getInstance(this, thisDeviceName, nsdFloats)

    // we are yet to find out who has connected us
    system.readKnowRequest(object : KnowListener {
      override fun received(text: String) {
        val data = Utils.getIpv4AndDeviceName(text)
        // save the device name here so that the user
        // can quick connect to it later
        nsdFloats.saveConnectedDevice(data.second)

        runOnUiThread { informConnection(data.second, data.first) }
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
  fun onConnectionSuccessful(connectedDevice: String) {
    val system = KRSystem.getInstance(this, thisDeviceName, nsdFloats)
    system.postKnowRequest(ipv4AddressWithDeviceName, {
      // client received know-request
      Log.d(TAG, "Know Request Successful")
      runOnUiThread {
        informConnection(connectedDevice, nsdFloats.hostAddress)
      }
    }, {
      Log.d(TAG, "Server failed to respond to KR")
      runOnUiThread {
        Toast.makeText(
          this,
          "$connectedDevice did not respond to request.",
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
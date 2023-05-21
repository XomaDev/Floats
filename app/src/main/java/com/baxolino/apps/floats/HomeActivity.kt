package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import com.baxolino.apps.floats.camera.ScanActivity
import com.baxolino.apps.floats.core.KRSystem
import com.baxolino.apps.floats.core.KRSystem.KnowListener
import com.baxolino.apps.floats.tools.ThemeHelper
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
import kotlin.random.Random


@ExperimentalGetImage
class HomeActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "HomeActivity"
  }

  private lateinit var adapter: BluetoothAdapter
  private lateinit var deviceName: String

  private lateinit var nsdFloats: NsdFloats

  @SuppressLint("HardwareIds")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_home)
    ThemeHelper.themeOfHomeActivity(this)

    val bluetoothManager = getSystemService(BluetoothManager::class.java)
    adapter = bluetoothManager.adapter

    val deviceText = findViewById<TextView>(R.id.device_label)
    val deviceId = getDeviceName()

    deviceText.text = deviceId
    deviceName = deviceId

    val qrImageView = findViewById<ImageView>(R.id.qr_image)
    generateQr(qrImageView, deviceId)

    val scanButton = findViewById<MaterialButton>(R.id.scanButton)

    // creating the instance will register
    // NSD service
    nsdFloats = NsdFloats.getInstance(this, deviceName)!!

    if (intent.hasExtra("address")) {
      // we are back from the qr scan activity
      // and we can connect to that bluetooth device from the address
      val name = intent.getStringExtra("address")

      // initiates nsd discovery and tries to find
      // the device with the {name}
      name?.let {
        Toast.makeText(this,
          "Connecting", Toast.LENGTH_SHORT).show()
        nsdFloats.discover(name)
      }
    } else {
      scanButton.setOnClickListener {
        nsdFloats
        startActivity(
          Intent(this, ScanActivity::class.java)
        )
      }
    }
  }

  private fun getDeviceName(): String {
    val name = Settings.Global.getString(
      contentResolver,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
        Settings.Global.DEVICE_NAME
      else "bluetooth_name"
    )
    return "$name #${Random.nextInt()}"
  }


  // called from NsdFloats.java class after
  // creating connection with another device

  fun deviceConnected(isServer: Boolean, device: String?) {
    val krSystem = KRSystem.getInstance(this, deviceName, nsdFloats)

    if (!isServer) {
      krSystem.postKnowRequest(deviceName, {
        // client received know-request
        Log.d(TAG, "Know Request Successful")
        runOnUiThread { informConnection(device!!) }
      }, {
        Log.d(TAG, "Server failed to respond to KR")
        runOnUiThread {
          Toast.makeText(
            this,
            "$device did not respond to request.",
            Toast.LENGTH_SHORT
          ).show()
        }
      })
    } else {
      krSystem.readKnowRequest(object : KnowListener {
        override fun received(name: String) {
          runOnUiThread { informConnection(name) }
        }

        override fun timeout() {
          runOnUiThread {
            Toast.makeText(
              applicationContext,
              "Client failed to respond to know request.",
              Toast.LENGTH_SHORT
            ).show()
          }
        }
      })
    }
  }

  private fun informConnection(deviceName: String) {
    startActivity(
      Intent(this, SessionActivity::class.java)
        .putExtra("deviceName", deviceName)
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
package com.baxolino.apps.floats

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.core.app.ActivityCompat
import com.baxolino.apps.floats.camera.ScanActivity
import com.baxolino.apps.floats.core.KRSystem
import com.baxolino.apps.floats.core.KRSystem.KnowListener
import com.baxolino.apps.floats.tools.PermissionHelper
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder


@ExperimentalGetImage
class HomeActivity : AppCompatActivity() {

    companion object {
        private val TAG = "HomeActivity"
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 7
    }

    private lateinit var adapter: BluetoothAdapter
    private lateinit var deviceName: String

    private var krSystem: KRSystem? = null

    private var alertDialog: AlertDialog? = null

    private lateinit var nsdFloats: NsdFloats

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_home)
        ThemeHelper.themeOfHomeActivity(this)

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        adapter = bluetoothManager.adapter

        val deviceText = findViewById<TextView>(R.id.device_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkBluetoothPermission()
        }

        deviceText.text = adapter.name
        deviceName = adapter.name

        val qrImageView = findViewById<ImageView>(R.id.qr_image)
        generateQr(qrImageView, adapter.name)

        val scanButton = findViewById<MaterialButton>(R.id.scanButton)

        // creating the instance will register
        // NSD service
        nsdFloats = NsdFloats.getInstance(this, deviceName)!!

        if (intent.hasExtra("address") && PermissionHelper.canAccessBluetooth(this)) {
            // we are back from the qr scan activity
            // and we can connect to that bluetooth device from the address
            val name = intent.getStringExtra("address")

            // initiates nsd discovery and tries to find
            // the device with the {name}
            nsdFloats.discover(name!!)
        } else {
            scanButton.setOnClickListener {
                nsdFloats
                startActivity(
                    Intent(this, ScanActivity::class.java)
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkBluetoothPermission() {
        if (!PermissionHelper.canAccessBluetooth(this)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
            return
        }
    }


    // called from NsdFloats.java class after
    // creating connection with another device
    @SuppressLint("MissingPermission")
    fun deviceConnected(isServer: Boolean, device: String?) {
        krSystem = KRSystem.getInstance(this, deviceName, nsdFloats)

        if (!isServer) {
            krSystem!!.postKnowRequest(deviceName, {
                // client received know-request
                Log.d(TAG, "Know Request Successful")

                runOnUiThread {
                    alertDialog?.apply {
                        dismiss()
                    }
                    informConnection(device!!)
                }
            }, {
                Log.d(TAG, "Server failed to respond to KR")
                runOnUiThread {
                    Toast.makeText(this,
                        "$device did not respond to request.",
                        Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            Log.d(TAG, "establishedConnection: waiting")
            krSystem!!.readKnowRequest(object: KnowListener {
                override fun received(name: String) {
                    runOnUiThread {
                        informConnection(name)
                    }
                }

                override fun timeout() {
                    Log.d(TAG, "Client failed to send KR")
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                MaterialAlertDialogBuilder(this, R.style.FloatsCustomDialogTheme)
                    .setTitle("Permission Denied")
                    .setMessage("'Nearby Devices' permission is required for the app to access Bluetooth.")
                    .setCancelable(false)
                    .setPositiveButton("Settings") { _: DialogInterface?, _: Int ->
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${packageName}")
                            )
                        )
                    }
                    .show()
            }
        }
    }
}
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
    private val connector = FloatsBluetooth(this)

    private var krSystem: KRSystem? = null

    private var alertDialog: AlertDialog? = null

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

        if (intent.hasExtra("address") && PermissionHelper.canAccessBluetooth(this)) {
            // we are back from the qr scan activity
            // and we can connect to that bluetooth device from the address
            val address = intent.getStringExtra("address")
            connect(address!!)
        } else {
            scanButton.setOnClickListener {
                startActivity(
                    Intent(this, ScanActivity::class.java)
                )
            }
            connector.acceptConnection(adapter)
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

    @SuppressLint("MissingPermission")
    private fun connect(name: String) {
        var remoteDevice: BluetoothDevice? = null

        for (device in adapter.bondedDevices) {
            if (device.name == name) {
                remoteDevice = device
                break
            }
        }

        if (remoteDevice != null) {
            alertDialog = MaterialAlertDialogBuilder(this, R.style.FloatsCustomDialogTheme)
                .setTitle("Connection")
                .setMessage("Requesting connection to ${remoteDevice.name}")
                .show()
            connector.requestConnection(remoteDevice)
        } else {
            Log.d(TAG, "Did not find device $name")
        }
    }

    // called from FloatsBluetooth.kt class after
    // creating connection with another device
    @SuppressLint("MissingPermission")
    fun establishedConnection(isServer: Boolean, device: BluetoothDevice?) {
        krSystem = KRSystem.getInstance(deviceName, connector)

        if (!isServer) {
            krSystem!!.postKnowRequest(deviceName, {
                // client received know-request
                Log.d(TAG, "Know Request Successful")

                runOnUiThread {
                    alertDialog?.apply {
                        dismiss()
                    }
                    informConnection(device!!.name)
                }
            }, {
                Log.d(TAG, "Server failed to respond to KR")
                Toast.makeText(this,
                    "${device!!.name} did not respond to request.",
                    Toast.LENGTH_SHORT).show();
            })
        } else {
            Log.d(TAG, "establishedConnection: waiting")
            krSystem!!.readKnowRequest {
                Log.d(TAG, "Received Server Name $it")
                runOnUiThread {
                    informConnection(it)
                }
            }
        }
    }

    private fun informConnection(deviceName: String) {
//        startActivity(
//            Intent(this, SessionActivity::class.java)
//                .putExtra("deviceName", deviceName)
//        )
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
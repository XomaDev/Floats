package com.baxolino.apps.floats

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class HomeActivity : AppCompatActivity() {

    companion object {
        private val TAG = "HomeActivity"
        private val BLUETOOTH_PERMISSION_REQUEST_CODE = 7
    }

    private lateinit var adapter: BluetoothAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        adapter = bluetoothManager.adapter

        val deviceText = findViewById<TextView>(R.id.device_label)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
            return
        }
        deviceText.text = adapter.name
        val bondedDevices = adapter.bondedDevices
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
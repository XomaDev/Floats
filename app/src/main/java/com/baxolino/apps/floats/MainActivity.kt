package com.baxolino.apps.floats

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.core.app.ActivityCompat
import com.baxolino.apps.floats.tools.ThemeHelper


@ExperimentalGetImage class MainActivity : AppCompatActivity() {

    companion object {
        private const val READ_PERMISSION_LEGACY_CODE = 8
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (true) {
            startActivity(
                Intent(this, SessionActivity::class.java)
            )
            return
        }
        if (hasStorageAccess()) {
            startActivity(
                Intent(this, HomeActivity::class.java)
            )
        }
        setContentView(R.layout.activity_welcome)
        activityWelcome()
        ThemeHelper.themOfMainActivity(this)
    }


    private fun activityWelcome() {
        val allowButton = findViewById<Button>(R.id.allow_button)
        allowButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uri = Uri.parse("package:${packageName}")
                manageExternalStorageResult.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        uri
                    )
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    READ_PERMISSION_LEGACY_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_PERMISSION_LEGACY_CODE) {
            checkStorageAccess()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    var manageExternalStorageResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkStorageAccess()
        }

    private fun checkStorageAccess() {
        if (!hasStorageAccess()) {
            Toast.makeText(
                this,
                "Access to storage was not granted.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            startActivity(
                Intent(this,
                    HomeActivity::class.java)
            )
        }
    }

    private fun hasStorageAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED)
    }
}
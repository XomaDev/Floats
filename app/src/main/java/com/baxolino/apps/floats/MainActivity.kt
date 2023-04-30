package com.baxolino.apps.floats

import android.content.Intent
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


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Environment.isExternalStorageManager()) {
            startActivity(
                Intent(this, HomeActivity::class.java)
            )
        }
        setContentView(R.layout.activity_welcome)
        activityWelcome()
    }



    fun activityWelcome() {
        val allowButton = findViewById<Button>(R.id.allow_button)
        allowButton.setOnClickListener {
            val uri = Uri.parse("package:${packageName}")
            manageExternalStorageResult.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    uri
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    var manageExternalStorageResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (!Environment.isExternalStorageManager()) {
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

}
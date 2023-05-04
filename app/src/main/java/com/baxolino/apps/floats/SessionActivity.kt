package com.baxolino.apps.floats

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.core.KRSystem
import java.io.IOException
import java.lang.Exception


class SessionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SessionActivity"
    }

    private var krSystem: KRSystem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)

        val button = findViewById<Button>(R.id.add)
        button.setOnClickListener {
            filePickResult.launch(
                Intent.createChooser(
                    Intent(Intent.ACTION_GET_CONTENT)
                        .setType("*/*"), "Choose a file"
                )
            )
        }
        try {
            krSystem = KRSystem.getInstance()
            krSystem!!.listenIncomingData()

            val deviceName = intent.getStringExtra("deviceName")
            val label = findViewById<TextView>(R.id.label)
            label.text = "Connected to ${deviceName}"
        } catch (io: Exception) {
            // we are still in the testing stage
        }
    }

    private var filePickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val contentUri = it.data!!.data
            contentUri?.let {
                val stream = contentResolver.openInputStream(contentUri)
                val fileName = contentUri.getName()

                Log.d(TAG, "File Name = $fileName")
                Thread {
                    krSystem?.pushStream(fileName, stream)
                }.start()
            }
        }

    private fun Uri.getName(): String {
        contentResolver.query(
            this,
            null,
            null,
            null,
            null
        )?.apply {
            val nameIndex = getColumnIndex(OpenableColumns.DISPLAY_NAME)
            moveToFirst()

            val fileName = getString(nameIndex)
            close()
            return fileName
        }
        throw Error("Something Went Wrong File Querying Name")
    }
}
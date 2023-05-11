package com.baxolino.apps.floats

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.core.KRSystem
import com.baxolino.apps.floats.tools.ThemeHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator


class SessionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SessionActivity"
    }

    private lateinit var krSystem: KRSystem

    private lateinit var fileNameLabel: TextView
    private lateinit var fileSizeLabel: TextView

    private lateinit var progressLabel: TextView

    private lateinit var progressBar: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)
        ThemeHelper.themeOfSessionActivity(this)

        val isConnected = intent.hasExtra("deviceName")

        if (isConnected) {
            // or else we are just testing
            val deviceName = intent.getStringExtra("deviceName")

            val label = findViewById<TextView>(R.id.label)
            label.text = "Connected to $deviceName"

            val fabButton = findViewById<FloatingActionButton>(R.id.add_files)

            fabButton.setOnClickListener {
                fileActivityResult.launch(
                    Intent.createChooser(
                        Intent(Intent.ACTION_GET_CONTENT)
                            .setType("*/*"), "Choose a file"
                    )
                )
            }
            krSystem = KRSystem.getInstance()

            fileNameLabel = findViewById(R.id.file_name)
            fileSizeLabel = findViewById(R.id.file_size)

            progressLabel = findViewById(R.id.progress_label)
            progressBar = findViewById(R.id.progress_bar)

            handleFileRequests();
        }
    }

    private fun handleFileRequests() {
        krSystem.checkFileRequests(object: KRSystem.FileRequestListener {
            override fun request(name: String, length: Int) {
                runOnUiThread {
                    fileNameLabel.text = name.substring(0, name.lastIndexOf('.'))
                    fileSizeLabel.text = Formatter.formatShortFileSize(applicationContext,
                        length.toLong()
                    )
                }
            }
            override fun update(received: Int, total: Int) {
                runOnUiThread {
                    val percent = (received.toFloat().div(total) * 100).toInt()

                    progressLabel.text = percent.toString()
                    progressBar.setProgress(percent, true)
                }
            }
        })
    }


    private var fileActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.data == null)
            // no file has been picked
            return@registerForActivityResult
        val uri = it.data?.data

        uri?.apply {
            val fileName = get(OpenableColumns.DISPLAY_NAME)
            val fileLength = get(OpenableColumns.SIZE)

            Log.d(TAG, "Picked File $fileName of length $fileLength")
            krSystem.requestFileTransfer(contentResolver.openInputStream(uri),
                fileName, fileLength.toInt())
        }
    }

    private fun Uri.get(property: String): String {
        contentResolver.query(
            this,
            null,
            null,
            null,
            null
        )?.apply {
            val nameIndex = getColumnIndex(property)
            moveToFirst()

            val fileName = getString(nameIndex)
            close()
            return fileName
        }
        throw Error("Something Went Wrong File Querying Name")
    }
}
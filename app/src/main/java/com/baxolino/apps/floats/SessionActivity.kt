package com.baxolino.apps.floats

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.core.KRSystem
import com.baxolino.apps.floats.tools.ThemeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator


class SessionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SessionActivity"
    }

    private lateinit var krSystem: KRSystem

    private lateinit var fileNameLabel: TextView
    private lateinit var fileSizeLabel: TextView

    private lateinit var transferSpeed: TextView

    private lateinit var progressBar: CircularProgressIndicator

    private var awaitingConnectionDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)
        ThemeHelper.themeOfSessionActivity(this)

        val isConnected = intent.hasExtra("deviceName")

        if (!isConnected)
            return

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

        transferSpeed = findViewById(R.id.transfer_speed)

        progressBar = findViewById(R.id.progress_bar)

        handleFileRequests()
    }

    private fun handleFileRequests() {
        krSystem.checkFileRequests(this, object: KRSystem.FileRequestListener {
            private var startTime: Long? = null

            override fun requested(name: String, length: Int) {
                runOnUiThread { onTransferRequested(name, length) }
                startTime = System.nanoTime()
            }

            override fun started() {
                runOnUiThread { awaitingConnectionDialog?.dismiss() }
            }

            override fun update(received: Int, total: Int) {
                runOnUiThread {
                    onUpdateInfoRequired(startTime!!, received, total)
                }
            }
        })
    }

    private fun onTransferRequested(name: String, length: Int) {
        fileNameLabel.text = name.substring(0, name.lastIndexOf('.'))
        fileSizeLabel.text = Formatter.formatShortFileSize(applicationContext,
            length.toLong()
        )

        awaitingConnectionDialog = MaterialAlertDialogBuilder(this, R.style.FloatsCustomDialogTheme)
            .setTitle("Awaiting")
            .setMessage(getString(R.string.awaiting_transfer_text))
            .show()
    }

    private fun onUpdateInfoRequired(startTime: Long, received: Int, total: Int) {
        progressBar.setProgress(
            // formula: received / total * 100 = progress
            (received.toFloat().div(total) * 100
                    ).toInt(), true)

        val difference = (System.nanoTime() - startTime).div(1_000_000)
        if (difference <= 1500)
            return
        val speed = Formatter.formatFileSize(applicationContext, received.div(difference.div(1000)))
        transferSpeed.text = "${speed}ps"
    }

    private var fileActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.data == null)
            // no file has been picked
            return@registerForActivityResult
        val uri = it.data?.data
        uri?.let {
            prepareTransfer(uri)
        }
    }

    private fun prepareTransfer(uri: Uri) {
        uri.apply {
            val fileName = get(OpenableColumns.DISPLAY_NAME)
            val fileLength = get(OpenableColumns.SIZE)

            Log.d(TAG, "Picked File $fileName of length $fileLength")
            krSystem.prepareNsdTransfer(
                applicationContext,
                fileName,
                fileLength.toInt(),
                contentResolver.openInputStream(uri)
            )
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
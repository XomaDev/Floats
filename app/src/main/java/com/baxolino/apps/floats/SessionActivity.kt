package com.baxolino.apps.floats

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.core.bytes.files.FileRequest
import com.baxolino.apps.floats.core.KRSystem
import com.baxolino.apps.floats.core.bytes.files.RequestHandler
import com.baxolino.apps.floats.tools.ThemeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator


class SessionActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "SessionActivity"
  }

  private lateinit var system: KRSystem

  private lateinit var fileNameLabel: TextView
  private lateinit var fileSizeLabel: TextView

  private lateinit var transferSpeed: TextView

  private lateinit var progressBar: CircularProgressIndicator

  private var awaitingConnectionDialog: AlertDialog? = null

  private var fileRequestId: Int? = null

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
    system = KRSystem.getInstance()

    fileNameLabel = findViewById(R.id.file_name)
    fileSizeLabel = findViewById(R.id.file_size)

    transferSpeed = findViewById(R.id.transfer_speed)

    progressBar = findViewById(R.id.progress_bar)

    lookForFileRequests()

    val frameProgress = findViewById<FrameLayout>(R.id.progress_frame)

    frameProgress.setOnLongClickListener {
      Log.d(TAG, "Cancelling transfer")
      Toast.makeText(
        applicationContext,
        "File transfer was cancelled by the user", Toast.LENGTH_LONG
      ).show()
      fileRequestId?.let { system.abortNsdTransfer() }

      // do a reverse progress bar animation
      progressBar.setProgress(0, true)
      return@setOnLongClickListener true
    }
  }

  private fun lookForFileRequests() {
    val listener = RequestHandler.RequestsListener {
      runOnUiThread { onTransferRequested(it.name, it.length) }

      it.setStartListener {
        runOnUiThread { awaitingConnectionDialog?.dismiss() }
      }
      it.setUpdateListener { received ->
        runOnUiThread {
          onUpdateInfoRequired(it.startTime, received, it.length)
        }
      }
      it.receive(applicationContext)
    }
    system.register(RequestHandler(listener))
  }

  private fun onTransferRequested(name: String, length: Int) {
    fileNameLabel.text = name
    fileSizeLabel.text = Formatter.formatShortFileSize(
      applicationContext,
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
              ).toInt(), true
    )

    val difference = (System.currentTimeMillis() - startTime)
    if (difference == 0L)
      return
    val speed = Formatter.formatFileSize(applicationContext,
      received.toFloat().div(difference.toFloat().div(1000f)).toLong()
    )
    transferSpeed.text = "${speed}ps"
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
      val fileLength = get(OpenableColumns.SIZE).toInt()

      Log.d(TAG, "Picked File $fileName of length $fileLength")

      askConfirmationDialog(uri, fileName, fileLength)
    }
  }

  private fun askConfirmationDialog(uri: Uri, fileName: String, fileLength: Int) {
    MaterialAlertDialogBuilder(this, R.style.FloatsCustomDialogTheme)
      .setTitle("Confirm")
      .setMessage(
        "Are you sure you want to send the file $fileName of size " +
                "${Formatter.formatFileSize(applicationContext, fileLength.toLong())}?"
      )

      .setPositiveButton("Proceed") { _, _ -> prepareTransfer(uri, fileName, fileLength) }
      .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
      .show()
  }

  private fun prepareTransfer(uri: Uri, fileName: String, fileLength: Int) {
    val request = FileRequest(
      contentResolver.openInputStream(uri), fileName, fileLength
    )
    system.execute(applicationContext, request)
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
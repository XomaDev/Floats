package com.baxolino.apps.floats

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.core.SessionService
import com.baxolino.apps.floats.core.TaskExecutor
import com.baxolino.apps.floats.core.files.FileNameUtil
import com.baxolino.apps.floats.core.files.FileReceiver
import com.baxolino.apps.floats.core.files.FileRequest
import com.baxolino.apps.floats.core.files.MessageReceiver
import com.baxolino.apps.floats.core.files.RequestHandler
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.tools.ThemeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar


class SessionActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "SessionActivity"
  }


  private lateinit var deviceName: String

  private lateinit var fileNameLabel: TextView
  private lateinit var fileSizeLabel: TextView

  lateinit var hostAddress: String

  private lateinit var transferSpeedText: TextView

  private lateinit var progressBar: CircularProgressIndicator
  private lateinit var frameProgress: FrameLayout

  private var receiver: FileReceiver? = null

  private val connection = SocketConnection.getMainSocket()
  private var executor = TaskExecutor(connection)

  // we don't use this to receive messages here,
  // we are required to call onPause() and onResume()
  // lifecycles on it
  private val messageReceiver = MessageReceiver()

  private val onDisconnectReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "Disconnect broadcast received")

      // clear the main socket connection
      // instance; or it gets messed up
      SocketConnection.clear()

      // close the connection
      connection.close()
      executor.stopStreams()

      // open the HomeActivity and notify the disconnect
      // event to the user
      startActivity(
        Intent(
          this@SessionActivity,
          HomeActivity::class.java
        ).putExtra(
          "event_disconnect",
          deviceName
        ).setFlags(
          Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
      )
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_session)

    ThemeHelper.themeOfSessionActivity(this)

    val isConnected = intent.hasExtra("deviceName")

    if (!isConnected)
      return


    // or else we are just testing
    deviceName = intent.getStringExtra("deviceName")!!
    hostAddress = intent.getStringExtra("hostAddress")!!

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

    fileNameLabel = findViewById(R.id.file_name)
    fileSizeLabel = findViewById(R.id.file_size)

    transferSpeedText = findViewById(R.id.transfer_speed)

    progressBar = findViewById(R.id.progress_bar)

    frameProgress = findViewById(R.id.progress_frame)

    lookForFileRequests()

    val onBackPressedCallback = object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        // if we do not do this, when back button is pressed, it'll open
        // home activity that'll cause problems
        moveTaskToBack(true)
      }
    }
    onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
  }

  private fun lookForFileRequests() {
    val listener = { receiver: FileReceiver ->
      this.receiver = receiver

      val fname = receiver.name
      runOnUiThread { onTransferRequested(fname, receiver.length) }

      receiver.setStartListener {
        runOnUiThread {
          frameProgress.setOnLongClickListener {
            cancelFileTransfer(receiver)
            return@setOnLongClickListener true
          }
        }
      }
      receiver.setUpdateListener {
        runOnUiThread {
          progressBar.setProgress(receiver.progress, true)
          if (receiver.transferSpeed.isNotEmpty())
            transferSpeedText.text = "${receiver.transferSpeed}ps"
        }
      }
      receiver.setFinishedListener {
        frameProgress.setOnLongClickListener(null)

        runOnUiThread {
          fileNameLabel.text = "No files being received"
          fileSizeLabel.text = "(> ^_^)>"
        }
        receiver.reset(this)
      }
      receiver.setDisruptionListener {
        runOnUiThread {
          Log.d(TAG, "lookForFileRequests: disrupted")
          progressBar.setProgress(0, true)
        }
      }
      receiver.receive(this)
    }
    executor.register(RequestHandler(listener))
  }

  private fun cancelFileTransfer(receiver: FileReceiver) {
    Toast.makeText(
      applicationContext,
      getString(R.string.transfer_cancelled_receiver), Toast.LENGTH_LONG
    ).show()

    receiver.cancel(this, executor)

    Handler(mainLooper).postDelayed({
      // reverse progress animation
      progressBar.setProgress(0, true)
    }, 80)
  }

  private fun onTransferRequested(name: String, length: Int) {
    fileNameLabel.text = FileNameUtil.toShortDisplayName(name)
    fileSizeLabel.text = Formatter.formatShortFileSize(
      applicationContext,
      length.toLong()
    )
    Snackbar.make(
      findViewById(R.id.session_layout),
      "Receiving $name",
      Snackbar.LENGTH_LONG
    ).show()
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

      .setPositiveButton("Proceed") { _, _ -> beginSocketTransfer(uri, fileName, fileLength) }
      .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
      .show()
  }

  private fun beginSocketTransfer(uri: Uri, fileName: String, fileLength: Int) {
    val request = FileRequest(
      uri, fileName, fileLength
    )
    executor.execute(applicationContext, request)
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

  override fun onResume() {
    super.onResume()
    messageReceiver.onResume(this)
    registerReceiver(
      onDisconnectReceiver, IntentFilter(
        SessionService.DISCONNECT_BROADCAST_ACTION
      )
    )
  }

  override fun onPause() {
    super.onPause()
    messageReceiver.onPause(this)
    unregisterReceiver(
      onDisconnectReceiver
    )
  }
}
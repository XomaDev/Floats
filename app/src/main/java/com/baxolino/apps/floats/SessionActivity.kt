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
import com.baxolino.apps.floats.core.SessionService.Companion.TRANSMISSION_BROADCAST_ACTION
import com.baxolino.apps.floats.core.files.FileNameUtil
import com.baxolino.apps.floats.core.files.FileReceiveService
import com.baxolino.apps.floats.core.files.FileUpdateInterface
import com.baxolino.apps.floats.core.files.MessageReceiver
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.tools.DynamicTheme
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.paperdb.Paper
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


class SessionActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "SessionActivity"
  }


  private lateinit var deviceName: String

  private lateinit var fileNameLabel: TextView
  private lateinit var fileSizeLabel: TextView

  private lateinit var hostAddress: String

  private lateinit var transferSpeedText: TextView

  private lateinit var progressBar: CircularProgressIndicator

  private lateinit var frameProgress: FrameLayout
  private lateinit var progressCard: MaterialCardView

  private lateinit var updater: FileUpdateInterface

  // we don't use this to receive messages here,
  // we are required to call onPause() and onResume()
  // lifecycles on it
  private var isConnected = false

  private lateinit var serviceVerifyExecutor: ScheduledThreadPoolExecutor

  private val onDisconnectReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "Disconnect broadcast received")
      onDisconnect()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate()")

    setContentView(R.layout.activity_session)
    DynamicTheme.themeOfSessionActivity(this)


    isConnected = intent.hasExtra("deviceName")

    if (!isConnected)
      return

    // init the Paper db
    Paper.init(this)

    updater = FileUpdateInterface.getUpdateInterface()

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

    progressCard = findViewById(R.id.progress_card)


    setupUpdateListeners()
    verifySessionServiceAlive()

    val onBackPressedCallback = object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        // if we do not do this, when back button is pressed, it'll open
        // home activity that'll cause problems
        moveTaskToBack(true)
      }
    }
    onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
  }


  private fun verifySessionServiceAlive() {
    serviceVerifyExecutor = ScheduledThreadPoolExecutor(1)
    serviceVerifyExecutor.scheduleAtFixedRate({
      Log.d(TAG, "Check")
      val book = Paper.book()
      if (!book.contains("last_beat")) {
        serviceVerifyExecutor.shutdown()
        onDisconnect()
        return@scheduleAtFixedRate
      }
      val lastBeat = book.read<Long>("last_beat")!!
      if ((System.currentTimeMillis() - lastBeat) >= 15000) {
        Log.d(TAG, "Service Disconnected")
        onDisconnect()
        serviceVerifyExecutor.shutdown()
      }
    }, 45, 5, TimeUnit.SECONDS)
    // a delay of 45 seconds; because some
    // devices take too long to start the service sometimes
  }

  private fun onDisconnect() {
    if (!isConnected)
      return
    isConnected = false
    if (!serviceVerifyExecutor.isShutdown)
      serviceVerifyExecutor.shutdownNow()
    Log.d(TAG, "onDisconnect()")
    // clear the main socket connection
    // instance; or it gets messed up
    SocketConnection.clear()
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
        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
      )
    )
  }


  private fun setupUpdateListeners() {
    Log.d(TAG, "listeners: Has previous interface = ${FileUpdateInterface.staticUpdateInterface}")

    if (updater.fileName.isNotEmpty()) {
      // this means that, some file is being received
      // currently
      updateTextViews()
      registerOnCancel()
    }

    updater.setStartListener {
      runOnUiThread {
        onTransferRequested(
          updater.fileName
        )

        updateTextViews()
        registerOnCancel()
      }
    }
    updater.setUpdateListener {
      runOnUiThread {
        progressBar.setProgress(updater.progress, true)
        if (updater.transferSpeed.isNotEmpty())
          transferSpeedText.text = "${updater.transferSpeed}ps"
      }
    }
    updater.setFinishedListener {
      frameProgress.setOnLongClickListener(null)
      progressBar.setOnLongClickListener(null)

      runOnUiThread {
        fileNameLabel.text = "No files being received"
        fileSizeLabel.text = "(> ^_^)>"
      }
    }
    updater.setDisruptionListener {
      runOnUiThread {
        Log.d(TAG, "lookForFileRequests: disrupted")
        progressBar.setProgress(0, true)
      }
    }
  }

  private fun updateTextViews() {
    fileNameLabel.text = FileNameUtil.toShortDisplayName(updater.fileName)
    fileSizeLabel.text = Formatter.formatShortFileSize(
      applicationContext,
      updater.fileLength.toLong()
    )
  }

  private fun registerOnCancel() {
    frameProgress.setOnLongClickListener {

      cancelFileTransfer()
      return@setOnLongClickListener true
    }
    // since some devices, don't register long
    // click of the frame progress
    progressCard.setOnLongClickListener {
      cancelFileTransfer()
      return@setOnLongClickListener true
    }
  }

  private fun cancelFileTransfer() {
    Toast.makeText(
      applicationContext,
      getString(R.string.transfer_cancelled_receiver), Toast.LENGTH_LONG
    ).show()

    sendBroadcast(
      Intent(TRANSMISSION_BROADCAST_ACTION)
        .putExtra("what", 1)
    ) // 1 refers to cancel transfer

    Handler(mainLooper).postDelayed({
      // reverse progress animation
      progressBar.setProgress(0, true)
    }, 80)
  }

  private fun onTransferRequested(name: String) {
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
                "${
                  Formatter.formatFileSize(
                    applicationContext,
                    fileLength.toLong()
                  )
                }? \n\nYou can only send one file at a time."
      )

      .setPositiveButton("Proceed") { _, _ -> beginSocketTransfer(uri, fileName, fileLength) }
      .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
      .show()
  }

  private fun beginSocketTransfer(uri: Uri, fileName: String, fileLength: Int) {
    sendBroadcast(
      Intent(TRANSMISSION_BROADCAST_ACTION)
        .putExtra("what", 0) // 0 refers to file send request
        .putExtra("file_uri", uri.toString())
        .putExtra("file_name", fileName)
        .putExtra("file_length", fileLength)
    )
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
    if (!isConnected)
      return
    MessageReceiver.onStart(this)
    registerReceiver(
      onDisconnectReceiver, IntentFilter(
        SessionService.DISCONNECT_BROADCAST_ACTION
      )
    )
  }

  override fun onPause() {
    super.onPause()
    if (!isConnected)
      return
    MessageReceiver.onStop(this)
    unregisterReceiver(
      onDisconnectReceiver
    )
  }
}
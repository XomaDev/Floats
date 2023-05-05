package com.baxolino.apps.floats

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.core.KRSystem
import com.baxolino.apps.floats.core.KRSystem.FileRequest
import com.baxolino.apps.floats.core.KRSystem.ReceiveListener
import com.baxolino.apps.floats.core.KRSystem.UpdateListener
import com.baxolino.apps.floats.tools.ThemeHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.lang.Exception


class SessionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SessionActivity"
    }

    private var krSystem: KRSystem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)

        val button = findViewById<FloatingActionButton>(R.id.floating_action_button)
        button.setOnClickListener {
            filePickResult.launch(
                Intent.createChooser(
                    Intent(Intent.ACTION_GET_CONTENT)
                        .setType("*/*"), "Choose a file"
                )
            )
        }

        val fileNameLabel = findViewById<TextView>(R.id.file_name)
        val progressBar = findViewById<LinearProgressIndicator>(R.id.progress_bar)

        val secondaryLabel = findViewById<TextView>(R.id.secondary_label)

        val deviceName = intent.getStringExtra("deviceName")
        val label = findViewById<TextView>(R.id.label)
        label.text = "Connected to $deviceName"

        try {
            krSystem = KRSystem.getInstance().apply {
                listenIncomingData()

                receiveListener = object :
                    ReceiveListener {
                    var initialTime: Long = 0

                    override fun acceptRequest(fileName: String?): Boolean {
                        runOnUiThread {
                            fileNameLabel.text = fileName!!.split('.')[0]
                        }
                        initialTime = System.currentTimeMillis() - 1
                        return true
                    }

                    override fun updateReceiveProgress(total: Int, received: Int) {
                        runOnUiThread {
                            val percentage = (received.toDouble() / total * 100).toInt()

                            progressBar.progress = percentage

                            val diff = System.currentTimeMillis() - initialTime
                            if (received != 0 && diff != 0L) {
                                val secs = diff / 1000
                                if (secs != 0L) {
                                    val speed = received / secs
                                    secondaryLabel.text = "$received / $total $percentage% $speed bytes / second"
                                }
                            }
                        }
                    }
                }
            }
        } catch (io: Exception) {
            // we are still in the testing stage
        }
        ThemeHelper.themeOfSessionActivity(this)
    }

    private var filePickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.data == null)
            // no file has been picked
                return@registerForActivityResult
            val contentUri = it.data?.data
            contentUri?.let {
                val stream = contentResolver.openInputStream(contentUri)
                val fileName = contentUri.getName()

                Log.d(TAG, "File Name = $fileName")
                Thread {
                    val fileRequest = FileRequest(fileName, stream, object : UpdateListener {
                        override fun updateProgress(total: Int, sent: Int) {
                            Log.d(TAG, "Sent $sent / $total")
                        }

                        override fun success() {

                        }
                    })
                    krSystem?.send(fileRequest)
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
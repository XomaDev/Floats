package com.baxolino.apps.floats

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.baxolino.apps.floats.tools.DynamicTheme
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.paperdb.Paper
import kotlin.system.exitProcess


class HomeActivity : AppCompatActivity() {

  companion object {
    init {
      System.loadLibrary("native-lib")
    }

    private const val TAG = "HomeActivity"

    const val RESTORE_SESSION_CHECK = "restore_session_request"
    const val RESTORE_SESSION_REPLY = "restore_session_reply"
  }

  private lateinit var home: HomeFragment
  private lateinit var files: FilesFragment

  private val restoreSessionReplyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
      Log.d(TAG, "Received restore reply")
      val deviceName = intent.getStringExtra("deviceName")!!
      val host = intent.getStringExtra("hostAddress")!!

      startActivity(
        Intent(this@HomeActivity, SessionActivity::class.java)
          .putExtra("deviceName", deviceName)
          .putExtra("hostAddress", host)
      )
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Log.d(TAG, "onCreate()")
    Paper.init(this)

    registerReceiver(
      restoreSessionReplyReceiver,
      IntentFilter(RESTORE_SESSION_REPLY)
    )
    sendBroadcast(
      Intent(
        RESTORE_SESSION_CHECK
      )
    )
    setContentView(R.layout.activity_home)
    DynamicTheme.setColorOfStatusBar(this)

    val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

    Log.d(TAG, "Registered")
    val contentView = findViewById<FrameLayout>(R.id.contentContainer)


    var currentTab = R.id.itemCode

    home = HomeFragment(this)
    val viewHome = home.onCreate(
      layoutInflater,
      null
    )
    contentView.addView(
      viewHome,
      MATCH_PARENT,
      MATCH_PARENT
    )

    files = FilesFragment(this)
    val viewPeople = files.onCreate(
      layoutInflater,
      null
    )

    bottomNavigationView.setOnItemSelectedListener { item ->
      if (item.itemId == currentTab)
        false
      else {
        when (item.itemId) {
          R.id.itemCode -> {
            currentTab = R.id.itemCode

            contentView.removeAllViews()
            contentView.addView(
              viewHome,
              MATCH_PARENT,
              MATCH_PARENT
            )
            true
          }

          R.id.itemFiles -> {
            currentTab = R.id.itemFiles

            contentView.removeAllViews()
            contentView.addView(
              viewPeople,
              MATCH_PARENT,
              MATCH_PARENT
            )
            true
          }
          else -> false
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    home.onResume()

    // we receive a reply from the SessionService if it is
    // in-case active
    registerReceiver(restoreSessionReplyReceiver,
      IntentFilter(RESTORE_SESSION_REPLY))
  }

  override fun onPause() {
    super.onPause()
    unregisterReceiver(restoreSessionReplyReceiver)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    val isGranted = grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED

    if (requestCode == HomeFragment.CAMERA_REQUEST_CODE) {
      home.onCameraPermission(isGranted)
    } else if (requestCode == HomeFragment.STORAGE_REQUEST_CODE) {
      home.onStoragePermission(isGranted)
    }
  }

}
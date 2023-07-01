package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.paperdb.Paper


class HomeActivity : AppCompatActivity() {

  companion object {
    init {
      System.loadLibrary("native-lib")
    }

    private const val TAG = "HomeActivity"
  }

  private lateinit var home: HomeFragment
  private lateinit var files: FilesFragment

  @SuppressLint("HardwareIds")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Log.d(TAG, "onCreate()")
    Paper.init(this)
    setContentView(R.layout.activity_home)

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
package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.core.transfer.SocketUtils
import com.baxolino.apps.floats.nsd.NsdInterface
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import io.paperdb.Paper


class HomeActivity : AppCompatActivity() {

  companion object {
    init {
      System.loadLibrary("native-lib")
    }

    private const val TAG = "HomeActivity"
  }

  @SuppressLint("HardwareIds")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Log.d(TAG, "onCreate()")
    Paper.init(this)
    setContentView(R.layout.activity_home)

    val localPort = SocketUtils.findAvailableTcpPort()
    Log.d("People", "onCreate: Port = $localPort")

    Log.d(TAG, "Registered")

    val home = HomeFragment(localPort)

    supportFragmentManager.beginTransaction()
      .replace(R.id.fragmentContainer, home)
      .commit()
  }
}
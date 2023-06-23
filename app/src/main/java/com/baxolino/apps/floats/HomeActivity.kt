package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.nsd.NsdInterface
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

    NsdInterface(this)
      .registerService(
        "${
          Build.MODEL
        }$${
          Paper
            .book()
            .read("name", "unknown")
        }"
      )

    val home = HomeFragment()

    supportFragmentManager.beginTransaction()
      .replace(R.id.fragmentContainer, PeopleFragment())
      .commit()
  }
}
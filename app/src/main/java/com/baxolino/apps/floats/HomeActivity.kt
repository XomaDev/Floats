package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity


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
    setContentView(R.layout.activity_home)

    val home = HomeFragment()

    supportFragmentManager.beginTransaction()
      .replace(R.id.fragmentContainer, PeopleFragment())
      .commit()
  }
}
package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
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
    val people = PeopleFragment()

    var currentId = R.id.itemCode
    supportFragmentManager.beginTransaction()
      .replace(R.id.fragmentContainer, home)
      .commit()

    findViewById<BottomNavigationView>(R.id.bottomNavigation)
      .setOnItemSelectedListener(
        object : NavigationBarView.OnItemSelectedListener {
          override fun onNavigationItemSelected(item: MenuItem): Boolean {
            if (currentId == item.itemId)
              return false
            when (item.itemId) {
              R.id.itemCode -> {
                supportFragmentManager.beginTransaction()
                  .replace(R.id.fragmentContainer, home)
                  .commit()
                currentId = R.id.itemCode
                return true
              }

              R.id.itemPeople -> {
                supportFragmentManager.beginTransaction()
                  .replace(R.id.fragmentContainer, people)
                  .commit()
                currentId = R.id.itemPeople
                return true
              }
            }
            return false
          }
        }
      )

  }
}
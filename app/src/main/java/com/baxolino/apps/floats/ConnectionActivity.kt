package com.baxolino.apps.floats

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.tools.DynamicTheme
import com.baxolino.apps.floats.tools.Utils
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ConnectionActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_connection)

    DynamicTheme.themeOfNoConnectionActivity(this)

    var running = true
    findViewById<MaterialButton>(R.id.retry_button).setOnClickListener {
      if (Utils.getIpv4(this) != null) {
        if (!running)
          // avoid multiple attempts
          return@setOnClickListener
        running = false
        startActivity(
          Intent(
            this,
            HomeActivity::class.java
          )
        )
      } else {
        Snackbar.make(
          findViewById(R.id.connection_layout),
          "Retry failed",
          Snackbar.LENGTH_LONG
        ).show()
      }
    }

    val executor = ScheduledThreadPoolExecutor(1)
    executor.scheduleAtFixedRate({
      if (Utils.getIpv4(this) != null) {
        if (!running)
          return@scheduleAtFixedRate
        running = false
        executor.shutdown()
        startActivity(
          Intent(
            this,
            HomeActivity::class.java
          )
        )
      }
    }, 0, 5, TimeUnit.SECONDS)
  }
}
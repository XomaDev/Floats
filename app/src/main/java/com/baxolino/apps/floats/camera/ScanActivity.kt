package com.baxolino.apps.floats.camera

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.baxolino.apps.floats.HomeActivity
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.tools.DynamicTheme


class ScanActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "ScanActivity"
  }

  private lateinit var cameraProvider: ProcessCameraProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_scan)

    DynamicTheme.applyNavigationBarTheme(this)

    val onBackPressedCallback = object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        finish()
      }
    }
    onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    findViewById<Button>(R.id.back_button).setOnClickListener {
      finish()
    }
    startCamera()
  }

  private fun startCamera() {
    val previewView = findViewById<PreviewView>(R.id.preview)
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
      // Used to bind the lifecycle of cameras to the lifecycle owner
      cameraProvider = cameraProviderFuture.get()

      // Preview
      val preview = Preview.Builder()
        .build()
        .also {
          it.setSurfaceProvider(previewView.surfaceProvider)
        }

      val imageAnalyzer = ImageAnalysis.Builder()
        .build()
        .also {
          it.setAnalyzer(ContextCompat.getMainExecutor(this), ImageAnalyzer(this))
        }


      // Select back camera as a default
      val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

      // Unbind use cases before rebinding
      cameraProvider.unbindAll()

      // Bind use cases to camera
      cameraProvider.bindToLifecycle(
        this, cameraSelector, preview, imageAnalyzer
      )

    }, ContextCompat.getMainExecutor(this))
  }

  // called by ImageAnalyzer class after
  fun retrievedQrAddress(rawString: String) {
    cameraProvider.unbindAll()
    startActivity(
      Intent(this, HomeActivity::class.java)
        .putExtra("content", rawString)
    )
  }
}
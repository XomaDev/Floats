package com.baxolino.apps.floats.camera

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@ExperimentalGetImage
class ImageAnalyzer(private val scanActivity: ScanActivity) : ImageAnalysis.Analyzer {


    companion object {
        private const val TAG = "ImageAnalyzer"
    }

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE)
                .build()


            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient(options)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val rawValue = barcodes[0].rawValue
                        if (rawValue != null) {
                            Log.d(TAG, "Raw Value = $rawValue")
                            scanActivity.retrievedQrAddress(rawValue)
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(scanActivity,
                        "Failed to read the code", Toast.LENGTH_SHORT)
                        .show()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
}

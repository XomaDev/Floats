package com.baxolino.apps.floats

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.baxolino.apps.floats.camera.ScanActivity
import com.baxolino.apps.floats.core.KnowEachOther
import com.baxolino.apps.floats.core.SessionService
import com.baxolino.apps.floats.core.transfer.SocketConnection
import com.baxolino.apps.floats.core.transfer.SocketUtils
import com.baxolino.apps.floats.tools.ThemeHelper
import com.baxolino.apps.floats.tools.Utils
import com.github.alexzhirkevich.customqrgenerator.QrData
import com.github.alexzhirkevich.customqrgenerator.vector.QrCodeDrawable
import com.github.alexzhirkevich.customqrgenerator.vector.QrVectorOptions
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorBallShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorColor
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorColors
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorFrameShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorPixelShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorShapes
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

  companion object {
    private const val TAG = "HomeActivity"
    private const val STORAGE_REQUEST_CODE = 7
    private const val CAMERA_REQUEST_CODE = 8
  }

  private lateinit var view: View
  private lateinit var intent: Intent

  private var permissionDialog: AlertDialog? = null

  private lateinit var deviceConnectionInfo: String
  private lateinit var ourId: String

  private var localPort = -1
  private lateinit var connector: SocketConnection

  private var hasConnection = true

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    // Inflate the layout for this fragment
    intent = requireActivity().intent
    view = inflater.inflate(R.layout.fragment_home, container, false)

    Log.d(TAG, "onCreate()")
    ThemeHelper.themeOfHomeActivity(view, requireActivity())

    val deviceText = view.findViewById<TextView>(R.id.device_label)
    val deviceId = Utils.getDeviceName(requireActivity().contentResolver)

    deviceText.text = deviceId
    ourId = deviceId

    val qrImageView = view.findViewById<ImageView>(R.id.qr_image)

    // create a new socket connection with the local port
    localPort = SocketUtils.findAvailableTcpPort()

    Log.d(TAG, "Port[$localPort]")
    connector = SocketConnection.getMainSocket()

    val ipv4 = Utils.getIpv4(requireActivity())
    if (ipv4 == null) {
      hasConnection = false
      startActivity(
        Intent(
          requireActivity(),
          ConnectionActivity::class.java
        )
      )
      return view
    } else {
      // just keep connection goes off
      lookOnDisconnect()
    }
    val connectionInfo = arrayOf(
      ByteBuffer.wrap(
        ipv4.address
      ).int,
      localPort,
    ).joinToString("\u0000")

    deviceConnectionInfo = connectionInfo
    generateQr(qrImageView, connectionInfo)

    view.findViewById<TextView>(R.id.documents_tip).text =
      Html.fromHtml(
        "Files go to the <b>Documents</b> folder.",
        Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
      )

    val scanButton = view.findViewById<MaterialCardView>(R.id.scanButton)

    scanButton.setOnClickListener {
      if (ContextCompat.checkSelfPermission(
          requireActivity(),
          Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_DENIED
      ) {
        ActivityCompat.requestPermissions(
          requireActivity(),
          arrayOf(Manifest.permission.CAMERA),
          CAMERA_REQUEST_CODE
        )
      } else {
        startActivity(
          Intent(requireActivity(), ScanActivity::class.java)
        )
      }
    }

    if (intent.hasExtra("content")) {
      onScanResult()
    } else {
      // we put it over here because, we don't want it called multiple
      // times
      connector.acceptOnPort(localPort) {
        Log.d(TAG, "Accepted()")
        onConnectionSuccessful(true)
      }
    }
    if (intent.hasExtra("event_disconnect")) {
      val device = intent.getStringExtra("event_disconnect")
      // we are disconnected from a device we
      // were once connected to

      MaterialAlertDialogBuilder(requireActivity(), R.style.FloatsCustomDialogTheme)
        .setTitle("Disconnected")
        .setMessage("Connection to device $device was broken.")
        .setPositiveButton("Dismiss") { dialog, _ ->
          dialog.dismiss()
        }
        .show()
    }

    return view
  }

  private fun lookOnDisconnect() {
    val executor = ScheduledThreadPoolExecutor(1)
    executor.scheduleAtFixedRate({
      if (Utils.getIpv4(requireActivity()) == null) {
        executor.shutdown()
        startActivity(
          Intent(
            requireActivity(),
            ConnectionActivity::class.java
          )
        )
      }
    }, 0, 1, TimeUnit.SECONDS)
  }

  override fun onResume() {
    super.onResume()
    if (Utils.getIpv4(requireActivity()) == null) {
      startActivity(
        Intent(
          requireActivity(),
          ConnectionActivity::class.java
        )
      )
      return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
      && permissionDialog == null
      && ContextCompat.checkSelfPermission(
        requireActivity(),
        Manifest.permission.WRITE_EXTERNAL_STORAGE
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        requireActivity(), arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        STORAGE_REQUEST_CODE
      )
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == STORAGE_REQUEST_CODE
      && grantResults.isNotEmpty()
      && grantResults[0] == PackageManager.PERMISSION_DENIED
    ) {
      permissionDialog =
        MaterialAlertDialogBuilder(requireActivity(), R.style.FloatsCustomDialogTheme)
          .setCancelable(false)
          .setTitle("Permission Denied")
          .setMessage("App needs write permission for file saving.")
          .setNeutralButton("Settings") { _, _ ->
            permissionDialog = null
            startActivity(
              Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(
                  Uri.parse("package:${requireActivity().packageName}")
                )
            )
          }.setPositiveButton("Ask again") { _, _ ->
            ActivityCompat.requestPermissions(
              requireActivity(),
              arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
              79
            )
          }
          .show()
    } else if (requestCode == CAMERA_REQUEST_CODE && grantResults.isNotEmpty()) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        startActivity(
          Intent(
            activity,
            ScanActivity::class.java
          )
        )
      } else {
        Snackbar.make(
          view.findViewById(R.id.home_layout),
          "Camera Permission was denied.",
          Snackbar.LENGTH_LONG
        ).show()
      }
    }
  }

  private fun onScanResult() {
    // we are back from the qr scan activity
    // and we can connect to that bluetooth device from the address
    val qrContent = intent.getStringExtra("content")

    // attempts to start connecting to the other
    // device
    qrContent?.let {
      val args = qrContent.split("\u0000")
      val ipv4Address = InetAddress.getByAddress(
        ByteBuffer
          .allocate(Integer.BYTES)
          .order(ByteOrder.BIG_ENDIAN)
          .putInt(args[0].toInt())
          .array()
      )
        .hostAddress!!

      val port = args[1].toInt()

      Log.d(TAG, "onScanResult: $args $ipv4Address")

      connector.connectOnPort(port, ipv4Address, false, {
        Log.d(TAG, "Connected()")
        onConnectionSuccessful(false)
      }, {
        Snackbar.make(
          view.findViewById(R.id.home_layout),
          it,
          Snackbar.LENGTH_LONG
        ).show()
      })
    }
  }

  private fun onConnectionSuccessful(isServer: Boolean) {
    var localPort = -1
    if (isServer) {
      // if we are the server, we are the one who creates
      // and send to the client
      localPort = SocketUtils.findAvailableTcpPort()
    }
    KnowEachOther.initiate(
      ourId,
      if (isServer) localPort else -1, connector
    )
    { name, host, hostPort ->
      // hostPort is valid if we are client
      val service = Intent(requireActivity(), SessionService::class.java)
        .putExtra("partner_device", name)
        .putExtra("server", isServer)
        .putExtra("host", host)
        .putExtra("port", if (isServer) localPort else hostPort)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        requireActivity().startForegroundService(service)
      else requireActivity().startService(service)

      startActivity(
        Intent(requireActivity(), SessionActivity::class.java)
          .putExtra("deviceName", name)
          .putExtra("hostAddress", host)
      )
    }
  }

  private fun generateQr(qrImageView: ImageView, text: String) {
    val primaryColor = ThemeHelper.variant60Color(requireActivity())

    val options = QrVectorOptions.Builder()
      .setPadding(.3f)
      .setColors(
        QrVectorColors(
          dark = QrVectorColor
            .Solid(primaryColor),
          ball = QrVectorColor.Solid(
            primaryColor
          )
        )
      )
      .setShapes(
        QrVectorShapes(
          darkPixel = QrVectorPixelShape
            .RoundCorners(.5f),
          ball = QrVectorBallShape
            .RoundCorners(.25f),
          frame = QrVectorFrameShape
            .RoundCorners(.25f),
        )
      )
      .build()
    qrImageView.background = QrCodeDrawable(
      QrData.Text(text),
      options
    )
  }
}
package com.baxolino.apps.floats

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Timer
import java.util.TimerTask
import java.util.UUID


@SuppressLint("MissingPermission")
class FloatsBluetooth(val homeActivity: HomeActivity) {

    var writeStream: OutputStream? = null
    var readStream: InputStream? = null

    companion object {
        private const val APP_NAME = "Floats"
        private const val TAG = "Floats"
        private val APP_UUID = UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66")

    }

    @SuppressLint("UnsafeOptInUsageError")
    fun requestConnection(device: BluetoothDevice) {
        Log.d(TAG, "requestConnection()")
        Thread {
            val socket = device.createRfcommSocketToServiceRecord(APP_UUID)
            // TODO:
            //  we may need to retry here
            Log.d(TAG, "Requesting...")
            socket.connect()
            Log.d(TAG, "requestConnection: Connection established")

            writeStream = socket.outputStream
            readStream = socket.inputStream

            homeActivity.establishedConnection(false, device)
        }.start()
    }

    fun acceptConnection(adapter: BluetoothAdapter) {
        Thread {
            val serverSocket = adapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
            var socket: BluetoothSocket?

            // create a timer that will try to accept any
            // incoming connection
            val t = Timer()
            t.schedule(object : TimerTask() {
                @SuppressLint("UnsafeOptInUsageError")
                override fun run() {
                    Log.d(TAG, "run() trying to accept!")
                    try {
                        socket = serverSocket.accept()
                        if (socket != null) {
                            Log.d(TAG, "Accepted Connection")

                            writeStream = socket!!.outputStream
                            readStream = socket!!.inputStream

                            homeActivity.establishedConnection(true, null)
                            t.cancel()
                        }
                    } catch (exception: IOException) {
                        Log.d(TAG, "run: Try Failed")
                    }
                }
            }, 0, 5000)
        }.start()
    }

    private fun write(bytes: ByteArray) {
        if (writeStream == null) {
            throw IllegalStateException("Write Stream Is Null")
        } else {
            writeStream!!.write(bytes)
        }
    }

}
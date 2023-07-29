package com.baxolino.apps.floats

import android.app.Activity
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.baxolino.apps.floats.listviews.FileDetails
import com.baxolino.apps.floats.listviews.FileListAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.paperdb.Paper
import org.json.JSONObject
import java.io.File


class FilesFragment(
  private val activity: Activity

) {

  companion object {
    private const val TAG = "FilesFragment"
  }

  private lateinit var view: View
  private var initialized = false

  fun onCreate(
    inflater: LayoutInflater,
    container: ViewGroup?
  ): View {
    if (initialized)
      return view
    view = inflater.inflate(R.layout.fragment_files, container, false)

    val listView = view.findViewById<ListView>(R.id.devices_list)

    val files = retrieveSavedFiles()
    listView.adapter = FileListAdapter(activity, files)
    if (files.isEmpty()) {
      view.findViewById<View>(R.id.emptyListTip).visibility = View.VISIBLE
    }
    view.findViewById<TextView>(R.id.filesTip).text =
      Html.fromHtml(
        "Files are saved into the <b>Documents</b> folder.",
        Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
      )
    view.findViewById<Button>(R.id.clearHistory).setOnClickListener {
      if (files.isEmpty())
        return@setOnClickListener
      MaterialAlertDialogBuilder(activity, R.style.FloatsCustomDialogTheme)
        .setTitle("Delete history?")
        .setMessage("Receive history will be permanently deleted")
        .setPositiveButton("Delete") { _, _ ->
          // this will refresh the list
          listView.adapter = null

          Paper.book("files").destroy()
          view.findViewById<View>(R.id.emptyListTip).visibility = View.VISIBLE
          Snackbar.make(view, "File history was cleared", Snackbar.LENGTH_LONG)
            .show()
        }.setNegativeButton("Cancel") { dialog, _ ->
          dialog.dismiss()
        }
        .show()
    }
    return view
  }

  private fun retrieveSavedFiles(): ArrayList<FileDetails> {
    val book = Paper.book("files")

    val list = ArrayList<FileDetails>()
    for (fileName in book.allKeys) {
      val data = JSONObject(book.read<String>(fileName)!!)

      Log.d(TAG, "File $fileName, data = $data")
      data.apply {
        val file = File(getString("path"))

        if (file.exists()) {
          list.add(
            FileDetails(
              getString("originalFileName"),
              file.absolutePath,
              getString("from"),
              getInt("size"),
              getLong("time")
            )
          )
        } else {
          // remove if it's deleted
          Log.d(TAG, "Deleting ${file.absolutePath}")
          book.delete(fileName)
        }
      }
    }
    return list
  }
}
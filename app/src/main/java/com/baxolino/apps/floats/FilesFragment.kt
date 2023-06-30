package com.baxolino.apps.floats

import android.app.Activity
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import com.baxolino.apps.floats.listviews.FileDetails
import com.baxolino.apps.floats.listviews.FileListAdapter
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

    val files = retrieveSavedFiles()
    val listView = view.findViewById<ListView>(R.id.devices_list)

    listView.adapter = FileListAdapter(activity, files)

    view.findViewById<TextView>(R.id.filesTip).text =
      Html.fromHtml(
        "Files are saved into the <b>Documents</b> folder.",
        Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
      )
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
              fileName,
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
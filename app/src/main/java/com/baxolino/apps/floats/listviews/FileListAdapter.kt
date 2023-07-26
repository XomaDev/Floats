package com.baxolino.apps.floats.listviews

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.core.files.FileNameUtil
import com.baxolino.apps.floats.tools.DynamicTheme
import java.io.File
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class FileListAdapter(context: Context?, itemList: ArrayList<FileDetails>) :
  ArrayAdapter<FileDetails?>(
    context!!, 0, itemList as List<FileDetails?>
  ) {
  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    // Inflate the custom layout for the item
    val details = getItem(position)!!

    var convertView = convertView
    if (convertView == null) {
      convertView =
        LayoutInflater
          .from(context)
          .inflate(R.layout.receive_device_item, parent, false)!!
    }
    val color = DynamicTheme.variant80Color(context)

    val shortFileName = FileNameUtil.toShortDisplayName(details.fileName)
    // we need to get the appropriate icon for the file
    // and also the appropriate action text

    val fileIcon = FileMapping.getDetails(
      details.fileName.split('.').last()
    )

    val file = File(details.filePath)

    // let's share the file when asked to...
    val actionIntent = Intent(Intent.ACTION_SEND)
      .apply {
        type = URLConnection.guessContentTypeFromName(file.name)

        putExtra(
          Intent.EXTRA_STREAM,
          FileProvider.getUriForFile(
            context,
            "com.baxolino.apps.floats.fileprovider", file
          )
        )
      }

    convertView.apply {

      findViewById<TextView>(R.id.actionButton)
        .apply {
          setOnClickListener {
            context.startActivity(actionIntent)
          }
          setTextColor(color)
        }

      findViewById<ImageView>(R.id.fileIcon).apply {
        setBackgroundResource(fileIcon) // the file type icon
        backgroundTintList = ColorStateList.valueOf(color)
      }

      findViewById<TextView>(R.id.fileName).apply {
        text = shortFileName
        setTextColor(color)
      }

      findViewById<TextView>(R.id.fromTextView).text = "· " + details.from

      findViewById<TextView>(R.id.sizeTextView).text =
        Formatter.formatFileSize(context, details.fileSize.toLong())

      findViewById<TextView>(R.id.timeTextView).text =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
          .format(Date(details.time))
    }

    return convertView
  }
}
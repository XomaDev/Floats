package com.baxolino.apps.floats.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.tools.DynamicTheme
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

    convertView.apply {
      findViewById<ImageView>(R.id.fileIcon)
        .backgroundTintList = ColorStateList.valueOf(color)

      findViewById<TextView>(R.id.timeLabel)
        .setTextColor(color)

      findViewById<TextView>(R.id.sizeLabel).setTextColor(color)

      findViewById<TextView>(R.id.actionButton)
        .setTextColor(color)

      findViewById<TextView>(R.id.fileName).apply {
        text = details.fileName
        setTextColor(color)
      }

      findViewById<TextView>(R.id.fromTextView).text = details.from

      findViewById<TextView>(R.id.sizeTextView).text =
        Formatter.formatFileSize(context, details.fileSize.toLong())

      findViewById<TextView>(R.id.timeTextView).text =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
          .format(Date(details.time))
    }

    return convertView
  }
}
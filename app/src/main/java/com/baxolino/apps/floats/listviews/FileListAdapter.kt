package com.baxolino.apps.floats.listviews

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.core.files.FileNameUtil
import com.baxolino.apps.floats.tools.DynamicTheme
import com.google.android.material.card.MaterialCardView
import java.io.File
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

    val fileMapping = FileMapping.getDetails(
      details.fileName.split('.').last()
    )

    val file = File(details.filePath)

    val map = MimeTypeMap.getSingleton()
    val ext = MimeTypeMap.getFileExtensionFromUrl(file.name)
    var type = map.getMimeTypeFromExtension(ext)

    if (type == null)
      type = "*/*"

    val actionIntent = Intent(Intent.ACTION_VIEW)
    actionIntent.setDataAndType(Uri.fromFile(file), type)

    convertView.apply {
      DynamicTheme.setSpecialBorderTheme(
        context,
        findViewById(R.id.actionOutline)
      )


      findViewById<ImageView>(R.id.fileIcon).apply {
        setBackgroundResource(fileMapping.first) // the file type icon
        backgroundTintList = ColorStateList.valueOf(color)
      }

      findViewById<TextView>(R.id.timeLabel)
        .setTextColor(color)

      findViewById<TextView>(R.id.sizeLabel).setTextColor(color)

      findViewById<TextView>(R.id.actionButton)
        .apply {
          text = fileMapping.second // set the appropriate action text
          setTextColor(color)
        }

      findViewById<TextView>(R.id.fileName).apply {
        text = shortFileName
        setTextColor(color)
      }

      findViewById<TextView>(R.id.fromTextView).text = details.from

      findViewById<MaterialCardView>(R.id.sizeCard).backgroundTintList =
        ColorStateList.valueOf(
          DynamicTheme.neutralCardColor(context)
        )
      findViewById<MaterialCardView>(R.id.timeCard).backgroundTintList =
        ColorStateList.valueOf(
          DynamicTheme.neutralCardColor(context)
        )
      findViewById<TextView>(R.id.sizeTextView).text =
        Formatter.formatFileSize(context, details.fileSize.toLong())

      findViewById<TextView>(R.id.timeTextView).text =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
          .format(Date(details.time))
    }

    return convertView
  }
}
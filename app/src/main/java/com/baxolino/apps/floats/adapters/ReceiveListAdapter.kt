package com.baxolino.apps.floats.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.tools.DynamicTheme
import com.google.android.material.card.MaterialCardView

class ReceiveListAdapter(context: Context?, itemList: ArrayList<CustomItem>) :
  ArrayAdapter<CustomItem?>(
    context!!, 0, itemList as List<CustomItem?>
  ) {
  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    // Inflate the custom layout for the item
    var convertView = convertView
    if (convertView == null) {
      convertView =
        LayoutInflater
          .from(context)
          .inflate(R.layout.receive_device_item, parent, false)!!
    }
    convertView.apply {
      DynamicTheme.variant80Color(context).let { color ->
        findViewById<ImageView>(R.id.fileIcon)
          .backgroundTintList = ColorStateList.valueOf(color)

        findViewById<TextView>(R.id.actionButton)
          .setTextColor(color)
        findViewById<TextView>(R.id.deviceTitle)
          .setTextColor(color)

        findViewById<TextView>(R.id.sizeLabel)
          .setTextColor(color)
        findViewById<TextView>(R.id.timeLabel)
          .setTextColor(color)
      }
    }

    return convertView
  }
}
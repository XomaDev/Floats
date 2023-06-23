package com.baxolino.apps.floats.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.tools.ThemeHelper
import com.google.android.material.card.MaterialCardView


class DeviceListAdapter(val context: Context, private val itemList: List<DeviceItem>) :
  BaseAdapter() {

  private val inflater: LayoutInflater = LayoutInflater.from(context)

  override fun getCount(): Int {
    return itemList.size
  }

  override fun getItem(position: Int): Any {
    return itemList[position]
  }

  override fun getItemId(position: Int): Long {
    return position.toLong()
  }

  override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
    var itemView = convertView
    val item = itemList[position]

    if (convertView == null) {
      itemView = inflater.inflate(R.layout.device_listview_item, parent, false)

      ThemeHelper.altVariant60Color(context).let { color ->
        itemView.findViewById<MaterialCardView>(R.id.deviceCard)
          .strokeColor = color

        itemView.findViewById<TextView>(R.id.deviceLetter).apply {
          text = item.deviceName[0].toString()
          setTextColor(color)
        }

        itemView.findViewById<TextView>(R.id.deviceName).apply {
          text = item.deviceName
          setTextColor(color)
        }
      }

      ThemeHelper.variant60Color(context).let {
        itemView.findViewById<TextView>(R.id.ownerName).apply {
          text = item.deviceOwner
          setTextColor(it)
        }
        itemView.findViewById<ImageView>(R.id.deviceItemAction).apply {
          backgroundTintList = ColorStateList.valueOf(it)
          setOnClickListener {

            val popupMenu = PopupMenu(context, it, Gravity.END, 0, R.style.popupMenuStyle)
            popupMenu.inflate(R.menu.popup_menu)
            PopupHelper.insertMenuItemIcons(context, popupMenu)


            popupMenu.setOnMenuItemClickListener { menuItem ->
              when (menuItem.itemId) {
                R.id.itemSend -> {
                  // Handle menu item 1 click
                  true
                }
                R.id.itemConnect -> {
                  // Handle menu item 2 click
                  true
                }
                // Handle other items as needed
                else -> false
              }
            }
            popupMenu.show()
          }
        }
      }
    }
    return itemView!!
  }
}
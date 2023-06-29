package com.baxolino.apps.floats

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import com.baxolino.apps.floats.adapters.CustomItem
import com.baxolino.apps.floats.adapters.ReceiveListAdapter


class FilesFragment(
  private val activity: Activity

) {

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

    val itemList = ArrayList<CustomItem>()
    itemList.add(CustomItem("Item 1", "Description 1"))
    listView.adapter = ReceiveListAdapter(activity, itemList)

    return view
  }
}
package com.baxolino.apps.floats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.baxolino.apps.floats.adapters.CustomListAdapter
import com.baxolino.apps.floats.adapters.DeviceItem
import com.baxolino.apps.floats.tools.ThemeHelper
import com.google.android.material.card.MaterialCardView
import io.paperdb.Paper

class PeopleFragment : Fragment() {

  private lateinit var view: View

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    view = inflater.inflate(R.layout.fragment_people, container, false)
    ThemeHelper.themeOfPeopleActivity(view, requireActivity())

    val nameLayout = view.findViewById<LinearLayout>(R.id.enterNameLayout)
    Paper.book().let { book ->
      if (!book.contains("name")) {
        nameLayout.visibility = View.VISIBLE

        val nameEditText = view.findViewById<EditText>(R.id.nameEditText)
        val okButton = view.findViewById<MaterialCardView>(R.id.check)
        okButton.setOnClickListener {
          book.write("name", nameEditText.text.trim())
        }
      }
    }

    val listView = view.findViewById<ListView>(R.id.device_list)

    // Create your item list with data
    val itemList = listOf(
      DeviceItem("Galaxy A03s", "Dennis Littawe"),
      DeviceItem("Xiaomi Mi Tab 5", "Peter muster")
    )

    val adapter = CustomListAdapter(requireActivity(), itemList)
    listView.adapter = adapter
    return view
  }
}
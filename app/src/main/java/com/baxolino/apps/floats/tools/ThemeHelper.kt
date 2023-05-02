package com.baxolino.apps.floats.tools

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import com.baxolino.apps.floats.HomeActivity
import com.baxolino.apps.floats.MainActivity
import com.baxolino.apps.floats.R
import com.google.android.material.color.DynamicColors

object ThemeHelper {

    private val HAS_DYNAMIC_THEMING = DynamicColors.isDynamicColorAvailable()

    fun themOfMainActivity(mainActivity: MainActivity) {
        if (!HAS_DYNAMIC_THEMING) return
        val welcomeImageView = mainActivity.findViewById<ImageView>(R.id.hello_image)
        welcomeImageView.backgroundTintList = ColorStateList.valueOf(
            mainActivity.getColor(
                com.google.android.material.R.color.material_dynamic_neutral_variant60
            )
        )
    }

    fun themeOfHomeActivity(homeActivity: HomeActivity) {
        if (!HAS_DYNAMIC_THEMING) return
        val gearImageView = homeActivity.findViewById<ImageView>(R.id.gear)
        gearImageView.backgroundTintList = ColorStateList.valueOf(
            homeActivity.getColor(
                com.google.android.material.R.color.material_dynamic_neutral_variant60
            )
        )

        val qrImageView = homeActivity.findViewById<ImageView>(R.id.qr_image)
        qrImageView.backgroundTintList = ColorStateList.valueOf(
            homeActivity.getColor(
                com.google.android.material.R.color.material_dynamic_neutral_variant60
            )
        )
    }

    fun variant60Color(context: Context): Int {
        return context.getColor(
            if (HAS_DYNAMIC_THEMING) {
                com.google.android.material.R.color.material_dynamic_neutral_variant60
            } else {
                R.color.alt_variant_60
            }
        )
    }
}
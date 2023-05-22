package com.baxolino.apps.floats.tools

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import com.baxolino.apps.floats.HomeActivity
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.SessionActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors


object ThemeHelper {

    private val HAS_DYNAMIC_THEMING = DynamicColors.isDynamicColorAvailable()


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

//        val deviceStatusCard = homeActivity.findViewById<MaterialCardView>(R.id.device_status)
//        deviceStatusCard.backgroundTintList = ColorStateList.valueOf(
//            homeActivity.getColor(
//                com.google.android.material.R.color.material_dynamic_neutral_variant20
//            )
//        )
//
//        val frameStatusCard = homeActivity.findViewById<MaterialCardView>(R.id.outline_status_card)
//        frameStatusCard.backgroundTintList = ColorStateList.valueOf(
//            homeActivity.getColor(
//                com.google.android.material.R.color.material_dynamic_neutral_variant10
//            )
//        )


        val deviceStatusCard = homeActivity.findViewById<MaterialCardView>(R.id.device_status)
        deviceStatusCard.backgroundTintList = ColorStateList.valueOf(
            homeActivity.getColor(
                R.color.card_view_background
            )
        )

        val frameStatusCard = homeActivity.findViewById<MaterialCardView>(R.id.outline_status_card)
        frameStatusCard.backgroundTintList = ColorStateList.valueOf(
            homeActivity.getColor(
                R.color.mtr_card_varient_background
            )
        )

    }

    fun themeOfSessionActivity(session: SessionActivity) {
        if (!HAS_DYNAMIC_THEMING) return
        val imageAdd = session.findViewById<ImageView>(R.id.image_add)

        imageAdd.backgroundTintList = ColorStateList.valueOf(
            session.getColor(
                com.google.android.material.R.color.material_dynamic_neutral_variant50
            )
        )

//        val labelFrame = session.findViewById<FrameLayout>(R.id.dotted_label_frame)
//        val drawable = labelFrame.background as GradientDrawable
//        drawable.mutate()
//
//        drawable.setStroke(2, session.getColor(
//            com.google.android.material.R.color.material_dynamic_primary80
//        ), 6f, 7f)


        val speedLabelFrame = session.findViewById<FrameLayout>(R.id.speed_label_frame)
        val drawableSpeed = speedLabelFrame.background as GradientDrawable
        drawableSpeed.mutate()

        drawableSpeed.setStroke(2, session.getColor(
            com.google.android.material.R.color.material_dynamic_neutral_variant40
        ), 6f, 7f)
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
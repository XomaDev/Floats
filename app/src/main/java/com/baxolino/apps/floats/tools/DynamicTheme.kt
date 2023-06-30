package com.baxolino.apps.floats.tools

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import com.baxolino.apps.floats.ConnectionActivity
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.SessionActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.elevation.SurfaceColors


object DynamicTheme {

  private val HAS_DYNAMIC_THEMING = DynamicColors.isDynamicColorAvailable()


  fun applyNavigationBarTheme(activity: Activity) {
    activity.window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(activity)
  }

  fun themeOfHomeActivity(view: View, activity: Activity) {
    if (!HAS_DYNAMIC_THEMING) return

    applyNavigationBarTheme(activity)

    val qrImageView = view.findViewById<ImageView>(R.id.qr_image)
    qrImageView.backgroundTintList = ColorStateList.valueOf(
      activity.getColor(
        com.google.android.material.R.color.material_dynamic_neutral_variant60
      )
    )

    val tipCard = view.findViewById<MaterialCardView>(R.id.tip_card)
    tipCard.backgroundTintList = ColorStateList.valueOf(
      activity.getColor(
        com.google.android.material.R.color.material_dynamic_neutral_variant50
      )
    )

    val tipText = view.findViewById<TextView>(R.id.documents_tip)
    tipText.setTextColor(
      activity.getColor(
        com.google.android.material.R.color.material_dynamic_primary80
      )
    )

    val buttonText = view.findViewById<TextView>(R.id.button_text)
    buttonText.setTextColor(
      activity.getColor(
        com.google.android.material.R.color.material_dynamic_primary70
      )
    )

    val qrScanIcon = view.findViewById<ImageView>(R.id.qr_scan_icon)
    qrScanIcon.backgroundTintList = ColorStateList.valueOf(
      activity.getColor(
        com.google.android.material.R.color.material_dynamic_primary80
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

    val speedLabelFrame = session.findViewById<FrameLayout>(R.id.speed_label_frame)
    setSpecialBorderTheme(session, speedLabelFrame)
  }

  fun setSpecialBorderTheme(context: Context, layout: FrameLayout) {
    val drawableSpeed = layout.background as GradientDrawable
    drawableSpeed.mutate()

    drawableSpeed.setStroke(
      2, context.getColor(
        com.google.android.material.R.color.material_dynamic_neutral_variant40
      ), 6f, 7f
    )
  }

  fun themeOfNoConnectionActivity(connection: ConnectionActivity) {
    if (!HAS_DYNAMIC_THEMING)
      return
    val alertIcon = connection.findViewById<ImageView>(R.id.alert_icon)

    alertIcon.backgroundTintList = ColorStateList.valueOf(
      connection.getColor(
        com.google.android.material.R.color.material_dynamic_primary80
      )
    )
  }

  fun neutralCardColor(context: Context): Int {
    return context.getColor(
      if (HAS_DYNAMIC_THEMING) {
        com.google.android.material.R.color.material_dynamic_neutral_variant20
      } else {
        R.color.card_view_background
      }
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

  fun variant70Color(context: Context): Int {
    return context.getColor(
      if (HAS_DYNAMIC_THEMING) {
        com.google.android.material.R.color.material_dynamic_neutral_variant70
      } else {
        R.color.alt_variant_dark
      }
    )
  }

  fun variant80Color(context: Context): Int {
    return context.getColor(
      if (HAS_DYNAMIC_THEMING) {
        com.google.android.material.R.color.material_dynamic_primary80
      } else {
        R.color.alt_variant_dark
      }
    )
  }

  fun getProgressBar(context: Context): RemoteViews {
    if (HAS_DYNAMIC_THEMING)
      return RemoteViews(context.packageName, R.layout.notification_progress_mtr)
    return RemoteViews(context.packageName, R.layout.notification_progress)
  }
}
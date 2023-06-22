package com.baxolino.apps.floats.tools

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.baxolino.apps.floats.ConnectionActivity
import com.baxolino.apps.floats.R
import com.baxolino.apps.floats.SessionActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.elevation.SurfaceColors


object ThemeHelper {

  private val HAS_DYNAMIC_THEMING = DynamicColors.isDynamicColorAvailable()


  fun applyNavigationBarTheme(fragment: FragmentActivity) {
    fragment.window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(fragment)
  }

  fun themeOfHomeActivity(view: View, fragment: FragmentActivity) {
    if (!HAS_DYNAMIC_THEMING) return

    applyNavigationBarTheme(fragment)

    val qrImageView = view.findViewById<ImageView>(R.id.qr_image)
    qrImageView.backgroundTintList = ColorStateList.valueOf(
      fragment.getColor(
        com.google.android.material.R.color.material_dynamic_neutral_variant60
      )
    )

    val tipCard = view.findViewById<MaterialCardView>(R.id.tip_card)
    tipCard.backgroundTintList = ColorStateList.valueOf(
      fragment.getColor(
        com.google.android.material.R.color.material_dynamic_neutral80
      )
    )

    val tipText = view.findViewById<TextView>(R.id.documents_tip)
    tipText.setTextColor(
      fragment.getColor(
        com.google.android.material.R.color.material_dynamic_primary80
      )
    )

    val buttonText = view.findViewById<TextView>(R.id.button_text)
    buttonText.setTextColor(
      fragment.getColor(
        com.google.android.material.R.color.material_dynamic_primary70
      )
    )

    val qrScanIcon = view.findViewById<ImageView>(R.id.qr_scan_icon)
    qrScanIcon.backgroundTintList = ColorStateList.valueOf(
      fragment.getColor(
        com.google.android.material.R.color.material_dynamic_primary80
      )
    )
  }

  fun themeOfPeopleActivity(view: View, fragment: FragmentActivity) {
    if (!HAS_DYNAMIC_THEMING) return
    applyNavigationBarTheme(fragment)

    val tickCardViw = view.findViewById<MaterialCardView>(R.id.check)
    tickCardViw.backgroundTintList = ColorStateList.valueOf(
      fragment.getColor(
        com.google.android.material.R.color.material_dynamic_primary70
      )
    )

    val tickIcon = view.findViewById<ImageView>(R.id.tickIcon)
    tickIcon.backgroundTintList = ColorStateList.valueOf(
      fragment.getColor(
        com.google.android.material.R.color.material_dynamic_primary20
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
    val drawableSpeed = speedLabelFrame.background as GradientDrawable
    drawableSpeed.mutate()

    drawableSpeed.setStroke(
      2, session.getColor(
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

  fun variant60Color(context: Context): Int {
    return context.getColor(
      if (HAS_DYNAMIC_THEMING) {
        com.google.android.material.R.color.material_dynamic_neutral_variant60
      } else {
        R.color.alt_variant_60
      }
    )
  }

  fun altVariant60Color(context: Context): Int {
    return context.getColor(
      if (HAS_DYNAMIC_THEMING) {
        com.google.android.material.R.color.material_dynamic_primary80
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

  fun getProgressBar(context: Context): RemoteViews {
    if (HAS_DYNAMIC_THEMING)
      return RemoteViews(context.packageName, R.layout.notification_progress_mtr)
    return RemoteViews(context.packageName, R.layout.notification_progress)
  }
}
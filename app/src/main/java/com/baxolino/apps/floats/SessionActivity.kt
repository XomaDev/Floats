package com.baxolino.apps.floats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.baxolino.apps.floats.tools.ThemeHelper

class SessionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)
        ThemeHelper.themeOfSessionActivity(this)
    }
}
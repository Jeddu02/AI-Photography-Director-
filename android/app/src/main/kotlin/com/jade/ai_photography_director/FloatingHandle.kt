package com.jade.ai_photography_director

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView

// Service overlays use a platform theme and do not run inside an AppCompat activity.
@SuppressLint("AppCompatCustomView")
class FloatingHandle(context: Context) : TextView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

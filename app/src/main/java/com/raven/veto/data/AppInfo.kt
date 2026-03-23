package com.raven.veto.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null,
    val isBlocked: Boolean = false
)


package com.example.mynewsapp.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

// Extension to get color from theme
fun Context.getColorThemeRes(@AttrRes id: Int): Int {
    val resolvedAttr = TypedValue()
    this.theme.resolveAttribute(id, resolvedAttr, true)
    return this.getColor(resolvedAttr.resourceId)
}
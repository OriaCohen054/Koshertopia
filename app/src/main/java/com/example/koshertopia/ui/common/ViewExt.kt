package com.example.koshertopia.ui.common

import android.content.Context
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.example.koshertopia.R

/** רקע לפי קובץ drawable בשם המדינה (למשל "italy", "israel") */
fun View.setCountryBackground(countryKey: String) {
    val ctx = context
    val resId = ctx.resources.getIdentifier(countryKey, "drawable", ctx.packageName)
    if (resId != 0) {
        background = AppCompatResources.getDrawable(ctx, resId)
    }
}

/** המרה משם תצוגה ("Italy") ל־key ("italy") לפי המערכים שלך */
fun Context.countryNameToKey(displayName: String): String {
    val names = resources.getStringArray(R.array.countries)
    val keys  = resources.getStringArray(R.array.countries_drawable)
    val idx = names.indexOfFirst { it.equals(displayName, ignoreCase = true) }
    return if (idx in keys.indices) keys[idx] else displayName.lowercase().replace(' ', '_')
}

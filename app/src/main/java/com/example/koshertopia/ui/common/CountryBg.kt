package com.example.koshertopia.ui.common

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.example.koshertopia.R

/** מחזיר resId של תמונת הרקע לפי שם מדינה, בהתאם לסדר שב-arrays.xml */
fun countryNameToBgRes(context: Context, countryName: String?): Int {
    if (countryName.isNullOrBlank()) return R.drawable.bg_default_country
    val countries = context.resources.getStringArray(R.array.countries)
    val drawables = context.resources.getStringArray(R.array.countries_drawable)
    val idx = countries.indexOfFirst { it.equals(countryName, ignoreCase = true) }
    if (idx in drawables.indices) {
        val name = drawables[idx]               // למשל "italy"
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id != 0) return id
    }
    return R.drawable.bg_default_country
}

/** מיישם רקע/תמונה לרכיב (ImageView או View אחר) לפי שם מדינה */
fun View.setCountryBackground(countryName: String?) {
    val resId = countryNameToBgRes(context, countryName)
    if (this is ImageView) setImageResource(resId) else setBackgroundResource(resId)
}

/** ניסיון חכם לחלץ שם מדינה מהמסמך (Country = שם מדינה, או "(Spain)" מתוך "+34 (Spain)") */
fun extractCountryName(country: String?, countryCode: String?, businessCountryCode: String?): String? {
    // לעתים country/countryCode כבר שם מדינה (לא מתחיל ב+)
    val direct = listOf(country, countryCode).firstOrNull { !it.isNullOrBlank() && !it!!.trim().startsWith("+") }
    if (!direct.isNullOrBlank()) return direct
    // "+34 (Spain)" -> "Spain"
    businessCountryCode?.let {
        val m = "\\(([^)]+)\\)".toRegex().find(it)
        if (m != null) return m.groupValues[1]
    }
    return null
}

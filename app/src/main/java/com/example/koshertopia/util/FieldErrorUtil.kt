package com.example.koshertopia.util

import android.content.Context
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.example.koshertopia.R

object FieldErrorUtil {

    fun markError(editText: EditText, context: Context) {
        editText.background = ContextCompat.getDrawable(context, R.drawable.input_background_error)
    }

    fun clearError(editText: EditText, context: Context) {
        editText.background = ContextCompat.getDrawable(context, R.drawable.input_background)
    }
}
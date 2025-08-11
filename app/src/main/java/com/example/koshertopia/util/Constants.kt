package com.example.koshertopia.util

object Constants {
    const val EXTRA_USER_TYPE = "user_type"
    const val BUSINESS_DB_NAME = "Business"
    const val TRAVELER_DB_NAME = "Travelers"
    const val TRAVELER_FOLDER_NAME = "traveler_images"
    const val BUSINESS_FOLDER_NAME = "business_images"

    // restaurant registration
    const val RESTAURANTS_DB_NAME = "Restaurants"
    const val RESTAURANT_MENU_FOLDER = "restaurant_menus"
    const val RESTAURANT_CERT_FOLDER = "restaurant_certificates"

    // Passing context between screens (country, currency etc.)
    const val EXTRA_SELECTED_COUNTRY = "selected_country"
    const val EXTRA_SELECTED_COUNTRY_CODE = "selected_country_code" // e.g. +972 (Israel)
    const val EXTRA_CURRENCY_SYMBOL = "currency_symbol" // optional if you set it upstream

    // Business type
    const val EXTRA_BUSINESS_TYPE = "business_type"

    const val EXTRA_FORCE_COMPLETE = "forceComplete"

}
package com.example.koshertopia

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.koshertopia.util.Constants
import com.example.koshertopia.util.LoginEnum

class SelectBusinessTypeActivity : AppCompatActivity() {

    private var selectedCountry: String? = null           // e.g. "Israel"
    private var selectedCountryCode: String? = null       // e.g. "972"
    private var currencySymbol: String? = null            // e.g. "₪"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activitiy_select_business_type)

        findViewById<TextView>(R.id.lBL_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // קבלת אקסטרות מהמסך הקודם (CreateBusinessAccountActivity)
        selectedCountry     = intent.getStringExtra(Constants.EXTRA_SELECTED_COUNTRY)
        selectedCountryCode = intent.getStringExtra(Constants.EXTRA_SELECTED_COUNTRY_CODE)
        currencySymbol      = intent.getStringExtra(Constants.EXTRA_CURRENCY_SYMBOL)

        // אריחים
        val tileRestaurants     = findViewById<LinearLayout>(R.id.tile_restaurants)
        val tileHotels          = findViewById<LinearLayout>(R.id.tile_hotels)
        val tileJewish          = findViewById<LinearLayout>(R.id.tile_jewish_services)
        val tileSupermarkets    = findViewById<LinearLayout>(R.id.tile_supermarkets)
        val tileMarkets         = findViewById<LinearLayout>(R.id.tile_markets)
        val tileAttractions     = findViewById<LinearLayout>(R.id.tile_attractions)
        val tileShabbat         = findViewById<LinearLayout>(R.id.tile_shabbat_hosts)
        val tileChabad          = findViewById<LinearLayout>(R.id.tile_chabad_houses)

        // כרגע – רק מסעדות
        tileRestaurants.isEnabled = true
        tileRestaurants.alpha = 1f
        tileRestaurants.setOnClickListener {
            startActivity(
                Intent(this, RestaurantRegistrationActivity::class.java)
                    .putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.BUSINESS.name)
                    .putExtra(Constants.EXTRA_BUSINESS_TYPE, "RESTAURANT")
                    .putExtra(Constants.EXTRA_SELECTED_COUNTRY, selectedCountry)
                    .putExtra(Constants.EXTRA_SELECTED_COUNTRY_CODE, selectedCountryCode)
                    .putExtra(Constants.EXTRA_CURRENCY_SYMBOL, currencySymbol)
            )
        }

        // השבתת שאר האריחים
        listOf(
            tileHotels, tileJewish, tileSupermarkets,
            tileMarkets, tileAttractions, tileShabbat, tileChabad
        ).forEach { disableTile(it) }
    }

    private fun disableTile(v: View) {
        v.isEnabled = false
        v.alpha = 0.5f
        v.isClickable = false
        v.isFocusable = false
    }
}

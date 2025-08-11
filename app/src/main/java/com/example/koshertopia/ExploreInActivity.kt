package com.example.koshertopia

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.koshertopia.ui.common.setCountryBackground
import com.example.koshertopia.ui.restaurants.KosherRestaurantsActivity
import com.example.koshertopia.util.Constants

class ExploreInActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explore_in)

        val root  = findViewById<View>(R.id.explore_root)
        val title = findViewById<TextView>(R.id.explore_in_LBL_title)
        val back  = findViewById<TextView>(R.id.lBL_back)

        // מגיע מ-Intent: "countryKey" וגם "countryNameToShow"
        val countryKey = (intent.getStringExtra("countryKey") ?: "israel").lowercase()
        val countryNameToShow =
            intent.getStringExtra("countryNameToShow")
                ?: countryKey.replace('_',' ').replaceFirstChar { it.uppercase() }

        title.text = "Explore in\n$countryNameToShow"

        // רקע לפי המדינה (בלי getIdentifier ידני)
        root.setCountryBackground(countryKey)

        // מפעילים רק את אריח המסעדות, השאר מנוטרלים
        val restaurantsTile = findViewById<View>(R.id.tile_restaurants)
        enable(restaurantsTile, true)
        listOf(
            R.id.tile_hotels, R.id.tile_jewish_services, R.id.tile_supermarkets,
            R.id.tile_markets, R.id.tile_attractions, R.id.tile_shabbat_hosts,
            R.id.tile_chabad_houses
        ).forEach { id -> enable(findViewById(id), false) }

        restaurantsTile.setOnClickListener {
            startActivity(
                Intent(this, KosherRestaurantsActivity::class.java)
                    .putExtra(Constants.EXTRA_SELECTED_COUNTRY, countryNameToShow) // "Italy"
                    // אם תרצי — אפשר להעביר גם את המפתח לתמונות:
                    .putExtra("countryKey", countryKey)
            )
        }

        back.setOnClickListener { finish() }
    }

    private fun enable(view: View, on: Boolean) {
        view.isEnabled = on
        view.isClickable = on
        view.alpha = if (on) 1f else 0.35f
    }
}

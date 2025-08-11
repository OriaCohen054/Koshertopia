package com.example.koshertopia.ui.restaurants

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.koshertopia.R
import com.example.koshertopia.data.remote.FavoritesRepo
import com.example.koshertopia.data.remote.RestaurantCardVM
import com.example.koshertopia.data.remote.RestaurantsRepo
import com.example.koshertopia.util.Constants.EXTRA_SELECTED_COUNTRY
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class KosherRestaurantsActivity : AppCompatActivity() {

    private lateinit var adapter: RestaurantsAdapter
    private var favReg: ListenerRegistration? = null
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var lastData: MutableList<RestaurantCardVM> = mutableListOf()

    private lateinit var countryName: String

    private var priceLevel: Int = 0 // 0=Any, 1..4
    private var priceFilter: Int? = null       // 1..4 (כמה סימני €)
    private var areaFilter: String? = null     // שם עיר/אזור
    private var moreKosherOnly: Boolean = false // לדוגמה מתוך "More" (רק כשר עם תעודה)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kosher_restaurants)

        // בתוך onCreate, אחרי setContentView:
        findViewById<com.google.android.material.chip.Chip>(R.id.chip_price).setOnClickListener { v ->
            priceLevel = (priceLevel + 1) % 5
            (v as com.google.android.material.chip.Chip).text = when (priceLevel) {
                0 -> "Price: Any"
                1 -> "Price: €"
                2 -> "Price: €€"
                3 -> "Price: €€€"
                else -> "Price: €€€€"
            }
            reload()
        }


//        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_price).setOnClickListener {
//            showPriceSheet()
//        }
//        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_location).setOnClickListener {
//            showLocationSheet()
//        }
//        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_filter_more).setOnClickListener {
//            showMoreSheet()
//        }

        findViewById<TextView>(R.id.restaurants_back)?.setOnClickListener { finish() }

        // מקבלים שם מדינה לתצוגה/שאילתה (repo שלך עובד עם countryName)
        countryName = intent.getStringExtra(EXTRA_SELECTED_COUNTRY)
            ?: intent.getStringExtra("countryName")
                    ?: intent.getStringExtra("countryKey") // fallback אם את שולחת key כשם
                    ?: run {
                Toast.makeText(this, "Country is missing", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        // Recycler + Adapter
        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.restaurants_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = RestaurantsAdapter(
            mutableListOf(),
            onCardClick = { card -> openDetails(card) },  // <-- היה it.id, עכשיו מעבירים את הכרטיס כולו
            onToggleFav  = { toggleFavorite(it) }
        )
        recycler.adapter = adapter

        // Favorites listener
        auth.currentUser?.uid?.let { uid ->
            favReg = FavoritesRepo.listen(uid, { ids ->
                lastData.forEach { it.favorite = ids.contains(it.id) }
                adapter.submit(lastData)
            }, { /* ignore */ })
        }

        // Filters
        findViewById<ChipGroup>(R.id.filter_group).setOnCheckedChangeListener { _, _ -> reload() }

        // initial load
        reload()
    }

    private fun showEmptyState(show: Boolean) {
        findViewById<View>(R.id.empty_states_no_restaurants)?.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.restaurants_recycler)?.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun reload() {
        val group = findViewById<ChipGroup>(R.id.filter_group)
        val shabbatOnly = group.findViewById<Chip>(R.id.chip_shabbat)?.isChecked == true
        val cuisineFilter = buildSet {
            if (group.findViewById<Chip>(R.id.chip_meat)?.isChecked == true) add("MEAT")
            if (group.findViewById<Chip>(R.id.chip_dairy)?.isChecked == true) add("DAIRY")
            if (group.findViewById<Chip>(R.id.chip_pareve)?.isChecked == true) add("PAREVE")
        }

        RestaurantsRepo.fetchByCountry(
            countryName = countryName,
            shabbatOnly = shabbatOnly,
            cuisineFilter = cuisineFilter,
            onResult = { list ->
                // כאן את יכולה להדפיס לבדיקה:
                // Log.d("Restaurants", "got=${list.size} shabbat=$shabbatOnly cuisine=$cuisineFilter")
                lastData = list.toMutableList()
                adapter.submit(lastData)
                showEmptyState(lastData.isEmpty())
            },
            onError = {
                Toast.makeText(this, "Failed to load restaurants", Toast.LENGTH_SHORT).show()
                lastData.clear()
                adapter.submit(lastData)
                showEmptyState(true)
            }
        )
    }

    private fun toggleFavorite(card: RestaurantCardVM) {
        val uid = auth.currentUser?.uid ?: return
        FavoritesRepo.toggle(uid, card.id, card.country) { isFav ->
            card.favorite = isFav
            adapter.notifyDataSetChanged()
        }
    }

    private fun openDetails(card: RestaurantCardVM) {
        val intent = Intent(this, RestaurantDetailsActivity::class.java)
        intent.putExtra("restaurantId", card.id)     // <-- שולחים רק מזהה
        // אם יש לך כבר שדות מוכנים מהכרטיס ורוצה למלא מראש:
        // intent.putExtra("name", card.name)
        // intent.putExtra("shortDescription", card.description)
        // intent.putExtra("logoUrl", card.logoUrl)
        startActivity(intent)
    }

    override fun onDestroy() {
        favReg?.remove()
        favReg = null
        super.onDestroy()
    }

    private fun showPriceSheet() {
        val options = arrayOf("Any", "€", "€€", "€€€", "€€€€")
        android.app.AlertDialog.Builder(this)
            .setTitle("Price")
            .setItems(options) { _, which ->
                priceFilter = when (which) {
                    0 -> null
                    else -> which // 1..4
                }
                reload()
            }.show()
    }

    private fun showLocationSheet() {
        // אופציות לדוגמה; אפשר להביא רשימת ערים דינמית מה־DB
        val options = arrayOf("Any","Rome","Jerusalem","New York")
        android.app.AlertDialog.Builder(this)
            .setTitle("Location")
            .setItems(options) { _, which ->
                areaFilter = options[which].takeIf { it != "Any" }
                reload()
            }.show()
    }

    private fun showMoreSheet() {
        val options = arrayOf("Only with kosher certificate")
        val checks = booleanArrayOf(moreKosherOnly)
        android.app.AlertDialog.Builder(this)
            .setTitle("More filters")
            .setMultiChoiceItems(options, checks) { _, _, isChecked ->
                moreKosherOnly = isChecked
            }
            .setPositiveButton("Apply") { _, _ -> reload() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

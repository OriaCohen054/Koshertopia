package com.example.koshertopia.ui.restaurants

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.koshertopia.R
import com.example.koshertopia.data.remote.RestaurantDetailsDTO
import com.example.koshertopia.data.remote.RestaurantLite
import com.example.koshertopia.data.remote.RestaurantsRepo

class RestaurantDetailsActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var pagerIndicator: TextView

    // UI refs
    private lateinit var titleTv: TextView
    private lateinit var subTv: TextView
    private lateinit var certTv: TextView
    private lateinit var hoursContainer: LinearLayout
    private lateinit var shabbatContainer: LinearLayout
    private lateinit var featuresContainer: LinearLayout
    private lateinit var tagsContainer: LinearLayout
    private lateinit var kosherBadge: TextView

    // keeps titles for the media pager (Logo/Menu/Certificate)
    private var mediaTitles: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_details)

        // Bind views
        titleTv = findViewById(R.id.details_LBL_title)
        subTv = findViewById(R.id.details_LBL_subtitle)
//        certTv = findViewById(R.id.details_LBL_certValue)
        hoursContainer = findViewById(R.id.details_LYT_hours)
        shabbatContainer = findViewById(R.id.details_LYT_shabbat)
        featuresContainer = findViewById(R.id.details_LYT_flags)
        tagsContainer = findViewById(R.id.details_TAGS_container)
        pager = findViewById(R.id.details_PAGER_media)
        pagerIndicator = findViewById(R.id.details_LBL_pagerIndicator)
        kosherBadge = findViewById(R.id.details_LBL_kosherBadge)

        // Back
        findViewById<TextView>(R.id.details_LBL_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Prefer full object if provided; otherwise load by id
        val passed = intent.getSerializableExtra("restaurant") as? RestaurantLite
        val id = intent.getStringExtra("restaurantId")

        when {
            passed != null -> {
                populateUi(
                    RestaurantDetailsDTO(
                        id = passed.restaurantId ?: "",
                        name = passed.name.orEmpty(),
                        shortDescription = passed.shortDescription.orEmpty(),
                        kosherCertification = passed.kosherCertification.orEmpty(),
                        cuisineType = passed.cuisineType.orEmpty(),
                        priceLevel = passed.priceLevel.orEmpty(),
                        languages = passed.languages ?: emptyList(),
                        hoursSunday = passed.hoursSunday.orEmpty(),
                        hoursMonday = passed.hoursMonday.orEmpty(),
                        hoursTuesday = passed.hoursTuesday.orEmpty(),
                        hoursWednesday = passed.hoursWednesday.orEmpty(),
                        hoursThursday = passed.hoursThursday.orEmpty(),
                        hoursFriday = passed.hoursFriday.orEmpty(),
                        hoursSaturday = passed.hoursSaturday.orEmpty(),
                        hasFridayMeal = passed.hasFridayMeal == true,
                        fridayMealCost = passed.fridayMealCost.orEmpty(),
                        hasShabbatMeal = passed.hasShabbatMeal == true,
                        shabbatMealCost = passed.shabbatMealCost.orEmpty(),
                        havdalahOnMotzash = passed.havdalahOnMotzash == true,
                        accessible = passed.accessible == true,
                        kidsMenu = passed.kidsMenu == true,
                        takeaway = passed.takeaway == true,
                        seating = passed.seating == true,
                        jew2go = passed.jew2go == true,
                        nearTransit = false, // per your note: remove this flag entirely
                        logoUrl = passed.logoUrl.orEmpty(),
                        kosherCertificateUrl = passed.kosherCertificateUrl.orEmpty(),
                        menuImageUrl = passed.menuImageUrl.orEmpty(),
                        // NEW (optional – if present in DTO they'll be used):
                        tableFee = passed.tableFee.orEmpty(),
                        toiletFee = passed.toiletFee.orEmpty(),
                        currencySymbol = passed.currencySymbol.orEmpty()
                    )
                )
            }
            !id.isNullOrBlank() -> {
                RestaurantsRepo.fetchById(
                    restaurantId = id,
                    onResult = { dto -> populateUi(dto) },
                    onError = {
                        Toast.makeText(this, "Failed to load restaurant", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
            else -> {
                Toast.makeText(this, "Missing restaurant data", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun populateUi(dto: RestaurantDetailsDTO) {
        val font = ResourcesCompat.getFont(this, R.font.poppins_regular)

        // Business name first (fallback safe)
        titleTv.text = dto.name.ifBlank { "—" }
        if (dto.shortDescription.isBlank()) {
            subTv.visibility = View.GONE
        } else {
            subTv.visibility = View.VISIBLE
            subTv.text = dto.shortDescription
        }

//        certTv.text = dto.kosherCertification.ifBlank { "—" }

        // Kosher badge over media
        val badgeText = dto.kosherCertification.ifBlank { "Kosher" }
        kosherBadge.text = badgeText
        kosherBadge.visibility = if (badgeText.isBlank()) View.GONE else View.VISIBLE

        // Opening Hours (Sun -> Sat; empty => Closed)
        hoursContainer.removeAllViews()
        val hoursRows = listOf(
            "Sunday" to dto.hoursSunday,
            "Monday" to dto.hoursMonday,
            "Tuesday" to dto.hoursTuesday,
            "Wednesday" to dto.hoursWednesday,
            "Thursday" to dto.hoursThursday,
            "Friday" to dto.hoursFriday,
            "Saturday" to dto.hoursSaturday
        )
        hoursRows.forEach { (day, value) ->
            val tv = TextView(this).apply {
                setTextColor(Color.BLACK)
                textSize = 14f
                typeface = font
                text = if (value.isBlank()) "$day   Closed" else "$day   $value"
            }
            hoursContainer.addView(tv)
        }

        // Shabbat details (✔ / ✖)
        shabbatContainer.removeAllViews()
        fun addShabbatRow(label: String, yes: Boolean, extra: String? = null) {
            val tv = TextView(this).apply {
                setTextColor(Color.BLACK)
                textSize = 14f
                typeface = font
                text = if (extra.isNullOrBlank()) {
                    "$label   ${if (yes) "✔" else "✖"}"
                } else {
                    "$label   ${if (yes) "✔" else "✖"}  ($extra)"
                }
            }
            shabbatContainer.addView(tv)
        }
        addShabbatRow("Friday meal", dto.hasFridayMeal, dto.fridayMealCost.takeIf { it.isNotBlank() })
        addShabbatRow("Shabbat meal", dto.hasShabbatMeal, dto.shabbatMealCost.takeIf { it.isNotBlank() })
        addShabbatRow("Havdalah (Sat. night)", dto.havdalahOnMotzash)

        // Features (✔ / ✖) — without "Near public transit"
        featuresContainer.removeAllViews()
        val features = listOf(
            "Accessible" to dto.accessible,
            "Kids menu"  to dto.kidsMenu,
            "Jew2Go"     to dto.jew2go
        )
        features.forEach { (label, ok) ->
            val tv = TextView(this).apply {
                setTextColor(Color.BLACK)
                textSize = 14f
                typeface = font
                text = "$label   ${if (ok) "✔" else "✖"}"
            }
            featuresContainer.addView(tv)
        }

        // Tags (chips-like) — purely visual, no icons
        tagsContainer.removeAllViews()
        fun addTag(text: String) {
            val tv = TextView(this).apply {
                setTextAppearance(this@RestaurantDetailsActivity, 0)
                setTextColor(Color.parseColor("#141414"))
                textSize = 14f
                typeface = font
                setPadding(12, 8, 12, 8)
                background = resources.getDrawable(R.drawable.bg_tag_soft, theme)
                setText(text)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = 8
            tagsContainer.addView(tv, lp)
        }
        if (dto.cuisineType.equals("Vegetarian", true)) addTag("Vegetarian")
        if (dto.kidsMenu) addTag("Kid‑Friendly")
        if (dto.accessible) addTag("Accessible")
        if (dto.jew2go) addTag("Jew2Go")

        // Optional fees (table/toilet) with currency symbol
        val cs = dto.currencySymbol?.takeIf { it.isNotBlank() } ?: ""
        fun priceOrRaw(raw: String?): String? {
            val t = raw?.trim(); if (t.isNullOrEmpty()) return null
            return t.toDoubleOrNull()?.let { if (cs.isNotEmpty()) "$cs$t" else t } ?: t
        }
        priceOrRaw(dto.tableFee)?.let { addFeatureLine("Table fee", it) }
        priceOrRaw(dto.toiletFee)?.let { addFeatureLine("Toilet fee", it) }

        // Media pager: **exactly** Logo / Menu / Certificate (skip empties), sits inside the CARD
        val pairs = listOf(
            "Logo" to dto.logoUrl,
            "Menu" to dto.menuImageUrl,
            "Certificate" to dto.kosherCertificateUrl
        ).filter { !it.second.isNullOrBlank() }

        val media = pairs.map { it.second!! }
        mediaTitles = pairs.map { it.first }

        pager.adapter = SimpleImagePagerAdapter(this, media)
        if (media.isEmpty()) {
            pagerIndicator.visibility = View.GONE
        } else {
            pagerIndicator.visibility = View.VISIBLE
            pagerIndicator.text = "${mediaTitles[0]}  1/${media.size}"
        }
        pager.unregisterOnPageChangeCallback(pageChangeCallback)
        pager.registerOnPageChangeCallback(pageChangeCallback)

        // Notes section at bottom: leave hidden unless you later set text
        val notesTitle: TextView = findViewById(R.id.details_LBL_notesTitle)
        val notesBody: TextView = findViewById(R.id.details_LBL_subtitle)
        // If you later add dto.notes, just uncomment:
        // val notes = dto.notes?.trim().orEmpty()
        val notes = "" // placeholder (no data change now)
        if (notes.isNotEmpty()) {
            notesTitle.visibility = View.VISIBLE
            notesBody.visibility = View.VISIBLE
            notesBody.text = notes
        } else {
            notesTitle.visibility = View.GONE
            notesBody.visibility = View.GONE
        }
    }

    private fun addFeatureLine(label: String, value: String) {
        val font = ResourcesCompat.getFont(this, R.font.poppins_regular)
        val tv = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 14f
            typeface = font
            text = "$label   $value"
        }
        featuresContainer.addView(tv)
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val count = pager.adapter?.itemCount ?: 0
            if (count > 0 && position in mediaTitles.indices) {
                pagerIndicator.text = "${mediaTitles[position]}  ${position + 1}/$count"
            }
        }
    }
}

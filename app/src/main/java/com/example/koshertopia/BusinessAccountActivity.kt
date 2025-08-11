// BusinessAccountActivity.kt
package com.example.koshertopia

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.koshertopia.data.remote.RestaurantDetailsDTO
import com.example.koshertopia.data.remote.RestaurantsRepo
import com.example.koshertopia.ui.restaurants.SimpleImagePagerAdapter
import com.example.koshertopia.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BusinessAccountActivity : BaseActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }

    // Header
    private lateinit var logoIv: ImageView
    private lateinit var titleTv: TextView
    private lateinit var contentContainer: LinearLayout

    // נשתמש בזה לרענון אוטומטי כשחוזרים מעריכה
    override fun onResume() {
        super.onResume()
        loadHeader()
        loadAndBindDetailsCard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_account)

        logoIv = findViewById(R.id.business_logo)
        titleTv = findViewById(R.id.business_title)
        contentContainer = findViewById(R.id.content_container)

        // תפריט עליון
        findViewById<View>(R.id.btn_menu).setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add("Edit business").setOnMenuItemClickListener {
                    startActivity(Intent(this@BusinessAccountActivity, EditBusinessActivity::class.java))
                    true
                }
                menu.add("Edit restaurant").setOnMenuItemClickListener {
                    startActivity(Intent(this@BusinessAccountActivity, EditRestaurantActivity::class.java))
                    true
                }
                menu.add("Log out").setOnMenuItemClickListener { showLogout(); true }
                show()
            }
        }

        loadHeader()
        loadAndBindDetailsCard()
    }

    private fun loadHeader() {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Constants.BUSINESS_DB_NAME).document(uid).get()
            .addOnSuccessListener { d ->
                val name = d.getString("businessName").orEmpty().ifBlank { "Business" }
                titleTv.text = name

                val logo = d.getString("imageUrl")
                if (!logo.isNullOrBlank()) {
                    Glide.with(this).load(logo)
                        .placeholder(R.drawable.ico_busines)
                        .error(R.drawable.ico_busines)
                        .into(logoIv)
                } else {
                    logoIv.setImageResource(R.drawable.ico_busines)
                }
            }
    }

    /** מנפח את הכרטיס ומזרים נתונים מה־Restaurant של המשתמש (doc id = uid) */
    private fun loadAndBindDetailsCard() {
        val uid = auth.currentUser?.uid ?: return

        // ננקה וננפח כרטיס חדש בכל טעינה
        contentContainer.removeAllViews()
        val card = LayoutInflater.from(this).inflate(R.layout.view_restaurant_details_card, contentContainer, false)
        contentContainer.addView(card)

        // טוען פרטי מסעדה
        RestaurantsRepo.fetchById(
            restaurantId = uid,
            onResult = { dto -> bindDetailsCard(card, dto) },
            onError = {
                // אם אין עדיין מסעדה — מציגים כרטיס ריק עם שם העסק וכפתור להשלמה
                val dto = RestaurantDetailsDTO(
                    id = uid,
                    name = titleTv.text.toString()
                )
                bindDetailsCard(card, dto)
                Toast.makeText(this, "Complete your restaurant details to show here", Toast.LENGTH_SHORT).show()
            }
        )
    }

    /** בדיוק כמו ב-RestaurantDetailsActivity.populateUi, רק שמקבל root של הכרטיס */
    private fun bindDetailsCard(root: View, dto: RestaurantDetailsDTO) {
        val font = ResourcesCompat.getFont(this, R.font.poppins_regular)

        val title = root.findViewById<TextView>(R.id.details_LBL_title)
        val sub   = root.findViewById<TextView>(R.id.details_LBL_subtitle)
        val hoursContainer   = root.findViewById<LinearLayout>(R.id.details_LYT_hours)
        val shabbatContainer = root.findViewById<LinearLayout>(R.id.details_LYT_shabbat)
        val featuresContainer= root.findViewById<LinearLayout>(R.id.details_LYT_flags)
        val tagsContainer    = root.findViewById<LinearLayout>(R.id.details_TAGS_container)
        val kosherBadge      = root.findViewById<TextView>(R.id.details_LBL_kosherBadge)

        val pager = root.findViewById<ViewPager2>(R.id.details_PAGER_media)
        val pagerIndicator = root.findViewById<TextView>(R.id.details_LBL_pagerIndicator)

        // כותרת/תיאור
        title.text = dto.name.ifBlank { "—" }
        if (dto.shortDescription.isBlank()) {
            sub.visibility = View.GONE
        } else {
            sub.visibility = View.VISIBLE
            sub.text = dto.shortDescription
        }

        // תג כשרות
        val badgeText = dto.kosherCertification.ifBlank { if (dto.kosherCertificateUrl.isNotBlank()) "Kosher" else "" }
        kosherBadge.text = badgeText
        kosherBadge.visibility = if (badgeText.isBlank()) View.GONE else View.VISIBLE

        // שעות פתיחה
        hoursContainer.removeAllViews()
        listOf(
            "Sunday" to dto.hoursSunday,
            "Monday" to dto.hoursMonday,
            "Tuesday" to dto.hoursTuesday,
            "Wednesday" to dto.hoursWednesday,
            "Thursday" to dto.hoursThursday,
            "Friday" to dto.hoursFriday,
            "Saturday" to dto.hoursSaturday
        ).forEach { (day, value) ->
            val tv = TextView(this).apply {
                setTextColor(Color.BLACK)
                textSize = 14f
                typeface = font
                text = if (value.isBlank()) "$day   Closed" else "$day   $value"
            }
            hoursContainer.addView(tv)
        }

        // שבת
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

        // מאפיינים
        featuresContainer.removeAllViews()
        val features = mutableListOf<Pair<String, Boolean>>(
            "Accessible" to dto.accessible,
            "Kids menu"  to dto.kidsMenu,
            "Jew2Go"     to dto.jew2go
        )
        // עמלות אם קיימות
        val cs = dto.currencySymbol.takeIf { it.isNotBlank() } ?: ""
        fun priceOrRaw(raw: String?): String? {
            val t = raw?.trim(); if (t.isNullOrEmpty()) return null
            return t.toDoubleOrNull()?.let { if (cs.isNotEmpty()) "$cs$t" else t.toString() } ?: t
        }
        priceOrRaw(dto.tableFee)?.let {
            val tv = TextView(this).apply {
                setTextColor(Color.BLACK); textSize = 14f; typeface = font; text = "Table fee   $it"
            }
            featuresContainer.addView(tv)
        }
        priceOrRaw(dto.toiletFee)?.let {
            val tv = TextView(this).apply {
                setTextColor(Color.BLACK); textSize = 14f; typeface = font; text = "Toilet fee   $it"
            }
            featuresContainer.addView(tv)
        }
        features.forEach { (label, ok) ->
            val tv = TextView(this).apply {
                setTextColor(Color.BLACK); textSize = 14f; typeface = font; text = "$label   ${if (ok) "✔" else "✖"}"
            }
            featuresContainer.addView(tv)
        }

        // תגיות
        tagsContainer.removeAllViews()
        fun addTag(text: String) {
            val tv = TextView(this).apply {
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
        if (dto.kidsMenu) addTag("Kid-Friendly")
        if (dto.accessible) addTag("Accessible")
        if (dto.jew2go) addTag("Jew2Go")

        // מדיה: בדיוק Logo → Menu → Certificate
        val pairs = listOf(
            "Logo" to dto.logoUrl,
            "Menu" to dto.menuImageUrl,
            "Certificate" to dto.kosherCertificateUrl
        ).filter { !it.second.isNullOrBlank() }

        val media = pairs.map { it.second!! }
        val titles = pairs.map { it.first }

        pager.adapter = SimpleImagePagerAdapter(this, media)
        if (media.isEmpty()) {
            pagerIndicator.visibility = View.GONE
        } else {
            pagerIndicator.visibility = View.VISIBLE
            pagerIndicator.text = "${titles[0]}  1/${media.size}"
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val count = pager.adapter?.itemCount ?: 0
                    if (count > 0 && position in titles.indices) {
                        pagerIndicator.text = "${titles[position]}  ${position + 1}/$count"
                    }
                }
            })
        }

        // הערות — אם אין, מסתירים (כמו במסך הפרטים)
        val notesTitle: TextView = root.findViewById(R.id.details_LBL_notesTitle)
        val notesBody:  TextView = root.findViewById(R.id.details_LBL_subtitle)
        val notes = dto.notes?.trim().orEmpty()
        if (notes.isNotEmpty()) {
            notesTitle.visibility = View.VISIBLE
            notesBody.visibility = View.VISIBLE
            notesBody.text = notes
        } else {
            notesTitle.visibility = View.GONE
            notesBody.visibility = View.GONE
        }
    }

    private fun showLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log Out?")
            .setMessage("Are you sure you want to log out?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log Out") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }.show()
    }
}

package com.example.koshertopia
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.koshertopia.ui.common.countryNameToKey
import com.example.koshertopia.ui.travel.CountriesAdapter
import com.example.koshertopia.ui.travel.CountryItem
import com.example.koshertopia.ui.travel.EditTravelerActivity
import com.example.koshertopia.util.Constants
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlin.math.max

class TravelerAccountActivity : BaseActivity(){

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var headerListener: ListenerRegistration? = null

    private lateinit var profileIv: ImageView
    private lateinit var firstNameTv: TextView
    private lateinit var lastNameTv: TextView
    private lateinit var hiNameTv: TextView
    private lateinit var search: AutoCompleteTextView
    private lateinit var countriesRv: RecyclerView
    private lateinit var adapter: CountriesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_traveler_account)

        window.statusBarColor = Color.parseColor("#F5F7FF")
        window.navigationBarColor = Color.WHITE

        applyEdgeToEdge(
            rootId = R.id.traveler_root,
            scrollId = R.id.content_scroll,
            bottomBarId = R.id.buttons_area
        )

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.buttons_area)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, max(v.paddingBottom, sys.bottom))
            insets
        }

        profileIv = findViewById(R.id.traveler_icon)
        firstNameTv = findViewById(R.id.traveler_name)
        lastNameTv  = findViewById(R.id.traveler_last_name)
        hiNameTv    = findViewById(R.id.hi_traveler_name)
        search      = findViewById(R.id.traveler_search_country)
        countriesRv = findViewById(R.id.countries_recycler)

        // תפריט (3 קווים)
        findViewById<ImageView>(R.id.menu_button).setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add("Edit Profile").setOnMenuItemClickListener {
                    startActivity(Intent(this@TravelerAccountActivity, EditTravelerActivity::class.java));

                    true
                }
                menu.add("Log out").setOnMenuItemClickListener {
                    confirmLogout()
                    true
                }
                show()
            }
        }

//        // TravelerAccountActivity.kt  (בתוך onCreate אחרי bindViews)
//        findViewById<View>(R.id.favorite_button)?.setOnClickListener {
//            val uid = auth.currentUser?.uid
//            if (uid.isNullOrBlank()) {
//                Toast.makeText(this, "Please sign in to view saved places", Toast.LENGTH_SHORT).show()
//            } else {
//                startActivity(Intent(this, SavedDestinationsActivity::class.java))
//            }
//        }

        // 1) טעינת פרטי מטייל ותמונת פרופיל
        loadTravelerHeader()

        // 2) אוטוקומפליט חיפוש מדינה
        val countries = resources.getStringArray(R.array.countries) // נשאר Array
        val drawablesKeys = resources.getStringArray(R.array.countries_drawable)
        val countriesList = countries.toList() // בשביל indexOf מאוחר יותר

        search.setAdapter(
            android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, countries)
        )
        search.threshold = 1 // אופציונלי: להתחיל חיפוש אחרי אות אחת

        search.setOnItemClickListener { parent, _, position, _ ->
            val display = parent.getItemAtPosition(position) as String
            val originalIndex = countriesList.indexOf(display) // חיפוש האינדקס במערך המקורי

            val key = if (originalIndex >= 0 && originalIndex < drawablesKeys.size) {
                drawablesKeys[originalIndex]
            } else {
                this.countryNameToKey(display)
            }

            openExplore(display, key)
        }

        // 3) רשימת מדינות – אופקית כמו בדוגמה
        adapter = CountriesAdapter(onClick = { item ->
            val key = this.countryNameToKey(item.name)
            openExplore(item.name, key)
        })
        countriesRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        countriesRv.adapter = adapter
        adapter.submit(buildCountryItems(countries, drawablesKeys))
    }

    private fun buildCountryItems(names: Array<String>, keys: Array<String>): List<CountryItem> {
        val list = mutableListOf<CountryItem>()
        for (i in names.indices) {
            val key = keys.getOrNull(i) ?: names[i].lowercase().replace(' ','_')
            val resId = resources.getIdentifier(key, "drawable", packageName)
            if (resId != 0) list += CountryItem(names[i], resId)
        }
        return list
    }

    private fun openExplore(displayName: String, countryKey: String) {
        startActivity(
            Intent(this, ExploreInActivity::class.java)
                .putExtra("countryKey", countryKey)
                .putExtra("countryNameToShow", displayName)
        )
    }

    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid ?: return
        headerListener?.remove()
        headerListener = db.collection(Constants.TRAVELER_DB_NAME)
            .document(uid)
            .addSnapshotListener { d, _ ->
                if (d != null && d.exists()) renderTravelerHeader(d)
            }
    }

    override fun onStop() {
        headerListener?.remove()
        headerListener = null
        super.onStop()
    }

    private fun renderTravelerHeader(d: DocumentSnapshot) {
        val f = d.getString("firstName").orEmpty()
        val l = d.getString("lastName").orEmpty()
        firstNameTv.text = if (f.isNotBlank()) f else "Name"
        lastNameTv.text  = if (l.isNotBlank()) l else "Last Name"
        hiNameTv.text    = "Hi ${if (f.isNotBlank()) f else "Traveler"} 👋"

        val urlFromDb   = d.getString("imageUrl")
        val urlLegacy   = d.getString("photoUrl")
        val googlePhoto = auth.currentUser?.photoUrl?.toString()
        val url = when {
            !urlFromDb.isNullOrBlank()   -> urlFromDb
            !urlLegacy.isNullOrBlank()   -> urlLegacy
            !googlePhoto.isNullOrBlank() -> googlePhoto
            else -> null
        }

        val updatedAt = d.getLong("updatedAt") ?: 0L

        if (url != null) {
            Glide.with(profileIv)
                .load(url)
                .circleCrop()
                // חשוב נגד קאש: נשתמש בחתימה לפי updatedAt
                .signature(com.bumptech.glide.signature.ObjectKey(updatedAt))
                .placeholder(R.drawable.ico_traveler)
                .error(R.drawable.ico_traveler)
                .into(profileIv)
        } else {
            profileIv.setImageResource(R.drawable.ico_traveler)
        }
    }

    private fun loadTravelerHeader() {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Constants.TRAVELER_DB_NAME).document(uid).get()
            .addOnSuccessListener { d ->
                val f = d.getString("firstName").orEmpty()
                val l = d.getString("lastName").orEmpty()
                firstNameTv.text = if (f.isNotBlank()) f else "Name"
                lastNameTv.text  = if (l.isNotBlank()) l else "Last Name"
                hiNameTv.text    = "Hi ${if (f.isNotBlank()) f else "Traveler"} 👋"

                // חיפוש תמונה: imageUrl -> photoUrl -> Google account photoUrl
                val urlFromDb   = d.getString("imageUrl")
                val urlLegacy   = d.getString("photoUrl")
                val googlePhoto = auth.currentUser?.photoUrl?.toString()
                val url = when {
                    !urlFromDb.isNullOrBlank() -> urlFromDb
                    !urlLegacy.isNullOrBlank()  -> urlLegacy
                    !googlePhoto.isNullOrBlank() -> googlePhoto
                    else -> null
                }

                if (url != null) {
                    Glide.with(profileIv)
                        .load(url)
                        .circleCrop()
                        .placeholder(R.drawable.ico_traveler)
                        .error(R.drawable.ico_traveler)
                        .into(profileIv)
                } else {
                    profileIv.setImageResource(R.drawable.ico_traveler)
                }
            }
    }

    private fun confirmLogout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Log out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log out") { _, _ -> performLogout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        val isGoogle = FirebaseAuth.getInstance()
            .currentUser?.providerData?.any { it.providerId == "google.com" } == true

        if (isGoogle) {
            // התנתקות מ-Google כדי למנוע “זיכרון” של החשבון בדפדוף הבא
            GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN)
                .signOut()
                .addOnCompleteListener {
                    finishLogout()
                }
        } else {
            finishLogout()
        }
    }

    private fun finishLogout() {
        FirebaseAuth.getInstance().signOut()

        // ניקוי ה־back stack כדי שלא יהיה ניתן לחזור למסכים “מחוברים”
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}

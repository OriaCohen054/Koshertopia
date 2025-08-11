package com.example.koshertopia

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.koshertopia.util.Constants
import com.example.koshertopia.util.LoginEnum
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.storage
import java.io.File

class RestaurantRegistrationActivity : BaseActivity() {

    // ======== Images ========
    private enum class PickTarget { MENU, CERT }
    private var pendingPickFor: PickTarget? = null
    private var tempCameraUri: Uri? = null
    private var menuUri: Uri? = null
    private var certUri: Uri? = null

    private lateinit var ivMenu: ImageView
    private lateinit var ivCert: ImageView
    private lateinit var lblUploadMenu: TextView
    private lateinit var lblUploadCert: TextView

    // ======== Form ========
    private lateinit var cbSeating: CheckBox
    private lateinit var etTableFee: EditText

    private lateinit var cbToilets: CheckBox
    private lateinit var etToiletFee: EditText

    private lateinit var cbFridayMeal: CheckBox
    private lateinit var etFridayMealFee: EditText

    private lateinit var cbSaturdayMeal: CheckBox
    private lateinit var etSaturdayMealFee: EditText

    private lateinit var cbHavdalah: CheckBox
    private lateinit var rgCuisineType: RadioGroup

    private lateinit var cbKidsMenu: CheckBox
    private lateinit var cbHighChairs: CheckBox
    private lateinit var cbPlayArea: CheckBox

    private lateinit var cbAccessible: CheckBox
    private lateinit var cbStroller: CheckBox
    private lateinit var cbParking: CheckBox

    private lateinit var cbJew2Go: CheckBox
    private lateinit var etNotes: EditText

    // Languages
    private lateinit var languagesContainer: LinearLayout
    private val selectedLanguages = linkedSetOf<String>() // שומר סדר בחירה

    // Opening hours fragment (שימי לב לשם המחלקה!)
    private lateinit var openingHoursFragment: SimpleOpeningHoursFragment

    // Extras
    private var selectedCountry: String? = null
    private var selectedCountryCode: String? = null
    private var currencySymbol: String = "$" // ברירת מחדל
    private var businessType: String = "RESTAURANT"

    private val imagePermRequestCode = 7010

    // Launchers
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK && tempCameraUri != null) {
            when (pendingPickFor) {
                PickTarget.MENU -> {
                    menuUri = tempCameraUri
                    ivMenu.setImageURI(menuUri)
                    ivMenu.background = ContextCompat.getDrawable(this, R.drawable.input_background)
                }
                PickTarget.CERT -> {
                    certUri = tempCameraUri
                    ivCert.setImageURI(certUri)
                    ivCert.background = ContextCompat.getDrawable(this, R.drawable.input_background)
                }
                else -> {}
            }
        }
        pendingPickFor = null
        tempCameraUri = null
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK && res.data?.data != null) {
            val uri = res.data!!.data
            when (pendingPickFor) {
                PickTarget.MENU -> {
                    menuUri = uri
                    ivMenu.setImageURI(menuUri)
                    ivMenu.background = ContextCompat.getDrawable(this, R.drawable.input_background)
                }
                PickTarget.CERT -> {
                    certUri = uri
                    ivCert.setImageURI(certUri)
                    ivCert.background = ContextCompat.getDrawable(this, R.drawable.input_background)
                }
                else -> {}
            }
        }
        pendingPickFor = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_registration)

        // Back
        findViewById<View>(R.id.restaurant_lBL_back)?.setOnClickListener { finish() }

        // Extras
        selectedCountry = intent.getStringExtra(Constants.EXTRA_SELECTED_COUNTRY)
        selectedCountryCode = intent.getStringExtra(Constants.EXTRA_SELECTED_COUNTRY_CODE)
        businessType = intent.getStringExtra(Constants.EXTRA_BUSINESS_TYPE) ?: "RESTAURANT"

        // מטבע מתוך arrays.xml (country_currencies) אם קיים
        currencySymbol = resolveCurrencySymbolFromArrays(selectedCountry)

        bindViews()
        setupMoneyToggles()
        inflateLanguages()
        attachOpeningHoursFragment()

        // Upload clicks
        val menuClicker = View.OnClickListener { selectImageFor(PickTarget.MENU) }
        val certClicker = View.OnClickListener { selectImageFor(PickTarget.CERT) }
        ivMenu.setOnClickListener(menuClicker)
        lblUploadMenu.setOnClickListener(menuClicker)
        ivCert.setOnClickListener(certClicker)
        lblUploadCert.setOnClickListener(certClicker)

        // Next
        findViewById<Button>(R.id.btn_next).setOnClickListener { onSubmit() }
    }

    private fun bindViews() {
        ivMenu = findViewById(R.id.image_upload_menu)
        ivCert = findViewById(R.id.image_upload_kosher)
        lblUploadMenu = findViewById(R.id.label_upload_menu)
        lblUploadCert = findViewById(R.id.label_upload_kosher)

        cbSeating = findViewById(R.id.checkbox_seating)
        etTableFee = findViewById(R.id.input_table_fee)
        etTableFee.hint = "Table fee ($currencySymbol)"

        cbToilets = findViewById(R.id.checkbox_toilets)
        etToiletFee = findViewById(R.id.input_toilet_fee)
        etToiletFee.hint = "Toilet fee ($currencySymbol)"

        cbFridayMeal = findViewById(R.id.checkbox_friday_meal)
        etFridayMealFee = findViewById(R.id.input_friday_meal_fee)
        etFridayMealFee.hint = "Meal fee ($currencySymbol)"

        cbSaturdayMeal = findViewById(R.id.checkbox_saturday_meal)
        etSaturdayMealFee = findViewById(R.id.input_saturday_meal_fee)
        etSaturdayMealFee.hint = "Meal fee ($currencySymbol)"

        cbHavdalah = findViewById(R.id.checkbox_havdalah)
        rgCuisineType = findViewById(R.id.radio_cuisine_type)

        cbKidsMenu = findViewById(R.id.checkbox_kids_menu)
        cbHighChairs = findViewById(R.id.checkbox_high_chairs)
        cbPlayArea = findViewById(R.id.checkbox_play_area)

        cbAccessible = findViewById(R.id.checkbox_accessible)
        cbStroller = findViewById(R.id.checkbox_stroller)
        cbParking = findViewById(R.id.checkbox_parking)

        cbJew2Go = findViewById(R.id.checkbox_jew2go)
        etNotes = findViewById(R.id.editText_notes)

        languagesContainer = findViewById(R.id.languages_container)
    }

    private fun setupMoneyToggles() {
        fun wire(cb: CheckBox, et: EditText) {
            et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            et.filters = arrayOf(InputFilter.LengthFilter(10))
            et.isEnabled = cb.isChecked
            cb.setOnCheckedChangeListener { _, checked ->
                et.isEnabled = checked
                if (!checked) et.setText("")
            }
        }
        wire(cbSeating, etTableFee)
        wire(cbToilets, etToiletFee)
        wire(cbFridayMeal, etFridayMealFee)
        wire(cbSaturdayMeal, etSaturdayMealFee)
    }

    /** בונה צ'יפים של שפות מתוך arrays.xml: languages_array */
    private fun inflateLanguages() {
        val langs = resources.getStringArray(R.array.languages_array)
        languagesContainer.removeAllViews()
        langs.forEach { lang ->
            val tv = TextView(this).apply {
                text = lang
                setPadding(28, 12, 28, 12)
                setTextColor(ContextCompat.getColor(this@RestaurantRegistrationActivity, android.R.color.white))
                background = ContextCompat.getDrawable(this@RestaurantRegistrationActivity, R.drawable.input_background)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(8, 8, 8, 8)
                layoutParams = params
                isClickable = true
                isFocusable = true
                alpha = 0.7f
            }
            tv.setOnClickListener {
                if (selectedLanguages.contains(lang)) {
                    selectedLanguages.remove(lang)
                    tv.alpha = 0.7f
                    tv.setTypeface(null, Typeface.NORMAL)
                } else {
                    selectedLanguages.add(lang)
                    tv.alpha = 1f
                    tv.setTypeface(null, Typeface.BOLD)
                }
            }
            languagesContainer.addView(tv)
        }
    }

    private fun attachOpeningHoursFragment() {
        openingHoursFragment = SimpleOpeningHoursFragment() // השם כפי שביקשת
        supportFragmentManager.beginTransaction()
            .replace(R.id.opening_hours_fragment_container, openingHoursFragment)
            .commit()
    }

    // ======== Image pick (camera/gallery) like your working screens ========

    private fun selectImageFor(target: PickTarget) {
        if (!hasImagePermissions()) {
            requestImagePermissions(target)
            return
        }
        pendingPickFor = target
        AlertDialog.Builder(this)
            .setTitle("Choose image source")
            .setItems(arrayOf("Camera", "Gallery")) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }.show()
    }

    private fun hasImagePermissions(): Boolean {
        val camOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val readOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        else
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return camOk && readOk
    }

    @SuppressLint("InlinedApi")
    private fun requestImagePermissions(target: PickTarget) {
        pendingPickFor = target
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms += Manifest.permission.READ_MEDIA_IMAGES
        else
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        requestPermissions(perms.toTypedArray(), imagePermRequestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == imagePermRequestCode) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                pendingPickFor?.let { selectImageFor(it) }
            } else {
                Toast.makeText(this, "Permissions required to select images", Toast.LENGTH_SHORT).show()
                pendingPickFor = null
            }
        }
    }

    private fun openCamera() {
        // כמו במסכי ההרשמה שלך: קובץ ב־getExternalFilesDir + FileProvider
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "restaurant_temp_${System.currentTimeMillis()}.jpg")
        tempCameraUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, tempCameraUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        cameraLauncher.launch(intent)
    }

    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        galleryLauncher.launch(galleryIntent)
    }

    // ======== Submit ========

    private fun onSubmit() {
        // תמונות – חובה שתיהן
        if (menuUri == null) {
            Toast.makeText(this, "Please upload the Menu image", Toast.LENGTH_LONG).show()
            return
        }
        if (certUri == null) {
            Toast.makeText(this, "Please upload the Kosher certificate image", Toast.LENGTH_LONG).show()
            return
        }

        // תמחור אם מסומן – חובה ומספרי
        fun feeOk(cb: CheckBox, et: EditText, label: String): Boolean {
            if (!cb.isChecked) return true
            val t = et.text.toString().trim()
            if (t.isEmpty()) {
                et.error = "$label is required"
                return false
            }
            return try {
                t.toDouble()
                true
            } catch (_: Exception) {
                et.error = "$label must be numeric"
                false
            }
        }
        if (!feeOk(cbSeating, etTableFee, "Table fee")) return
        if (!feeOk(cbToilets, etToiletFee, "Toilet fee")) return
        if (!feeOk(cbFridayMeal, etFridayMealFee, "Friday meal fee")) return
        if (!feeOk(cbSaturdayMeal, etSaturdayMealFee, "Saturday meal fee")) return

        // Cuisine type חובה
        val cuisineId = rgCuisineType.checkedRadioButtonId
        if (cuisineId == -1) {
            Toast.makeText(this, "Please select a cuisine type", Toast.LENGTH_LONG).show()
            return
        }
        val cuisineText = findViewById<RadioButton>(cuisineId).text.toString()

        // שפות – לפחות אחת
        if (selectedLanguages.isEmpty()) {
            Toast.makeText(this, "Please select at least one language", Toast.LENGTH_LONG).show()
            return
        }

        val openingHours = openingHoursFragment.getAllOpeningHours()
        if (!openingHoursFragment.validateNoOverlaps(openingHours)) {
            Toast.makeText(this, "Opening hours contain overlaps", Toast.LENGTH_LONG).show()
            return
        }
        if (!openingHoursFragment.validateAllDaysHaveRangeOrClosed()) {
            Toast.makeText(this, "Please set at least one time range for every open day", Toast.LENGTH_LONG).show()
            return
        }

        // העלאה ושמירה
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_LONG).show()
            return
        }

        uploadImageThen(menuUri!!, "${Constants.RESTAURANT_MENU_FOLDER}/$uid.jpg") { menuUrl ->
            if (menuUrl == null) {
                Toast.makeText(this, "Failed to upload menu image", Toast.LENGTH_LONG).show()
                return@uploadImageThen
            }
            uploadImageThen(certUri!!, "${Constants.RESTAURANT_CERT_FOLDER}/$uid.jpg") { certUrl ->
                if (certUrl == null) {
                    Toast.makeText(this, "Failed to upload certificate image", Toast.LENGTH_LONG).show()
                    return@uploadImageThen
                }
                saveRestaurant(uid, menuUrl, certUrl, openingHours, cuisineText)
            }
        }
    }

    private fun uploadImageThen(uri: Uri, path: String, cont: (String?) -> Unit) {
        val ref = com.google.firebase.Firebase.storage.reference.child(path)
        ref.putFile(uri)
            .addOnSuccessListener { ref.downloadUrl.addOnSuccessListener { cont(it.toString()) } }
            .addOnFailureListener { cont(null) }
    }

    private fun saveRestaurant(
        uid: String,
        menuUrl: String,
        certUrl: String,
        openingHours: Map<String, List<Pair<String, String>>>,
        cuisineType: String
    ) {
        val db = FirebaseFirestore.getInstance()

        val data = hashMapOf(
            "uid" to uid,
            "businessType" to businessType,
            "country" to (selectedCountry ?: ""),
            "countryCode" to (selectedCountryCode ?: ""),
            "currency" to currencySymbol,

            "seatingAvailable" to cbSeating.isChecked,
            "tableFee" to etTableFee.text.toString().trim(),
            "toiletsAvailable" to cbToilets.isChecked,
            "toiletFee" to etToiletFee.text.toString().trim(),

            "fridayMealAvailable" to cbFridayMeal.isChecked,
            "fridayMealFee" to etFridayMealFee.text.toString().trim(),
            "saturdayMealAvailable" to cbSaturdayMeal.isChecked,
            "saturdayMealFee" to etSaturdayMealFee.text.toString().trim(),
            "havdalahAvailable" to cbHavdalah.isChecked,

            "cuisineType" to cuisineType,
            "kidsMenu" to cbKidsMenu.isChecked,
            "highChairs" to cbHighChairs.isChecked,
            "playArea" to cbPlayArea.isChecked,

            "accessible" to cbAccessible.isChecked,
            "strollerAccess" to cbStroller.isChecked,
            "accessibleParking" to cbParking.isChecked,

            "jew2go" to cbJew2Go.isChecked,
            "notes" to etNotes.text.toString().trim(),

            "languages" to selectedLanguages.toList(),
            "openingHours" to openingHours,

            "menuImageUrl" to menuUrl,
            "kosherCertificateUrl" to certUrl
        )

        db.collection(Constants.RESTAURANTS_DB_NAME)
            .document(uid)
            .set(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Restaurant saved", Toast.LENGTH_LONG).show()

                // 1) לצאת מחשבון כדי שמסך ההתחברות באמת יוצג
                FirebaseAuth.getInstance().signOut()

                // 2) לנווט למסך ההתחברות עם ניקוי ה־back stack
                val intent = Intent(this, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.BUSINESS.name) // להעביר כ-String
                }
                startActivity(intent)
                finish() // ליתר ביטחון
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save restaurant: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    /** מוצא סימן מטבע לפי המדינה מתוך המערך country_currencies (אם הוספת) בפורמט: "Israel|₪" */
    private fun resolveCurrencySymbolFromArrays(countryName: String?): String {
        if (countryName.isNullOrBlank()) return "$" // ברירת מחדל בטוחה
        val countries = resources.getStringArray(R.array.countries)
        val symbols = resources.getStringArray(R.array.currency_symbols_by_country)
        val idx = countries.indexOfFirst { it.equals(countryName, ignoreCase = true) }
        return if (idx in symbols.indices) symbols[idx] else "$"
    }
}

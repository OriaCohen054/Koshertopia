package com.example.koshertopia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputFilter
import android.text.InputType
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.koshertopia.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.storage
import java.io.File

class EditRestaurantActivity : BaseActivity() {

    // Fire
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Views
    private lateinit var ivMenu: ImageView
    private lateinit var ivCert: ImageView
    private lateinit var lblMenu: TextView
    private lateinit var lblCert: TextView

    private lateinit var cbSeating: CheckBox
    private lateinit var etTableFee: EditText
    private lateinit var cbToilets: CheckBox
    private lateinit var etToiletFee: EditText

    private lateinit var cbFridayMeal: CheckBox
    private lateinit var etFridayMealFee: EditText
    private lateinit var cbSaturdayMeal: CheckBox
    private lateinit var etSaturdayMealFee: EditText
    private lateinit var cbHavdalah: CheckBox

    private lateinit var rgCuisine: RadioGroup
    private lateinit var cbKids: CheckBox
    private lateinit var cbHigh: CheckBox
    private lateinit var cbPlay: CheckBox

    private lateinit var cbAccessible: CheckBox
    private lateinit var cbStroller: CheckBox
    private lateinit var cbParking: CheckBox

    private lateinit var cbJew2Go: CheckBox
    private lateinit var etNotes: EditText

    private lateinit var languagesContainer: LinearLayout
    private val selectedLanguages = linkedSetOf<String>()

    // Opening hours fragment (שמרתי את השם שהשתמשת בו)
    private lateinit var openingHoursFragment: SimpleOpeningHoursFragment

    // Images
    private var menuUri: Uri? = null
    private var certUri: Uri? = null
    private var cameraUri: Uri? = null
    private enum class PickFor { MENU, CERT }
    private var pendingPick: PickFor? = null
    private val imagePermCode = 7020

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK && cameraUri != null) {
            when (pendingPick) {
                PickFor.MENU -> { menuUri = cameraUri; ivMenu.setImageURI(menuUri) }
                PickFor.CERT -> { certUri = cameraUri; ivCert.setImageURI(certUri) }
                else -> {}
            }
        }
        pendingPick = null
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            when (pendingPick) {
                PickFor.MENU -> { menuUri = res.data?.data; ivMenu.setImageURI(menuUri) }
                PickFor.CERT -> { certUri = res.data?.data; ivCert.setImageURI(certUri) }
                else -> {}
            }
        }
        pendingPick = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_restaurant)

        bindViews()
        wireClicks()
        inflateLanguages()
        attachOpeningHoursFragment()
        loadRestaurant()
    }

    private fun bindViews() {
        findViewById<TextView>(R.id.restaurant_lBL_back).setOnClickListener { finish() }

        ivMenu = findViewById(R.id.image_upload_menu)
        ivCert = findViewById(R.id.image_upload_kosher)
        lblMenu = findViewById(R.id.label_upload_menu)
        lblCert = findViewById(R.id.label_upload_kosher)

        cbSeating = findViewById(R.id.checkbox_seating)
        etTableFee = findViewById(R.id.input_table_fee)
        cbToilets = findViewById(R.id.checkbox_toilets)
        etToiletFee = findViewById(R.id.input_toilet_fee)

        cbFridayMeal = findViewById(R.id.checkbox_friday_meal)
        etFridayMealFee = findViewById(R.id.input_friday_meal_fee)
        cbSaturdayMeal = findViewById(R.id.checkbox_saturday_meal)
        etSaturdayMealFee = findViewById(R.id.input_saturday_meal_fee)
        cbHavdalah = findViewById(R.id.checkbox_havdalah)

        rgCuisine = findViewById(R.id.radio_cuisine_type)
        cbKids = findViewById(R.id.checkbox_kids_menu)
        cbHigh = findViewById(R.id.checkbox_high_chairs)
        cbPlay = findViewById(R.id.checkbox_play_area)

        cbAccessible = findViewById(R.id.checkbox_accessible)
        cbStroller = findViewById(R.id.checkbox_stroller)
        cbParking = findViewById(R.id.checkbox_parking)

        cbJew2Go = findViewById(R.id.checkbox_jew2go)
        etNotes = findViewById(R.id.editText_notes)

        languagesContainer = findViewById(R.id.languages_container)

        findViewById<Button>(R.id.edit_rest_BTN_cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.edit_rest_BTN_save).setOnClickListener { onSave() }

        // numeric filters
        listOf(etTableFee, etToiletFee, etFridayMealFee, etSaturdayMealFee).forEach {
            it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            it.filters = arrayOf(InputFilter.LengthFilter(10))
            it.isEnabled = false
        }
        cbSeating.setOnCheckedChangeListener { _, c -> etTableFee.isEnabled = c; if (!c) etTableFee.setText("") }
        cbToilets.setOnCheckedChangeListener { _, c -> etToiletFee.isEnabled = c; if (!c) etToiletFee.setText("") }
        cbFridayMeal.setOnCheckedChangeListener { _, c -> etFridayMealFee.isEnabled = c; if (!c) etFridayMealFee.setText("") }
        cbSaturdayMeal.setOnCheckedChangeListener { _, c -> etSaturdayMealFee.isEnabled = c; if (!c) etSaturdayMealFee.setText("") }
    }

    private fun wireClicks() {
        val pickMenu = android.view.View.OnClickListener { selectImageFor(PickFor.MENU) }
        val pickCert = android.view.View.OnClickListener { selectImageFor(PickFor.CERT) }
        ivMenu.setOnClickListener(pickMenu); lblMenu.setOnClickListener(pickMenu)
        ivCert.setOnClickListener(pickCert); lblCert.setOnClickListener(pickCert)
    }

    private fun inflateLanguages() {
        val langs = resources.getStringArray(R.array.languages_array)
        languagesContainer.removeAllViews()
        langs.forEach { lang ->
            val tv = TextView(this).apply {
                text = lang
                setPadding(28, 12, 28, 12)
                setTextColor(ContextCompat.getColor(this@EditRestaurantActivity, android.R.color.white))
                background = ContextCompat.getDrawable(this@EditRestaurantActivity, R.drawable.input_background)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(8,8,8,8); layoutParams = p
                isClickable = true; isFocusable = true; alpha = 0.7f
            }
            tv.setOnClickListener {
                if (selectedLanguages.contains(lang)) { selectedLanguages.remove(lang); tv.alpha = 0.7f; tv.setTypeface(null, android.graphics.Typeface.NORMAL) }
                else { selectedLanguages.add(lang); tv.alpha = 1f; tv.setTypeface(null, android.graphics.Typeface.BOLD) }
            }
            languagesContainer.addView(tv)
        }
    }

    private fun attachOpeningHoursFragment() {
        openingHoursFragment = SimpleOpeningHoursFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.opening_hours_fragment_container, openingHoursFragment)
            .commit()
    }

    private fun loadRestaurant() {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Constants.RESTAURANTS_DB_NAME).document(uid).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) return@addOnSuccessListener
                fun s(k: String) = d.getString(k).orEmpty()
                fun b(k: String) = d.getBoolean(k) == true
                // images
                val menu = s("menuImageUrl")
                val cert = s("kosherCertificateUrl")
                if (menu.isNotBlank()) Glide.with(this).load(menu).into(ivMenu)
                if (cert.isNotBlank()) Glide.with(this).load(cert).into(ivCert)

                // seat/toilet/fees
                cbSeating.isChecked = b("seatingAvailable")
                etTableFee.setText(s("tableFee")); etTableFee.isEnabled = cbSeating.isChecked

                cbToilets.isChecked = b("toiletsAvailable")
                etToiletFee.setText(s("toiletFee")); etToiletFee.isEnabled = cbToilets.isChecked

                cbFridayMeal.isChecked = b("fridayMealAvailable")
                etFridayMealFee.setText(s("fridayMealFee")); etFridayMealFee.isEnabled = cbFridayMeal.isChecked

                cbSaturdayMeal.isChecked = b("saturdayMealAvailable")
                etSaturdayMealFee.setText(s("saturdayMealFee")); etSaturdayMealFee.isEnabled = cbSaturdayMeal.isChecked

                cbHavdalah.isChecked = b("havdalahAvailable")

                // cuisine
                when (s("cuisineType").uppercase()) {
                    "MEAT" -> (rgCuisine.getChildAt(0) as RadioButton).isChecked = true
                    "DAIRY" -> (rgCuisine.getChildAt(1) as RadioButton).isChecked = true
                    "PAREVE" -> (rgCuisine.getChildAt(2) as RadioButton).isChecked = true
                }

                cbKids.isChecked = b("kidsMenu")
                cbHigh.isChecked = b("highChairs")
                cbPlay.isChecked = b("playArea")

                cbAccessible.isChecked = b("accessible")
                cbStroller.isChecked = b("strollerAccess")
                cbParking.isChecked = b("accessibleParking")

                cbJew2Go.isChecked = b("jew2go")
                etNotes.setText(s("notes"))

                // languages
                val langs = (d.get("languages") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val all = resources.getStringArray(R.array.languages_array)
                for (i in 0 until languagesContainer.childCount) {
                    val tv = languagesContainer.getChildAt(i) as TextView
                    if (langs.contains(tv.text.toString())) { tv.performClick() } // יסמן
                }

                (d.get("openingHours") as? Map<String, *>)?.let {
                    openingHoursFragment.setAllOpeningHoursCompat(it)
                }
            }
    }

    private fun onSave() {
        // minimal validation
        val cuisineId = rgCuisine.checkedRadioButtonId
        if (cuisineId == -1) { toast("Please select a cuisine type"); return }

        if (cbSeating.isChecked && etTableFee.text.toString().trim().isEmpty()) { etTableFee.error = "Required"; return }
        if (cbToilets.isChecked && etToiletFee.text.toString().trim().isEmpty()) { etToiletFee.error = "Required"; return }
        if (cbFridayMeal.isChecked && etFridayMealFee.text.toString().trim().isEmpty()) { etFridayMealFee.error = "Required"; return }
        if (cbSaturdayMeal.isChecked && etSaturdayMealFee.text.toString().trim().isEmpty()) { etSaturdayMealFee.error = "Required"; return }

        val cuisineText = findViewById<RadioButton>(cuisineId).text.toString()
        if (selectedLanguages.isEmpty()) { toast("Please select at least one language"); return }

        val openingHours = openingHoursFragment.getAllOpeningHours()
        // אם יש לך פונקציות ולידציה לפרגמנט – קראי להן כאן (השארתי כמו בקוד שלך)

        val uid = auth.currentUser?.uid ?: return
        val data = hashMapOf(
            "uid" to uid,
            "cuisineType" to cuisineText,
            "seatingAvailable" to cbSeating.isChecked,
            "tableFee" to etTableFee.text.toString().trim(),
            "toiletsAvailable" to cbToilets.isChecked,
            "toiletFee" to etToiletFee.text.toString().trim(),
            "fridayMealAvailable" to cbFridayMeal.isChecked,
            "fridayMealFee" to etFridayMealFee.text.toString().trim(),
            "saturdayMealAvailable" to cbSaturdayMeal.isChecked,
            "saturdayMealFee" to etSaturdayMealFee.text.toString().trim(),
            "havdalahAvailable" to cbHavdalah.isChecked,
            "kidsMenu" to cbKids.isChecked,
            "highChairs" to cbHigh.isChecked,
            "playArea" to cbPlay.isChecked,
            "accessible" to cbAccessible.isChecked,
            "strollerAccess" to cbStroller.isChecked,
            "accessibleParking" to cbParking.isChecked,
            "jew2go" to cbJew2Go.isChecked,
            "notes" to etNotes.text.toString().trim(),
            "languages" to selectedLanguages.toList(),
            "openingHours" to openingHours,
            "updatedAt" to System.currentTimeMillis()
        )

        fun commit(menuUrl: String?, certUrl: String?) {
            menuUrl?.let { data["menuImageUrl"] = it }
            certUrl?.let { data["kosherCertificateUrl"] = it }
            db.collection(Constants.RESTAURANTS_DB_NAME).document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { toast("Saved"); setResult(RESULT_OK); finish() }
                .addOnFailureListener { toast("Failed: ${it.message}") }
        }

        // uploads only if changed
        val storage = com.google.firebase.Firebase.storage
        val uploads = mutableListOf<kotlin.Pair<String, (String?)->Unit>>()

        var newMenuUrl: String? = null
        var newCertUrl: String? = null

        if (menuUri != null) {
            uploads += ("${Constants.RESTAURANT_MENU_FOLDER}/${uid}.jpg" to { url -> newMenuUrl = url })
        }
        if (certUri != null) {
            uploads += ("${Constants.RESTAURANT_CERT_FOLDER}/${uid}.jpg" to { url -> newCertUrl = url })
        }

        if (uploads.isEmpty()) {
            commit(null, null)
        } else {
            // upload chain
            fun uploadNext(i: Int) {
                if (i >= uploads.size) { commit(newMenuUrl, newCertUrl); return }
                val (path, setter) = uploads[i]
                val ref = storage.reference.child(path)
                val uri = if (path.contains(Constants.RESTAURANT_MENU_FOLDER)) menuUri!! else certUri!!
                ref.putFile(uri)
                    .addOnSuccessListener { ref.downloadUrl.addOnSuccessListener { setter(it.toString()); uploadNext(i+1) } }
                    .addOnFailureListener { toast("Upload failed"); }
            }
            uploadNext(0)
        }
    }

    private fun selectImageFor(target: PickFor) {
        pendingPick = target
        if (!hasImagePermissions()) { requestImagePermissions(); return }
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
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val read = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return cam && read
    }

    private fun requestImagePermissions() {
        val need = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            need += Manifest.permission.READ_MEDIA_IMAGES
        else
            need += Manifest.permission.READ_EXTERNAL_STORAGE
        ActivityCompat.requestPermissions(this, need.toTypedArray(), imagePermCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == imagePermCode && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            pendingPick?.let { selectImageFor(it) }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*" }
        galleryLauncher.launch(intent)
    }

    private fun openCamera() {
        val photoFile = File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "rest_${System.currentTimeMillis()}.jpg")
        cameraUri = androidx.core.content.FileProvider.getUriForFile(
            this, "${applicationContext.packageName}.fileprovider", photoFile
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        cameraLauncher.launch(intent)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

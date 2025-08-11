package com.example.koshertopia

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputFilter
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.koshertopia.util.Constants
import com.example.koshertopia.util.Constants.EXTRA_FORCE_COMPLETE
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.storage
import java.io.File

class EditBusinessActivity : BaseActivity() {



    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }

    // Views
    private lateinit var banner: TextView
    private lateinit var backLbl: TextView
    private lateinit var logoIv: ImageView
    private lateinit var changeLogoLbl: TextView

    private lateinit var nameEt: EditText
    private lateinit var countryTv: TextView
    private lateinit var addressEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var ccBizTv: TextView
    private lateinit var phoneBizEt: EditText
    private lateinit var ccOwnerTv: TextView
    private lateinit var phoneOwnerEt: EditText

    private lateinit var cancelBtn: Button
    private lateinit var saveBtn: Button

    private var forceComplete = false

    // image
    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null
    private val imagePermRequestCode = 7011

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK && cameraImageUri != null) {
            selectedImageUri = cameraImageUri
            logoIv.setImageURI(selectedImageUri)
        }
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            selectedImageUri = res.data?.data
            logoIv.setImageURI(selectedImageUri)
        }
    }

    data class BusinessDoc(
        val businessName: String? = null,
        val address: String? = null,
        val email: String? = null,
        val country: String? = null,
        val businessCountryCode: String? = null,
        val businessPhone: String? = null,
        val ownerCountryCode: String? = null,
        val ownerPhone: String? = null,
        val imageUrl: String? = null,
        val imageUri: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_business)

        bindViews()
        wirePickers()
        wireActions()

        forceComplete = intent.getBooleanExtra(EXTRA_FORCE_COMPLETE, false)
        if (forceComplete) {
            banner.visibility = android.view.View.VISIBLE
            backLbl.visibility = android.view.View.GONE
            cancelBtn.isEnabled = false
            cancelBtn.alpha = 0.4f
        }

        loadBusiness()
    }

    private fun bindViews() {
        banner = findViewById(R.id.edit_business_LBL_complete_banner)
        backLbl = findViewById(R.id.edit_LBL_back)
        logoIv  = findViewById(R.id.edit_business_IMG_logo)
        changeLogoLbl = findViewById(R.id.edit_business_LBL_change_photo)

        nameEt = findViewById(R.id.signup_EDT_business_name)
        countryTv = findViewById(R.id.signup_TXT_country)
        addressEt = findViewById(R.id.address)
        emailEt   = findViewById(R.id.signup_business_EDT_email)
        ccBizTv   = findViewById(R.id.signup_business_TXT_country_code)
        phoneBizEt= findViewById(R.id.signup_business_TXT_phone)
        ccOwnerTv = findViewById(R.id.signup_owner_TXT_country_code)
        phoneOwnerEt = findViewById(R.id.signup_owner_TXT_phone)

        cancelBtn = findViewById(R.id.edit_BTN_cancel)
        saveBtn   = findViewById(R.id.edit_BTN_save)

        backLbl.setOnClickListener { finish() }

        // phone numeric
        phoneBizEt.inputType   = InputType.TYPE_CLASS_NUMBER
        phoneOwnerEt.inputType = InputType.TYPE_CLASS_NUMBER
        phoneBizEt.filters   = arrayOf(InputFilter.LengthFilter(15))
        phoneOwnerEt.filters = arrayOf(InputFilter.LengthFilter(15))

        emailEt.isEnabled = false
    }

    private fun wireActions() {
        changeLogoLbl.setOnClickListener { checkImagePermissionsAndSelect() }
        cancelBtn.setOnClickListener { finish() }
        saveBtn.setOnClickListener { onSave() }

        val countries = resources.getStringArray(R.array.countries).toList()
        countryTv.setOnClickListener { showCountryBottomSheet(countries) }

        val codes = resources.getStringArray(R.array.country_codes).toList()
        ccBizTv.setOnClickListener { showCodeBottomSheet(ccBizTv, phoneBizEt, codes) }
        ccOwnerTv.setOnClickListener { showCodeBottomSheet(ccOwnerTv, phoneOwnerEt, codes) }
    }

    private fun wirePickers() { /* launchers already initialized */ }

    private fun loadBusiness() {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Constants.BUSINESS_DB_NAME).document(uid).get()
            .addOnSuccessListener { d ->
                val doc = d.toObject(BusinessDoc::class.java) ?: BusinessDoc()
                val logo = doc.imageUrl ?: doc.imageUri
                if (!logo.isNullOrBlank()) {
                    Glide.with(this).load(logo)
                        .circleCrop()
                        .placeholder(R.drawable.ico_busines)
                        .error(R.drawable.ico_busines)
                        .into(logoIv)
                } else {
                    logoIv.setImageResource(R.drawable.ico_busines)
                }
                nameEt.setText(doc.businessName.orEmpty())
                countryTv.text = doc.country ?: "Country"
                addressEt.setText(doc.address.orEmpty())
                emailEt.setText(doc.email.orEmpty())
                ccBizTv.text = doc.businessCountryCode ?: "Business country code"
                phoneBizEt.setText(doc.businessPhone.orEmpty())
                ccOwnerTv.text = doc.ownerCountryCode ?: "Owner country code"
                phoneOwnerEt.setText(doc.ownerPhone.orEmpty())
            }
    }

    private fun onSave() {
        if (!validateForm()) return
        val uid = auth.currentUser?.uid ?: return

        val updates = mutableMapOf<String, Any>(
            "businessName" to nameEt.text.toString().trim(),
            "country" to countryTv.text.toString().trim(),
            "address" to addressEt.text.toString().trim(),
            "businessCountryCode" to ccBizTv.text.toString().trim(),
            "businessPhone" to phoneBizEt.text.toString().trim(),
            "ownerCountryCode" to ccOwnerTv.text.toString().trim(),
            "ownerPhone" to phoneOwnerEt.text.toString().trim(),
            "updatedAt" to System.currentTimeMillis()
        )

        fun finishAndReturn() {
            setResult(RESULT_OK)
            finish()
        }

        val storage = com.google.firebase.Firebase.storage
        val imageUri = selectedImageUri
        if (imageUri != null) {
            val path = "${Constants.BUSINESS_FOLDER_NAME}/${uid}.jpg"
            val ref = storage.reference.child(path)
            ref.putFile(imageUri)
                .addOnSuccessListener { ref.downloadUrl.addOnSuccessListener { url ->
                    updates["imageUrl"] = url.toString()
                    db.collection(Constants.BUSINESS_DB_NAME).document(uid)
                        .set(updates, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener { finishAndReturn() }
                        .addOnFailureListener { toast("Failed to save: ${it.message}") }
                } }
                .addOnFailureListener { toast("Failed to upload logo: ${it.message}") }
        } else {
            db.collection(Constants.BUSINESS_DB_NAME).document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { finishAndReturn() }
                .addOnFailureListener { toast("Failed to save: ${it.message}") }
        }
    }

    private fun validateForm(): Boolean {
        var ok = true
        fun markErr(v: android.view.View, msg: String) {
            if (v is EditText) v.error = msg else toast(msg)
            ok = false
        }

        val name = nameEt.text.toString().trim()
        val country = countryTv.text.toString().trim()
        val addr = addressEt.text.toString().trim()
        val ccBiz = ccBizTv.text.toString().trim()
        val telBiz= phoneBizEt.text.toString().trim()
        val ccOwn = ccOwnerTv.text.toString().trim()
        val telOwn= phoneOwnerEt.text.toString().trim()

        if (name.isEmpty()) markErr(nameEt, "Business name is required")
        if (country == "Country") { markErr(countryTv, "Please select country") }
        if (addr.isEmpty()) markErr(addressEt, "Address is required")
        if (ccBiz == "Business country code") markErr(ccBizTv, "Select business country code")
        if (!telBiz.matches(Regex("^[0-9]{5,15}$"))) markErr(phoneBizEt, "Enter valid phone")
        if (ccOwn == "Owner country code") markErr(ccOwnerTv, "Select owner country code")
        if (!telOwn.matches(Regex("^[0-9]{5,15}$"))) markErr(phoneOwnerEt, "Enter valid owner phone")

        // לוגו חובה אם במצב Force
        if (forceComplete) {
            val hasLogo = selectedImageUri != null || (logoIv.drawable != null)
            if (!hasLogo) { toast("Please set a logo"); ok = false }
        }
        return ok
    }

    private fun showCountryBottomSheet(countries: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_bottom_sheet_list, null)
        val listView = view.findViewById<ListView>(R.id.dialog_list_view)
        listView.adapter = ArrayAdapter(this, R.layout.item_prefix, countries)
        listView.setOnItemClickListener { _, _, pos, _ ->
            countryTv.text = countries[pos]
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showCodeBottomSheet(target: TextView, phone: EditText, codes: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_bottom_sheet_list, null)
        val listView = view.findViewById<ListView>(R.id.dialog_list_view)
        listView.adapter = ArrayAdapter(this, R.layout.item_prefix, codes)
        listView.setOnItemClickListener { _, _, pos, _ ->
            val selected = codes[pos]
            target.text = selected
            phone.setText("")
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- image permissions ----------
    private fun checkImagePermissionsAndSelect() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.CAMERA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                need += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                need += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), imagePermRequestCode)
        } else {
            showImageChooser()
        }
    }

    @SuppressLint("InlinedApi")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == imagePermRequestCode) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showImageChooser()
            } else {
                toast("Permissions required to select image")
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun showImageChooser() {
        val options = arrayOf("Camera", "Gallery")
        AlertDialog.Builder(this)
            .setTitle("Choose image source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }.show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        galleryLauncher.launch(intent)
    }

    private fun openCamera() {
        val photoFile = File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "biz_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(
            this, "${applicationContext.packageName}.fileprovider", photoFile
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        cameraLauncher.launch(intent)
    }
}

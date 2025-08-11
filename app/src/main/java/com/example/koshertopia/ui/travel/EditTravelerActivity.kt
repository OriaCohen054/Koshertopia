package com.example.koshertopia.ui.travel
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.koshertopia.BaseActivity
import com.example.koshertopia.R
import com.example.koshertopia.util.Constants
import com.example.koshertopia.util.FieldErrorUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.storage
import java.io.File
import java.io.FileOutputStream

class EditTravelerActivity : BaseActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val uid: String get() = auth.currentUser?.uid ?: ""

    // Views
    private lateinit var backLbl: TextView
    private lateinit var banner: TextView
    private lateinit var photoIv: ImageView
    private lateinit var changePhotoLbl: TextView

    private lateinit var firstNameEt: EditText
    private lateinit var lastNameEt: EditText
    private lateinit var emailEt: EditText // read-only
    private lateinit var ccTv: TextView
    private lateinit var phoneEt: EditText
    private lateinit var genderGroup: RadioGroup

    private lateinit var cancelBtn: Button
    private lateinit var saveBtn: Button

    // Image picking
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private var cameraImageUri: Uri? = null
    private var selectedImageUri: Uri? = null
    private var isImageSelected: Boolean = false

    // Data
    data class TravelerDoc(
        val uid: String? = null,
        val firstName: String? = null,
        val lastName: String? = null,
        val email: String? = null,
        val countryCode: String? = null,
        val phoneNumber: String? = null,   // <-- תואם למסמך שקיים
        val gender: String? = null,
        val imageUrl: String? = null,      // <-- תואם למסמך שקיים
        val photoUrl: String? = null,      // <-- תמיכה לאחור (Google)
        val updatedAt: Long? = null
    )
    private var original = TravelerDoc()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_traveler)

        bindViews()
        wireImagePickers()
        wireCountryCodePicker()
        wireValidation()
        wireActions()

        if (uid.isEmpty()) {
            toast("Not logged in")
            finish()
            return
        }
        loadProfile()
    }

    private fun bindViews() {
        backLbl = findViewById(R.id.edit_LBL_back)
        banner = findViewById(R.id.edit_LBL_complete_banner)
        photoIv = findViewById(R.id.edit_IMG_photo)
        changePhotoLbl = findViewById(R.id.edit_LBL_change_photo)

        firstNameEt = findViewById(R.id.edit_EDT_first_name_en)
        lastNameEt = findViewById(R.id.edit_EDT_last_name_en)
        emailEt = findViewById(R.id.edit_EDT_email)
        ccTv = findViewById(R.id.edit_TXT_country_code)
        phoneEt = findViewById(R.id.edit_EDT_phone)
        genderGroup = findViewById(R.id.edit_RG_gender)

        cancelBtn = findViewById(R.id.edit_BTN_cancel)
        saveBtn = findViewById(R.id.edit_BTN_save)

        backLbl.setOnClickListener { finish() }
        changePhotoLbl.setOnClickListener { checkImagePermissionsAndShowDialog() }

        // טלפון – ספרות בלבד
        phoneEt.inputType = InputType.TYPE_CLASS_NUMBER
        phoneEt.filters = arrayOf(InputFilter { src, _, _, _, _, _ -> if (src.matches(Regex("[0-9]+"))) src else "" })

        genderGroup.setOnCheckedChangeListener { _, _ ->
            genderGroup.setBackgroundResource(android.R.color.transparent)
        }
    }

    private fun wireActions() {
        cancelBtn.setOnClickListener { finish() }
        saveBtn.setOnClickListener { onSave() }
    }

    private fun wireValidation() {
        addRealtimeValidation(firstNameEt)
        addRealtimeValidation(lastNameEt)
        addRealtimeValidation(phoneEt)
        // Email read-only – לא צריך
    }

    private fun addRealtimeValidation(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                FieldErrorUtil.clearError(editText, this@EditTravelerActivity)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    private fun wireImagePickers() {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val uri = result.data?.data
                if (uri != null) {
                    photoIv.setImageURI(uri)
                    selectedImageUri = uri
                    isImageSelected = true
                    photoIv.setBackgroundResource(R.drawable.input_background)
                }
            }
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && cameraImageUri != null) {
                photoIv.setImageURI(cameraImageUri)
                selectedImageUri = cameraImageUri
                isImageSelected = true
                photoIv.setBackgroundResource(R.drawable.input_background)
            } else {
                toast("Failed to capture image")
            }
        }
    }

    private fun wireCountryCodePicker() {
        val countryCodes = resources.getStringArray(R.array.country_codes).toList()
        ccTv.setOnClickListener { showCountryCodeBottomSheet(countryCodes) }
    }

    private fun loadProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return finish()
        FirebaseFirestore.getInstance()
            .collection(Constants.TRAVELER_DB_NAME)
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                original = doc.toObject(TravelerDoc::class.java) ?: TravelerDoc()

                val img = original.imageUrl ?: original.photoUrl // תמיכה לאחור
                val updatedAt = doc.getLong("updatedAt") ?: 0L

                // טעינת תמונה קיימת אם יש
                if (!img.isNullOrBlank()) {
                    Glide.with(this)
                        .load(img)
                        .circleCrop()
                        .signature(com.bumptech.glide.signature.ObjectKey(updatedAt)) // לרענון קאש
                        .placeholder(R.drawable.ico_traveler)
                        .error(R.drawable.ico_traveler)
                        .into(photoIv)
                } else {
                    photoIv.setImageResource(R.drawable.ico_traveler)
                }

                firstNameEt.setText(original.firstName.orEmpty())
                lastNameEt.setText(original.lastName.orEmpty())
                emailEt.setText(original.email.orEmpty()) // read-only
                ccTv.text = original.countryCode ?: "Country code"
                phoneEt.setText(original.phoneNumber.orEmpty())

                when (original.gender) {
                    getString(R.string.male) -> genderGroup.check(R.id.edit_RB_male)
                    getString(R.string.female) -> genderGroup.check(R.id.edit_RB_female)
                    getString(R.string.other) -> genderGroup.check(R.id.edit_RB_other)
                }

                // אם חסרים שדות חובה – הצגת באנר
                val needsCompletion = original.phoneNumber.isNullOrBlank()
                        || original.countryCode.isNullOrBlank()
                        || original.gender.isNullOrBlank()
                banner.visibility = if (needsCompletion) android.view.View.VISIBLE else android.view.View.GONE
            }
            .addOnFailureListener { e -> toast("Failed to load: ${e.message}") }
    }
    private fun onSave() {
        val errors = validateForm()
        if (errors.isNotEmpty()) { showErrors(errors); return }

        val updates = mutableMapOf<String, Any>()
        fun putIfChanged(k: String, new: String?, old: String?) {
            val n = new ?: ""; val o = old ?: ""
            if (n != o) updates[k] = n
        }

        putIfChanged("firstName",   firstNameEt.text.toString().trim(), original.firstName)
        putIfChanged("lastName",    lastNameEt.text.toString().trim(),  original.lastName)
        putIfChanged("countryCode", ccTv.text.toString().trim(),        original.countryCode)
        putIfChanged("phoneNumber", phoneEt.text.toString().trim(),     original.phoneNumber)
        putIfChanged("gender",      selectedGender(),                   original.gender)

        // תמונה – נשמור תמיד תחת imageUrl (גם אם פעם נשמר photoUrl)
        if (selectedImageUri != null) {
            uploadImageToFirebase(selectedImageUri!!, uid, Constants.TRAVELER_FOLDER_NAME) { url ->
                url?.let { if (it != (original.imageUrl ?: original.photoUrl)) updates["imageUrl"] = it }
                commitUpdates(updates)
            }
        } else {
            commitUpdates(updates)
        }
    }

    private fun commitUpdates(updates: MutableMap<String, Any>) {
        if (updates.isEmpty()) { toast("No changes"); return }
        updates["updatedAt"] = System.currentTimeMillis()
        FirebaseFirestore.getInstance()
            .collection(Constants.TRAVELER_DB_NAME)
            .document(uid)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { toast("Saved"); finish() }
            .addOnFailureListener { e -> toast("Failed: ${e.message}") }
    }

    private fun validateForm(): Map<android.view.View, String> {
        val map = mutableMapOf<android.view.View, String>()
        FieldErrorUtil.clearError(firstNameEt, this)
        FieldErrorUtil.clearError(lastNameEt, this)
        FieldErrorUtil.clearError(phoneEt, this)

        val first = firstNameEt.text.toString().trim()
        val last  = lastNameEt.text.toString().trim()
        val cc    = ccTv.text.toString().trim()
        val phone = phoneEt.text.toString().trim()

        if (first.isEmpty() || !first.matches(Regex("^[A-Za-z ]+$"))) map[firstNameEt] = "Only English letters are allowed"
        if (last.isEmpty()  || !last.matches(Regex("^[A-Za-z ]+$")))  map[lastNameEt]  = "Only English letters are allowed"

        if (cc == "Country code") {
            ccTv.setBackgroundResource(R.drawable.input_background_error)
            map[ccTv] = "Please select a country code"
        } else {
            ccTv.setBackgroundResource(R.drawable.input_background)
            val numeric = cc.split(" ")[0].removePrefix("+")
            val lenRes = resources.getIdentifier("phone_length_$numeric", "integer", packageName)
            if (lenRes == 0) {
                map[phoneEt] = "Select valid country code first"
            } else {
                val expected = resources.getInteger(lenRes)
                if (!phone.matches(Regex("^[0-9]+$")) || phone.length != expected) {
                    map[phoneEt] = "Phone number must be $expected digits"
                }
            }
        }
        return map
    }

    private fun showErrors(map: Map<android.view.View, String>) {
        map.forEach { (view, msg) ->
            when (view) {
                is EditText -> {
                    view.error = msg
                    FieldErrorUtil.markError(view, this)
                    if (view.text.isNullOrEmpty()) view.requestFocus()
                }
                is TextView -> {
                    toast(msg)
                }
                else -> toast(msg)
            }
        }
    }

    private fun selectedGender(): String =
        findViewById<RadioButton>(genderGroup.checkedRadioButtonId)?.text?.toString() ?: ""

    private fun isEnglishOnly(text: String) = text.matches(Regex("^[A-Za-z ]+$"))

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ----------------- Country code picker -----------------
    private fun showCountryCodeBottomSheet(codes: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_bottom_sheet_list, null)
        val listView = view.findViewById<ListView>(R.id.dialog_list_view)

        val adapter = ArrayAdapter(this, R.layout.item_prefix, codes)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = codes[position]
            ccTv.text = selected
            ccTv.setBackgroundResource(R.drawable.input_background)

            // איפוס טלפון
            phoneEt.text.clear()
            phoneEt.isEnabled = true

            // התאמת אורך לפי קידומת
            val numericCode = selected.split(" ")[0].substring(1) // לדוגמה 972
            val resId = resources.getIdentifier("phone_length_$numericCode", "integer", packageName)
            if (resId != 0) {
                val maxLength = resources.getInteger(resId)
                phoneEt.filters = arrayOf(
                    InputFilter.LengthFilter(maxLength),
                    InputFilter { src, _, _, _, _, _ -> if (src.matches(Regex("[0-9]+"))) src else "" }
                )
            }
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // ----------------- Image picking & permissions -----------------
    @SuppressLint("InlinedApi")
    private fun checkImagePermissionsAndShowDialog() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 1000)
        } else {
            showImageSourceDialog()
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Camera", "Gallery")
        AlertDialog.Builder(this)
            .setTitle("Choose image source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryIntent.type = "image/*"
        imagePickerLauncher.launch(galleryIntent)
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun openCamera() {
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_photo_edit.jpg")
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (intent.resolveActivity(packageManager) != null) {
            cameraLauncher.launch(intent)
        } else {
            toast("No camera app found")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 1000) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showImageSourceDialog()
            } else {
                val permanentlyDenied = permissions.indices.any { index ->
                    grantResults[index] == PackageManager.PERMISSION_DENIED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[index])
                }

                if (permanentlyDenied) {
                    showPermissionsSettingsDialog()
                } else {
                    toast("Permissions required to continue")
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun showPermissionsSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission required")
            .setMessage("You have denied some permissions permanently. Please enable them in settings to continue.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ----------------- Upload to Firebase Storage -----------------
    private fun uploadImageToFirebase(imageUri: Uri, uid: String, folderName: String, onComplete: (String?) -> Unit) {
        val fileName = "$folderName/$uid.jpg"
        val imageRef = Firebase.storage.reference.child(fileName)

        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val tempFile = File.createTempFile("upload_edit", ".jpg", cacheDir)
            val outputStream = FileOutputStream(tempFile)

            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            val tempFileUri = Uri.fromFile(tempFile)

            imageRef.putFile(tempFileUri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                        onComplete(uri.toString())
                        tempFile.delete()
                    }.addOnFailureListener {
                        onComplete(null); tempFile.delete()
                    }
                }
                .addOnFailureListener {
                    onComplete(null); tempFile.delete()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        }
    }
}

package com.example.koshertopia

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
import android.text.TextWatcher
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import com.example.koshertopia.util.Constants
import com.example.koshertopia.util.FieldErrorUtil
import com.example.koshertopia.util.LoginEnum
import com.example.koshertopia.util.UserRepo
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.io.File
import java.io.FileOutputStream

class CreateBusinessAccountActivity : BaseActivity() {

    private lateinit var businessNameEditText: EditText

    private lateinit var countryCodeTextView: TextView
    private lateinit var addressEditText: EditText

    private lateinit var emailEditText: EditText

    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText

    private lateinit var businessCountryCodeTextView: TextView
    private lateinit var businessPhoneEditText: EditText

    private lateinit var ownerCountryCodeTextView: TextView
    private lateinit var ownerPhoneEditText: EditText

    private lateinit var photoImageView: ImageView
    private lateinit var addPhotoTextView: TextView

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val RC_SIGN_IN = 1001

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private var isImageSelected: Boolean = false
    private var cameraImageUri: Uri? = null
    private var selectedImageUri: Uri? = null
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_business_account)

        initViews()
        initFirebase()
        setupGoogleSignIn()
        setListeners()
    }

    private fun initViews() {
        businessNameEditText = findViewById(R.id.signup_EDT_business_name)
        addressEditText = findViewById(R.id.address)
        countryCodeTextView = findViewById(R.id.signup_TXT_country)
        emailEditText = findViewById(R.id.signup_business_EDT_email)
        passwordEditText = findViewById(R.id.signup_business_EDT_password)
        confirmPasswordEditText = findViewById(R.id.signup_business_EDT_confirm)
        businessPhoneEditText = findViewById(R.id.signup_business_TXT_phone)
        businessCountryCodeTextView = findViewById(R.id.signup_business_TXT_country_code)
        ownerCountryCodeTextView = findViewById(R.id.signup_owner_TXT_country_code)
        ownerPhoneEditText = findViewById(R.id.signup_owner_TXT_phone)
        photoImageView = findViewById(R.id.business_Logo)
        addPhotoTextView=findViewById(R.id.signup_business_LBL_add_photo)

        addressEditText.isEnabled = false
        ownerPhoneEditText.isEnabled = false
        businessPhoneEditText.isEnabled = false

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageUri: Uri? = result.data?.data
                if (imageUri != null) {
                    photoImageView.setImageURI(imageUri)
                    isImageSelected=true
                    selectedImageUri = imageUri
                    photoImageView.setBackgroundResource(R.drawable.input_background)
                }
            }
        }

        // register camera launcher
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && cameraImageUri != null) {
                photoImageView.setImageURI(cameraImageUri)
                isImageSelected=true
                selectedImageUri = cameraImageUri
                photoImageView.setBackgroundResource(R.drawable.input_background)
            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }



    }

    private fun initFirebase() {
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        
        val googleButton = findViewById<Button>(R.id.signup_BTN_google)
        googleButton.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                signInWithGoogle()
            }
        }
    }
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    addBusinesToBusinessDb()


                } else {
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    @OptIn(UnstableApi::class)
    private fun addBusinesToBusinessDb() {
        val current = FirebaseAuth.getInstance().currentUser ?: return
        val uid = current.uid
        val db =FirebaseFirestore.getInstance()

        val account = GoogleSignIn.getLastSignedInAccount(this)
        val email = account?.email?.trim()?.lowercase() ?: run {
          Toast.makeText(this, "Google account has no email", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val businessData = mapOf(
            "uid" to uid,
            "businessName" to (account?.givenName ?: ""),
            "email"        to email,
            "imageUri"     to (account?.photoUrl?.toString())
        )

        UserRepo.ensureUniqueAndCreate(
            db = db,
            selectedCollection = Constants.BUSINESS_DB_NAME,    // "Business"
            otherCollection = Constants.TRAVELER_DB_NAME,       // "Travelers"
            emailRaw = email,
            uid = uid,
            data = businessData,
            onAlreadyInSelected = {
               Toast.makeText(this, "You already signed up as Business. Please log in.",Toast.LENGTH_LONG).show()
            },
            onExistsInOther = {
                android.widget.Toast.makeText(this, "This Google account is registered as Traveler. Please choose Traveler.", android.widget.Toast.LENGTH_LONG).show()
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            },
            onCreated = {
                Toast.makeText(this, "Business created successfully", Toast.LENGTH_SHORT).show()
                val selectedCountry = countryCodeTextView.text.toString()             // "Israel"
                val selectedCountryCode = extractDialCode(businessCountryCodeTextView.text.toString())
                val currencySymbol = currencyForCountry(selectedCountry)

                startActivity(
                    Intent(this, SelectBusinessTypeActivity::class.java)
                        .putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.BUSINESS.name)
                        .putExtra(Constants.EXTRA_SELECTED_COUNTRY, selectedCountry)
                        .putExtra(Constants.EXTRA_SELECTED_COUNTRY_CODE, selectedCountryCode)
                        .putExtra(Constants.EXTRA_CURRENCY_SYMBOL, currencySymbol)
                )
                finish()
            },
            onError = {
                android.widget.Toast.makeText(this, "Failed to save business: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }
    private fun currencyForCountry(country: String): String = when (country) {
        "Israel" -> "₪"
        "United States" -> "$"
        "United Kingdom" -> "£"
        // אירופה נפוצות:
        "France", "Germany", "Italy", "Spain", "Portugal", "Netherlands", "Greece" -> "€"
        else -> "$"
    }

    private fun extractDialCode(text: String): String {
        // מקבל משהו בסגנון: "+972 (Israel)" → מחזיר "972"
        return text.substringAfter("+").takeWhile { it.isDigit() }
    }

    private fun setListeners() {
        addPhotoTextView.setOnClickListener {
            checkImagePermissionsAndShowDialog()
        }

        val countryCodes = resources.getStringArray(R.array.countries).toList()
        countryCodeTextView.setOnClickListener {
            showCountryCodeBottomSheet(countryCodes)
        }
        val countryCodes1 = resources.getStringArray(R.array.country_codes).toList()
        ownerCountryCodeTextView.setOnClickListener {
            showOwnerCountryCodeBottomSheet(countryCodes1)
        }
        businessCountryCodeTextView.setOnClickListener {
            showBusinessCountryCodeBottomSheet(countryCodes1)
        }

        findViewById<Button>(R.id.signup_BTN_continue).setOnClickListener {
            if (isValidForm()) {
                createBusinessAccount(
                    businessName = businessNameEditText.text.toString().trim(),
                    address = addressEditText.text.toString().trim(),
                    email = emailEditText.text.toString().trim(),
                    countryCode = countryCodeTextView.text.toString().trim(),
                    businessPhone = businessPhoneEditText.text.toString().trim(),
                    businessCountryCode = businessCountryCodeTextView.text.toString().trim(),
                    ownerCountryCode = ownerCountryCodeTextView.text.toString().trim(),
                    ownerPhone = ownerPhoneEditText.text.toString().trim(),
                    password = passwordEditText.text.toString(),
                    imageUri = selectedImageUri)
            }
        }

        val userTypeString = intent.getStringExtra(Constants.EXTRA_USER_TYPE)
        val userType = LoginEnum.valueOf(userTypeString ?: LoginEnum.BUSINESS.name)
        val backBtn: TextView = findViewById(R.id.lBL_back)
        backBtn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra(Constants.EXTRA_USER_TYPE, userType.name)
            startActivity(intent)
            finish()
        }

        addRealtimeValidation(businessNameEditText)
        addRealtimeValidation(addressEditText)
        addRealtimeValidation(emailEditText)
        addRealtimeValidation(businessPhoneEditText)
        addRealtimeValidation(ownerPhoneEditText)
        addRealtimeValidation(passwordEditText)
        addRealtimeValidation(confirmPasswordEditText)
    }

    private fun createBusinessAccount(
        businessName: String,
        address: String,
        email: String,
        countryCode: String,
        businessPhone: String,
        businessCountryCode: String,
        ownerCountryCode: String,
        ownerPhone: String,
        password: String,
        imageUri: Uri?
    ) {
        val emailLower = email.trim().lowercase()
        val db = FirebaseFirestore.getInstance()

        UserRepo.ensureEmailUniqueBeforeAuth(
            db = db,
            selectedCollection = Constants.BUSINESS_DB_NAME,   // "Business"
            otherCollection = Constants.TRAVELER_DB_NAME,      // "Travelers"
            emailRaw = emailLower,
            onUnique = {
                FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(emailLower, password)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: return@addOnSuccessListener
                        val save = { imageUrl: String? ->
                            saveUserToFirestore(uid, businessName, address, emailLower, countryCode, businessPhone, businessCountryCode, ownerCountryCode, ownerPhone, imageUrl)
                        }
                        if (imageUri != null) {
                            uploadImageToFirebase(imageUri, uid, Constants.BUSINESS_FOLDER_NAME) { url -> save(url) }
                        } else {
                            save(null)
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Registration failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            },
            onExistsInSelected = {
                Toast.makeText(this, "This email is already registered as Business. Please log in.", Toast.LENGTH_LONG).show()
            },
            onExistsInOther = {
                Toast.makeText(this, "This email is registered as Traveler. Please choose Traveler.", Toast.LENGTH_LONG).show()
            },
            onError = { e ->
                Toast.makeText(this, "Sign-up check failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }


    private fun saveUserToFirestore(
        uid: String,
        businessName: String,
        address: String,
        email: String,
        countryCode: String,
        businessPhone: String,
        businessCountryCode: String,
        ownerCountryCode: String,
        ownerPhone: String,
        imageUrl: String?
    ) {
        val user = hashMapOf(
            "uid" to uid,
            "businessName" to businessName,
            "address" to address,
            "email" to email,
            "countryCode" to countryCode,
            "businessPhone" to businessPhone,
            "businessCountryCode" to businessCountryCode,
            "ownerCountryCode" to ownerCountryCode,
            "ownerPhone" to ownerPhone,
            "imageUrl" to imageUrl
        )

        Firebase.firestore.collection(Constants.BUSINESS_DB_NAME)
            .document(uid)
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Account created successfully", Toast.LENGTH_LONG).show()

                val selectedCountry = countryCodeTextView.text.toString()             // "Israel"
                val selectedCountryCode = extractDialCode(businessCountryCodeTextView.text.toString())
                val currencySymbol = currencyForCountry(selectedCountry)

                startActivity(
                    Intent(this, SelectBusinessTypeActivity::class.java)
                        .putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.BUSINESS.name)
                        .putExtra(Constants.EXTRA_SELECTED_COUNTRY, selectedCountry)
                        .putExtra(Constants.EXTRA_SELECTED_COUNTRY_CODE, selectedCountryCode)
                        .putExtra(Constants.EXTRA_CURRENCY_SYMBOL, currencySymbol)
                )
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save business data: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }


    fun uploadImageToFirebase(imageUri: Uri, uid: String, folderName:String,onComplete: (String?) -> Unit) {
        val fileName = "$folderName/$uid.jpg"
        val imageRef = Firebase.storage.reference.child(fileName)

        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val tempFile = File.createTempFile("upload", ".jpg", cacheDir)
            val outputStream = FileOutputStream(tempFile)

            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            val tempFileUri = Uri.fromFile(tempFile)

            imageRef.putFile(tempFileUri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                        onComplete(uri.toString())
                        tempFile.delete() // ניקוי הקובץ הזמני
                    }.addOnFailureListener {
                        onComplete(null)
                        tempFile.delete()
                    }
                }
                .addOnFailureListener {
                    onComplete(null)
                    tempFile.delete()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        }
    }
    private fun addRealtimeValidation(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                FieldErrorUtil.clearError(editText, this@CreateBusinessAccountActivity)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
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
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_photo.jpg")

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
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1000) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showImageSourceDialog()
            } else {
                val permanentlyDenied = permissions.anyIndexed { index, permission ->
                    grantResults[index] == PackageManager.PERMISSION_DENIED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
                }
                if (permanentlyDenied) {
                    showPermissionsSettingsDialog()
                } else {
                    Toast.makeText(this, "Permissions required to continue", Toast.LENGTH_SHORT).show()
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

    private fun showCountryCodeBottomSheet(codes: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_bottom_sheet_list, null)
        val listView = view.findViewById<ListView>(R.id.dialog_list_view)

        val adapter = ArrayAdapter(this, R.layout.item_prefix, codes)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = codes[position]
            countryCodeTextView.text = selected
            countryCodeTextView.setBackgroundResource(R.drawable.input_background)

            // שחרור שדה כתובת
            addressEditText.isEnabled = true
            FieldErrorUtil.clearError(addressEditText, this)

            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showOwnerCountryCodeBottomSheet(codes: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_bottom_sheet_list, null)
        val listView = view.findViewById<ListView>(R.id.dialog_list_view)

        val adapter = ArrayAdapter(this, R.layout.item_prefix, codes)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = codes[position]
            ownerCountryCodeTextView.text = selected
            ownerCountryCodeTextView.setBackgroundResource(R.drawable.input_background)

            ownerPhoneEditText.text.clear()
            ownerPhoneEditText.isEnabled = true

            // אורך מספר לפי מדינה
            val numericCode = selected.split(" ")[0].substring(1)
            val resId = resources.getIdentifier("phone_length_$numericCode", "integer", packageName)
            if (resId != 0) {
                val maxLength = resources.getInteger(resId)
                ownerPhoneEditText.filters = arrayOf(
                    android.text.InputFilter.LengthFilter(maxLength),
                    android.text.InputFilter { source, _, _, _, _, _ ->
                        if (source.matches(Regex("[0-9]+"))) source else ""
                    }
                )
            }

            FieldErrorUtil.clearError(ownerPhoneEditText, this)

            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }
    private fun showBusinessCountryCodeBottomSheet(codes: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_bottom_sheet_list, null)
        val listView = view.findViewById<ListView>(R.id.dialog_list_view)

        val adapter = ArrayAdapter(this, R.layout.item_prefix, codes)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = codes[position]
            businessCountryCodeTextView.text = selected
            businessCountryCodeTextView.setBackgroundResource(R.drawable.input_background)

            businessPhoneEditText.text.clear()
            businessPhoneEditText.isEnabled = true

            val numericCode = selected.split(" ")[0].substring(1)
            val resId = resources.getIdentifier("phone_length_$numericCode", "integer", packageName)
            if (resId != 0) {
                val maxLength = resources.getInteger(resId)
                businessPhoneEditText.filters = arrayOf(
                    android.text.InputFilter.LengthFilter(maxLength),
                    android.text.InputFilter { source, _, _, _, _, _ ->
                        if (source.matches(Regex("[0-9]+"))) source else ""
                    }
                )
            }

            FieldErrorUtil.clearError(businessPhoneEditText, this)

            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }
    private fun isValidForm(): Boolean {
        var isValid = true

        val businessName = businessNameEditText.text.toString().trim()
        val address = addressEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val countryCode = countryCodeTextView.text.toString().trim()

        val businessPhone = businessPhoneEditText.text.toString().trim()
        val businessCountryCode = businessCountryCodeTextView.text.toString().trim()
        val ownerCountryCode = ownerCountryCodeTextView.text.toString().trim()
        val ownerPhone = ownerPhoneEditText.text.toString().trim()

        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        // ניקוי שגיאות
        FieldErrorUtil.clearError(businessNameEditText, this)
        FieldErrorUtil.clearError(addressEditText, this)
        FieldErrorUtil.clearError(emailEditText, this)
        FieldErrorUtil.clearError(businessPhoneEditText, this)
        FieldErrorUtil.clearError(ownerPhoneEditText, this)
        FieldErrorUtil.clearError(passwordEditText, this)
        FieldErrorUtil.clearError(confirmPasswordEditText, this)

        businessCountryCodeTextView.setBackgroundResource(R.drawable.input_background)
        ownerCountryCodeTextView.setBackgroundResource(R.drawable.input_background)

        // שם עסק
        if (businessName.isEmpty()) {
            businessNameEditText.error = "Business name is required"
            FieldErrorUtil.markError(businessNameEditText, this)
            isValid = false
        }

        // כתובת
        if (address.isEmpty()) {
            addressEditText.error = "Address is required"
            FieldErrorUtil.markError(addressEditText, this)
            isValid = false
        }

        // אימייל
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Enter a valid email address"
            FieldErrorUtil.markError(emailEditText, this)
            isValid = false
        }

        // מדינה ראשית (כתובת)
        if (countryCode == "Country") {
            countryCodeTextView.setBackgroundResource(R.drawable.input_background_error)
            isValid = false
        } else {
            countryCodeTextView.setBackgroundResource(R.drawable.input_background)
        }

        // ✅ בדיקת קידומת עסק
        if (businessCountryCode == "Business country code") {
            businessCountryCodeTextView.setBackgroundResource(R.drawable.input_background_error)
            FieldErrorUtil.markError(businessPhoneEditText, this)
            isValid = false
        } else {
            businessCountryCodeTextView.setBackgroundResource(R.drawable.input_background)
            if (!validatePhoneByCountryCode(businessPhone, businessCountryCode, businessPhoneEditText)) {
                businessPhoneEditText.setBackgroundResource(R.drawable.input_background_error)
                isValid = false
            } else {
                businessPhoneEditText.setBackgroundResource(R.drawable.input_background)
            }
        }

        // ✅ בדיקת קידומת בעלים
        if (ownerCountryCode == "Owner country code") {
            ownerCountryCodeTextView.setBackgroundResource(R.drawable.input_background_error)
            FieldErrorUtil.markError(ownerPhoneEditText, this)
            isValid = false
        } else {
            ownerCountryCodeTextView.setBackgroundResource(R.drawable.input_background)
            if (!validatePhoneByCountryCode(ownerPhone, ownerCountryCode, ownerPhoneEditText)) {
                ownerPhoneEditText.setBackgroundResource(R.drawable.input_background_error)
                isValid = false
            } else {
                ownerPhoneEditText.setBackgroundResource(R.drawable.input_background)
            }
        }

        // לוגו
        if (isImageSelected) {
            val drawable = photoImageView.drawable
            val defaultDrawable = ContextCompat.getDrawable(this, R.drawable.ico_busines)
            if (drawable == null || (defaultDrawable != null && drawable.constantState == defaultDrawable.constantState)) {
                Toast.makeText(this, "Please upload a logo photo", Toast.LENGTH_SHORT).show()
                photoImageView.setBackgroundResource(R.drawable.photo_background_error)
                isValid = false
            } else {
                photoImageView.setBackgroundResource(R.drawable.input_background)
            }
        } else {
            photoImageView.setBackgroundResource(R.drawable.photo_background_error)
            Toast.makeText(this, "Please select a logo photo", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        // סיסמה
        val passwordLayout = findViewById<TextInputLayout>(R.id.signup_layout_password)
        val confirmPasswordLayout = findViewById<TextInputLayout>(R.id.signup_layout_confirm)

        val passwordRegex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}")

        if (!passwordRegex.matches(password)) {
            passwordLayout.error = "Password must have uppercase, lowercase, number, special char, and 8+ chars"
            isValid = false
        } else {
            passwordLayout.error = null
        }

        if (password != confirmPassword) {
            confirmPasswordLayout.error = "Passwords do not match"
            isValid = false
        } else {
            confirmPasswordLayout.error = null
        }

        return isValid
    }

    private fun validatePhoneByCountryCode(phone: String, countryCode: String, field: EditText): Boolean {
        val pureCountryCode = countryCode.split(" ")[0]
        val numericCode = pureCountryCode.substring(1)
        val resourceName = "phone_length_$numericCode"
        val resId = resources.getIdentifier(resourceName, "integer", packageName)

        if (resId == 0) {
            field.error = "Please select a valid country code"
            FieldErrorUtil.markError(field, this)
            return false
        }

        val expectedLength = resources.getInteger(resId)
        return when {
            phone.isEmpty() -> {
                field.error = "Phone number is required"
                FieldErrorUtil.markError(field, this)
                false
            }
            !phone.matches(Regex("^[0-9]+$")) -> {
                field.error = "Phone number must contain only digits"
                FieldErrorUtil.markError(field, this)
                false
            }
            phone.length != expectedLength -> {
                field.error = "Phone number must be $expectedLength digits"
                FieldErrorUtil.markError(field, this)
                false
            }
            else -> {
                FieldErrorUtil.clearError(field, this)
                true
            }
        }
    }

}

//data class Business(
//    val businessName: String = "",
//    val address: String = "",
//    val email: String = "",
//    val countryCode: String? = null,
//    val businessPhone: String? = null,
//    val businessCountryCode: String? = null,
//    val ownerPhone: String? = null,
//    val ownerCountryCode: String? = null,
//    val imageUri: String? = null
//)

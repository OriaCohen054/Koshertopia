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
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
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
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
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


class CreateTravelerAccountActivity : BaseActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private val RC_SIGN_IN = 1001

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var photoImageView: ImageView
    private var isImageSelected: Boolean = false
    private var cameraImageUri: Uri? = null
    private var selectedImageUri: Uri? = null
    


    private lateinit var countryCodeTextView: TextView

    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var genderRadioGroup: RadioGroup

    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_create_traveler_account)



        firebaseAuth = FirebaseAuth.getInstance()

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



        photoImageView = findViewById(R.id.photoImageView)

        val userTypeString = intent.getStringExtra(Constants.EXTRA_USER_TYPE)
        val userType = LoginEnum.valueOf(userTypeString ?: LoginEnum.TRAVELER.name)

        val backBtn: TextView = findViewById(R.id.lBL_back)
        backBtn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra(Constants.EXTRA_USER_TYPE, userType.name)
            startActivity(intent)
            finish()
        }

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

        val addPhotoTextView: TextView = findViewById(R.id.signup_LBL_add_photo)

        addPhotoTextView.setOnClickListener {
            checkImagePermissionsAndShowDialog()
        }

        countryCodeTextView = findViewById(R.id.signup_TXT_country_code)

        val countryCodes = resources.getStringArray(R.array.country_codes).toList()

        countryCodeTextView.setOnClickListener {
            showCountryCodeBottomSheet(countryCodes)
        }

        firstNameEditText = findViewById(R.id.signup_EDT_first_name_en)
        lastNameEditText = findViewById(R.id.signup_EDT_last_name_en)
        setAllowedCharacters(firstNameEditText, Regex("[A-Za-z ]"))
        setAllowedCharacters(lastNameEditText, Regex("[A-Za-z ]"))

        emailEditText = findViewById(R.id.signup_EDT_email)
        countryCodeTextView = findViewById(R.id.signup_TXT_country_code)
        phoneEditText = findViewById(R.id.signup_EDT_phone)
        phoneEditText.isEnabled = false


        genderRadioGroup = findViewById(R.id.signup_RG_gender)

        genderRadioGroup.setOnCheckedChangeListener { _, _ ->
            genderRadioGroup.setBackgroundResource(android.R.color.transparent)
        }
        passwordEditText = findViewById(R.id.signup_EDT_password)
        confirmPasswordEditText = findViewById(R.id.signup_EDT_confirm)


        // Allow only digits in phoneEditText
        phoneEditText.inputType = InputType.TYPE_CLASS_NUMBER
        phoneEditText.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
            if (source.matches(Regex("[0-9]+"))) source else ""
        })

        findViewById<Button>(R.id.signup_BTN_continue).setOnClickListener {
            if (isValidForm()) {
                createTravelerAccount(
                    firstName = firstNameEditText.text.toString().trim(),
                    lastName = lastNameEditText.text.toString().trim(),
                    email = emailEditText.text.toString().trim(),
                    password = passwordEditText.text.toString(),
                    countryCode = countryCodeTextView.text.toString().trim(),
                    phoneNumber = phoneEditText.text.toString().trim(),
                    gender = getSelectedGender(),
                    imageUri = selectedImageUri)
            }
        }

        phoneEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    FieldErrorUtil.clearError(phoneEditText, this@CreateTravelerAccountActivity)
                }
            }
        })

//        photoImageView.setColorFilter(R.color.white, PorterDuff.Mode.SRC_IN)
        addRealtimeValidation(firstNameEditText)
        addRealtimeValidation(lastNameEditText)
        addRealtimeValidation(emailEditText)
        addRealtimeValidation(phoneEditText)
        addRealtimeValidation(passwordEditText)
        addRealtimeValidation(confirmPasswordEditText)

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

    private fun getSelectedGender(): String {
        val selectedId = genderRadioGroup.checkedRadioButtonId
        return if (selectedId != -1) {
            findViewById<RadioButton>(selectedId).text.toString()
        } else {
            ""
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
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
//                    val user = firebaseAuth.currentUser
//                    Toast.makeText(this, "Signed in as ${user?.displayName}", Toast.LENGTH_SHORT).show()
                    addUserToTravelersDb()


                } else {
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    @OptIn(UnstableApi::class)
    private fun addUserToTravelersDb() {
        val current =FirebaseAuth.getInstance().currentUser ?: return
        val uid = current.uid
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        val account = GoogleSignIn.getLastSignedInAccount(this)
        val email = account?.email?.trim()?.lowercase() ?: run {
            Toast.makeText(this, "Google account has no email", Toast.LENGTH_SHORT).show()
            return
        }

        val travelerData = mapOf(
            "uid" to uid,
            "firstName" to (account?.givenName ?: ""),
            "lastName"  to (account?.familyName ?: ""),
            "email"     to email, // תמיד lowercase
            "photoUrl"  to (account?.photoUrl?.toString())
        )

        UserRepo.ensureUniqueAndCreate(
            db = db,
            selectedCollection = Constants.TRAVELER_DB_NAME,   // "Travelers"
            otherCollection = Constants.BUSINESS_DB_NAME,      // "Business"
            emailRaw = email,
            uid = uid,
            data = travelerData,
            onAlreadyInSelected = {
                Toast.makeText(this, "You already signed up as Traveler. Please log in.", Toast.LENGTH_LONG).show()
            },
            onExistsInOther = {
               Toast.makeText(this, "This Google account is registered as Business. Please choose Business.",Toast.LENGTH_LONG).show()
             FirebaseAuth.getInstance().signOut()
            },
            onCreated = {
               Toast.makeText(this, "Traveler created successfully",Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                intent.putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.TRAVELER)
                startActivity(intent)
                finish()
            },
            onError = {
              Toast.makeText(this, "Failed to save user: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        )
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

            // איפוס שדה הטלפון והפיכתו לפעיל
            phoneEditText.text.clear()
            phoneEditText.isEnabled = true

            // הגבלת אורך וסינון תווים לפי הקידומת
            val numericCode = selected.split(" ")[0].substring(1) // לדוגמה: 972
            val resId = resources.getIdentifier("phone_length_$numericCode", "integer", packageName)
            if (resId != 0) {
                val maxLength = resources.getInteger(resId)
                phoneEditText.filters = arrayOf(
                    InputFilter.LengthFilter(maxLength),
                    InputFilter { source, _, _, _, _, _ ->
                        if (source.matches(Regex("[0-9]+"))) source else ""
                    }
                )
            }

            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun isValidForm(): Boolean {
        var isValid = true
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val countryCode = countryCodeTextView.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()
        val genderSelectedId = genderRadioGroup.checkedRadioButtonId
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        // ניקוי שגיאות קודמות
        FieldErrorUtil.clearError(firstNameEditText, this)
        FieldErrorUtil.clearError(lastNameEditText, this)
        FieldErrorUtil.clearError(emailEditText, this)
        FieldErrorUtil.clearError(phoneEditText, this)
        FieldErrorUtil.clearError(passwordEditText, this)
        FieldErrorUtil.clearError(confirmPasswordEditText, this)

        if (firstName.isEmpty() || !isEnglishOnly(firstName)) {
            firstNameEditText.error = "Only English letters are allowed"
            FieldErrorUtil.markError(firstNameEditText, this)
            isValid = false
        } else {
            FieldErrorUtil.clearError(firstNameEditText, this)
        }

        if (lastName.isEmpty() || !isEnglishOnly(lastName)) {
            lastNameEditText.error = "Only English letters are allowed"
            FieldErrorUtil.markError(lastNameEditText, this)
            isValid = false
        } else {
            FieldErrorUtil.clearError(lastNameEditText, this)
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Enter a valid email address"
            FieldErrorUtil.markError(emailEditText, this)
            isValid = false
        }

        if (countryCode == "Country code") {
            Toast.makeText(this, "Please select a country code", Toast.LENGTH_SHORT).show()
            countryCodeTextView.setBackgroundResource(R.drawable.input_background_error)
            isValid = false
        } else {
            countryCodeTextView.setBackgroundResource(R.drawable.input_background)
        }

        val pureCountryCode = countryCode.split(" ")[0]
        val numericCode = pureCountryCode.substring(1)
        val resourceName = "phone_length_$numericCode"
        val resId = resources.getIdentifier(resourceName, "integer", packageName)

        if (resId != 0) {
            val expectedLength = resources.getInteger(resId)

            if (phone.isEmpty()) {
                phoneEditText.error = "Phone number is required"
                FieldErrorUtil.markError(phoneEditText, this)
                isValid = false
            } else if (!phone.matches(Regex("^[0-9]+$"))) {
                phoneEditText.error = "Phone number must contain only digits"
                FieldErrorUtil.markError(phoneEditText, this)
                isValid = false
            } else if (phone.length != expectedLength) {
                phoneEditText.error = "Phone number must be $expectedLength digits"
                FieldErrorUtil.markError(phoneEditText, this)
                isValid = false
            } else {
                FieldErrorUtil.clearError(phoneEditText, this)
            }
        } else {
            phoneEditText.error = "Please select a valid country code first"
            FieldErrorUtil.markError(phoneEditText, this)
            isValid = false
        }

        if (genderSelectedId == -1) {
            Toast.makeText(this, "Please select a gender", Toast.LENGTH_SHORT).show()
            genderRadioGroup.setBackgroundResource(R.drawable.radio_group_background_error)
            isValid = false
        } else {
            genderRadioGroup.setBackgroundResource(android.R.color.transparent)
        }
if( isImageSelected) {
    val drawable = photoImageView.drawable
    val defaultDrawable = ContextCompat.getDrawable(this, R.drawable.ico_traveler)

    if (drawable == null || (defaultDrawable != null && drawable.constantState == defaultDrawable.constantState)) {
        Toast.makeText(this, "Please upload a profile photo", Toast.LENGTH_SHORT).show()
        photoImageView.setBackgroundResource(R.drawable.photo_background_error)
        isValid = false
    } else {
        photoImageView.setBackgroundResource(R.drawable.input_background)
    }
}else
{
    isValid = false
    photoImageView.setBackgroundResource(R.drawable.photo_background_error)
    Toast.makeText(this, "Please select a profile photo", Toast.LENGTH_SHORT).show()
}

        val passwordLayout = findViewById<TextInputLayout>(R.id.signup_layout_password)
        val confirmPasswordLayout = findViewById<TextInputLayout>(R.id.signup_layout_confirm)

        val passwordRegex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$")

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

    private fun setAllowedCharacters(editText: EditText, allowedRegex: Regex) {
        var lastValid = ""

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val current = s.toString()
                val filtered = current.filter { it.toString().matches(allowedRegex) }

                if (current != filtered) {
                    editText.removeTextChangedListener(this)
                    editText.setText(filtered)

                    val position = filtered.length.coerceAtMost(editText.text.length)
                    editText.setSelection(position)
                    editText.addTextChangedListener(this)
                }
            }
        })
    }


    private fun addRealtimeValidation(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                FieldErrorUtil.clearError(editText, this@CreateTravelerAccountActivity)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    private fun isEnglishOnly(text: String): Boolean {
        return text.matches(Regex("^[A-Za-z ]+$"))
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
    private fun createTravelerAccount(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        countryCode: String,
        phoneNumber: String,
        gender: String,
        imageUri: Uri?
    ) {
        val emailLower = email.trim().lowercase()
        val db = FirebaseFirestore.getInstance()

        UserRepo.ensureEmailUniqueBeforeAuth(
            db = db,
            selectedCollection = Constants.TRAVELER_DB_NAME,   // "Travelers"
            otherCollection = Constants.BUSINESS_DB_NAME,      // "Business"
            emailRaw = emailLower,
            onUnique = {
                FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(emailLower, password)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: return@addOnSuccessListener
                        val save = { imageUrl: String? ->
                            saveUserToFirestore(uid, firstName, lastName, emailLower, countryCode, phoneNumber, gender, imageUrl)
                        }
                        if (imageUri != null) {
                            uploadImageToFirebase(imageUri, uid, Constants.TRAVELER_FOLDER_NAME) { url -> save(url) }
                        } else {
                            save(null)
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Registration failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            },
            onExistsInSelected = {
                Toast.makeText(this, "This email is already registered as Traveler. Please log in.", Toast.LENGTH_LONG).show()
            },
            onExistsInOther = {
                Toast.makeText(this, "This email is registered as Business. Please choose Business.", Toast.LENGTH_LONG).show()
            },
            onError = { e ->
                Toast.makeText(this, "Sign-up check failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun saveUserToFirestore(
        uid: String,
        firstName: String,
        lastName: String,
        email: String,
        countryCode: String,
        phoneNumber: String,
        gender: String,
        imageUrl: String?
    ) {
        val user = hashMapOf(
            "uid" to uid,
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to email,
            "countryCode" to countryCode,
            "phoneNumber" to phoneNumber,
            "gender" to gender,
            "imageUrl" to imageUrl
        )

        Firebase.firestore.collection(Constants.TRAVELER_DB_NAME)
            .document(uid)
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Account created successfully", Toast.LENGTH_LONG).show()
                val intent = Intent(this, LoginActivity::class.java)
                intent.putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.TRAVELER)
                startActivity(intent)
                finish()
                // אולי תעבירי למסך הבא כאן
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save user data: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

 }

inline fun <T> Array<out T>.anyIndexed(predicate: (index: Int, T) -> Boolean): Boolean {
    for (i in indices) {
        if (predicate(i, this[i])) return true
    }
    return false
}

//data class Traveler(
//    val firstName: String = "",
//    val lastName: String = "",
//    val email: String = "",
//    val photoUrl: String? = null,
//    val countryCode: String? = null,
//    val phone: String? = null,
//    val gender: String? = null
//)

package com.example.koshertopia

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.koshertopia.util.Constants
import com.example.koshertopia.util.LoginEnum
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 1001

    private var userType: LoginEnum = LoginEnum.TRAVELER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // סוג המשתמש הגיע מהמסך הראשי
        val userTypeString = intent.getStringExtra(Constants.EXTRA_USER_TYPE)
        userType = LoginEnum.valueOf(userTypeString ?: LoginEnum.TRAVELER.name)

        val emailEt = findViewById<EditText>(R.id.login_EDT_email)
        val passEt = findViewById<EditText>(R.id.login_EDT_password)
        val btnLogin = findViewById<Button>(R.id.login_BTN_login)
        val btnGoogle = findViewById<Button>(R.id.login_BTN_google)
        val lblSignup = findViewById<TextView>(R.id.login_LBL_signup)
        val lblForgot = findViewById<TextView>(R.id.login_LBL_forgot)
        val backBtn = findViewById<TextView>(R.id.login_LBL_back)

        // Google setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // התחברות עם אימייל+סיסמה
        btnLogin.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val password = passEt.text.toString()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEt.error = "Enter a valid email"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                passEt.error = "Password is required"
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val collection = when (userType) {
                        LoginEnum.TRAVELER -> Constants.TRAVELER_DB_NAME   // "Travelers"
                        LoginEnum.BUSINESS -> Constants.BUSINESS_DB_NAME   // "Business"
                    }
                    db.collection(collection)
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { snap ->
                            if (!snap.isEmpty) {
                                goToNextScreenByType()
                            } else {
                                auth.signOut()
                                Toast.makeText(
                                    this,
                                    "This email belongs to a different account type.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            auth.signOut()
                            Toast.makeText(this, "Login check failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    passEt.error = "Incorrect email or password"
                    Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
                }
        }

        // Google sign-in (קוראים לפונקציה)
        btnGoogle.setOnClickListener { startGoogleSignIn() }

        // הרשמה לפי סוג
        lblSignup.setOnClickListener {
            when (userType) {
                LoginEnum.TRAVELER -> startActivity(
                    Intent(this, CreateTravelerAccountActivity::class.java)
                        .putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.TRAVELER.name)
                )
                LoginEnum.BUSINESS -> startActivity(
                    Intent(this, CreateBusinessAccountActivity::class.java)
                        .putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.BUSINESS.name)
                )
            }
        }

        // שכחתי סיסמה
        lblForgot.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // חזרה למסך ראשי
        backBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun startGoogleSignIn() {
        // signOut כדי לוודא בחירת חשבון מחדש
        googleSignInClient.signOut().addOnCompleteListener {
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }
    }

    // תוצאת Google Sign-In
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        val email = account.email
                        if (email.isNullOrEmpty()) {
                            auth.signOut()
                            Toast.makeText(this, "Google account has no email", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                        enforceUserTypeForGoogle(email)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Google authentication failed", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // אכיפת סוג המשתמש אחרי Google
    private fun enforceUserTypeForGoogle(email: String) {
        val selectedCollection = when (userType) {
            LoginEnum.TRAVELER -> Constants.TRAVELER_DB_NAME   // "Travelers"
            LoginEnum.BUSINESS -> Constants.BUSINESS_DB_NAME   // "Business"
        }
        val otherCollection = when (userType) {
            LoginEnum.TRAVELER -> Constants.BUSINESS_DB_NAME
            LoginEnum.BUSINESS -> Constants.TRAVELER_DB_NAME
        }

        db.collection(selectedCollection)
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    goToNextScreenByType()
                } else {
                    // בדיקה אם קיים כאוסף אחר
                    db.collection(otherCollection)
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { otherSnap ->
                            if (!otherSnap.isEmpty) {
                                auth.signOut()
                                val msg = when (userType) {
                                    LoginEnum.TRAVELER -> "This Google account is registered as a Business. Please choose Business."
                                    LoginEnum.BUSINESS -> "This Google account is registered as a Traveler. Please choose Traveler."
                                }
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                            } else {
                                // לא קיים כלל – לא נרשמים אוטומטית במסך לוגין
                                auth.signOut()
                                Toast.makeText(this, "No account found. Please sign up first.", Toast.LENGTH_LONG).show()
                                val createIntent = when (userType) {
                                    LoginEnum.TRAVELER -> Intent(this, CreateTravelerAccountActivity::class.java)
                                    LoginEnum.BUSINESS -> Intent(this, CreateBusinessAccountActivity::class.java)
                                }.putExtra(Constants.EXTRA_USER_TYPE, userType.name)
                                startActivity(createIntent)
                            }
                        }
                        .addOnFailureListener { e ->
                            auth.signOut()
                            Toast.makeText(this, "Type check failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                auth.signOut()
                Toast.makeText(this, "Login check failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun goToNextScreenByType() {
        val next = when (userType) {
            LoginEnum.TRAVELER -> Intent(this, TravelerAccountActivity::class.java)
            LoginEnum.BUSINESS -> Intent(this, BusinessAccountActivity::class.java)
        }
        startActivity(next)
        finish()
    }
}

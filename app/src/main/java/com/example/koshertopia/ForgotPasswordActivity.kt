package com.example.koshertopia

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var emailEditText: EditText
    private lateinit var resetButton: Button
    private lateinit var backTextView: TextView
    private lateinit var auth: FirebaseAuth

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        emailEditText = findViewById(R.id.reset_password_EDT_email)
        resetButton = findViewById(R.id.reset_password_BTN)
        backTextView = findViewById(R.id.lBL_back)
        auth = FirebaseAuth.getInstance()

        val backBtn: TextView = findViewById(R.id.lBL_back)
        backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        resetButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.error = "Please enter a valid email"
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        AlertDialog.Builder(this)
                            .setTitle("Success")
                            .setMessage("Reset link sent to your email")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .show()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

}
package com.example.koshertopia


import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.koshertopia.util.Constants
import com.example.koshertopia.util.LoginEnum


class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//         FirebaseApp.initializeApp(this)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val travelerBtn = findViewById<Button>(R.id.btn_travelers)
        travelerBtn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.TRAVELER.name)
            startActivity(intent)
        }

        val businessBtn = findViewById<Button>(R.id.btn_business)
        businessBtn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra(Constants.EXTRA_USER_TYPE, LoginEnum.BUSINESS.name)
            startActivity(intent)
        }

    }
}
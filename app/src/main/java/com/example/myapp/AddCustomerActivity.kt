package com.example.myapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


class AddCustomerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_customer)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val etName = findViewById<EditText>(R.id.et_name)
        val etMobile = findViewById<EditText>(R.id.et_mobile)
        val etAddress = findViewById<EditText>(R.id.et_address)
        val btnAdd = findViewById<Button>(R.id.btn_add_customer)

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val mobile = etMobile.text.toString().trim()
            val address = etAddress.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (mobile.isNotEmpty() && !mobile.matches(Regex("^\\d{10}\$"))) {
                Toast.makeText(this, "Mobile must be exactly 10 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            lifecycleScope.launch {
                val db = CustomerDatabase.getDatabase(this@AddCustomerActivity)
                val dao = db.customerDao()
                val existing = dao.getCustomerByName(name)
                if (existing != null) {
                    runOnUiThread {
                        Toast.makeText(
                            this@AddCustomerActivity,
                            "customer already exist",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val intent = Intent()
                    intent.putExtra("name", name)
                    intent.putExtra("mobile", mobile)
                    intent.putExtra("address", address)
                    intent.putExtra("accountCreated", now)
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
            }
        }
    }
}
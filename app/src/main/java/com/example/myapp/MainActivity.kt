package com.example.myapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CustomerAdapter
    private val customerViewModel: CustomerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.btnShowReport).setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.btnMenu).setOnClickListener {
            val intent = Intent(this, BusinessProfileActivity::class.java)
            startActivity(intent)
        }

        recyclerView = findViewById(R.id.recyclerViewCustomers)
        val fabAddCustomer: ExtendedFloatingActionButton = findViewById(R.id.fab_add_customer)
        val searchView: SearchView = findViewById(R.id.searchView)
        val tvTotalCustomers = findViewById<TextView>(R.id.tvTotalCustomers)
        val tvTotalDue = findViewById<TextView>(R.id.tvTotalDue)
        val tvTotalAdvance = findViewById<TextView>(R.id.tvTotalAdvance)
        val tvEmptyList = findViewById<TextView>(R.id.tvEmptyList)

        adapter = CustomerAdapter(emptyList()) { customer ->
            val intent = Intent(this, CustomerDetailActivity::class.java)
            intent.putExtra("customer_id", customer.id)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        customerViewModel.customers.observe(this, Observer { customers ->
            adapter.updateList(customers)

            // --- Summary Card Logic ---
            val totalCustomers = customers.size
            // You'll Get: sum of negative balances (we gave customer, they owe us)
            val totalGet = customers.filter { it.balance < 0.0 }.sumOf { Math.abs(it.balance) }
            // You'll Give: sum of positive balances (customer overpaid, we owe them)
            val totalGive = customers.filter { it.balance > 0.0 }.sumOf { it.balance }

            tvTotalCustomers.text = totalCustomers.toString()
            tvTotalDue.text = "₹%.0f".format(totalGive)
            tvTotalAdvance.text = "₹%.0f".format(totalGet)
            tvEmptyList.visibility = if (customers.isEmpty()) View.VISIBLE else View.GONE
        })

        fabAddCustomer.setOnClickListener {
            val intent = Intent(this, AddCustomerActivity::class.java)
            addCustomerLauncher.launch(intent)
        }

        // Stylish search bar focus handling
        searchView.setOnClickListener {
            searchView.isIconified = false
            searchView.requestFocus()
        }
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) searchView.setIconified(false)
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) customerViewModel.restoreAll()
                else customerViewModel.search(newText)
                return true
            }
        })

        // Initially set the business name header
        updateBusinessNameHeader()
    }

    override fun onResume() {
        super.onResume()
        updateBusinessNameHeader()
    }

    private fun updateBusinessNameHeader() {
        val prefs = getSharedPreferences("business_profile", MODE_PRIVATE)
        val businessName = prefs.getString("business_name", "My Business") ?: "My Business"
        val businessImagePath = prefs.getString("profile_image_path", null)
        findViewById<TextView>(R.id.tvBusinessName)?.text = businessName
        ProfileImageUtils.applyProfileVisual(
            imagePath = businessImagePath,
            displayName = businessName,
            imageView = findViewById<ImageView>(R.id.ivBusinessProfilePhoto),
            initialView = findViewById<TextView>(R.id.tvBusinessProfileInitial),
            emptyFallback = "M"
        )
    }

    private val addCustomerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val name = data.getStringExtra("name") ?: ""
                val mobile = data.getStringExtra("mobile") ?: ""
                val address = data.getStringExtra("address") ?: ""
                val accountCreated = data.getStringExtra("accountCreated") ?: ""
                if (name.isNotBlank()) {
                    val customer = CustomerEntity(
                        name = name,
                        mobile = mobile,
                        address = address,
                        balance = 0.0,
                        accountCreated = accountCreated
                    )
                    customerViewModel.addCustomer(customer)
                }
            }
        }
    }
}
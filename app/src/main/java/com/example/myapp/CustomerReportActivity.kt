package com.example.myapp

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CustomerReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_report)

        val customerId = intent.getIntExtra("customer_id", -1)
        val customerName = intent.getStringExtra("customer_name").orEmpty()

        findViewById<ImageButton>(R.id.btnReportBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvReportCustomerName).text = if (customerName.isBlank()) "Customer Report" else customerName

        if (customerId == -1) {
            finish()
            return
        }

        loadReport(customerId)
    }

    private fun loadReport(customerId: Int) {
        lifecycleScope.launch {
            val txnDao = CustomerDatabase.getDatabase(this@CustomerReportActivity).transactionDao()
            val allTxns = txnDao.getTransactionsForCustomerRaw(customerId)

            val yearCal = Calendar.getInstance().apply {
                set(Calendar.MONTH, 0)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val thisYearStart = yearCal.timeInMillis

            val thisYearTxns = allTxns.filter { parseTxnDate(it.date) >= thisYearStart }
            val thisYearGiven = thisYearTxns.sumOf { it.given }
            val thisYearReceived = thisYearTxns.sumOf { it.received }

            val sinceJoinedGiven = allTxns.sumOf { it.given }
            val sinceJoinedReceived = allTxns.sumOf { it.received }

            runOnUiThread {
                findViewById<TextView>(R.id.tvThisYearGiven).text = "₹%.2f".format(thisYearGiven)
                findViewById<TextView>(R.id.tvThisYearReceived).text = "₹%.2f".format(thisYearReceived)
                findViewById<TextView>(R.id.tvSinceJoinedGiven).text = "₹%.2f".format(sinceJoinedGiven)
                findViewById<TextView>(R.id.tvSinceJoinedReceived).text = "₹%.2f".format(sinceJoinedReceived)
            }
        }
    }

    private fun parseTxnDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateStr)?.time ?: 0
        } catch (_: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(dateStr)?.time ?: 0
            } catch (_: Exception) {
                0
            }
        }
    }
}

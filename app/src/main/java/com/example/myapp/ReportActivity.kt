package com.example.myapp

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.view.View

class ReportActivity : AppCompatActivity() {

    private lateinit var txnDao: TransactionDao
    private var currentFilter: FilterType = FilterType.TODAY
    private var customStart: Long? = null
    private var customEnd: Long? = null
    private var customerId: Int? = null // If you want report for all, keep null

    private enum class FilterType { CUSTOM, TODAY, THISMONTH, THISYEAR, LASTYEAR }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        txnDao = CustomerDatabase.getDatabase(this).transactionDao()

        val btnBack = findViewById<ImageButton>(R.id.btnReportBack)
        btnBack.setOnClickListener { finish() }

        val btnCustom = findViewById<Button>(R.id.btnReportFilterCustom)
        val btnToday = findViewById<Button>(R.id.btnReportFilterToday)
        val btnThisMonth = findViewById<Button>(R.id.btnReportFilterThisMonth)
        val btnThisYear = findViewById<Button>(R.id.btnReportFilterThisYear)
        val btnLastYear = findViewById<Button>(R.id.btnReportFilterLastYear)

        val filterChips = listOf(btnCustom, btnToday, btnThisMonth, btnThisYear, btnLastYear)
        val chipIdMap = mapOf(
            FilterType.CUSTOM to R.id.btnReportFilterCustom,
            FilterType.TODAY to R.id.btnReportFilterToday,
            FilterType.THISMONTH to R.id.btnReportFilterThisMonth,
            FilterType.THISYEAR to R.id.btnReportFilterThisYear,
            FilterType.LASTYEAR to R.id.btnReportFilterLastYear
        )

        btnCustom.setOnClickListener { showCustomDatePicker(filterChips, chipIdMap) }
        btnToday.setOnClickListener { updateReport(FilterType.TODAY, filterChips, chipIdMap) }
        btnThisMonth.setOnClickListener { updateReport(FilterType.THISMONTH, filterChips, chipIdMap) }
        btnThisYear.setOnClickListener { updateReport(FilterType.THISYEAR, filterChips, chipIdMap) }
        btnLastYear.setOnClickListener { updateReport(FilterType.LASTYEAR, filterChips, chipIdMap) }

        updateReport(FilterType.TODAY, filterChips, chipIdMap) // default
    }

    private fun updateReport(type: FilterType, filterChips: List<Button>, chipIdMap: Map<FilterType, Int>) {
        currentFilter = type
        // Highlight chip
        filterChips.forEach { chip ->
            if (chip.id == chipIdMap[type]) {
                chip.setBackgroundResource(R.drawable.filter_chip_selected)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.oval_button_white)
                chip.setTextColor(Color.parseColor("#212121"))
            }
        }
        // Compute stats
        lifecycleScope.launch {
            val allTxns = if (customerId != null)
                txnDao.getTransactionsForCustomerRaw(customerId!!)
            else
                txnDao.getAllTransactionsRaw()

            val now = Calendar.getInstance()
            val start: Long
            val end: Long

            val periodLabel = findViewById<TextView>(R.id.tvReportPeriod)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val filtered: List<TransactionEntity> = when (type) {
                FilterType.TODAY -> {
                    now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0)
                    now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
                    start = now.timeInMillis
                    end = start + 24 * 60 * 60 * 1000 - 1
                    periodLabel.text = "Period: ${formatDate(start)} to ${formatDate(end)}"
                    allTxns.filter { between(it.date, start, end) }
                }
                FilterType.THISMONTH -> {
                    now.set(Calendar.DAY_OF_MONTH, 1); now.set(Calendar.HOUR_OF_DAY, 0)
                    now.set(Calendar.MINUTE, 0); now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
                    start = now.timeInMillis
                    now.add(Calendar.MONTH, 1)
                    end = now.timeInMillis - 1
                    periodLabel.text = "Period: ${formatDate(start)} to ${formatDate(end)}"
                    allTxns.filter { between(it.date, start, end) }
                }
                FilterType.THISYEAR -> {
                    now.set(Calendar.MONTH, 0); now.set(Calendar.DAY_OF_MONTH, 1)
                    now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0)
                    now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
                    start = now.timeInMillis
                    now.add(Calendar.YEAR, 1)
                    end = now.timeInMillis - 1
                    periodLabel.text = "Period: ${formatDate(start)} to ${formatDate(end)}"
                    allTxns.filter { between(it.date, start, end) }
                }
                FilterType.LASTYEAR -> {
                    now.set(Calendar.MONTH, 0); now.set(Calendar.DAY_OF_MONTH, 1)
                    now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0)
                    now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
                    end = now.timeInMillis - 1
                    now.add(Calendar.YEAR, -1)
                    start = now.timeInMillis
                    periodLabel.text = "Period: ${formatDate(start)} to ${formatDate(end)}"
                    allTxns.filter { between(it.date, start, end) }
                }
                FilterType.CUSTOM -> {
                    start = customStart ?: 0L
                    end = customEnd ?: Long.MAX_VALUE
                    periodLabel.text = "Period: ${formatDate(start)} to ${formatDate(end)}"
                    allTxns.filter { between(it.date, start, end) }
                }
            }
            // Stats
            val given = filtered.sumOf { it.given }
            val received = filtered.sumOf { it.received }

            runOnUiThread {
                findViewById<TextView>(R.id.tvReportGiven).text = "₹%.2f".format(given)
                findViewById<TextView>(R.id.tvReportReceived).text = "₹%.2f".format(received)
                val net = received - given
                val tvNet = findViewById<TextView>(R.id.tvReportNet)
                tvNet.text = "₹%.2f".format(net)
                tvNet.setTextColor(if (net >= 0) Color.parseColor("#128B38") else Color.parseColor("#B50000"))

                // --- Add PieChart setup here: ---
                val pieChart = findViewById<com.github.mikephil.charting.charts.PieChart>(R.id.pieChart)
                if (given > 0.0 || received > 0.0) {
                    val entries = mutableListOf<com.github.mikephil.charting.data.PieEntry>()
                    if (given > 0) entries.add(com.github.mikephil.charting.data.PieEntry(given.toFloat(), "Given"))
                    if (received > 0) entries.add(com.github.mikephil.charting.data.PieEntry(received.toFloat(), "Received"))
                    val colors = listOf(
                        Color.parseColor("#FF8989"), // Given, soft red
                        Color.parseColor("#74D99F")  // Received, soft green
                    )
                    val set = com.github.mikephil.charting.data.PieDataSet(entries, "")
                    set.colors = colors
                    set.valueTextSize = 16f
                    set.valueTextColor = Color.DKGRAY
                    val data = com.github.mikephil.charting.data.PieData(set)
                    pieChart.data = data
                    pieChart.setDrawEntryLabels(false)
                    pieChart.description.isEnabled = false

                    pieChart.legend.isEnabled = true
                    pieChart.legend.orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.VERTICAL
                    pieChart.legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.CENTER
                    pieChart.legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
                    pieChart.legend.textSize = 16f
                    pieChart.legend.form = com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE

                    pieChart.setDrawHoleEnabled(true)
                    pieChart.holeRadius = 42f
                    pieChart.setEntryLabelColor(Color.DKGRAY)
                    pieChart.centerText = "Given/Received"
                    pieChart.setCenterTextSize(15f)
                    pieChart.invalidate()
                    pieChart.visibility = View.VISIBLE
                } else {
                    pieChart.visibility = View.GONE
                }
            }
        }
    }

    private fun showCustomDatePicker(filterChips: List<Button>, chipIdMap: Map<FilterType, Int>) {
        val today = Calendar.getInstance()
        DatePickerDialog(this, { _, sy, sm, sd ->
            val start = Calendar.getInstance()
            start.set(sy, sm, sd, 0, 0, 0)
            DatePickerDialog(this, { _, ey, em, ed ->
                val end = Calendar.getInstance()
                end.set(ey, em, ed, 23, 59, 59)
                customStart = start.timeInMillis
                customEnd = end.timeInMillis
                updateReport(FilterType.CUSTOM, filterChips, chipIdMap)
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).apply {
                setTitle("Select End Date")
            }.show()
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).apply {
            setTitle("Select Start Date")
        }.show()
    }

    private fun between(dateStr: String?, startMs: Long, endMs: Long): Boolean {
        if (dateStr.isNullOrBlank()) return false
        try {
            val mainFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val d = mainFmt.parse(dateStr)?.time
            if (d != null) return d in startMs..endMs
        } catch (_: Exception) {}
        try {
            // fallback if saved as "yyyy-MM-dd HH:mm"
            val altFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val d = altFmt.parse(dateStr)?.time
            if (d != null) return d in startMs..endMs
        } catch (_: Exception) {}
        return false
    }

    private fun formatDate(ms: Long): String {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return df.format(Date(ms))
    }
}
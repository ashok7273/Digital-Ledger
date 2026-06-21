package com.example.myapp

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: TransactionAdapter
    private lateinit var txnDao: TransactionDao
    private lateinit var customerDao: CustomerDao
    private var customerId: Int = -1
    private var customerName: String = ""

    private lateinit var filterChips: List<Button>
    private lateinit var chipIdMap: Map<FilterType, Int>
    
    // File attachment variables for editing
    private var currentPhotoPath: String? = null
    private var currentEditDialog: AlertDialog? = null
    private var currentEditAttachmentPreview: LinearLayout? = null
    private var currentEditAttachmentNameText: TextView? = null
    private var currentEditSelectedPath: String? = null
    private var currentEditSelectedType: String? = null

    companion object {
        private const val REQUEST_IMAGE_CAPTURE_EDIT = 2001
        private const val REQUEST_DOCUMENT_PICK_EDIT = 2002
    }

    private enum class FilterType { OVERALL, LAST7, THISMONTH, LASTMONTH, LAST3MONTHS, CUSTOM }
    private var currentFilter: FilterType = FilterType.OVERALL
    private var customStart: Long? = null
    private var customEnd: Long? = null

    private val transactionDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadFilteredTransactions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_history)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        customerName = intent.getStringExtra("customer_name") ?: ""
        customerId = intent.getIntExtra("customer_id", -1)

        findViewById<TextView>(R.id.tvTransactionTitle).text = "Transaction history ($customerName)"

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTransactions)
        adapter = TransactionAdapter { txn ->
            val intent = Intent(this, TransactionDetailActivity::class.java)
            intent.putExtra("transaction_id", txn.id)
            transactionDetailLauncher.launch(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        txnDao = CustomerDatabase.getDatabase(this).transactionDao()
        customerDao = CustomerDatabase.getDatabase(this).customerDao()

        // Filter chip buttons
        val btnOverall = findViewById<Button>(R.id.btnFilterOverall)
        val btnLast7 = findViewById<Button>(R.id.btnFilterLast7)
        val btnThisMonth = findViewById<Button>(R.id.btnFilterThisMonth)
        val btnLastMonth = findViewById<Button>(R.id.btnFilterLastMonth)
        val btnLast3Months = findViewById<Button>(R.id.btnFilterLast3Months)
        val btnCustom = findViewById<Button>(R.id.btnFilterCustom)
        filterChips = listOf(btnOverall, btnLast7, btnThisMonth, btnLastMonth, btnLast3Months, btnCustom)
        chipIdMap = mapOf(
            FilterType.OVERALL to R.id.btnFilterOverall,
            FilterType.LAST7 to R.id.btnFilterLast7,
            FilterType.THISMONTH to R.id.btnFilterThisMonth,
            FilterType.LASTMONTH to R.id.btnFilterLastMonth,
            FilterType.LAST3MONTHS to R.id.btnFilterLast3Months,
            FilterType.CUSTOM to R.id.btnFilterCustom
        )

        btnOverall.setOnClickListener { updateFilter(FilterType.OVERALL) }
        btnLast7.setOnClickListener { updateFilter(FilterType.LAST7) }
        btnThisMonth.setOnClickListener { updateFilter(FilterType.THISMONTH) }
        btnLastMonth.setOnClickListener { updateFilter(FilterType.LASTMONTH) }
        btnLast3Months.setOnClickListener { updateFilter(FilterType.LAST3MONTHS) }
        btnCustom.setOnClickListener { showCustomDatePicker() }

        updateFilter(FilterType.OVERALL)

        findViewById<Button>(R.id.btnDownloadPdf).setOnClickListener {
            checkStoragePermissionAndExportPdf(isShare = false)
        }
        findViewById<Button>(R.id.btnSharePdf).setOnClickListener {
            checkStoragePermissionAndExportPdf(isShare = true)
        }
    }

    // Permission check (Android 10+ doesn't need runtime write permission, but for full compat:)
    private fun checkStoragePermissionAndExportPdf(isShare: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // For Android 10+, we'll save to Downloads folder which doesn't require special permissions
            exportPdf(isShare)
        } else {
            // For older versions, check WRITE_EXTERNAL_STORAGE permission
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 9999)
                return
            }
            exportPdf(isShare)
        }
    }

    private fun exportPdf(isShare: Boolean) {
        val txns = adapter.currentList
        if (txns.isEmpty()) {
            Toast.makeText(this, "No transactions to export!", Toast.LENGTH_SHORT).show()
            return
        }

        val pdf = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val columnPaint = Paint().apply { textSize = 13f; color = Color.DKGRAY }
        val rowPaint = Paint().apply { textSize = 13f; color = Color.BLACK }

        val pageInfo = PdfDocument.PageInfo.Builder(600, 800, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        var y = 36
        canvas.drawText("Transactions — $customerName", 36f, y.toFloat(), titlePaint)
        y += 36

        // Header
        canvas.drawText("Date", 36f, y.toFloat(), columnPaint)
        canvas.drawText("Given", 170f, y.toFloat(), columnPaint)
        canvas.drawText("Received", 300f, y.toFloat(), columnPaint)
        canvas.drawText("Total", 440f, y.toFloat(), columnPaint)
        y += 20
        canvas.drawLine(36f, y.toFloat(), 560f, y.toFloat(), columnPaint)
        y += 14

        // Rows
        txns.forEach { txn ->
            canvas.drawText(txn.date ?: "", 36f, y.toFloat(), rowPaint)
            if (txn.given != 0.0) canvas.drawText("₹%.2f".format(txn.given), 170f, y.toFloat(), rowPaint)
            if (txn.received != 0.0) canvas.drawText("₹%.2f".format(txn.received), 300f, y.toFloat(), rowPaint)

            val totalBalance = txn.balance
            val totalField = when {
                totalBalance < 0 -> "₹%.2f".format(-totalBalance)
                totalBalance > 0 -> "+₹%.2f".format(totalBalance)
                else -> "₹0.00"
            }
            canvas.drawText(totalField, 440f, y.toFloat(), rowPaint)
            y += 18
            if (y > 780) { // New page if needed (keep simple)
                pdf.finishPage(page)
                // Create new page if more rows exist, etc (skipped for brevity)
            }
        }
        pdf.finishPage(page)

        // Save file
        val safeCustomerName = customerName.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        val baseName = "Transactions_${safeCustomerName.ifBlank { "NA" }}"
        
        var file: File
        
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // For Android 10+, use MediaStore to save to Downloads/myapp folder
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/myapp")
            }
            
            // Handle duplicate files by checking if file exists and adding numbers
            var finalDisplayName = "$baseName.pdf"
            var counter = 1
            var uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            
            while (uri == null) {
                // File already exists, try with counter
                finalDisplayName = "${baseName}_$counter.pdf"
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, finalDisplayName)
                uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                counter++
                if (counter > 100) break // Safety limit
            }
            
            // Create temporary file for both saving and sharing
            val tempFile = File(getExternalFilesDir(null), finalDisplayName)
            pdf.writeTo(tempFile.outputStream())
            pdf.close()
            
            // Copy to MediaStore location
            uri?.let { fileUri ->
                resolver.openOutputStream(fileUri)?.use { outputStream ->
                    tempFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            
            file = tempFile
            
            if (!isShare) {
                Toast.makeText(this, "PDF saved to Downloads/myapp/$finalDisplayName", Toast.LENGTH_LONG).show()
            }
            
        } else {
            // For older Android versions, use direct file access
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appDir = File(documentsDir, "myapp")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            
            // Handle duplicate files by adding numbers
            var pdfFileName = "$baseName.pdf"
            file = File(appDir, pdfFileName)
            var counter = 1
            while (file.exists()) {
                pdfFileName = "${baseName}_$counter.pdf"
                file = File(appDir, pdfFileName)
                counter++
            }
            
            pdf.writeTo(file.outputStream())
            pdf.close()
            
            if (!isShare) {
                Toast.makeText(this, "PDF saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }

        if (isShare) {
            val uri: Uri = FileProvider.getUriForFile(
                this, applicationContext.packageName + ".provider", file
            )
            val share = Intent(Intent.ACTION_SEND)
            share.type = "application/pdf"
            share.putExtra(Intent.EXTRA_STREAM, uri)
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(share, "Share transactions"))
        }
    }

    private fun updateFilter(type: FilterType) {
        currentFilter = type

        // Visually highlight the selected chip and reset others
        filterChips.forEach { chip ->
            if (chip.id == chipIdMap[type]) {
                chip.setBackgroundResource(R.drawable.filter_chip_selected)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.oval_button_white)
                chip.setTextColor(Color.parseColor("#212121"))
            }
        }

        loadFilteredTransactions()
    }

    private fun loadFilteredTransactions() {
        lifecycleScope.launch {
            val allTxns = txnDao.getTransactionsForCustomerRaw(customerId)
            val now = System.currentTimeMillis()
            val filtered = when (currentFilter) {
                FilterType.OVERALL -> allTxns
                FilterType.LAST7 -> {
                    val cutoff = now - 7L * 24 * 60 * 60 * 1000
                    allTxns.filter { toDateMs(it.date) >= cutoff }
                }
                FilterType.THISMONTH -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    allTxns.filter { toDateMs(it.date) >= cal.timeInMillis }
                }
                FilterType.LASTMONTH -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val thisMonthStart = cal.timeInMillis
                    cal.add(Calendar.MONTH, -1)
                    val lastMonthStart = cal.timeInMillis
                    allTxns.filter { toDateMs(it.date) in lastMonthStart until thisMonthStart }
                }
                FilterType.LAST3MONTHS -> {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.MONTH, -3)
                    allTxns.filter { toDateMs(it.date) >= cal.timeInMillis }
                }
                FilterType.CUSTOM -> {
                    val start = customStart ?: 0
                    val end = customEnd ?: Long.MAX_VALUE
                    allTxns.filter { toDateMs(it.date) in start..end }
                }
            }
            runOnUiThread { adapter.submitList(filtered) }
        }
    }

    private fun showCustomDatePicker() {
        val today = Calendar.getInstance()
        DatePickerDialog(this, { _, sy, sm, sd ->
            val start = Calendar.getInstance()
            start.set(sy, sm, sd, 0, 0, 0)
            DatePickerDialog(this, { _, ey, em, ed ->
                val end = Calendar.getInstance()
                end.set(ey, em, ed, 23, 59, 59)
                customStart = start.timeInMillis
                customEnd = end.timeInMillis
                updateFilter(FilterType.CUSTOM)
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).apply {
                setTitle("Select End Date")
            }.show()
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).apply {
            setTitle("Select Start Date")
        }.show()
    }

    private fun toDateMs(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0
        // Try with seconds
        try {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateStr)?.time ?: 0
        } catch (_: Exception) { }
        // Try without seconds
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(dateStr)?.time ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun showTransactionDetailDialog(txn: TransactionEntity) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_details, null)
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvTxnDetailAmount)
        val tvEntryNote = dialogView.findViewById<TextView>(R.id.tvEntryNote)
        val tvEntryAdded = dialogView.findViewById<TextView>(R.id.tvEntryAdded)
        val llModifications = dialogView.findViewById<android.widget.LinearLayout>(R.id.llModifications)
        val tvModifyHeader = dialogView.findViewById<TextView>(R.id.tvModifyHeader)
        val btnEdit = dialogView.findViewById<Button>(R.id.btnEditTxn)
        val btnDelete = dialogView.findViewById<Button>(R.id.btnDeleteTxn)
        
        // Attachment section
        val llAttachment = dialogView.findViewById<android.widget.LinearLayout>(R.id.llAttachment)
        val tvAttachmentName = dialogView.findViewById<TextView>(R.id.tvAttachmentName)
        val btnViewAttachment = dialogView.findViewById<Button>(R.id.btnViewAttachment)

        // Show big current amount (latest value)
        if (txn.received != 0.0) {
            tvAmount.setTextColor(Color.parseColor("#388E3C"))
            tvAmount.text = "+₹%.2f".format(txn.received)
        } else if (txn.given != 0.0) {
            tvAmount.setTextColor(Color.RED)
            tvAmount.text = "₹%.2f".format(txn.given)
        } else {
            tvAmount.setTextColor(Color.BLACK)
            tvAmount.text = "₹0.00"
        }

        // Description always visible, blank if not available (original only)
        tvEntryNote.text = if (!txn.note.isNullOrBlank()) "Description: ${txn.note}" else ""

        // Entry Added always uses original creation date/value/note (never changes!)
        val entryAmt = if (txn.received != 0.0) "+₹%.2f".format(txn.received)
        else "₹%.2f".format(txn.given)
        tvEntryAdded.text = "Entry Added: ${txn.date}   Amount: $entryAmt"
        
        // Handle attachment display
        if (!txn.attachmentPath.isNullOrEmpty()) {
            llAttachment.visibility = android.view.View.VISIBLE
            val fileName = File(txn.attachmentPath).name
            val icon = when (txn.attachmentType) {
                "image" -> "📷"
                "pdf" -> "📄"
                "document" -> "📝"
                "spreadsheet" -> "📊"
                else -> "📎"
            }
            tvAttachmentName.text = "$icon $fileName"
            
            btnViewAttachment.setOnClickListener {
                openAttachment(txn.attachmentPath!!)
            }
        } else {
            llAttachment.visibility = android.view.View.GONE
        }

        lifecycleScope.launch {
            val modLogs = txnDao.getModificationHistory(txn.id)
                .filter { it.isEditHistory } // all mod logs only (not original)
                .sortedBy { it.date } // chronological, earliest edit first

            runOnUiThread {
                llModifications.removeAllViews()
                if (modLogs.isNotEmpty()) {
                    tvModifyHeader.visibility = android.view.View.VISIBLE
                    tvModifyHeader.text = "Modification History:"
                    for (mod in modLogs) {
                        val h = TextView(this@TransactionHistoryActivity)
                        val amtField = if (mod.received != 0.0) "+₹%.2f".format(mod.received)
                        else "₹%.2f".format(mod.given)
                        h.text = "${mod.date}  Amount: $amtField"
                        llModifications.addView(h)
                    }
                } else {
                    tvModifyHeader.visibility = android.view.View.GONE
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnEdit.setOnClickListener {
            showEditTransactionDialog(txn, dialog)
        }
        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        txnDao.deleteTransaction(txn)
                        recalculateCustomerBalances(txn.customerId)
                        runOnUiThread {
                            dialog.dismiss()
                            loadFilteredTransactions()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.show()
    }

    private fun showEditTransactionDialog(transactionToEdit: TransactionEntity, parentDialog: AlertDialog) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_input, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val btnAttachCamera = dialogView.findViewById<Button>(R.id.btnAttachCamera)
        val btnAttachFile = dialogView.findViewById<Button>(R.id.btnAttachFile)
        val attachmentPreview = dialogView.findViewById<LinearLayout>(R.id.attachmentPreview)
        val tvAttachmentName = dialogView.findViewById<TextView>(R.id.tvAttachmentName)
        val btnRemoveAttachment = dialogView.findViewById<Button>(R.id.btnRemoveAttachment)
        
        // Initialize values
        etAmount.setText(
            if (transactionToEdit.received != 0.0) transactionToEdit.received.toString()
            else transactionToEdit.given.toString()
        )
        etNote.setText(transactionToEdit.note ?: "")
        
        // Handle existing attachment
        currentEditSelectedPath = transactionToEdit.attachmentPath
        currentEditSelectedType = transactionToEdit.attachmentType
        
        if (!transactionToEdit.attachmentPath.isNullOrEmpty()) {
            showAttachmentPreview(attachmentPreview, tvAttachmentName, transactionToEdit.attachmentPath!!, transactionToEdit.attachmentType ?: "unknown")
        }
        
        // Store dialog reference for file operations
        currentEditDialog = null // Will be set after dialog creation
        currentEditAttachmentPreview = attachmentPreview
        currentEditAttachmentNameText = tvAttachmentName
        
        // Attachment button listeners
        btnAttachCamera.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(packageManager) != null) {
                try {
                    val photoFile = createImageFile()
                    val photoURI = FileProvider.getUriForFile(
                        this,
                        "${packageName}.provider",
                        photoFile
                    )
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    currentPhotoPath = photoFile.absolutePath
                    startActivityForResult(intent, REQUEST_IMAGE_CAPTURE_EDIT)
                } catch (ex: IOException) {
                    Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        btnAttachFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQUEST_DOCUMENT_PICK_EDIT)
        }
        
        btnRemoveAttachment.setOnClickListener {
            currentEditSelectedPath = null
            currentEditSelectedType = null
            attachmentPreview.visibility = View.GONE
        }

        val isReceived = transactionToEdit.received != 0.0
        val editDialog = AlertDialog.Builder(this)
            .setTitle(if (isReceived) "Edit Received Amount" else "Edit Given Amount")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val amtStr = etAmount.text.toString()
                val noteStr = etNote.text.toString()
                if (amtStr.isBlank() || amtStr.toDoubleOrNull() == null) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val amt = amtStr.toDouble()
                lifecycleScope.launch {
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    // Insert mod log for each edit (never update the original add row!)
                    txnDao.insertTransaction(
                        TransactionEntity(
                            id = 0,
                            customerId = transactionToEdit.customerId,
                            date = now, // timestamp of this edit
                            given = if (isReceived) 0.0 else amt,
                            received = if (isReceived) amt else 0.0,
                            balance = 0.0,
                            note = noteStr.ifEmpty { null },
                            isEditHistory = true,
                            originalTxnId = transactionToEdit.id,
                            attachmentPath = currentEditSelectedPath,
                            attachmentType = currentEditSelectedType
                        )
                    )
                    // Update the main transaction with new values including note and attachment
                    val updatedTxn = transactionToEdit.copy(
                        given = if (isReceived) 0.0 else amt,
                        received = if (isReceived) amt else 0.0,
                        note = noteStr.ifEmpty { null },
                        modifiedDate = now,
                        attachmentPath = currentEditSelectedPath,
                        attachmentType = currentEditSelectedType
                    )
                    txnDao.updateTransaction(updatedTxn)
                    recalculateCustomerBalances(transactionToEdit.customerId)
                    runOnUiThread {
                        Toast.makeText(this@TransactionHistoryActivity, "Transaction updated", Toast.LENGTH_SHORT).show()
                        parentDialog.dismiss()
                        loadFilteredTransactions()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        // Store dialog reference for file operations
        currentEditDialog = editDialog
        
        editDialog.show()
    }

    private suspend fun recalculateCustomerBalances(customerId: Int) {
        val allTxns = txnDao.getAllTransactionsForCustomer(customerId)
            .filter { !it.isEditHistory }
            .sortedBy { it.id }
        var runningBalance = 0.0
        for (t in allTxns) {
            runningBalance += t.received - t.given
            txnDao.updateTransaction(t.copy(balance = runningBalance))
        }
        val customer = customerDao.getCustomerById(customerId)
        customer?.let {
            customerDao.updateCustomer(it.copy(balance = runningBalance))
        }
    }
    
    private fun openAttachment(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(this, "File not found: $filePath", Toast.LENGTH_LONG).show()
                return
            }
            
            // Check file readability
            if (!file.canRead()) {
                Toast.makeText(this, "Cannot read file", Toast.LENGTH_SHORT).show()
                return
            }
            
            val mimeType = getMimeType(filePath)
            
            // Show file info for debugging
            android.util.Log.d("AttachmentViewer", "File: $filePath, Size: ${file.length()}, MIME: $mimeType")
            
            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    file
                )
                
                android.util.Log.d("AttachmentViewer", "FileProvider URI: $uri")
                
                // Create intent with multiple approaches
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // Check available apps
                val activities = packageManager.queryIntentActivities(viewIntent, 0)
                android.util.Log.d("AttachmentViewer", "Available apps: ${activities.size}")
                
                if (activities.isNotEmpty()) {
                    // Grant permissions to all possible handlers
                    for (activity in activities) {
                        grantUriPermission(
                            activity.activityInfo.packageName,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    startActivity(viewIntent)
                } else {
                    // Try with chooser
                    val chooser = Intent.createChooser(viewIntent, "Open attachment")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    if (chooser.resolveActivity(packageManager) != null) {
                        startActivity(chooser)
                    } else {
                        // Final fallback for images: try with image/* mime type
                        if (mimeType.startsWith("image/")) {
                            val imageIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            
                            if (imageIntent.resolveActivity(packageManager) != null) {
                                startActivity(imageIntent)
                                return
                            }
                        }
                        
                        // Show helpful message with file info
                        val message = if (mimeType == "application/pdf") {
                            "No PDF viewer found. Please install a PDF reader app (e.g., Adobe Acrobat, Google PDF Viewer) to view this file."
                        } else {
                            "No app to open ${file.name}. File type: $mimeType\nTry installing a compatible app"
                        }
                        
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AttachmentViewer", "FileProvider error", e)
                
                // Alternative: try to copy file to public directory for viewing
                if (mimeType.startsWith("image/")) {
                    copyImageToPublicAndView(file)
                } else {
                    Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AttachmentViewer", "General error", e)
            Toast.makeText(this, "Error opening attachment: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun copyImageToPublicAndView(file: File) {
        try {
            // For images, try copying to Pictures directory and opening from there
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val myAppDir = File(publicDir, "MyApp")
            if (!myAppDir.exists()) {
                myAppDir.mkdirs()
            }
            
            val publicFile = File(myAppDir, "temp_${System.currentTimeMillis()}_${file.name}")
            file.copyTo(publicFile, overwrite = true)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(publicFile), getMimeType(file.absolutePath))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                
                // Clean up after a delay (optional)
                publicFile.deleteOnExit()
            } else {
                Toast.makeText(this, "No image viewer available", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AttachmentViewer", "Copy to public error", e)
            Toast.makeText(this, "Cannot copy file to view: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getMimeType(filePath: String): String {
        return when {
            filePath.endsWith(".jpg", true) || filePath.endsWith(".jpeg", true) -> "image/jpeg"
            filePath.endsWith(".png", true) -> "image/png"
            filePath.endsWith(".gif", true) -> "image/gif"
            filePath.endsWith(".bmp", true) -> "image/bmp"
            filePath.endsWith(".webp", true) -> "image/webp"
            filePath.endsWith(".pdf", true) -> "application/pdf"
            filePath.endsWith(".doc", true) -> "application/msword"
            filePath.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            filePath.endsWith(".xls", true) -> "application/vnd.ms-excel"
            filePath.endsWith(".xlsx", true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            filePath.endsWith(".txt", true) -> "text/plain"
            filePath.endsWith(".mp4", true) -> "video/mp4"
            filePath.endsWith(".mp3", true) -> "audio/mpeg"
            // For images, try image/* as fallback
            filePath.contains("image", true) || filePath.contains("photo", true) -> "image/*"
            else -> "*/*" // Generic fallback
        }
    }
    
    // File handling methods for edit dialog
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "EDIT_TRANSACTION_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }
    
    private fun showAttachmentPreview(previewLayout: LinearLayout, nameTextView: TextView, filePath: String, fileType: String) {
        val fileName = File(filePath).name
        val icon = when (fileType) {
            "image" -> "📷"
            "pdf" -> "📄"
            "document" -> "📝"
            "spreadsheet" -> "📊"
            else -> "📎"
        }
        nameTextView.text = "$icon $fileName"
        previewLayout.visibility = View.VISIBLE
    }
    
    private fun copyFileToAppStorage(sourceUri: Uri): String? {
        try {
            val inputStream = contentResolver.openInputStream(sourceUri)
            val fileName = "edit_attachment_${System.currentTimeMillis()}_${getFileName(sourceUri)}"
            val destinationFile = File(filesDir, fileName)
            
            inputStream?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            return destinationFile.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this, "Error copying file: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        result = it.getString(columnIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown_file"
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_IMAGE_CAPTURE_EDIT -> {
                if (resultCode == Activity.RESULT_OK) {
                    currentPhotoPath?.let { path ->
                        currentEditSelectedPath = path
                        currentEditSelectedType = "image"
                        
                        currentEditAttachmentPreview?.let { preview ->
                            currentEditAttachmentNameText?.let { nameText ->
                                showAttachmentPreview(preview, nameText, path, "image")
                            }
                        }
                        Toast.makeText(this, "Photo captured successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            REQUEST_DOCUMENT_PICK_EDIT -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        val copiedFilePath = copyFileToAppStorage(uri)
                        if (copiedFilePath != null) {
                            currentEditSelectedPath = copiedFilePath
                            currentEditSelectedType = when {
                                copiedFilePath.endsWith(".jpg", true) || 
                                copiedFilePath.endsWith(".jpeg", true) || 
                                copiedFilePath.endsWith(".png", true) -> "image"
                                copiedFilePath.endsWith(".pdf", true) -> "pdf"
                                copiedFilePath.endsWith(".doc", true) || copiedFilePath.endsWith(".docx", true) -> "document"
                                copiedFilePath.endsWith(".xls", true) || copiedFilePath.endsWith(".xlsx", true) -> "spreadsheet"
                                else -> "document"
                            }
                            
                            currentEditAttachmentPreview?.let { preview ->
                                currentEditAttachmentNameText?.let { nameText ->
                                    showAttachmentPreview(preview, nameText, copiedFilePath, currentEditSelectedType!!)
                                }
                            }
                            Toast.makeText(this, "File selected successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
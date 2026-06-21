package com.example.myapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.Toast
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.LinearLayout
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CustomerDetailActivity : AppCompatActivity() {

    private lateinit var customerViewModel: CustomerViewModel
    private var currentCustomer: CustomerEntity? = null
    private lateinit var recentAdapter: RecentTransactionAdapter
    
    // File attachment related variables
    private var selectedAttachmentPath: String? = null
    private var selectedAttachmentType: String? = null
    private var currentPhotoPath: String? = null
    private var currentDialogPreview: LinearLayout? = null
    private var currentDialogNameText: TextView? = null
    
    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1001
        private const val REQUEST_DOCUMENT_PICK = 1002
        private const val REQUEST_CAMERA_PERMISSION = 1003
        private const val REQUEST_ADD_TRANSACTION = 1004
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_detail)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Customer Details"

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        // Setup recent transactions RecyclerView and adapter
        val rvRecentTransactions = findViewById<RecyclerView>(R.id.rvRecentTransactions)
        recentAdapter = RecentTransactionAdapter(emptyList())
        rvRecentTransactions.adapter = recentAdapter
        rvRecentTransactions.layoutManager = LinearLayoutManager(this)

        val btnReceived = findViewById<Button>(R.id.btnReceived)
        val btnGiven = findViewById<Button>(R.id.btnGiven)
        val btnTransactionHistory = findViewById<Button>(R.id.btnTransactionHistory)
        val btnCall = findViewById<ImageButton>(R.id.btnCall)
        val btnReport = findViewById<ImageButton>(R.id.btnReport)

        customerViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(CustomerViewModel::class.java)

        btnTransactionHistory.setOnClickListener {
            currentCustomer?.let { customer ->
                val intent = Intent(this, TransactionHistoryActivity::class.java)
                intent.putExtra("customer_id", customer.id)
                intent.putExtra("customer_name", customer.name)
                startActivity(intent)
            }
        }

        btnReceived.setOnClickListener {
            currentCustomer?.let { customer ->
                val intent = Intent(this, AddTransactionActivity::class.java)
                intent.putExtra("customer_id", customer.id)
                intent.putExtra("customer_name", customer.name)
                intent.putExtra("customer_balance", customer.balance)
                intent.putExtra("is_received", true)
                startActivityForResult(intent, REQUEST_ADD_TRANSACTION)
            }
        }
        btnGiven.setOnClickListener {
            currentCustomer?.let { customer ->
                val intent = Intent(this, AddTransactionActivity::class.java)
                intent.putExtra("customer_id", customer.id)
                intent.putExtra("customer_name", customer.name)
                intent.putExtra("customer_balance", customer.balance)
                intent.putExtra("is_received", false)
                startActivityForResult(intent, REQUEST_ADD_TRANSACTION)
            }
        }

        // Profile block may not exist in all layout variants; wire it only when present.
        findViewById<View?>(R.id.profileViewBlock)?.setOnClickListener {
            currentCustomer?.let { customer ->
                val intent = Intent(this, ViewProfileActivity::class.java)
                intent.putExtra("customer_id", customer.id)
                startActivity(intent)
            }
        }

        // Call button
        btnCall.setOnClickListener {
            val mobile = currentCustomer?.mobile?.trim()
            if (mobile.isNullOrEmpty()) {
                Toast.makeText(this, "No mobile number for this customer.", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:$mobile")
                startActivity(intent)
            }
        }

        // Report button shows your new dialog
        btnReport.setOnClickListener {
            currentCustomer?.let { customer ->
                val intent = Intent(this, CustomerReportActivity::class.java)
                intent.putExtra("customer_id", customer.id)
                intent.putExtra("customer_name", customer.name)
                startActivity(intent)
            }
        }

        refreshCustomer()
    }

    override fun onResume() {
        super.onResume()
        refreshCustomer()
    }

    private fun refreshCustomer() {
        val customerId = intent.getIntExtra("customer_id", -1)
        if (customerId != -1) {
            lifecycleScope.launch {
                val customer = customerViewModel.getCustomerById(customerId)
                currentCustomer = customer
                runOnUiThread {
                    val tvCustomerName = findViewById<TextView>(R.id.tvCustomerName)
                    val tvBalanceAmount = findViewById<TextView>(R.id.tvBalanceAmount)
                    when {
                        customer != null -> {
                            tvCustomerName.text = customer.name
                            updateBalanceDisplay(customer.balance)
                            findViewById<Button>(R.id.btnReceived).isEnabled = true
                            findViewById<Button>(R.id.btnGiven).isEnabled = true

                            // Update recent transactions for this customer
                            lifecycleScope.launch {
                                val recent = CustomerDatabase.getDatabase(this@CustomerDetailActivity)
                                    .transactionDao()
                                    .getRecentTransactionsForCustomer(customer.id)
                                runOnUiThread {
                                    recentAdapter.update(recent)
                                }
                            }
                        }
                        else -> {
                            tvCustomerName.text = "Customer not found"
                            tvBalanceAmount.text = ""
                            findViewById<Button>(R.id.btnReceived).isEnabled = false
                            findViewById<Button>(R.id.btnGiven).isEnabled = false
                            recentAdapter.update(emptyList())
                        }
                    }
                }
            }
        }
    }

    private fun showAmountInputDialog(isReceived: Boolean, customer: CustomerEntity, isEdit: Boolean = false, transactionToEdit: TransactionEntity? = null) {
        // Reset attachment selection for new dialog
        selectedAttachmentPath = null
        selectedAttachmentType = null
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_transaction_input, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val btnAttachCamera = dialogView.findViewById<Button>(R.id.btnAttachCamera)
        val btnAttachFile = dialogView.findViewById<Button>(R.id.btnAttachFile)
        val attachmentPreview = dialogView.findViewById<LinearLayout>(R.id.attachmentPreview)
        val tvAttachmentName = dialogView.findViewById<TextView>(R.id.tvAttachmentName)
        val btnRemoveAttachment = dialogView.findViewById<Button>(R.id.btnRemoveAttachment)
        
        if (isEdit && transactionToEdit != null) {
            etAmount.setText(
                if (transactionToEdit.received != 0.0) transactionToEdit.received.toString() else transactionToEdit.given.toString()
            )
            etNote.setText(transactionToEdit.note ?: "")
            // If editing and has attachment, show it
            if (!transactionToEdit.attachmentPath.isNullOrEmpty()) {
                selectedAttachmentPath = transactionToEdit.attachmentPath
                selectedAttachmentType = transactionToEdit.attachmentType
                showAttachmentPreview(attachmentPreview, tvAttachmentName, transactionToEdit.attachmentPath!!, transactionToEdit.attachmentType ?: "unknown")
            }
        }
        
        // Attachment button listeners
        btnAttachCamera.setOnClickListener {
            checkCameraPermissionAndCapture()
        }
        
        btnAttachFile.setOnClickListener {
            selectDocument()
        }
        
        btnRemoveAttachment.setOnClickListener {
            selectedAttachmentPath = null
            selectedAttachmentType = null
            attachmentPreview.visibility = View.GONE
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isReceived) "Receive Amount" else "Give Amount")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val amtStr = etAmount.text.toString()
                val noteStr = etNote.text.toString()
                if (amtStr.isBlank() || amtStr.toDoubleOrNull() == null) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val amt = amtStr.toDouble()
                lifecycleScope.launch {
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    val db = CustomerDatabase.getDatabase(this@CustomerDetailActivity)
                    val txnDao = db.transactionDao()

                    var newBalance = if (isReceived) customer.balance + amt else customer.balance - amt

                    if (isEdit && transactionToEdit != null) {
                        // Save edit history
                        txnDao.insertTransaction(
                            transactionToEdit.copy(
                                id = 0,
                                modifiedDate = now,
                                isEditHistory = true,
                                originalTxnId = transactionToEdit.id
                            )
                        )
                        
                        // Update the main transaction
                        val updated = transactionToEdit.copy(
                            given = if (isReceived) 0.0 else amt,
                            received = if (isReceived) amt else 0.0,
                            note = noteStr.ifEmpty { null },
                            modifiedDate = now,
                            isEditHistory = false,
                            attachmentPath = selectedAttachmentPath ?: transactionToEdit.attachmentPath,
                            attachmentType = selectedAttachmentType ?: transactionToEdit.attachmentType
                        )
                        txnDao.updateTransaction(updated)
                        
                        // Recalculate all balances for this customer
                        recalculateCustomerBalances(transactionToEdit.customerId)
                        
                        // Get updated customer
                        val updatedCustomer = db.customerDao().getCustomerById(customer.id)
                        currentCustomer = updatedCustomer
                        newBalance = updatedCustomer?.balance ?: 0.0
                    } else {
                        // New transaction
                        val updated = customer.copy(balance = newBalance)
                        customerViewModel.updateCustomer(updated)
                        val transaction = TransactionEntity(
                            customerId = updated.id,
                            date = now,
                            given = if (isReceived) 0.0 else amt,
                            received = if (isReceived) amt else 0.0,
                            balance = updated.balance,
                            note = noteStr.ifEmpty { null },
                            attachmentPath = selectedAttachmentPath,
                            attachmentType = selectedAttachmentType
                        )
                        txnDao.insertTransaction(transaction)
                        currentCustomer = updated
                    }

                    runOnUiThread {
                        updateBalanceDisplay(newBalance)
                        Toast.makeText(
                            this@CustomerDetailActivity,
                            if (isReceived) {
                                if (isEdit) "Transaction modified!" else "Amount received added!"
                            } else {
                                if (isEdit) "Transaction modified!" else "Amount given added!"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                        refreshCustomer()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        // Show the dialog first, then set up attachment handling
        dialog.show()
        
        // Update attachment buttons to refresh the preview when files are selected
        btnAttachCamera.setOnClickListener {
            currentDialogPreview = attachmentPreview
            currentDialogNameText = tvAttachmentName
            checkCameraPermissionAndCapture()
        }
        
        btnAttachFile.setOnClickListener {
            currentDialogPreview = attachmentPreview
            currentDialogNameText = tvAttachmentName
            selectDocument()
        }
    }

    private fun updateBalanceDisplay(balance: Double) {
        val tvBalanceAmount = findViewById<TextView>(R.id.tvBalanceAmount)
        val tvBalanceLabel = findViewById<TextView>(R.id.tvBalanceLabel)
        tvBalanceLabel.text = "Balance:"
        tvBalanceLabel.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        when {
            balance < 0 -> {
                tvBalanceAmount.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                tvBalanceAmount.text = "₹%.2f".format(-balance)
            }
            balance > 0 -> {
                tvBalanceAmount.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                tvBalanceAmount.text = "+₹%.2f".format(balance)
            }
            else -> {
                tvBalanceAmount.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                tvBalanceAmount.text = "₹0.00"
            }
        }
    }

    private fun showReportDialog() {
        val customer = currentCustomer ?: return
        lifecycleScope.launch {
            val txnDao = CustomerDatabase.getDatabase(this@CustomerDetailActivity).transactionDao()
            val allTxns = txnDao.getTransactionsForCustomerRaw(customer.id)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            // Start of current year
            val yearCal = Calendar.getInstance()
            yearCal.set(Calendar.MONTH, 0)
            yearCal.set(Calendar.DAY_OF_MONTH, 1)
            yearCal.set(Calendar.HOUR_OF_DAY, 0)
            yearCal.set(Calendar.MINUTE, 0)
            yearCal.set(Calendar.SECOND, 0)
            yearCal.set(Calendar.MILLISECOND, 0)
            val thisYearStart = yearCal.timeInMillis

            val thisYearTxns = allTxns.filter {
                parseTxnDate(it.date) >= thisYearStart
            }
            val thisYearGiven = thisYearTxns.sumOf { it.given }
            val thisYearReceived = thisYearTxns.sumOf { it.received }

            // Since Joined: all transactions for this customer
            val sinceJoinedGiven = allTxns.sumOf { it.given }
            val sinceJoinedReceived = allTxns.sumOf { it.received }

            val message =
                "This year:\n" +
                        "Total Given: ₹%.2f\n".format(thisYearGiven) +
                        "Total Received: ₹%.2f\n\n".format(thisYearReceived) +
                        "Since Joined:\n" +
                        "Total Given: ₹%.2f\n".format(sinceJoinedGiven) +
                        "Total Received: ₹%.2f".format(sinceJoinedReceived)

            runOnUiThread {
                AlertDialog.Builder(this@CustomerDetailActivity)
                    .setTitle("Report")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    // Helper that parses both possible formats for robust filtering
    private fun parseTxnDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0
        try {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateStr)?.time ?: 0
        } catch (_: Exception) { }
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(dateStr)?.time ?: 0
        } catch (_: Exception) { 0 }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    // File attachment helper methods
    private fun checkCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        } else {
            dispatchTakePictureIntent()
        }
    }
    
    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.provider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }
    
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "TRANSACTION_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }
    
    private fun selectDocument() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain",
                "image/jpeg",
                "image/png",
                "image/jpg",
                "image/*"
            ))
        }
        startActivityForResult(intent, REQUEST_DOCUMENT_PICK)
    }
    
    private fun showAttachmentPreview(previewLayout: LinearLayout, nameTextView: TextView, filePath: String, fileType: String) {
        val fileName = File(filePath).name
        nameTextView.text = "📎 $fileName"
        previewLayout.visibility = View.VISIBLE
    }
    
    private fun copyFileToAppStorage(sourceUri: Uri): String? {
        try {
            val inputStream = contentResolver.openInputStream(sourceUri)
            val fileName = "attachment_${System.currentTimeMillis()}_${getFileName(sourceUri)}"
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
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakePictureIntent()
                } else {
                    Toast.makeText(this, "Camera permission required to take photos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                if (resultCode == Activity.RESULT_OK) {
                    currentPhotoPath?.let { path ->
                        selectedAttachmentPath = path
                        selectedAttachmentType = "image"
                        Toast.makeText(this, "Photo captured successfully", Toast.LENGTH_SHORT).show()
                        
                        // Update dialog preview if dialog is open
                        currentDialogPreview?.let { preview ->
                            currentDialogNameText?.let { nameText ->
                                showAttachmentPreview(preview, nameText, path, "image")
                            }
                        }
                    }
                }
            }
            REQUEST_DOCUMENT_PICK -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        val copiedFilePath = copyFileToAppStorage(uri)
                        if (copiedFilePath != null) {
                            selectedAttachmentPath = copiedFilePath
                            selectedAttachmentType = when {
                                copiedFilePath.endsWith(".jpg", true) || 
                                copiedFilePath.endsWith(".jpeg", true) || 
                                copiedFilePath.endsWith(".png", true) -> "image"
                                copiedFilePath.endsWith(".pdf", true) -> "pdf"
                                copiedFilePath.endsWith(".doc", true) || copiedFilePath.endsWith(".docx", true) -> "document"
                                copiedFilePath.endsWith(".xls", true) || copiedFilePath.endsWith(".xlsx", true) -> "spreadsheet"
                                copiedFilePath.endsWith(".txt", true) -> "document"
                                else -> "document"
                            }
                            Toast.makeText(this, "Document selected successfully", Toast.LENGTH_SHORT).show()
                            
                            // Update dialog preview if dialog is open
                            currentDialogPreview?.let { preview ->
                                currentDialogNameText?.let { nameText ->
                                    showAttachmentPreview(preview, nameText, copiedFilePath, selectedAttachmentType!!)
                                }
                            }
                        }
                    }
                }
            }
            REQUEST_ADD_TRANSACTION -> {
                if (resultCode == Activity.RESULT_OK) {
                    // Refresh the customer data and recent transactions
                    refreshCustomer()
                    Toast.makeText(this, "Transaction saved successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun recalculateCustomerBalances(customerId: Int) {
        val db = CustomerDatabase.getDatabase(this)
        val txnDao = db.transactionDao()
        val customerDao = db.customerDao()
        
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
}
package com.example.myapp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionActivity : AppCompatActivity() {
    
    private lateinit var customer: CustomerEntity
    private var isReceived: Boolean = false
    private var isEdit: Boolean = false
    private var transactionToEdit: TransactionEntity? = null
    
    // UI components
    private lateinit var etAmount: EditText
    private lateinit var etNote: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnAddAttachment: Button
    private lateinit var rvAttachments: RecyclerView
    private lateinit var tvTransactionType: TextView
    private lateinit var tvCustomerCaption: TextView
    private lateinit var btnBack: ImageButton
    
    // Attachment handling
    private lateinit var attachmentAdapter: AttachmentAdapter
    private val attachments = mutableListOf<AttachmentItem>()
    private var capturedImageFile: File? = null
    
    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1001
        private const val REQUEST_FILE_PICKER = 1002
        private const val REQUEST_GALLERY_PICKER = 1003
        private const val REQUEST_CAMERA_PERMISSION = 1004
        private const val MAX_ATTACHMENTS = 5
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)
        
        // Get data from intent
        val customerId = intent.getIntExtra("customer_id", -1)
        val customerName = intent.getStringExtra("customer_name") ?: ""
        val customerBalance = intent.getDoubleExtra("customer_balance", 0.0)
        isReceived = intent.getBooleanExtra("is_received", false)
        isEdit = intent.getBooleanExtra("is_edit", false)
        
        // Validate customer ID
        if (customerId == -1) {
            Toast.makeText(this, "Invalid customer data. Please try again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Create customer object
        customer = CustomerEntity(
            id = customerId,
            name = customerName,
            balance = customerBalance
        )
        
        // Handle edit mode
        if (isEdit) {
            val txnId = intent.getIntExtra("transaction_id", -1)
            val amount = intent.getDoubleExtra("amount", 0.0)
            val note = intent.getStringExtra("note")
            val attachmentPath = intent.getStringExtra("attachment_path")
            val attachmentType = intent.getStringExtra("attachment_type")
            
            transactionToEdit = TransactionEntity(
                id = txnId,
                customerId = customerId,
                date = "",
                given = if (!isReceived) amount else 0.0,
                received = if (isReceived) amount else 0.0,
                balance = customerBalance,
                note = note,
                attachmentPath = attachmentPath,
                attachmentType = attachmentType
            )
        }
        
        initializeViews()
        setupUI()
        setupClickListeners()
    }
    
    private fun initializeViews() {
        tvTransactionType = findViewById(R.id.tvTransactionType)
        tvCustomerCaption = findViewById(R.id.tvCustomerCaption)
        btnBack = findViewById(R.id.btnBack)
        etAmount = findViewById(R.id.etAmount)
        etNote = findViewById(R.id.etNote)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        btnAddAttachment = findViewById(R.id.btnAddAttachment)
        rvAttachments = findViewById(R.id.rvAttachments)
        
        // Setup RecyclerView
        attachmentAdapter = AttachmentAdapter(
            attachments,
            onRemoveClick = { position ->
                attachmentAdapter.removeAttachment(position)
                updateAttachmentsVisibility()
            },
            onItemClick = { attachment ->
                openAttachment(attachment.path)
            }
        )
        rvAttachments.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAttachments.adapter = attachmentAdapter
    }
    
    private fun setupUI() {
        val transactionText = if (isReceived) "Received Amount" else "Given Amount"
        tvTransactionType.text = transactionText
        // Header is now gradient, so title stays white
        tvTransactionType.setTextColor(ContextCompat.getColor(this, R.color.white))

        // Set customer caption
        val captionText = if (isReceived) "from ${customer.name}" else "to ${customer.name}"
        tvCustomerCaption.text = captionText

        title = transactionText

        // Color the save button to match transaction type
        val saveColor = if (isReceived) {
            ContextCompat.getColor(this, R.color.color_received)
        } else {
            ContextCompat.getColor(this, R.color.color_given)
        }
        btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(saveColor)
        
        // Fill data if editing
        if (isEdit && transactionToEdit != null) {
            val amount = if (isReceived) transactionToEdit!!.received else transactionToEdit!!.given
            etAmount.setText(amount.toString())
            etNote.setText(transactionToEdit!!.note ?: "")
            // Handle existing attachment
            if (!transactionToEdit!!.attachmentPath.isNullOrEmpty()) {
                val attachmentName = File(transactionToEdit!!.attachmentPath!!).name
                val attachmentItem = AttachmentItem(
                    path = transactionToEdit!!.attachmentPath!!,
                    type = if (transactionToEdit!!.attachmentType == "image") "image" else "document",
                    name = attachmentName
                )
                attachmentAdapter.addAttachment(attachmentItem)
                updateAttachmentsVisibility()
            }
        }
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        btnAddAttachment.setOnClickListener {
            showAttachmentOptions()
        }
        
        btnSave.setOnClickListener {
            saveTransaction()
        }
        
        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun saveTransaction() {
        val amtStr = etAmount.text.toString().trim()
        val noteStr = etNote.text.toString().trim()
        
        if (amtStr.isBlank()) {
            etAmount.error = "Amount is required"
            etAmount.requestFocus()
            return
        }
        
        val amount = amtStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            etAmount.error = "Enter a valid amount"
            etAmount.requestFocus()
            return
        }
        
        // Disable save button to prevent double clicks
        btnSave.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val db = CustomerDatabase.getDatabase(this@AddTransactionActivity)
                val customerDao = db.customerDao()
                val txnDao = db.transactionDao()
                
                val currentCustomer = customerDao.getCustomerById(customer.id)
                if (currentCustomer == null) {
                    runOnUiThread {
                        btnSave.isEnabled = true
                        Toast.makeText(this@AddTransactionActivity, "Customer not found. Please refresh and try again.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                val newBalance = if (isReceived) {
                    currentCustomer.balance + amount
                } else {
                    currentCustomer.balance - amount
                }
                
                if (isEdit && transactionToEdit != null) {
                    // Handle edit mode
                    // Save edit history
                    txnDao.insertTransaction(
                        transactionToEdit!!.copy(
                            id = 0,
                            modifiedDate = now,
                            isEditHistory = true,
                            originalTxnId = transactionToEdit!!.id
                        )
                    )
                    
                    // Update the main transaction
                    // Get first attachment for backward compatibility
                    val firstAttachment = attachments.firstOrNull()
                    val updatedTransaction = transactionToEdit!!.copy(
                        given = if (isReceived) 0.0 else amount,
                        received = if (isReceived) amount else 0.0,
                        note = noteStr.ifEmpty { null },
                        attachmentPath = firstAttachment?.path,
                        attachmentType = firstAttachment?.type,
                        modifiedDate = now
                    )
                    txnDao.updateTransaction(updatedTransaction)
                    
                    // Update customer balance
                    customerDao.updateCustomer(currentCustomer.copy(balance = newBalance))
                } else {
                    // Handle new transaction
                    // Get first attachment for backward compatibility
                    val firstAttachment = attachments.firstOrNull()
                    val transaction = TransactionEntity(
                        customerId = customer.id,
                        date = now,
                        given = if (isReceived) 0.0 else amount,
                        received = if (isReceived) amount else 0.0,
                        balance = newBalance,
                        note = noteStr.ifEmpty { null },
                        attachmentPath = firstAttachment?.path,
                        attachmentType = firstAttachment?.type
                    )
                    txnDao.insertTransaction(transaction)
                    
                    // Update customer balance
                    customerDao.updateCustomer(currentCustomer.copy(balance = newBalance))
                }
                
                runOnUiThread {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnSave.isEnabled = true
                    Toast.makeText(this@AddTransactionActivity, "Error saving transaction: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showAttachmentOptions() {
        if (attachments.size >= MAX_ATTACHMENTS) {
            Toast.makeText(this, "Maximum $MAX_ATTACHMENTS attachments allowed", Toast.LENGTH_SHORT).show()
            return
        }
        
        val options = arrayOf("Camera", "Gallery", "Files")
        AlertDialog.Builder(this)
            .setTitle("Add Attachment")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndCapture()
                    1 -> selectFromGallery()
                    2 -> selectDocument()
                }
            }
            .show()
    }
    
    private fun updateAttachmentsVisibility() {
        rvAttachments.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
    }
    
    private fun selectFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY_PICKER)
    }

    private fun checkCameraPermissionAndCapture() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        } else {
            captureImage()
        }
    }
    
    private fun captureImage() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                capturedImageFile = File(
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "IMG_${timestamp}.jpg"
                )
                val photoURI = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    capturedImageFile!!
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            } catch (e: Exception) {
                Toast.makeText(this, "Error capturing image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun selectDocument() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        startActivityForResult(Intent.createChooser(intent, "Select File"), REQUEST_FILE_PICKER)
    }
    

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode != Activity.RESULT_OK) return
        
        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                if (capturedImageFile?.exists() == true) {
                    addAttachmentToList(
                        path = capturedImageFile!!.absolutePath,
                        type = "image",
                        name = capturedImageFile!!.name
                    )
                }
            }
            REQUEST_GALLERY_PICKER -> {
                data?.data?.let { uri ->
                    try {
                        val fileName = getFileName(uri) ?: "image_${System.currentTimeMillis()}.jpg"
                        val file = copyUriToFile(uri, fileName)
                        addAttachmentToList(
                            path = file.absolutePath,
                            type = "image",
                            name = fileName
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            REQUEST_FILE_PICKER -> {
                data?.data?.let { uri ->
                    try {
                        val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
                        val file = copyUriToFile(uri, fileName)
                        val mimeType = contentResolver.getType(uri)
                        val type = when {
                            mimeType?.startsWith("image/") == true -> "image"
                            else -> "document"
                        }
                        addAttachmentToList(
                            path = file.absolutePath,
                            type = type,
                            name = fileName
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun addAttachmentToList(path: String, type: String, name: String) {
        if (attachments.size >= MAX_ATTACHMENTS) {
            Toast.makeText(this, "Maximum $MAX_ATTACHMENTS attachments allowed", Toast.LENGTH_SHORT).show()
            return
        }
        
        val attachment = AttachmentItem(path, type, name)
        attachmentAdapter.addAttachment(attachment)
        updateAttachmentsVisibility()
    }
    
    private fun getFileName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    }
    
    private fun copyUriToFile(uri: Uri, fileName: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(getExternalFilesDir(null), "${timestamp}_$fileName")
        
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        return file
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    captureImage()
                } else {
                    Toast.makeText(this, "Camera permission required for capturing images", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun openAttachment(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(this, "File not found: $filePath", Toast.LENGTH_LONG).show()
                return
            }
            
            if (!file.canRead()) {
                Toast.makeText(this, "Cannot read file", Toast.LENGTH_SHORT).show()
                return
            }
            
            val mimeType = getMimeType(filePath)
            
            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    file
                )
                
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                val activities = packageManager.queryIntentActivities(viewIntent, 0)
                
                if (activities.isNotEmpty()) {
                    for (activity in activities) {
                        grantUriPermission(
                            activity.activityInfo.packageName,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    startActivity(viewIntent)
                } else {
                    // Try with chooser first
                    val chooser = Intent.createChooser(viewIntent, "Open attachment")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    if (chooser.resolveActivity(packageManager) != null) {
                        startActivity(chooser)
                    } else {
                        // Special handling for PDF files
                        if (mimeType == "application/pdf") {
                            // Try alternative MIME types for PDF
                            val alternativePdfIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            
                            if (alternativePdfIntent.resolveActivity(packageManager) != null) {
                                startActivity(alternativePdfIntent)
                                return
                            }
                        }
                        
                        // Show specific message for PDF files
                        val message = if (mimeType == "application/pdf") {
                            "No PDF viewer found. Please install a PDF reader app (e.g., Adobe Acrobat, Google PDF Viewer) to view this file."
                        } else {
                            "No app found to open ${file.name}. File type: $mimeType\nTry installing a compatible app."
                        }
                        
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AttachmentViewer", "FileProvider error", e)
                Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AttachmentViewer", "General error", e)
            Toast.makeText(this, "Error opening attachment: ${e.message}", Toast.LENGTH_LONG).show()
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
            else -> "application/octet-stream"
        }
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
}
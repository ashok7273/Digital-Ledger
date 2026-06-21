package com.example.myapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionDetailActivity : AppCompatActivity() {

    private lateinit var txnDao: TransactionDao
    private lateinit var customerDao: CustomerDao
    private var transactionId: Int = -1
    private var currentTransaction: TransactionEntity? = null

    private var currentPhotoPath: String? = null
    private var currentEditAttachmentPreview: LinearLayout? = null
    private var currentEditAttachmentNameText: TextView? = null
    private var currentEditSelectedPath: String? = null
    private var currentEditSelectedType: String? = null

    companion object {
        private const val REQUEST_IMAGE_CAPTURE_EDIT = 3001
        private const val REQUEST_DOCUMENT_PICK_EDIT = 3002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)

        transactionId = intent.getIntExtra("transaction_id", -1)
        if (transactionId == -1) {
            finish()
            return
        }

        txnDao = CustomerDatabase.getDatabase(this).transactionDao()
        customerDao = CustomerDatabase.getDatabase(this).customerDao()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnEditTxn).setOnClickListener {
            currentTransaction?.let { txn -> showEditTransactionDialog(txn) }
        }
        findViewById<Button>(R.id.btnDeleteTxn).setOnClickListener {
            currentTransaction?.let { txn -> confirmDelete(txn) }
        }

        loadTransactionDetails()
    }

    private fun loadTransactionDetails() {
        lifecycleScope.launch {
            val txn = txnDao.getTransactionById(transactionId)
            if (txn == null) {
                runOnUiThread { finish() }
                return@launch
            }

            currentTransaction = txn
            val modLogs = txnDao.getModificationHistory(txn.id)
                .filter { it.isEditHistory }
                .sortedBy { it.date }

            runOnUiThread {
                renderTransaction(txn, modLogs)
            }
        }
    }

    private fun renderTransaction(txn: TransactionEntity, modLogs: List<TransactionEntity>) {
        val tvDetailTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val tvAmount = findViewById<TextView>(R.id.tvTxnDetailAmount)
        val tvEntryNote = findViewById<TextView>(R.id.tvEntryNote)
        val tvEntryAdded = findViewById<TextView>(R.id.tvEntryAdded)
        val tvModifyHeader = findViewById<TextView>(R.id.tvModifyHeader)
        val llModifications = findViewById<LinearLayout>(R.id.llModifications)
        val llAttachment = findViewById<LinearLayout>(R.id.llAttachment)
        val tvAttachmentName = findViewById<TextView>(R.id.tvAttachmentName)
        val btnViewAttachment = findViewById<Button>(R.id.btnViewAttachment)

        tvDetailTitle.text = txn.date

        if (txn.received != 0.0) {
            tvAmount.setTextColor(Color.parseColor("#388E3C"))
            tvAmount.text = "+₹%.2f".format(txn.received)
        } else if (txn.given != 0.0) {
            tvAmount.setTextColor(Color.parseColor("#D32F2F"))
            tvAmount.text = "₹%.2f".format(txn.given)
        } else {
            tvAmount.setTextColor(Color.BLACK)
            tvAmount.text = "₹0.00"
        }

        tvEntryNote.text = if (!txn.note.isNullOrBlank()) "Description: ${txn.note}" else "Description: -"
        val entryAmt = if (txn.received != 0.0) "+₹%.2f".format(txn.received) else "₹%.2f".format(txn.given)
        tvEntryAdded.text = "Entry Added: ${txn.date}   Amount: $entryAmt"

        llModifications.removeAllViews()
        if (modLogs.isNotEmpty()) {
            tvModifyHeader.visibility = View.VISIBLE
            for (mod in modLogs) {
                val row = TextView(this)
                val amtField = if (mod.received != 0.0) "+₹%.2f".format(mod.received) else "₹%.2f".format(mod.given)
                row.text = "${mod.date}  Amount: $amtField"
                row.setTextColor(getColor(R.color.text_primary))
                row.textSize = 13f
                row.setPadding(0, 4, 0, 4)
                llModifications.addView(row)
            }
        } else {
            tvModifyHeader.visibility = View.GONE
        }

        if (!txn.attachmentPath.isNullOrEmpty()) {
            llAttachment.visibility = View.VISIBLE
            val fileName = File(txn.attachmentPath).name
            val icon = when (txn.attachmentType) {
                "image" -> "📷"
                "pdf" -> "📄"
                "document" -> "📝"
                "spreadsheet" -> "📊"
                else -> "📎"
            }
            tvAttachmentName.text = "$icon $fileName"
            btnViewAttachment.setOnClickListener { openAttachment(txn.attachmentPath!!) }
        } else {
            llAttachment.visibility = View.GONE
        }
    }

    private fun confirmDelete(txn: TransactionEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    txnDao.deleteTransaction(txn)
                    recalculateCustomerBalances(txn.customerId)
                    runOnUiThread {
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditTransactionDialog(transactionToEdit: TransactionEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_transaction_input, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val btnAttachCamera = dialogView.findViewById<Button>(R.id.btnAttachCamera)
        val btnAttachFile = dialogView.findViewById<Button>(R.id.btnAttachFile)
        val attachmentPreview = dialogView.findViewById<LinearLayout>(R.id.attachmentPreview)
        val tvAttachmentName = dialogView.findViewById<TextView>(R.id.tvAttachmentName)
        val btnRemoveAttachment = dialogView.findViewById<Button>(R.id.btnRemoveAttachment)

        etAmount.setText(
            if (transactionToEdit.received != 0.0) transactionToEdit.received.toString()
            else transactionToEdit.given.toString()
        )
        etNote.setText(transactionToEdit.note ?: "")

        currentEditSelectedPath = transactionToEdit.attachmentPath
        currentEditSelectedType = transactionToEdit.attachmentType

        if (!transactionToEdit.attachmentPath.isNullOrEmpty()) {
            showAttachmentPreview(
                attachmentPreview,
                tvAttachmentName,
                transactionToEdit.attachmentPath!!,
                transactionToEdit.attachmentType ?: "unknown"
            )
        }

        currentEditAttachmentPreview = attachmentPreview
        currentEditAttachmentNameText = tvAttachmentName

        btnAttachCamera.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(packageManager) != null) {
                try {
                    val photoFile = createImageFile()
                    val photoURI = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    currentPhotoPath = photoFile.absolutePath
                    startActivityForResult(intent, REQUEST_IMAGE_CAPTURE_EDIT)
                } catch (_: IOException) {
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
        AlertDialog.Builder(this)
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

                    txnDao.insertTransaction(
                        TransactionEntity(
                            id = 0,
                            customerId = transactionToEdit.customerId,
                            date = now,
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
                        Toast.makeText(this@TransactionDetailActivity, "Transaction updated", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        loadTransactionDetails()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                Toast.makeText(this, "File not found", Toast.LENGTH_LONG).show()
                return
            }
            val mimeType = getMimeType(filePath)
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (viewIntent.resolveActivity(packageManager) != null) {
                startActivity(viewIntent)
            } else {
                Toast.makeText(this, "No app found to open this file", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening attachment: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getMimeType(filePath: String): String {
        return when {
            filePath.endsWith(".jpg", true) || filePath.endsWith(".jpeg", true) -> "image/jpeg"
            filePath.endsWith(".png", true) -> "image/png"
            filePath.endsWith(".pdf", true) -> "application/pdf"
            filePath.endsWith(".doc", true) -> "application/msword"
            filePath.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            filePath.endsWith(".xls", true) -> "application/vnd.ms-excel"
            filePath.endsWith(".xlsx", true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            filePath.endsWith(".txt", true) -> "text/plain"
            else -> "*/*"
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile("EDIT_TRANSACTION_${timeStamp}_", ".jpg", storageDir).apply {
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
        return try {
            val inputStream = contentResolver.openInputStream(sourceUri)
            val fileName = "edit_attachment_${System.currentTimeMillis()}_${getFileName(sourceUri)}"
            val destinationFile = File(filesDir, fileName)
            inputStream?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this, "Error copying file: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) result = it.getString(columnIndex)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) result = result?.substring(cut + 1)
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

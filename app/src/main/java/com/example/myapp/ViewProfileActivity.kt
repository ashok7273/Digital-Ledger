package com.example.myapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.ViewGroup
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewProfileActivity : AppCompatActivity() {

    private var customerId: Int = -1
    private var customer: CustomerEntity? = null
    private var isEditMode: Boolean = false
    private var pendingCameraFile: File? = null
    private var pendingMediaAction: MediaAction? = null

    private enum class MediaAction {
        CAMERA, GALLERY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_profile)

        customerId = intent.getIntExtra("customer_id", -1)

        val btnProfileBack = findViewById<ImageButton>(R.id.btnProfileBack)
        val tvProfileName = findViewById<TextView>(R.id.tvProfileName)
        val tvProfileLetter = findViewById<TextView>(R.id.tvProfileLetter)
        val profilePhotoContainer = findViewById<FrameLayout>(R.id.profilePhotoContainer)
        val profileCircle = findViewById<ImageView>(R.id.profilePicCircle)
        val etProfileMobile = findViewById<EditText>(R.id.etProfileMobile)
        val etProfileAddress = findViewById<EditText>(R.id.etProfileAddress)
        val etProfileEmail = findViewById<EditText>(R.id.etProfileEmail)
        val etProfileAadhar = findViewById<EditText>(R.id.etProfileAadhar)
        val etProfileGST = findViewById<EditText>(R.id.etProfileGST)
        val btnSaveProfile = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveProfile)
        val btnDeleteProfile = findViewById<Button>(R.id.btnDeleteProfile)

        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val copied = copyUriToPrivateFile(uri)
                persistCustomerPhoto(copied.absolutePath)
            } catch (_: Exception) {
                Toast.makeText(this, "Failed to select image", Toast.LENGTH_SHORT).show()
            }
        }

        val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (!success) return@registerForActivityResult
            val file = pendingCameraFile
            if (file != null && file.exists()) {
                persistCustomerPhoto(file.absolutePath)
            }
            pendingCameraFile = null
        }

        val mediaPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted = result.values.all { it }
            if (!granted) {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                pendingMediaAction = null
                return@registerForActivityResult
            }

            when (pendingMediaAction) {
                MediaAction.CAMERA -> {
                    openCameraCapture(
                        onPrepared = { file -> pendingCameraFile = file },
                        onError = { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
                    ) { uri -> takePictureLauncher.launch(uri) }
                }

                MediaAction.GALLERY -> pickImageLauncher.launch("image/*")
                null -> Unit
            }
            pendingMediaAction = null
        }

        fun setEditMode(enabled: Boolean) {
            val fields = listOf(etProfileMobile, etProfileAddress, etProfileEmail, etProfileAadhar, etProfileGST)
            fields.forEach { field ->
                field.isEnabled = enabled
                field.isFocusable = enabled
                field.isFocusableInTouchMode = enabled
                field.isCursorVisible = enabled
            }

            btnSaveProfile.text = if (enabled) "Save Profile" else "Edit Profile"
            if (enabled) {
                btnSaveProfile.backgroundTintList = ColorStateList.valueOf(getColor(R.color.primary_blue))
                btnSaveProfile.setTextColor(getColor(R.color.white))
            } else {
                btnSaveProfile.backgroundTintList = ColorStateList.valueOf(getColor(R.color.primary_blue_light))
                btnSaveProfile.setTextColor(getColor(R.color.primary_blue))
            }
        }

        fun loadCustomerData() {
            customer?.let { c ->
                tvProfileName.text = c.name
                ProfileImageUtils.applyProfileVisual(
                    imagePath = c.profileImagePath,
                    displayName = c.name,
                    imageView = profileCircle,
                    initialView = tvProfileLetter
                )

                etProfileMobile.setText(c.mobile?.takeIf { it.isNotBlank() } ?: "")
                etProfileAddress.setText(c.address?.takeIf { it.isNotBlank() } ?: "")
                etProfileEmail.setText(c.email?.takeIf { it.isNotBlank() } ?: "")
                etProfileAadhar.setText(c.aadhar?.takeIf { it.isNotBlank() } ?: "")
                etProfileGST.setText(c.gstNo?.takeIf { it.isNotBlank() } ?: "")
            }
        }

        fun exitEditModeWithoutSaving() {
            isEditMode = false
            loadCustomerData()
            setEditMode(enabled = false)
        }

        profilePhotoContainer.setOnClickListener {
            val activeCustomer = customer ?: return@setOnClickListener
            val imagePath = activeCustomer.profileImagePath
            showProfilePhotoOptions(
                hasImage = !imagePath.isNullOrBlank() && File(imagePath).exists(),
                onViewPhoto = {
                    if (imagePath.isNullOrBlank()) {
                        Toast.makeText(this, "No profile photo to view", Toast.LENGTH_SHORT).show()
                    } else {
                        showFullScreenPhoto(
                            imagePath = imagePath,
                            onUpdatePhoto = {
                                showUpdatePhotoOptions(
                                    onPickCamera = {
                                        pendingMediaAction = MediaAction.CAMERA
                                        ensurePermissionFor(
                                            MediaAction.CAMERA,
                                            onGranted = {
                                                openCameraCapture(
                                                    onPrepared = { file -> pendingCameraFile = file },
                                                    onError = { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
                                                ) { uri -> takePictureLauncher.launch(uri) }
                                            },
                                            onRequest = { permissions -> mediaPermissionLauncher.launch(permissions) }
                                        )
                                    },
                                    onPickGallery = {
                                        pendingMediaAction = MediaAction.GALLERY
                                        ensurePermissionFor(
                                            MediaAction.GALLERY,
                                            onGranted = { pickImageLauncher.launch("image/*") },
                                            onRequest = { permissions -> mediaPermissionLauncher.launch(permissions) }
                                        )
                                    }
                                )
                            },
                            onRemovePhoto = {
                                removeProfilePhoto {
                                    persistCustomerPhoto(null)
                                }
                            }
                        )
                    }
                },
                onUpdatePhoto = {
                    showUpdatePhotoOptions(
                        onPickCamera = {
                            pendingMediaAction = MediaAction.CAMERA
                            ensurePermissionFor(
                                MediaAction.CAMERA,
                                onGranted = {
                                    openCameraCapture(
                                        onPrepared = { file -> pendingCameraFile = file },
                                        onError = { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
                                    ) { uri -> takePictureLauncher.launch(uri) }
                                },
                                onRequest = { permissions -> mediaPermissionLauncher.launch(permissions) }
                            )
                        },
                        onPickGallery = {
                            pendingMediaAction = MediaAction.GALLERY
                            ensurePermissionFor(
                                MediaAction.GALLERY,
                                onGranted = { pickImageLauncher.launch("image/*") },
                                onRequest = { permissions -> mediaPermissionLauncher.launch(permissions) }
                            )
                        }
                    )
                },
                onRemovePhoto = {
                    removeProfilePhoto {
                        persistCustomerPhoto(null)
                    }
                }
            )
        }

        btnProfileBack.setOnClickListener {
            if (isEditMode) {
                exitEditModeWithoutSaving()
            } else {
                finish()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isEditMode) {
                    exitEditModeWithoutSaving()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Load customer
        lifecycleScope.launch {
            val customerEntity = CustomerDatabase.getDatabase(this@ViewProfileActivity)
                .customerDao().getCustomerById(customerId)
            customer = customerEntity
            loadCustomerData()
        }

        setEditMode(enabled = false)

        btnSaveProfile.setOnClickListener {
            if (!isEditMode) {
                isEditMode = true
                setEditMode(enabled = true)
                return@setOnClickListener
            }

            // Validate and save
            val mobile = etProfileMobile.text.toString().trim()
            val address = etProfileAddress.text.toString().trim()
            val email = etProfileEmail.text.toString().trim()
            val aadhar = etProfileAadhar.text.toString().trim()
            val gstNo = etProfileGST.text.toString().trim()

            if (mobile.isNotEmpty() && !mobile.matches(Regex("^\\d{10}\$"))) {
                Toast.makeText(this, "Mobile must be exactly 10 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Invalid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (aadhar.isNotEmpty() && !aadhar.matches(Regex("^\\d{12}\$"))) {
                Toast.makeText(this, "Aadhar must be exactly 12 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            customer?.let { c ->
                lifecycleScope.launch {
                    val updated = c.copy(
                        mobile = mobile,
                        address = address,
                        email = email,
                        aadhar = aadhar,
                        gstNo = gstNo
                    )
                    CustomerDatabase.getDatabase(this@ViewProfileActivity).customerDao().updateCustomer(updated)
                    customer = updated
                    loadCustomerData()
                    setResult(RESULT_OK)

                    Toast.makeText(this@ViewProfileActivity, "Profile saved!", Toast.LENGTH_SHORT).show()
                    isEditMode = false
                    setEditMode(enabled = false)
                }
            }
        }

        btnDeleteProfile.setOnClickListener {
            customer?.let { c ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Customer")
                    .setMessage("Delete this customer and all their transactions?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            val db = CustomerDatabase.getDatabase(this@ViewProfileActivity)
                            db.customerDao().deleteCustomer(c)
                            db.transactionDao().getAllTransactionsForCustomer(c.id)
                                .forEach { db.transactionDao().deleteTransaction(it) }
                            runOnUiThread {
                                Toast.makeText(this@ViewProfileActivity, "Customer deleted", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@ViewProfileActivity, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun persistCustomerPhoto(path: String?) {
        val currentCustomer = customer ?: return
        lifecycleScope.launch {
            val updated = currentCustomer.copy(profileImagePath = path)
            CustomerDatabase.getDatabase(this@ViewProfileActivity).customerDao().updateCustomer(updated)
            customer = updated
            runOnUiThread {
                val profileCircle = findViewById<ImageView>(R.id.profilePicCircle)
                val tvProfileLetter = findViewById<TextView>(R.id.tvProfileLetter)
                val tvProfileName = findViewById<TextView>(R.id.tvProfileName)
                tvProfileName.text = updated.name
                ProfileImageUtils.applyProfileVisual(
                    imagePath = updated.profileImagePath,
                    displayName = updated.name,
                    imageView = profileCircle,
                    initialView = tvProfileLetter
                )
                setResult(RESULT_OK)
            }
        }
    }

    private fun ensurePermissionFor(
        action: MediaAction,
        onGranted: () -> Unit,
        onRequest: (Array<String>) -> Unit
    ) {
        val required = when (action) {
            MediaAction.CAMERA -> arrayOf(Manifest.permission.CAMERA)
            MediaAction.GALLERY -> emptyArray()
        }

        val denied = required.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (denied.isEmpty()) {
            onGranted()
        } else {
            onRequest(denied.toTypedArray())
        }
    }

    private fun openCameraCapture(
        onPrepared: (File) -> Unit,
        onError: (String) -> Unit,
        launch: (Uri) -> Unit
    ) {
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (dir == null) {
                onError("Storage not available")
                return
            }
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "CUSTOMER_PROFILE_${timestamp}.jpg")
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            onPrepared(file)
            launch(uri)
        } catch (_: Exception) {
            onError("Unable to open camera")
        }
    }

    private fun copyUriToPrivateFile(uri: Uri): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "CUSTOMER_PROFILE_PICK_${timestamp}.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read selected image")

        return file
    }

    private fun showProfilePhotoOptions(
        hasImage: Boolean,
        onViewPhoto: () -> Unit,
        onUpdatePhoto: () -> Unit,
        onRemovePhoto: () -> Unit
    ) {
        if (hasImage) {
            AlertDialog.Builder(this)
                .setTitle("Profile Picture")
                .setItems(arrayOf("View Photo", "Update Photo", "Remove Photo")) { _, which ->
                    when (which) {
                        0 -> onViewPhoto()
                        1 -> onUpdatePhoto()
                        2 -> onRemovePhoto()
                    }
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Profile Picture")
                .setItems(arrayOf("Update Photo")) { _, _ -> onUpdatePhoto() }
                .show()
        }
    }

    private fun showUpdatePhotoOptions(
        onPickCamera: () -> Unit,
        onPickGallery: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle("Update Profile Picture")
            .setItems(arrayOf("Camera", "Gallery")) { _, which ->
                when (which) {
                    0 -> onPickCamera()
                    1 -> onPickGallery()
                }
            }
            .show()
    }

    private fun removeProfilePhoto(onRemoved: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Remove Photo")
            .setMessage("Remove current profile photo?")
            .setPositiveButton("Remove") { _, _ ->
                onRemoved()
                Toast.makeText(this, "Profile photo removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFullScreenPhoto(
        imagePath: String,
        onUpdatePhoto: () -> Unit,
        onRemovePhoto: () -> Unit
    ) {
        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            Toast.makeText(this, "Photo not found", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val fullImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageURI(Uri.fromFile(imageFile))
            setOnClickListener { dialog.dismiss() }
        }

        val backButton = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (44 * resources.displayMetrics.density).toInt(),
                (44 * resources.displayMetrics.density).toInt()
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = (24 * resources.displayMetrics.density).toInt()
                marginStart = (16 * resources.displayMetrics.density).toInt()
            }
            background = AppCompatResources.getDrawable(context, R.drawable.photo_overlay_icon_bg)
            setImageResource(R.drawable.baseline_arrow_back_24)
            imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(
                (10 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
            contentDescription = "Back"
            setOnClickListener { dialog.dismiss() }
        }

        val editButton = Button(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (24 * resources.displayMetrics.density).toInt()
                marginEnd = (16 * resources.displayMetrics.density).toInt()
            }
            background = AppCompatResources.getDrawable(context, R.drawable.photo_overlay_action_bg)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            text = "Edit Photo"
            setAllCaps(false)
            backgroundTintList = null
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            elevation = 0f
            contentDescription = "Edit photo"
            setOnClickListener {
                AlertDialog.Builder(this@ViewProfileActivity)
                    .setTitle("Photo Options")
                    .setItems(arrayOf("Update Photo", "Remove Photo")) { _, which ->
                        when (which) {
                            0 -> {
                                dialog.dismiss()
                                onUpdatePhoto()
                            }

                            1 -> {
                                dialog.dismiss()
                                onRemovePhoto()
                            }
                        }
                    }
                    .show()
            }
        }

        root.addView(fullImage)
        root.addView(backButton)
        root.addView(editButton)
        dialog.setContentView(root)
        dialog.show()
    }
}

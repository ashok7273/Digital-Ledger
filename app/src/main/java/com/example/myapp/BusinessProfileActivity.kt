package com.example.myapp

import android.Manifest
import android.app.Dialog
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class BusinessProfileActivity : AppCompatActivity() {
    private lateinit var prefs: android.content.SharedPreferences

    private enum class MediaAction {
        CAMERA, GALLERY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_profile)

        prefs = getSharedPreferences("business_profile", MODE_PRIVATE)
        val etBusinessName = findViewById<EditText>(R.id.etBusinessName)
        val tvProfileInitial = findViewById<TextView>(R.id.tvProfileInitial)
        val profileCircle = findViewById<ImageView>(R.id.profilePicCircle)
        val profilePhotoContainer = findViewById<FrameLayout>(R.id.profilePhotoContainer)
        val etProfileName = findViewById<EditText>(R.id.etProfileName)
        val etProfileMobile = findViewById<EditText>(R.id.etProfileMobile)
        val etProfileAddress = findViewById<EditText>(R.id.etProfileAddress)
        val etProfileEmail = findViewById<EditText>(R.id.etProfileEmail)
        val etProfileGSTIN = findViewById<EditText>(R.id.etProfileGSTIN)
        val spinnerBusinessType = findViewById<Spinner>(R.id.spinnerBusinessType)
        val spinnerBusinessCategory = findViewById<Spinner>(R.id.spinnerBusinessCategory)
        val etContactPerson = findViewById<EditText>(R.id.etContactPerson)
        val btnSave = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveBusinessProfile)
        val btnBack = findViewById<ImageButton>(R.id.btnProfileBack)
        var isEditMode = false
        var pendingMediaAction: MediaAction? = null
        var pendingCameraFile: File? = null
        var savedImagePath: String? = prefs.getString("profile_image_path", null)
        var draftImagePath: String? = savedImagePath

        val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val copied = copyUriToPrivateFile(uri)
                draftImagePath = copied.absolutePath
                saveProfileImagePath(draftImagePath)
                applyProfileVisual(
                    imagePath = draftImagePath,
                    businessName = etBusinessName.text.toString(),
                    imageView = profileCircle,
                    initialView = tvProfileInitial
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to select image", Toast.LENGTH_SHORT).show()
            }
        }

        val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (!success) return@registerForActivityResult
            val file = pendingCameraFile
            if (file != null && file.exists()) {
                draftImagePath = file.absolutePath
                saveProfileImagePath(draftImagePath)
                applyProfileVisual(
                    imagePath = draftImagePath,
                    businessName = etBusinessName.text.toString(),
                    imageView = profileCircle,
                    initialView = tvProfileInitial
                )
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
                        onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                    ) { uri -> takePictureLauncher.launch(uri) }
                }
                MediaAction.GALLERY -> pickImageLauncher.launch("image/*")
                null -> Unit
            }
            pendingMediaAction = null
        }

        // Spinner setup
        val typeList = listOf("Retail", "Wholesale", "Online Services", "Other")
        val catList = listOf("Kirana", "Photo Studio", "Apparel Store", "Jewellery", "Other")
        spinnerBusinessType.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeList)
        spinnerBusinessCategory.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, catList)

        fun loadBusinessFields() {
            etBusinessName.setText(prefs.getString("business_name", "My Business"))
            etProfileName.setText(prefs.getString("profile_name", prefs.getString("profile_mobile", "")))
            etProfileMobile.setText(prefs.getString("profile_mobile", ""))
            etProfileAddress.setText(prefs.getString("profile_address", ""))
            etProfileEmail.setText(prefs.getString("profile_email", ""))
            etProfileGSTIN.setText(prefs.getString("profile_gstin", ""))
            etContactPerson.setText(prefs.getString("contact_person", ""))
            spinnerBusinessType.setSelection(
                typeList.indexOf(prefs.getString("business_type", typeList[0])).coerceAtLeast(0)
            )
            spinnerBusinessCategory.setSelection(
                catList.indexOf(prefs.getString("business_category", catList[0])).coerceAtLeast(0)
            )
        }

        loadBusinessFields()

        // Avatar initials/image logic (and live updates)
        val colors = listOf("#9575CD", "#4FC3F7", "#81C784", "#FFD54F", "#FF8A65", "#F06292")
        fun updateAvatar() {
            applyProfileVisual(
                imagePath = draftImagePath,
                businessName = etBusinessName.text.toString(),
                imageView = profileCircle,
                initialView = tvProfileInitial,
                colors = colors
            )
        }
        updateAvatar()
        etBusinessName.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) updateAvatar() }
        etBusinessName.setOnEditorActionListener { _, _, _ -> updateAvatar(); false }
        etBusinessName.setOnKeyListener { _, _, _ -> updateAvatar(); false }

        fun setEditMode(enabled: Boolean) {
            val textFields = listOf(
                etBusinessName,
                etProfileName,
                etProfileMobile,
                etProfileAddress,
                etProfileEmail,
                etProfileGSTIN,
                etContactPerson
            )
            textFields.forEach { field ->
                field.isEnabled = enabled
                field.isFocusable = enabled
                field.isFocusableInTouchMode = enabled
                field.isCursorVisible = enabled
                field.isLongClickable = enabled
            }
            spinnerBusinessType.isEnabled = enabled
            spinnerBusinessCategory.isEnabled = enabled

            btnSave.text = if (enabled) "Save Profile" else "Edit Profile"
            if (enabled) {
                btnSave.backgroundTintList = ColorStateList.valueOf(getColor(R.color.primary_blue))
                btnSave.setTextColor(getColor(R.color.white))
            } else {
                btnSave.backgroundTintList = ColorStateList.valueOf(getColor(R.color.primary_blue_light))
                btnSave.setTextColor(getColor(R.color.primary_blue))
            }
        }

        fun exitEditModeWithoutSaving() {
            isEditMode = false
            savedImagePath = prefs.getString("profile_image_path", null)
            draftImagePath = savedImagePath
            loadBusinessFields()
            updateAvatar()
            setEditMode(enabled = false)
        }

        btnBack.setOnClickListener {
            if (isEditMode) {
                exitEditModeWithoutSaving()
            } else {
                finish()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isEditMode) {
                    exitEditModeWithoutSaving()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Screen opens in view mode; user must tap Edit Profile to edit.
        setEditMode(enabled = false)

        profilePhotoContainer.setOnClickListener {
            showProfilePhotoOptions(
                hasImage = !draftImagePath.isNullOrBlank() && File(draftImagePath!!).exists(),
                onViewPhoto = {
                    if (draftImagePath.isNullOrBlank()) {
                        Toast.makeText(this, "No profile photo to view", Toast.LENGTH_SHORT).show()
                    } else {
                        showFullScreenPhoto(
                            imagePath = draftImagePath!!,
                            onUpdatePhoto = {
                                showUpdatePhotoOptions(
                                    onPickCamera = {
                                        pendingMediaAction = MediaAction.CAMERA
                                        ensurePermissionFor(
                                            MediaAction.CAMERA,
                                            onGranted = {
                                                openCameraCapture(
                                                    onPrepared = { file -> pendingCameraFile = file },
                                                    onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                                                ) { uri -> takePictureLauncher.launch(uri) }
                                            },
                                            onRequest = { perms -> mediaPermissionLauncher.launch(perms) }
                                        )
                                    },
                                    onPickGallery = {
                                        pendingMediaAction = MediaAction.GALLERY
                                        ensurePermissionFor(
                                            MediaAction.GALLERY,
                                            onGranted = { pickImageLauncher.launch("image/*") },
                                            onRequest = { perms -> mediaPermissionLauncher.launch(perms) }
                                        )
                                    }
                                )
                            },
                            onRemovePhoto = {
                                removeProfilePhoto(
                                    onRemoved = {
                                        draftImagePath = null
                                        saveProfileImagePath(null)
                                        applyProfileVisual(
                                            imagePath = null,
                                            businessName = etBusinessName.text.toString(),
                                            imageView = profileCircle,
                                            initialView = tvProfileInitial
                                        )
                                    }
                                )
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
                                        onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                                    ) { uri -> takePictureLauncher.launch(uri) }
                                },
                                onRequest = { perms -> mediaPermissionLauncher.launch(perms) }
                            )
                        },
                        onPickGallery = {
                            pendingMediaAction = MediaAction.GALLERY
                            ensurePermissionFor(
                                MediaAction.GALLERY,
                                onGranted = { pickImageLauncher.launch("image/*") },
                                onRequest = { perms -> mediaPermissionLauncher.launch(perms) }
                            )
                        }
                    )
                },
                onRemovePhoto = {
                    removeProfilePhoto(
                        onRemoved = {
                            draftImagePath = null
                            saveProfileImagePath(null)
                            applyProfileVisual(
                                imagePath = null,
                                businessName = etBusinessName.text.toString(),
                                imageView = profileCircle,
                                initialView = tvProfileInitial
                            )
                        }
                    )
                }
            )
        }

        btnSave.setOnClickListener {
            if (!isEditMode) {
                isEditMode = true
                setEditMode(enabled = true)
                return@setOnClickListener
            }

            val businessName = etBusinessName.text.toString().trim()
            val profileName = etProfileName.text.toString().trim()
            val mobile = etProfileMobile.text.toString().trim()
            val address = etProfileAddress.text.toString().trim()
            val email = etProfileEmail.text.toString().trim()
            val gstin = etProfileGSTIN.text.toString().trim()
            val businessType = spinnerBusinessType.selectedItem?.toString() ?: ""
            val businessCategory = spinnerBusinessCategory.selectedItem?.toString() ?: ""
            val contactPerson = etContactPerson.text.toString().trim()

            // Validation (GSTIN, Email, Mobile, as per your needs)
            if (mobile.isNotEmpty() && !mobile.matches(Regex("^\\d{10}\$"))) {
                Toast.makeText(this, "Mobile must be exactly 10 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Invalid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("business_name", businessName)
                .putString("profile_name", profileName)
                .putString("profile_mobile", mobile)
                .putString("profile_address", address)
                .putString("profile_email", email)
                .putString("profile_gstin", gstin)
                .putString("business_type", businessType)
                .putString("business_category", businessCategory)
                .putString("contact_person", contactPerson)
                .putString("profile_image_path", draftImagePath)
                .apply()

            // Optionally: notify main page to update business name
            sendBroadcast(Intent("com.example.myapp.BUSINESS_PROFILE_UPDATED"))

            Toast.makeText(this, "Business profile saved!", Toast.LENGTH_SHORT).show()
            isEditMode = false
            savedImagePath = draftImagePath
            loadBusinessFields()
            updateAvatar()
            setEditMode(enabled = false)
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
            val file = File(dir, "PROFILE_${timestamp}.jpg")
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            onPrepared(file)
            launch(uri)
        } catch (e: Exception) {
            onError("Unable to open camera")
        }
    }

    private fun copyUriToPrivateFile(uri: Uri): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "PROFILE_PICK_${timestamp}.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read selected image")

        return file
    }

    private fun applyProfileVisual(
        imagePath: String?,
        businessName: String,
        imageView: ImageView,
        initialView: TextView,
        colors: List<String> = listOf("#9575CD", "#4FC3F7", "#81C784", "#FFD54F", "#FF8A65", "#F06292")
    ) {
        if (!imagePath.isNullOrBlank()) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                imageView.setBackgroundResource(R.drawable.profile_circle_bg)
                imageView.backgroundTintList = null
                imageView.setImageURI(null)
                imageView.setImageURI(Uri.fromFile(imageFile))
                initialView.visibility = View.GONE
                return
            }
        }

        imageView.setImageDrawable(null)
        initialView.visibility = View.VISIBLE

        val name = businessName.ifBlank { "My Business" }
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "M"
        initialView.text = initial
        val colorStr = colors[Math.abs(name.hashCode()) % colors.size]
        imageView.setBackgroundResource(R.drawable.profile_circle_bg)
        imageView.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorStr))
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
            setBackgroundColor(Color.BLACK)
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
            imageTintList = ColorStateList.valueOf(Color.WHITE)
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
            setTextColor(Color.WHITE)
            textSize = 14f
            elevation = 0f
            contentDescription = "Edit photo"
            setOnClickListener {
                AlertDialog.Builder(this@BusinessProfileActivity)
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

    private fun saveProfileImagePath(path: String?) {
        prefs.edit().putString("profile_image_path", path).apply()
    }
}
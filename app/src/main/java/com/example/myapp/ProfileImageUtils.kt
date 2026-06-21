package com.example.myapp

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import kotlin.math.abs

object ProfileImageUtils {
    private val avatarColors = listOf(
        "#9575CD", "#4FC3F7", "#81C784", "#FFD54F", "#FF8A65", "#F06292", "#BA68C8", "#AED581"
    )

    fun applyProfileVisual(
        imagePath: String?,
        displayName: String,
        imageView: ImageView,
        initialView: TextView,
        emptyFallback: String = "?"
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

        val resolvedName = displayName.ifBlank { emptyFallback }
        val initial = resolvedName.firstOrNull()?.uppercaseChar()?.toString() ?: emptyFallback
        initialView.text = initial

        val color = avatarColors[abs(resolvedName.hashCode()) % avatarColors.size]
        imageView.setBackgroundResource(R.drawable.profile_circle_bg)
        imageView.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
    }
}
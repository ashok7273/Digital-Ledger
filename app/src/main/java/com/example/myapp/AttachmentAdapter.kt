package com.example.myapp

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

data class AttachmentItem(
    val path: String,
    val type: String, // "image", "document"
    val name: String
)

class AttachmentAdapter(
    private val attachments: MutableList<AttachmentItem>,
    private val onRemoveClick: (Int) -> Unit,
    private val onItemClick: (AttachmentItem) -> Unit
) : RecyclerView.Adapter<AttachmentAdapter.AttachmentViewHolder>() {

    class AttachmentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivAttachment)
        val fileName: TextView = view.findViewById(R.id.tvFileName)
        val removeButton: ImageButton = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attachment, parent, false)
        return AttachmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        val attachment = attachments[position]
        
        holder.fileName.text = attachment.name
        
        if (attachment.type == "image") {
            // Load image using BitmapFactory
            try {
                val bitmap = BitmapFactory.decodeFile(attachment.path)
                if (bitmap != null) {
                    holder.imageView.setImageBitmap(bitmap)
                } else {
                    holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } catch (e: Exception) {
                holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            // Show document icon
            holder.imageView.setImageResource(android.R.drawable.ic_menu_agenda)
        }
        
        holder.removeButton.setOnClickListener {
            onRemoveClick(position)
        }
        
        // Add click listener to open attachment
        holder.itemView.setOnClickListener {
            onItemClick(attachment)
        }
    }

    override fun getItemCount() = attachments.size

    fun addAttachment(attachment: AttachmentItem) {
        attachments.add(attachment)
        notifyItemInserted(attachments.size - 1)
    }

    fun removeAttachment(position: Int) {
        if (position >= 0 && position < attachments.size) {
            attachments.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getAttachments(): List<AttachmentItem> = attachments.toList()
}
package com.example.myapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val date: String,            // When entry was created/added
    val given: Double,
    val received: Double,
    val balance: Double,
    val note: String? = null,              // Optional note
    val modifiedDate: String? = null,      // When row was last modified (null for original)
    val isEditHistory: Boolean = false,    // true if this row is an edit log, not original txn
    val originalTxnId: Int? = null,        // If this is an edit/history, points to original txn
    val attachmentPath: String? = null,    // Path to attached file (optional)
    val attachmentType: String? = null     // Type of attachment: "image", "pdf", "document" (optional)
)